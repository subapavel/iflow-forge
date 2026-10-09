package io.poxray.ejb;

import io.poxray.IssueLog;
import io.poxray.model.DcRef;
import io.poxray.model.EjbModule;
import io.poxray.sca.ScaArchive;
import io.poxray.sca.XmlDocs;
import io.poxray.sca.ZipArchive;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.w3c.dom.Element;

/**
 * Reads an EJB module DC from its source archive: beans and their roles, descriptors
 * ({@code ejb-j2ee-engine.xml}, {@code persistence.xml}, {@code job-definition.xml}) and JPA entities.
 * When the DC source archive has no Java sources, the sources deployed in the EAR ({@code src.zip}) are used.
 */
public final class EjbReader {

    public static final String EJB_MODULE = "EJBModule";
    public static final String ENTERPRISE_APPLICATION = "Enterprise Application";

    private static final String MODULE_INTERFACE = "com.sap.aii.af.lib.mp.module.Module";
    private static final String MDB_JOB = "MDBJobImplementation";
    private static final Pattern ACTIVATION_PROPERTY = Pattern.compile(
            "@ActivationConfigProperty\\s*\\(\\s*propertyName\\s*=\\s*\"([^\"]*)\"\\s*,\\s*propertyValue\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*\\)");
    private static final Pattern JOB_SELECTOR = Pattern.compile("JobDefinition\\s*=\\s*'([^']+)'");
    private static final Pattern PERSISTENCE_CONTEXT = Pattern.compile("@PersistenceContext\\s*\\(([^)]*)\\)");

    /** Import prefix → technology tag, in reporting order. */
    private static final Map<String, String> TECHNOLOGIES = new LinkedHashMap<>();

    static {
        TECHNOLOGIES.put("com.sap.conn.jco", "jco");
        TECHNOLOGIES.put("com.sap.mw.jco", "jco");
        TECHNOLOGIES.put("javax.persistence", "jpa");
        TECHNOLOGIES.put("java.sql", "jdbc");
        TECHNOLOGIES.put("javax.sql", "jdbc");
        TECHNOLOGIES.put("javax.jms", "jms");
        TECHNOLOGIES.put("javax.jws", "jaxws");
        TECHNOLOGIES.put("javax.xml.ws", "jaxws");
        TECHNOLOGIES.put("javax.xml.bind", "jaxb");
        TECHNOLOGIES.put("com.sap.engine.interfaces.messaging", "xiMessaging");
        TECHNOLOGIES.put("com.sap.aii.af", "adapterFramework");
        TECHNOLOGIES.put("com.sap.scheduler", "scheduler");
        TECHNOLOGIES.put("commonj.sdo", "sdo");
        TECHNOLOGIES.put("com.sap.glx.sdo", "sdo");
        TECHNOLOGIES.put("java.net.HttpURLConnection", "http");
        TECHNOLOGIES.put("java.net.http", "http");
        TECHNOLOGIES.put("org.apache.http", "http");
        TECHNOLOGIES.put("javax.mail", "mail");
        TECHNOLOGIES.put("org.mvel2", "mvel");
    }

    private EjbReader() {
    }

    public static boolean isEjbModule(ScaArchive.DcSource dc) {
        return EJB_MODULE.equals(dc.definition().type());
    }

    public static EjbModule read(ScaArchive sca, ScaArchive.DcSource dc, List<ScaArchive.DcSource> all,
            BpmFunctionLinks links, IssueLog issues) {
        String dcName = dc.definition().fullName();
        ScaArchive.DcSource ear = earOf(dc, all);
        String application = ear == null ? null
                : ear.definition().vendor() + "/" + ear.definition().name().replace('/', '~');

        // ---- sources: DC source archive, deployed EAR sources as fallback / cross-check
        Map<String, Source> sources = new TreeMap<>();
        for (var e : dc.sources().entries().entrySet()) {
            if (e.getKey().endsWith(".java")) {
                sources.put(e.getKey(), new Source(dc.sourceEntry() + "!/" + e.getKey(), e.getValue()));
            }
        }
        Map<String, Source> deployed = deployedSources(sca, dc, ear);
        if (sources.isEmpty() && !deployed.isEmpty()) {
            sources.putAll(deployed);
            issues.info("SOURCE_FROM_DEPLOYMENT", "No sources in the DC source archive, using sources deployed in the EAR",
                    IssueLog.at(dcName, null, null, null), null);
        } else if (!deployed.isEmpty()) {
            compareDeployed(sources, deployed, dcName, issues);
        }

        // ---- descriptors
        Map<String, String> jndiNames = new LinkedHashMap<>();
        Map<String, Map<String, String>> beanProps = new LinkedHashMap<>();
        descriptor(dc, "META-INF/ejb-j2ee-engine.xml").ifPresent(d -> engineDescriptor(d, jndiNames, beanProps));
        List<EjbModule.PersistenceUnit> units = descriptor(dc, "META-INF/persistence.xml")
                .map(EjbReader::persistenceUnits).orElse(List.of());
        List<EjbModule.JobDefinition> jobs = descriptor(dc, "META-INF/job-definition.xml")
                .map(EjbReader::jobDefinitions).orElse(List.of());
        descriptor(dc, "META-INF/ejb-jar.xml").ifPresent(d -> issues.info("UNSUPPORTED_DESCRIPTOR",
                "ejb-jar.xml is not interpreted; beans declared only there (EJB 2.x) are missing",
                IssueLog.at(dcName, null, null, null), dc.sourceEntry()));

        // ---- parse
        Map<String, JavaSource> types = new LinkedHashMap<>();
        Map<String, Source> sourceOf = new LinkedHashMap<>();
        for (Source s : sources.values()) {
            JavaSource js = JavaSource.parse(s.path(), new String(s.content(), StandardCharsets.UTF_8));
            if (js.isType()) {
                types.put(js.qualifiedName(), js);
                sourceOf.put(js.qualifiedName(), s);
            }
        }

        // ---- beans: first interfaces (needed to resolve @EJB injections), then the rest
        Map<String, EjbModule.Interfaces> beanInterfaces = new LinkedHashMap<>();
        Map<String, String> beanNameByType = new LinkedHashMap<>();
        for (JavaSource js : types.values()) {
            if (beanType(js) != null) {
                EjbModule.Interfaces ifs = interfaces(js, types);
                beanInterfaces.put(js.qualifiedName(), ifs);
                String name = beanName(js);
                beanNameByType.put(js.qualifiedName(), name);
                ifs.local().forEach(i -> beanNameByType.putIfAbsent(i, name));
                ifs.remote().forEach(i -> beanNameByType.putIfAbsent(i, name));
            }
        }

        List<EjbModule.Bean> beans = new ArrayList<>();
        List<EjbModule.JavaClass> classes = new ArrayList<>();
        List<EjbModule.JpaEntity> entities = new ArrayList<>();
        Set<String> moduleTechnologies = new LinkedHashSet<>();
        for (JavaSource js : types.values()) {
            List<String> tech = technologies(js);
            moduleTechnologies.addAll(tech);
            EjbModule.SourceRef src = sourceRef(sourceOf.get(js.qualifiedName()));
            String type = beanType(js);
            if (js.hasAnnotation("Entity")) {
                entities.add(new EjbModule.JpaEntity(js.qualifiedName(),
                        js.annotation("Table").flatMap(a -> a.attr("name")).orElse(null)));
            }
            if (type == null) {
                classes.add(new EjbModule.JavaClass(js.qualifiedName(), js.kind, src));
                continue;
            }
            beans.add(bean(js, type, tech, src, beanInterfaces.get(js.qualifiedName()), beanNameByType, jndiNames,
                    beanProps, jobs, application, links, dcName, issues));
        }

        return new EjbModule(dcName, ear == null ? null : ear.definition().fullName(), application, beans, classes,
                entities, units, jobs, List.copyOf(moduleTechnologies));
    }

    // ------------------------------------------------------------------------------------------ beans

    private static EjbModule.Bean bean(JavaSource js, String type, List<String> tech, EjbModule.SourceRef src,
            EjbModule.Interfaces ifs, Map<String, String> beanNameByType, Map<String, String> jndiNames,
            Map<String, Map<String, String>> beanProps, List<EjbModule.JobDefinition> jobs, String application,
            BpmFunctionLinks links, String dcName, IssueLog issues) {
        String name = beanName(js);
        List<EjbModule.Usage> usedBy = links.usages(application, name, ifs, dcName, issues);

        List<String> roles = new ArrayList<>();
        if (js.contains("\\binvokeSdo\\s*\\(") || !usedBy.isEmpty()) {
            roles.add("bpmFunction");
        }
        boolean module = js.interfaces.stream().map(js::resolve).anyMatch(MODULE_INTERFACE::equals);
        if (module) {
            roles.add("adapterModule");
        }
        EjbModule.WebService ws = null;
        if (js.hasAnnotation("WebService")) {
            JavaSource.Annotation a = js.annotation("WebService").orElseThrow();
            ws = new EjbModule.WebService(a.attr("serviceName").orElse(null), a.attr("portName").orElse(null),
                    a.attr("endpointInterface").orElse(null), a.attr("targetNamespace").orElse(null),
                    a.attr("wsdlLocation").orElse(null));
            roles.add(js.hasAnnotation("XIEnabled") ? "xiProxyProvider" : "webService");
        }
        boolean job = js.superclass != null && js.superclass.endsWith(MDB_JOB);
        if (job) {
            roles.add("schedulerJob");
        }
        if (roles.isEmpty()) {
            roles.add("other");
        }

        Map<String, String> activation = new LinkedHashMap<>();
        js.annotation("MessageDriven").map(JavaSource.Annotation::args).ifPresent(args -> {
            Matcher m = ACTIVATION_PROPERTY.matcher(args);
            while (m.find()) {
                activation.put(m.group(1), m.group(2));
            }
        });
        EjbModule.JobDefinition jobDef = null;
        if (job) {
            String selector = activation.getOrDefault("messageSelector", "");
            Matcher m = JOB_SELECTOR.matcher(selector);
            String jobName = m.find() ? m.group(1) : null;
            jobDef = jobs.stream().filter(j -> j.name().equals(jobName)).findFirst()
                    .orElse(jobName == null ? null : new EjbModule.JobDefinition(jobName, null));
        }

        boolean jco = tech.contains("jco");
        List<String> injects = js.injectedEjbs().stream()
                .map(t -> beanNameByType.getOrDefault(js.resolve(t), t)).distinct().toList();
        String unit = null;
        Matcher pc = PERSISTENCE_CONTEXT.matcher(js.text);
        if (pc.find()) {
            JavaSource.Annotation a = new JavaSource.Annotation("PersistenceContext", pc.group(1));
            unit = a.attr("unitName").or(() -> a.attr("name")).orElse(null);
        }

        boolean noInterfaces = ifs.local().isEmpty() && ifs.remote().isEmpty();
        return new EjbModule.Bean(name, js.qualifiedName(), type, jndiNames.get(name), noInterfaces ? null : ifs,
                roles, tech,
                module ? js.callArguments("getContextData") : null,
                ws, jobDef, activation, beanProps.get(name),
                jco ? js.callArguments("getFunction") : null,
                jco ? js.callArguments("getDestination") : null,
                injects, unit, usedBy, src);
    }

    private static String beanType(JavaSource js) {
        if (js.hasAnnotation("Stateless")) {
            return "stateless";
        }
        if (js.hasAnnotation("Stateful")) {
            return "stateful";
        }
        if (js.hasAnnotation("Singleton")) {
            return "singleton";
        }
        if (js.hasAnnotation("MessageDriven")) {
            return "messageDriven";
        }
        return null;
    }

    /** {@code name} attribute of the bean annotation, else the simple class name (EJB 3 default). */
    private static String beanName(JavaSource js) {
        for (String a : List.of("Stateless", "Stateful", "Singleton", "MessageDriven")) {
            Optional<String> n = js.annotation(a).flatMap(x -> x.attr("name"));
            if (n.isPresent()) {
                return n.get();
            }
        }
        return js.simpleName;
    }

    /**
     * Business interfaces: {@code @Local}/{@code @Remote} on the bean (class literals) plus implemented interfaces
     * annotated {@code @Local}/{@code @Remote} in their own source; if none is declared, a single implemented
     * interface is local (EJB 3 default).
     */
    private static EjbModule.Interfaces interfaces(JavaSource bean, Map<String, JavaSource> types) {
        Set<String> local = new LinkedHashSet<>();
        Set<String> remote = new LinkedHashSet<>();
        bean.annotation("Local").ifPresent(a -> a.classes().forEach(c -> local.add(bean.resolve(c))));
        bean.annotation("Remote").ifPresent(a -> a.classes().forEach(c -> remote.add(bean.resolve(c))));
        List<String> implemented = bean.interfaces.stream().map(bean::resolve)
                .filter(i -> !i.startsWith("java.") && !i.startsWith("javax.ejb.")).toList();
        for (String i : implemented) {
            JavaSource is = types.get(i);
            if (is != null && is.hasAnnotation("Local")) {
                local.add(i);
            } else if (is != null && is.hasAnnotation("Remote")) {
                remote.add(i);
            }
        }
        if (local.isEmpty() && remote.isEmpty() && implemented.size() == 1 && !implemented.contains(MODULE_INTERFACE)) {
            local.add(implemented.getFirst());
        }
        return new EjbModule.Interfaces(List.copyOf(local), List.copyOf(remote));
    }

    private static List<String> technologies(JavaSource js) {
        Set<String> out = new LinkedHashSet<>();
        TECHNOLOGIES.forEach((prefix, tag) -> {
            if (js.imports(prefix)) {
                out.add(tag);
            }
        });
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------ EAR

    /** EAR DC whose .dcdef depends on the EJB module DC. */
    private static ScaArchive.DcSource earOf(ScaArchive.DcSource dc, List<ScaArchive.DcSource> all) {
        DcRef self = new DcRef(dc.definition().vendor(), dc.definition().name());
        return all.stream()
                .filter(d -> ENTERPRISE_APPLICATION.equals(d.definition().type()))
                .filter(d -> d.definition().dependencies().contains(self))
                .findFirst().orElse(null);
    }

    /**
     * Sources deployed in the EAR: {@code src.zip} → {@code <vendor>~<name~>~ejbjar_srcarchive.zip} → Java files.
     * Keys are paths relative to the source folder (e.g. {@code com/x/Y.java}).
     */
    private static Map<String, Source> deployedSources(ScaArchive sca, ScaArchive.DcSource dc, ScaArchive.DcSource ear) {
        Map<String, Source> out = new TreeMap<>();
        if (ear == null || ear.deployEntry() == null) {
            return out;
        }
        ZipArchive earZip = sca.zip().get(ear.deployEntry()).map(b -> ZipArchive.of(b, ear.deployEntry())).orElse(null);
        byte[] srcZip = earZip == null ? null : earZip.get("src.zip").orElse(null);
        if (srcZip == null) {
            return out;
        }
        String prefix = dc.definition().vendor() + "~" + dc.definition().name().replace('/', '~') + "~";
        for (var e : ZipArchive.of(srcZip, "src.zip").entries().entrySet()) {
            if (e.getKey().startsWith(prefix) && e.getKey().endsWith("_srcarchive.zip")) {
                String base = ear.deployEntry() + "!/src.zip!/" + e.getKey() + "!/";
                for (var j : ZipArchive.of(e.getValue(), e.getKey()).entries().entrySet()) {
                    if (j.getKey().endsWith(".java")) {
                        out.put(j.getKey(), new Source(base + j.getKey(), j.getValue()));
                    }
                }
            }
        }
        return out;
    }

    /** Reports deployed sources that differ from (or are missing in) the DC source archive. */
    private static void compareDeployed(Map<String, Source> sources, Map<String, Source> deployed, String dc,
            IssueLog issues) {
        List<String> differing = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (var d : deployed.entrySet()) {
            String rel = d.getKey();
            Map.Entry<String, Source> match = sources.entrySet().stream()
                    .filter(e -> e.getKey().equals(rel) || e.getKey().endsWith("/" + rel)).findFirst().orElse(null);
            if (match == null) {
                differing.add(rel + " (only deployed)");
            } else {
                seen.add(match.getKey());
                if (!normalize(match.getValue().content()).equals(normalize(d.getValue().content()))) {
                    differing.add(rel);
                }
            }
        }
        for (String path : sources.keySet()) {
            if (!seen.contains(path)) {
                differing.add(path + " (not deployed)");
            }
        }
        if (!differing.isEmpty()) {
            issues.warn("DEPLOYED_SOURCE_DIFFERS",
                    "Sources deployed in the EAR differ from the DC source archive: " + String.join(", ", differing),
                    IssueLog.at(dc, null, null, null), null);
        }
    }

    private static String normalize(byte[] content) {
        return new String(content, StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
    }

    // ------------------------------------------------------------------------------------------ descriptors

    private static Optional<Element> descriptor(ScaArchive.DcSource dc, String suffix) {
        return dc.sources().entries().entrySet().stream()
                .filter(e -> e.getKey().endsWith(suffix))
                .findFirst()
                .map(e -> XmlDocs.parse(e.getValue(), dc.sourceEntry() + "!/" + e.getKey()).getDocumentElement());
    }

    private static void engineDescriptor(Element root, Map<String, String> jndi, Map<String, Map<String, String>> props) {
        Element beans = XmlDocs.child(root, "enterprise-beans");
        if (beans == null) {
            return;
        }
        for (Element b : XmlDocs.children(beans, "enterprise-bean")) {
            String name = XmlDocs.childText(b, "ejb-name");
            String jndiName = XmlDocs.childText(b, "jndi-name");
            if (name != null && jndiName != null) {
                jndi.put(name, jndiName);
            }
            Element bp = XmlDocs.child(b, "bean-props");
            if (name != null && bp != null) {
                Map<String, String> m = new LinkedHashMap<>();
                for (Element p : XmlDocs.children(bp, "property")) {
                    m.put(XmlDocs.childText(p, "property-name"), XmlDocs.childText(p, "property-value"));
                }
                props.put(name, m);
            }
        }
    }

    private static List<EjbModule.PersistenceUnit> persistenceUnits(Element root) {
        List<EjbModule.PersistenceUnit> out = new ArrayList<>();
        for (Element u : XmlDocs.children(root, "persistence-unit")) {
            List<String> classes = XmlDocs.children(u, "class").stream().map(c -> c.getTextContent().trim()).toList();
            String ds = XmlDocs.childText(u, "jta-data-source");
            out.add(new EjbModule.PersistenceUnit(u.getAttribute("name"),
                    ds != null ? ds : XmlDocs.childText(u, "non-jta-data-source"), classes));
        }
        return out;
    }

    private static List<EjbModule.JobDefinition> jobDefinitions(Element root) {
        List<EjbModule.JobDefinition> out = new ArrayList<>();
        for (Element j : XmlDocs.children(root, "job-definition")) {
            String description = j.getAttribute("description");
            out.add(new EjbModule.JobDefinition(j.getAttribute("name"), description.isEmpty() ? null : description));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------ helpers

    private record Source(String path, byte[] content) {
    }

    private static EjbModule.SourceRef sourceRef(Source s) {
        return s == null ? null : new EjbModule.SourceRef(s.path(), sha256(s.content()));
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
