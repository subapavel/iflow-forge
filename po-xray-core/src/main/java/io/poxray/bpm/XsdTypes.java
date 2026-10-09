package io.poxray.bpm;

import io.poxray.model.DataType;
import io.poxray.moin.MoinElement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Type names and flattened field lists of XSD element declarations stored as MOIN XSD1 partitions. */
final class XsdTypes {

    private static final String UNBOUNDED_MOIN = "-1";
    private static final int MAX_FIELD_DEPTH = 20;

    private final BpmContext ctx;

    XsdTypes(BpmContext ctx) {
        this.ctx = ctx;
    }

    /** Type name of an element declaration: built-in, named type, or the base of an anonymous simple type. */
    String typeOfElement(MoinElement element) {
        return typeName(typeId(element));
    }

    String typeName(String typeId) {
        if (typeId == null) {
            return null;
        }
        String builtin = Builtins.TYPES.get(typeId);
        if (builtin != null) {
            return builtin;
        }
        MoinElement t = ctx.index.get(typeId).orElse(null);
        if (t == null) {
            return "unknown:" + typeId;
        }
        if (t.attr("name") != null) {
            return t.attr("name");
        }
        if (t.is(Galaxy.XSD_SIMPLE_TYPE)) {
            return typeName(ctx.index.second(Galaxy.SIMPLE_TYPE_BASE, typeId).orElse(null));
        }
        return null; // anonymous complex type: structure is in fields
    }

    DataType dataType(MoinElement element) {
        List<DataType.Field> fields = new ArrayList<>();
        collect(typeId(element), "", fields, new ArrayDeque<>(), 0);
        return new DataType(element.id(), element.attr("name"), element.attr("namespace"),
                typeOfElement(element), List.copyOf(fields));
    }

    private String typeId(MoinElement element) {
        return ctx.index.second(Galaxy.ELEMENT_TYPE, element.id())
                .or(() -> ctx.index.second(Galaxy.ELEMENT_ANONYMOUS_TYPE, element.id()))
                .orElse(null);
    }

    private void collect(String typeId, String prefix, List<DataType.Field> out, Deque<String> stack, int depth) {
        MoinElement type = typeId == null ? null : ctx.index.get(typeId).orElse(null);
        if (type == null || !type.is(Galaxy.XSD_COMPLEX_TYPE) || stack.contains(typeId) || depth > MAX_FIELD_DEPTH) {
            return;
        }
        stack.push(typeId);
        ctx.index.second(Galaxy.COMPLEX_TYPE_CONTENT, typeId)
                .ifPresent(particle -> particle(particle, prefix, out, stack, depth));
        stack.pop();
    }

    private void particle(String particleId, String prefix, List<DataType.Field> out, Deque<String> stack, int depth) {
        MoinElement particle = ctx.index.get(particleId).orElse(null);
        MoinElement content = ctx.index.secondElement(Galaxy.PARTICLE_CONTENT, particleId).orElse(null);
        if (particle == null || content == null) {
            return;
        }
        if (content.is(Galaxy.XSD_MODEL_GROUP)) {
            for (String p : ctx.index.seconds(Galaxy.MODEL_GROUP_PARTICLES, content.id())) {
                particle(p, prefix, out, stack, depth + 1);
            }
        } else if (content.is(Galaxy.XSD_ELEMENT)) {
            String path = prefix.isEmpty() ? content.attr("name") : prefix + "/" + content.attr("name");
            out.add(new DataType.Field(path, typeOfElement(content), min(particle.attr("minOccurs")),
                    max(particle.attr("maxOccurs"))));
            collect(typeId(content), path, out, stack, depth + 1);
        }
    }

    private static Integer min(String v) {
        return v == null || "1".equals(v) ? null : Integer.valueOf(v);
    }

    private static String max(String v) {
        if (v == null || "1".equals(v)) {
            return null;
        }
        return UNBOUNDED_MOIN.equals(v) ? "unbounded" : v;
    }
}
