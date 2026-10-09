package io.poxray.bpm;

import io.poxray.model.Expression;
import io.poxray.moin.MoinElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts a Galaxy {@code Rule.Expression} into {@link Expression}.
 * <p>
 * Rendering rules (docs/HANDOFF.md §2.2): consecutive Reference steps form a path {@code DO_X/a/b};
 * a FunctionInvocation renders {@code name(arg, ...)} with its parameter mapping parts as positional arguments;
 * a step after an invocation continues the path on its result (the implicit {@code result} segment is skipped);
 * a Condition renders {@code if(c) then p else n}.
 */
final class ExpressionReader {

    static final int MAX_DEPTH = 64;
    private static final String RESULT_SEGMENT = "result";
    private static final String GENERATED_PREFIX = "#generated=true#";
    private static final Pattern NS_PREFIX = Pattern.compile("(?<![A-Za-z0-9_\"])[A-Za-z_][A-Za-z0-9_]*:(?=[A-Za-z_])");

    private final BpmContext ctx;
    private final FunctionNames functions;

    ExpressionReader(BpmContext ctx, FunctionNames functions) {
        this.ctx = ctx;
        this.functions = functions;
    }

    /** Reads the expression with the given ID; null if the ID is null. */
    Expression read(String exprId, String process) {
        if (exprId == null) {
            return null;
        }
        MoinElement e = ctx.index.get(exprId).orElse(null);
        if (e == null) {
            ctx.issues.warn("UNRESOLVED_REF", "Expression " + exprId + " not found", ctx.at(process, null, null), null);
            return null;
        }
        Render r = new Render(process);
        String rendered = render(exprId, r, 0);
        String text = e.attr("stringExpression");
        Boolean generated = null;
        if (text != null && text.startsWith(GENERATED_PREFIX)) {
            text = text.substring(GENERATED_PREFIX.length());
            generated = Boolean.TRUE;
        }
        Boolean wasRendered = null;
        if (text == null || text.isBlank()) {
            text = rendered;
            wasRendered = Boolean.TRUE;
        } else if (!equivalent(text, rendered)) {
            ctx.issues.info("EXPRESSION_TEXT_MISMATCH",
                    "Editor text differs from the expression tree. text: " + text.strip() + " | tree: " + rendered,
                    ctx.at(process, e, null), ctx.source(e.partition()));
        }
        return new Expression(text, wasRendered, generated, List.copyOf(r.reads), List.copyOf(r.functions));
    }

    Optional<Expression> readOptional(Optional<String> exprId, String process) {
        return exprId.map(id -> read(id, process));
    }

    /**
     * Compares editor text with the rendered tree, ignoring whitespace and namespace prefixes ({@code dm0:X});
     * a segment the tree could not name ({@code ?}, SAP built-in feature) matches any name.
     */
    static boolean equivalent(String text, String rendered) {
        String t = normalize(text);
        String r = normalize(rendered);
        if (t.equals(r)) {
            return true;
        }
        if (!r.contains("?")) {
            return false;
        }
        String regex = Arrays.stream(r.split("\\?", -1)).map(Pattern::quote)
                .collect(Collectors.joining("[^/(),]+"));
        return t.matches(regex);
    }

    private static String normalize(String s) {
        return NS_PREFIX.matcher(s.replaceAll("\\s+", "")).replaceAll("");
    }

    /** Path segment name: the Reference's QName, else the name of the XSD feature it points to, else null. */
    private String referenceName(String referenceId) {
        String name = ctx.qname(Galaxy.REFERENCE_QNAME, referenceId);
        if (name != null) {
            return name;
        }
        return ctx.index.firstElement(Galaxy.REFERENCE_FEATURE, referenceId).map(f -> f.attr("name")).orElse(null);
    }

    private String render(String exprId, Render r, int depth) {
        if (depth > MAX_DEPTH) {
            return "...";
        }
        StringBuilder sb = new StringBuilder();
        StringBuilder chain = null;
        boolean afterInvocation = false;
        boolean first = true;
        for (String stepId : ctx.index.firsts(Galaxy.EXPRESSION_STEPS, exprId)) {
            MoinElement step = ctx.index.get(stepId).orElse(null);
            boolean invocation = false;
            if (step == null) {
                sb.append('?');
                ctx.issues.warn("UNRESOLVED_REF", "Expression step " + stepId + " not found",
                        ctx.at(r.process, null, null), null);
            } else {
                switch (step.metaclass()) {
                    case Galaxy.REFERENCE -> {
                        String name = referenceName(stepId);
                        // implicit result of the preceding invocation: QName "result" or none at all
                        if (afterInvocation && (name == null || RESULT_SEGMENT.equals(name))) {
                            afterInvocation = false;
                            first = false;
                            continue;
                        }
                        name = name == null ? "?" : name;
                        if (!sb.isEmpty()) {
                            sb.append('/');
                        }
                        sb.append(name);
                        if (chain != null) {
                            chain.append('/').append(name);
                        } else if (first) {
                            chain = new StringBuilder(name);
                        }
                    }
                    case Galaxy.PARENT -> {
                        if (!sb.isEmpty()) {
                            sb.append('/');
                        }
                        sb.append("..");
                        if (chain != null) {
                            chain.append("/..");
                        }
                    }
                    case Galaxy.LITERAL -> sb.append(literal(step));
                    case Galaxy.FUNCTION_INVOCATION -> {
                        chain = flush(chain, r);
                        sb.append(invocation(step, r, depth));
                        invocation = true;
                    }
                    case Galaxy.CONDITION -> {
                        chain = flush(chain, r);
                        sb.append("if(").append(sub(Galaxy.CONDITION_CONDITION, stepId, r, depth))
                                .append(") then ").append(sub(Galaxy.CONDITION_POSITIVE, stepId, r, depth))
                                .append(" else ").append(sub(Galaxy.CONDITION_NEGATIVE, stepId, r, depth));
                    }
                    default -> {
                        sb.append('?').append(step.simpleType());
                        ctx.issues.warn("UNKNOWN_ELEMENT_TYPE", "Unknown expression step " + step.metaclass(),
                                ctx.at(r.process, step, null), ctx.source(step.partition()));
                    }
                }
            }
            afterInvocation = invocation;
            first = false;
        }
        flush(chain, r);
        return sb.toString();
    }

    private String sub(String association, String ownerId, Render r, int depth) {
        return ctx.index.first(association, ownerId).map(x -> render(x, r, depth + 1)).orElse("");
    }

    private String invocation(MoinElement step, Render r, int depth) {
        String fid = ctx.index.first(Galaxy.INVOCATION_FUNCTION, step.id()).orElse(null);
        String name = fid == null ? null : functions.resolve(fid);
        if (name == null) {
            functions.reportUnknown(fid, r.process, step);
            name = "unknown:" + fid;
        }
        r.functions.add(name);
        List<String> args = new ArrayList<>();
        ctx.index.first(Galaxy.INVOCATION_PARAMETERS, step.id()).ifPresent(mapping -> {
            for (String part : ctx.index.firsts(Galaxy.MAPPING_PARTS, mapping)) {
                ctx.index.first(Galaxy.PART_EXPRESSION, part).ifPresent(x -> args.add(render(x, r, depth + 1)));
            }
        });
        String infix = fid == null ? null : Builtins.INFIX.get(fid);
        if (infix != null && args.size() == 2) {
            return args.get(0) + " " + infix + " " + args.get(1);
        }
        return name + "(" + String.join(", ", args) + ")";
    }

    private String literal(MoinElement literal) {
        String value = literal.attr("value") == null ? "" : literal.attr("value");
        String type = ctx.index.first(Galaxy.LITERAL_TYPE, literal.id()).map(Builtins.TYPES::get).orElse(null);
        return Builtins.STRING_TYPE.equals(type) ? "\"" + value + "\"" : value;
    }

    private static StringBuilder flush(StringBuilder chain, Render r) {
        if (chain != null) {
            r.reads.add(chain.toString());
        }
        return null;
    }

    private static final class Render {
        final String process;
        final Set<String> reads = new LinkedHashSet<>();
        final Set<String> functions = new LinkedHashSet<>();

        Render(String process) {
            this.process = process;
        }
    }
}
