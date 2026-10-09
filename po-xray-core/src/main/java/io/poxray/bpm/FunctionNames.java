package io.poxray.bpm;

import io.poxray.model.Issue;
import io.poxray.moin.MoinElement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves function interface IDs to names: custom functions from the DC, SAP built-ins from {@link Builtins},
 * and built-ins learned from {@code stringExpression}: when an expression text contains exactly as many
 * {@code name(} calls as the step tree has FunctionInvocations, they pair up in pre-order.
 */
final class FunctionNames {

    private static final Pattern CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_.\\-]*)\\s*\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");

    private final BpmContext ctx;
    private final Map<String, String> learned = new HashMap<>();

    FunctionNames(BpmContext ctx) {
        this.ctx = ctx;
        learn();
    }

    /** Name for a function interface ID, or null if unknown. */
    String resolve(String functionId) {
        MoinElement custom = ctx.index.get(functionId).orElse(null);
        if (custom != null) {
            return custom.name();
        }
        String builtin = Builtins.FUNCTIONS.get(functionId);
        return builtin != null ? builtin : learned.get(functionId);
    }

    boolean isCustom(String functionId) {
        return ctx.index.get(functionId).map(e -> e.is(Galaxy.FUNCTION_INTERFACE)).orElse(false);
    }

    /** Function names called in an expression text, in order ({@code if} excluded, string literals ignored). */
    static List<String> calls(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = CALL.matcher(STRING_LITERAL.matcher(text).replaceAll("\"\""));
        while (m.find()) {
            if (!"if".equals(m.group(1))) {
                out.add(m.group(1));
            }
        }
        return out;
    }

    private void learn() {
        for (MoinElement e : ctx.index.elementsOf(Galaxy.EXPRESSION)) {
            String text = e.attr("stringExpression");
            if (text == null || text.isBlank()) {
                continue;
            }
            List<String> ids = new ArrayList<>();
            invocations(e.id(), ids, 0);
            List<String> names = calls(text);
            if (ids.isEmpty() || ids.size() != names.size()) {
                continue;
            }
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i);
                String name = names.get(i);
                if (id == null || isCustom(id)) {
                    continue;
                }
                String known = Builtins.FUNCTIONS.get(id);
                String previous = known != null ? known : learned.putIfAbsent(id, name);
                if (previous != null && !previous.equals(name)) {
                    ctx.issues.warn("BUILTIN_FUNCTION_CONFLICT",
                            "Built-in function " + id + " is '" + previous + "' but expression text calls '" + name
                                    + "' at the same position: " + text.strip(),
                            ctx.at(null, e, null), ctx.source(e.partition()));
                }
            }
        }
    }

    /** Function interface IDs of all invocations below an expression, in pre-order (text order). */
    void invocations(String exprId, List<String> out, int depth) {
        if (depth > ExpressionReader.MAX_DEPTH) {
            return;
        }
        for (MoinElement step : ctx.index.firstElements(Galaxy.EXPRESSION_STEPS, exprId)) {
            if (step.is(Galaxy.FUNCTION_INVOCATION)) {
                String fid = ctx.index.first(Galaxy.INVOCATION_FUNCTION, step.id()).orElse(null);
                if (fid == null || !Builtins.INFIX.containsKey(fid)) {
                    out.add(fid); // infix operators have no "name(" token in the text
                }
                ctx.index.first(Galaxy.INVOCATION_PARAMETERS, step.id()).ifPresent(mapping -> {
                    for (String part : ctx.index.firsts(Galaxy.MAPPING_PARTS, mapping)) {
                        ctx.index.first(Galaxy.PART_EXPRESSION, part).ifPresent(x -> invocations(x, out, depth + 1));
                    }
                });
            } else if (step.is(Galaxy.CONDITION)) {
                for (String a : List.of(Galaxy.CONDITION_CONDITION, Galaxy.CONDITION_POSITIVE, Galaxy.CONDITION_NEGATIVE)) {
                    ctx.index.first(a, step.id()).ifPresent(x -> invocations(x, out, depth + 1));
                }
            }
        }
    }

    /** Reports an unknown function ID once. */
    void reportUnknown(String functionId, String process, MoinElement where) {
        ctx.issues.add(Issue.WARNING, "UNKNOWN_BUILTIN_FUNCTION",
                "Function interface " + functionId + " is neither in the DC nor in the built-in table",
                ctx.at(process, null, null), where == null ? null : ctx.source(where.partition()));
    }
}
