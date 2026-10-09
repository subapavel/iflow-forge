package io.poxray.ejb;

import io.poxray.IssueLog;
import io.poxray.model.BpmComponent;
import io.poxray.model.EjbModule;
import io.poxray.model.Function;
import io.poxray.model.Ref;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Links BPM catalog functions ({@code .function} lookup names) to EJB beans by (application, bean name),
 * and reports functions whose bean is not contained in the SCA.
 */
public final class BpmFunctionLinks {

    private record Link(String dc, Function function) {
    }

    private final Map<String, List<Link>> byBean = new LinkedHashMap<>();
    private final Set<String> matched = new HashSet<>();

    public BpmFunctionLinks(List<BpmComponent> bpm) {
        for (BpmComponent b : bpm) {
            for (Function f : b.catalog().functions()) {
                if (f.ejb() != null) {
                    byBean.computeIfAbsent(key(f.ejb().application(), f.ejb().bean()), k -> new ArrayList<>())
                            .add(new Link(b.dc(), f));
                }
            }
        }
    }

    /** Usages of a bean by BPM functions; reports interface mismatches. */
    List<EjbModule.Usage> usages(String application, String bean, EjbModule.Interfaces interfaces,
            String moduleDc, IssueLog issues) {
        String key = key(application, bean);
        List<EjbModule.Usage> out = new ArrayList<>();
        for (Link l : byBean.getOrDefault(key, List.of())) {
            matched.add(key);
            out.add(new EjbModule.Usage("bpmFunction", l.dc(), new Ref(l.function().id(), l.function().name())));
            String iface = l.function().ejb().iface();
            boolean declared = interfaces != null
                    && (interfaces.local().contains(iface) || interfaces.remote().contains(iface));
            if (iface != null && !declared) {
                issues.warn("EJB_INTERFACE_MISMATCH",
                        "Function '" + l.function().name() + "' looks up interface " + iface + " which bean '" + bean
                                + "' does not declare as business interface",
                        IssueLog.at(moduleDc, null, new Ref(l.function().id(), l.function().name()), null),
                        l.function().source());
            }
        }
        return out;
    }

    /** Functions whose bean was not found in any EJB module of the SCA. */
    public void reportMissing(IssueLog issues) {
        byBean.forEach((key, links) -> {
            if (!matched.contains(key)) {
                for (Link l : links) {
                    issues.warn("MISSING_SOURCE",
                            "Function '" + l.function().name() + "' is implemented by EJB " + l.function().lookupName()
                                    + " which is not contained in the SCA",
                            IssueLog.at(l.dc(), null, new Ref(l.function().id(), l.function().name()), null),
                            l.function().source());
                }
            }
        });
    }

    private static String key(String application, String bean) {
        return application + "|" + bean;
    }
}
