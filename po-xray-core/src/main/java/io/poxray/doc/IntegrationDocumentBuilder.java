package io.poxray.doc;

import static io.poxray.doc.Doc.code;
import static io.poxray.doc.Doc.line;
import static io.poxray.doc.Doc.text;

import io.poxray.integration.XsdFields;
import io.poxray.model.DataType;
import io.poxray.model.IntegrationInventory;
import io.poxray.model.IntegrationInventory.Channel;
import io.poxray.model.IntegrationInventory.DirRef;
import io.poxray.model.IntegrationInventory.FieldMapping;
import io.poxray.model.IntegrationInventory.FunctionLibrary;
import io.poxray.model.IntegrationInventory.Ico;
import io.poxray.model.IntegrationInventory.InterfaceAssignment;
import io.poxray.model.IntegrationInventory.MappingLogic;
import io.poxray.model.IntegrationInventory.ObjRef;
import io.poxray.model.IntegrationInventory.Receiver;
import io.poxray.model.IntegrationInventory.UserFunction;
import io.poxray.model.Issue;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Human-readable documentation of an {@link IntegrationInventory}: overview of the ICOs, per ICO sender, receivers,
 * interface determinations and mappings, channels with adapter and modules, the ESR objects (interfaces, operation
 * and message mappings, message types, data types with their fields) and issues.
 */
public final class IntegrationDocumentBuilder {

    /** Channel attributes shown in the document (all attributes are in the JSON). */
    private static final Pattern KEY_ATTRIBUTE = Pattern.compile(
            "(?i).*(url|host|port|path|address|endpoint|destination|queue|directory|filename|server|service|user|"
                    + "authentication|method|contenttype|program|client|system).*");

    private final Labels l;
    private final List<Doc.Block> out = new ArrayList<>();

    private IntegrationDocumentBuilder(Locale locale) {
        this.l = new Labels(locale);
    }

    public static List<Doc.Block> build(IntegrationInventory inventory, Locale locale) {
        IntegrationDocumentBuilder b = new IntegrationDocumentBuilder(locale);
        b.inventory(inventory);
        return List.copyOf(b.out);
    }

    private void inventory(IntegrationInventory inv) {
        heading(1, l.get("int.title", inv.query().component()));
        bullets(text(l.get("int.system") + ": " + nz(inv.source().system()) + " (" + nz(inv.source().url()) + ")"),
                text(l.get("int.query", inv.query().component(), l.get("int.direction." + inv.query().direction()))),
                text(l.get("sca.generated", inv.generator().name(), inv.generator().version(), inv.generator().readAt())),
                text(l.get("int.counts", inv.icos().size(), inv.channels().size(), inv.esr().serviceInterfaces().size(),
                        inv.esr().operationMappings().size(), inv.esr().messageMappings().size(),
                        inv.esr().dataTypes().size(), inv.issues().size())));
        if (!inv.components().isEmpty()) {
            bullets(text(l.get("int.components") + ": "
                    + inv.components().stream().map(IntegrationInventory.Component::name).collect(Collectors.joining(", "))));
        }

        heading(2, l.get("int.overview"));
        Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.sender"), l.get("col.interface"), l.get("col.receivers"),
                l.get("col.mapping"));
        for (Ico ico : inv.icos()) {
            t.row(text(ico.sender().component()), code(name(ico.sender().iface())),
                    text(ico.receivers().stream().map(this::receiverSummary).collect(Collectors.joining("; "))),
                    code(ico.receivers().stream().flatMap(r -> r.interfaces().stream())
                            .map(a -> name(a.mapping())).filter(s -> !s.isEmpty()).distinct().collect(Collectors.joining(", "))));
        }
        table(t);

        heading(2, l.get("int.icos"));
        for (Ico ico : inv.icos()) {
            ico(ico);
        }
        channels(inv.channels());
        esr(inv.esr());
        issues(inv.issues());
    }

    private String receiverSummary(Receiver r) {
        String ifaces = r.interfaces().stream().flatMap(a -> a.targets().stream()).map(tg -> name(tg.iface()))
                .distinct().collect(Collectors.joining(", "));
        return nz(r.component()) + (ifaces.isEmpty() ? "" : " (" + ifaces + ")")
                + (r.condition() == null ? "" : " [" + l.get("int.conditional") + "]");
    }

    private void ico(Ico ico) {
        heading(3, ico.sender().component() + " | " + name(ico.sender().iface()));
        if (ico.description() != null && ico.generatedFrom() == null) {
            paragraph(ico.description());
        }
        List<List<Doc.Inline>> facts = new ArrayList<>();
        facts.add(line(new Doc.Text(l.get("int.sender") + ": " + nz(ico.sender().component()) + ", "),
                new Doc.Code(name(ico.sender().iface())), new Doc.Text(" (" + nz(ns(ico.sender().iface())) + ")")));
        if (!ico.sender().operations().isEmpty()) {
            facts.add(text(l.get("int.operations") + ": " + String.join(", ", ico.sender().operations())));
        }
        if (ico.sender().channel() != null) {
            facts.add(line(new Doc.Text(l.get("int.senderChannel") + ": "), new Doc.Code(dir(ico.sender().channel()))));
        }
        if (ico.generatedFrom() != null) {
            facts.add(text(l.get("int.generatedFrom") + ": " + ico.generatedFrom()));
        }
        if (ico.dynamicRouting() != null) {
            facts.add(line(new Doc.Text(l.get("int.dynamicRouting") + ": "), new Doc.Code(ico.dynamicRouting()
                    .mappings().stream().map(IntegrationDocumentBuilder::name).collect(Collectors.joining(", ")))));
        }
        if (ico.noReceiverBehaviour() != null) {
            facts.add(text(l.get("int.noReceiver") + ": " + ico.noReceiverBehaviour()));
        }
        bulletList(facts);
        for (Receiver r : ico.receivers()) {
            List<Doc.Inline> header = new ArrayList<>();
            header.add(new Doc.Strong(l.get("int.receiver") + ": " + nz(r.component())));
            if (r.condition() != null) {
                header.add(new Doc.Text(" — " + l.get("int.when") + " "));
                header.add(new Doc.Code(r.condition().text()));
            }
            if (r.qualityOfService() != null) {
                header.add(new Doc.Text(" (QoS " + r.qualityOfService() + ")"));
            }
            paragraph(header);
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("int.operation"), l.get("col.condition"), l.get("col.mapping"),
                    l.get("col.targetInterfaces"), l.get("col.parameters"));
            for (InterfaceAssignment a : r.interfaces()) {
                t.row(text(a.operation()), code(a.condition() == null ? null : a.condition().text()), code(name(a.mapping())),
                        code(a.targets().stream().map(tg -> name(tg.iface())
                                + (tg.multiplicity() == null ? "" : " [" + tg.multiplicity() + "]")).collect(Collectors.joining(", "))),
                        code(a.parameters().entrySet().stream().map(e -> e.getKey() + " = " + dir(e.getValue()))
                                .collect(Collectors.joining(", "))));
            }
            table(t);
            if (!r.channels().isEmpty()) {
                bullets(line(new Doc.Text(l.get("int.receiverChannels") + ": "),
                        new Doc.Code(r.channels().stream().map(IntegrationDocumentBuilder::dir).collect(Collectors.joining(", ")))));
            }
        }
    }

    private void channels(List<Channel> channels) {
        if (channels.isEmpty()) {
            return;
        }
        heading(2, l.get("int.channels"));
        Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.channel"), l.get("col.direction"), l.get("col.adapter"),
                l.get("col.modules"), l.get("col.keyAttributes"));
        for (Channel c : channels) {
            t.row(code(dir(c.id())), text(c.direction()), text(c.adapter()),
                    code(c.modules().stream().map(m -> m.name() + (m.parameters().isEmpty() ? "" : " " + m.parameters()))
                            .collect(Collectors.joining("; "))),
                    code(keyAttributes(c.attributes())));
        }
        table(t);
    }

    private static String keyAttributes(Map<String, String> attributes) {
        return attributes.entrySet().stream()
                .filter(e -> KEY_ATTRIBUTE.matcher(e.getKey()).matches() && !e.getValue().isBlank()
                        && !"0".equals(e.getValue()))
                .map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
    }

    private void esr(IntegrationInventory.Esr esr) {
        heading(2, l.get("int.esr"));
        if (!esr.serviceInterfaces().isEmpty()) {
            heading(3, l.get("int.serviceInterfaces"));
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.interface"), l.get("col.swc"), l.get("col.category"),
                    l.get("int.operations"));
            for (var si : esr.serviceInterfaces()) {
                t.row(code(name(si.id()) + " | " + nz(ns(si.id()))), text(si.id().swc()), text(si.category()),
                        text(si.operations().stream().map(op -> op.name() + " (" + nz(op.mode()) + "): "
                                + name(op.request()) + (op.response() == null ? "" : " → " + name(op.response())))
                                .collect(Collectors.joining("; "))));
            }
            table(t);
        }
        if (!esr.operationMappings().isEmpty()) {
            heading(3, l.get("int.operationMappings"));
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.mapping"), l.get("int.from"), l.get("int.to"),
                    l.get("int.steps"));
            for (var om : esr.operationMappings()) {
                t.row(code(name(om.id())), code(name(om.sourceOperation())), code(name(om.targetOperation())),
                        text(om.steps().stream().map(s -> s.kind() + ": " + name(s.program())
                                + (s.program() == null || s.program().program() == null ? ""
                                        : " / " + s.program().program())
                                + (s.program() == null || "XI_TRAFO".equals(s.program().type()) ? "" : " (" + s.program().type() + ")"))
                                .collect(Collectors.joining("; "))));
            }
            table(t);
        }
        if (!esr.messageMappings().isEmpty()) {
            heading(3, l.get("int.messageMappings"));
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.mapping"), l.get("col.source"), l.get("col.target"),
                    l.get("col.multiplicity"));
            for (var mm : esr.messageMappings()) {
                t.row(code(name(mm.id())), code(name(mm.source())), code(name(mm.target())), text(mm.multiplicity()));
            }
            table(t);
            for (var mm : esr.messageMappings()) {
                if (mm.logic() != null) {
                    mappingLogic(mm);
                }
            }
        }
        if (!esr.mappingPrograms().isEmpty()) {
            bullets(text(l.get("int.mappingPrograms") + ": " + esr.mappingPrograms().stream()
                    .map(p -> p.type() + " " + name(p)).collect(Collectors.joining(", "))));
        }
        if (!esr.functionLibraries().isEmpty()) {
            heading(3, l.get("int.functionLibraries"));
            for (FunctionLibrary lib : esr.functionLibraries()) {
                heading(4, name(lib.id()) + " | " + nz(ns(lib.id())) + " (" + nz(lib.id().swc()) + ")");
                paragraph(lib.description());
                functionStorage(lib);
            }
        }
        if (!esr.importedArchives().isEmpty()) {
            heading(3, l.get("int.importedArchives"));
            for (var a : esr.importedArchives()) {
                heading(4, name(a.id()) + " | " + nz(ns(a.id())) + (a.fileName() == null ? "" : " (" + a.fileName() + ")"));
                paragraph(a.description());
                List<List<Doc.Inline>> facts = new ArrayList<>();
                if (a.maven() != null) {
                    facts.add(line(new Doc.Text("Maven: "), new Doc.Code(a.maven())));
                }
                if (!a.sapApis().isEmpty()) {
                    facts.add(text(l.get("int.sapApis") + ": " + String.join(", ", a.sapApis())));
                }
                if (!a.packages().isEmpty()) {
                    facts.add(text(l.get("int.packages") + ": " + a.packages().entrySet().stream()
                            .map(e -> e.getKey() + " (" + e.getValue() + ")").collect(Collectors.joining(", "))));
                }
                if (!facts.isEmpty()) {
                    bulletList(facts);
                }
                Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.program"), l.get("col.kind"),
                        l.get("col.mappingApi"), l.get("int.sapApis"));
                for (var p : a.programs()) {
                    t.row(code(p.className() == null ? p.path() : p.className()), text(p.kind()),
                            text(p.mappingApi()), text(String.join(", ", p.sapApis())));
                }
                table(t);
                for (var p : a.programs()) {
                    if (p.text() != null) {
                        paragraph(line(new Doc.Strong(p.path())));
                        out.add(new Doc.CodeBlock("xslt".equals(p.kind()) ? "xml" : "java", p.text()));
                    }
                }
            }
        }
        if (!esr.messageTypes().isEmpty()) {
            heading(3, l.get("int.messageTypes"));
            Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.messageType"), l.get("col.dataType"),
                    l.get("col.xmlNamespace"));
            for (var mt : esr.messageTypes()) {
                t.row(code(name(mt.id())), code(name(mt.dataType())), code(mt.xmlNamespace()));
            }
            table(t);
        }
        if (!esr.externalDefinitions().isEmpty()) {
            bullets(text(l.get("int.externalDefinitions") + ": " + esr.externalDefinitions().stream()
                    .map(d -> name(d.id()) + " (" + String.join(", ", d.messages()) + ")").collect(Collectors.joining("; "))));
        }
        if (!esr.dataTypes().isEmpty()) {
            heading(3, l.get("int.dataTypes"));
            for (var dt : esr.dataTypes()) {
                heading(4, name(dt.id()) + " | " + nz(ns(dt.id())));
                paragraph(dt.description());
                List<DataType.Field> fields = XsdFields.fields(dt.xsd(), name(dt.id()));
                Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.field"), l.get("col.type"), l.get("col.cardinality"));
                for (DataType.Field f : fields) {
                    t.row(code(f.path()), code(f.type()),
                            text((f.min() == null ? "1" : f.min().toString()) + ".." + (f.max() == null ? "1" : f.max())));
                }
                table(t);
            }
        }
    }

    /** Field logic of one message mapping: parameters, libraries, field table, disabled fields, local functions. */
    private void mappingLogic(IntegrationInventory.MessageMapping mm) {
        MappingLogic logic = mm.logic();
        heading(4, name(mm.id()));
        List<List<Doc.Inline>> facts = new ArrayList<>();
        if (!logic.parameters().isEmpty()) {
            facts.add(line(new Doc.Text(l.get("int.parameters") + ": "), new Doc.Code(logic.parameters().stream()
                    .map(p -> p.name() + " (" + p.type() + ", " + p.direction() + ")").collect(Collectors.joining(", ")))));
        }
        if (!logic.libraries().isEmpty()) {
            facts.add(line(new Doc.Text(l.get("int.libraries") + ": "), new Doc.Code(logic.libraries().stream()
                    .map(r -> r.name() + " | " + r.namespace()).collect(Collectors.joining(", ")))));
        }
        if (!logic.disabledFields().isEmpty()) {
            facts.add(line(new Doc.Text(l.get("int.disabledFields") + ": "),
                    new Doc.Code(String.join(", ", logic.disabledFields()))));
        }
        if (!facts.isEmpty()) {
            bulletList(facts);
        }
        Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.target"), l.get("col.logic"));
        for (FieldMapping f : logic.fields()) {
            t.row(code(f.target()), code(f.expression()));
        }
        table(t);
        if (logic.localFunctions() != null) {
            paragraph(line(new Doc.Strong(l.get("int.localFunctions"))));
            functionStorage(logic.localFunctions());
        }
    }

    /** Imports, archives, global code and the user-defined functions of a library or of a mapping. */
    private void functionStorage(FunctionLibrary lib) {
        List<List<Doc.Inline>> facts = new ArrayList<>();
        if (!lib.archives().isEmpty()) {
            facts.add(line(new Doc.Text(l.get("int.archives") + ": "), new Doc.Code(lib.archives().stream()
                    .map(r -> r.name() + " | " + r.namespace()).collect(Collectors.joining(", ")))));
        }
        if (!lib.imports().isEmpty()) {
            facts.add(line(new Doc.Text(l.get("int.imports") + ": "), new Doc.Code(lib.imports().stream()
                    .map(i -> i.replaceFirst("^import\\s+", "").replaceFirst(";$", ""))
                    .collect(Collectors.joining(", ")))));
        }
        if (!lib.sapApis().isEmpty()) {
            facts.add(text(l.get("int.sapApis") + ": " + String.join(", ", lib.sapApis())));
        }
        if (!facts.isEmpty()) {
            bulletList(facts);
        }
        javaCode(l.get("int.globals"), lib.globals());
        javaCode(l.get("int.init"), lib.init());
        javaCode(l.get("int.cleanup"), lib.cleanup());
        for (UserFunction f : lib.functions()) {
            paragraph(line(new Doc.Strong(l.get("int.function", f.name())),
                    new Doc.Text((f.description() == null ? "" : " – " + f.description()) + " ("
                            + l.get("int.execution." + f.execution()) + ")")));
            List<List<Doc.Inline>> items = new ArrayList<>();
            for (var a : f.arguments()) {
                items.add(line(new Doc.Code(a.name() + ": " + a.javaType()), new Doc.Text(" " + l.get("arg." + a.kind())
                        + (a.description() == null ? "" : " – " + a.description()))));
            }
            if (!f.sapApis().isEmpty()) {
                items.add(text(l.get("int.sapApis") + ": " + String.join(", ", f.sapApis())));
            }
            if (!items.isEmpty()) {
                bulletList(items);
            }
            if (f.code() != null) {
                out.add(new Doc.CodeBlock("java", f.code()));
            }
        }
    }

    private void javaCode(String caption, String code) {
        if (code != null) {
            paragraph(line(new Doc.Strong(caption)));
            out.add(new Doc.CodeBlock("java", code));
        }
    }

    private void issues(List<Issue> issues) {
        heading(2, l.get("issues.title"));
        if (issues.isEmpty()) {
            paragraph(l.get("issues.none"));
            return;
        }
        Doc.TableBuilder t = new Doc.TableBuilder(l.get("col.severity"), l.get("col.code"), l.get("col.description"));
        for (Issue i : issues) {
            t.row(text(l.get("severity." + i.severity())), code(i.code()), text(i.message()));
        }
        table(t);
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static String name(ObjRef r) {
        if (r == null || r.name() == null) {
            return "";
        }
        return "ifmoper".equals(r.type()) ? r.name().replace('.', '/') : r.name();
    }

    private static String ns(ObjRef r) {
        return r == null ? null : r.namespace();
    }

    private static String dir(DirRef d) {
        if (d == null) {
            return "";
        }
        return (d.party() == null ? "" : d.party() + " | ") + nz(d.component()) + " | " + nz(d.name());
    }

    private void heading(int level, String text) {
        out.add(new Doc.Heading(level, text));
    }

    private void paragraph(String text) {
        if (text != null && !text.isBlank()) {
            out.add(new Doc.Paragraph(List.of(new Doc.Text(text))));
        }
    }

    private void paragraph(List<Doc.Inline> content) {
        out.add(new Doc.Paragraph(content));
    }

    @SafeVarargs
    private void bullets(List<Doc.Inline>... items) {
        List<List<Doc.Inline>> list = new ArrayList<>(items.length);
        for (List<Doc.Inline> item : items) {
            list.add(item);
        }
        bulletList(list);
    }

    private void bulletList(List<List<Doc.Inline>> items) {
        out.add(new Doc.Bullets(List.copyOf(items)));
    }

    private void table(Doc.TableBuilder t) {
        if (!t.isEmpty()) {
            out.add(t.build());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
