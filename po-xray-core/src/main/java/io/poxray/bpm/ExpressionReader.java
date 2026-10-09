package io.poxray.bpm;

import io.poxray.model.Expression;
import io.poxray.model.FunctionCall;
import io.poxray.moin.MoinElement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Converts a Galaxy {@code Rule.Expression} into {@link Expression}.
 * <p>
 * Rendering rules (docs/HANDOFF.md §2.2): consecutive Reference steps form a path {@code DO_X/a/b};
 * a FunctionInvocation renders {@code name(arg, ...)} with its parameter mapping parts as arguments, ordered by
 * the function's formal parameters (catalog / .function); a step after an invocation continues the path on its
 * result (the implicit {@code result} segment is skipped); a Condition renders {@code if(c) then p else n}.
 * <p>
 * Notation: functional by default (unambiguous). When the editor text is known, an operator is rendered with
 * its symbol ({@code a < b}) if the editor text uses that notation; names and operands always come from the tree.
 */
final class ExpressionReader {

    static final int MAX_DEPTH = 64;
    private static final String RESULT_SEGMENT = "result";
    private static final String GENERATED_PREFIX = "#generated=true#";
    private static final Pattern NS_PREFIX = Pattern.compile("(?<![A-Za-z0-9_\"])[A-Za-z_][A-Za-z0-9_]*:(?=[A-Za-z_])");
    private static final Set<String> BINARY_OPERATORS =
            Set.of("=", "!=", "<", "<=", ">", ">=", "+", "-", "*", "div", "mod", "and", "or");
    private static final Set<String> UNARY_OPERATORS = Set.of("!");
    private static final Pattern PRINCIPALS = Pattern.compile("getPrincipals\\(\"([^\"]*)\"\\)");
    private static final Set<String> QUOTED_TYPES = Set.of("xsd:string", "xsd:normalizedString", "xsd:token",
            "xsd:anyURI", "xsd:language", "xsd:Name", "xsd:NCName", "xsd:NMTOKEN", "xsd:ID", "xsd:IDREF", "xsd:QName");

    private final BpmContext ctx;
    private final FunctionResolver functions;
    private final BuiltinFunctionCatalog catalog;

    ExpressionReader(BpmContext ctx, FunctionResolver functions, BuiltinFunctionCatalog catalog) {
        this.ctx = ctx;
        this.functions = functions;
        this.catalog = catalog;
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
        String text = editorText(e);
        Boolean generated = text != null && e.attr("stringExpression").startsWith(GENERATED_PREFIX) ? Boolean.TRUE : null;
        Render r = new Render(process, text == null || text.isBlank() ? null : normalize(text));
        String rendered = render(exprId, r, 0);
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

    /**
     * Renders the expression tree only (names and operands from the tree and the function catalogs), with the
     * notation adapted to the editor text where present. For validation against BPMN 2.0 exports.
     */
    String renderTree(String exprId) {
        String text = ctx.index.get(exprId).map(ExpressionReader::editorText).orElse(null);
        return render(exprId, new Render(null, text == null || text.isBlank() ? null : normalize(text)), 0);
    }

    /** Editor text without the {@code #generated=true#} marker; null if none. */
    private static String editorText(MoinElement e) {
        String text = e.attr("stringExpression");
        return text != null && text.startsWith(GENERATED_PREFIX) ? text.substring(GENERATED_PREFIX.length()) : text;
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
        // role assignment: the editor shows getPrincipals("<uniqueName>") as "<uniqueName>#<displayName>"
        java.util.regex.Matcher p = PRINCIPALS.matcher(rendered.strip());
        if (p.matches() && text.strip().startsWith(p.group(1) + "#")) {
            return true;
        }
        if (!r.contains("?")) {
            return false;
        }
        String regex = Arrays.stream(r.split("\\?", -1)).map(Pattern::quote)
                .collect(Collectors.joining("[^/(),]+"));
        return t.matches(regex);
    }

    static String normalize(String s) {
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
        FunctionResolver.Resolved f = functions.resolve(fid);
        if (f == null) {
            functions.reportUnknown(fid, r.process, step);
        }
        String name = f == null ? "unknown:" + fid : f.name();
        int slot = r.functions.size(); // pre-order: the call precedes calls in its arguments
        r.functions.add(null);
        List<FunctionCall.Argument> arguments = arguments(step, f, r, depth);
        r.functions.set(slot, f == null
                ? new FunctionCall(fid, null, null, "unknown", arguments)
                : new FunctionCall(fid, f.name(), f.category(), f.source(), arguments));
        List<String> args = arguments.stream().map(FunctionCall.Argument::text).toList();
        String functional = name + "(" + String.join(", ", args) + ")";
        String symbolic = f == null || r.hint == null ? null : symbolic(f.symbol(), args, r.hint);
        return symbolic != null && !r.hint.contains(normalize(functional)) ? symbolic : functional;
    }

    /**
     * Parameter mapping parts ordered by the formal parameters (parts with unknown names keep file order at the
     * end); parts without an expression are skipped.
     */
    private List<FunctionCall.Argument> arguments(MoinElement step, FunctionResolver.Resolved f, Render r, int depth) {
        record Arg(int order, FunctionCall.Argument argument) {
        }
        List<String> params = f == null ? List.of() : f.parameterNames();
        List<Arg> args = new ArrayList<>();
        ctx.index.first(Galaxy.INVOCATION_PARAMETERS, step.id()).ifPresent(mapping -> {
            int unknown = params.size();
            for (String partId : ctx.index.firsts(Galaxy.MAPPING_PARTS, mapping)) {
                String expr = ctx.index.first(Galaxy.PART_EXPRESSION, partId).orElse(null);
                MoinElement part = ctx.index.get(partId).orElse(null);
                if (expr == null || part == null) {
                    continue;
                }
                String param = ctx.qname(Galaxy.PART_QNAME, partId);
                int pos = param == null ? -1 : params.indexOf(param);
                args.add(new Arg(pos >= 0 ? pos : unknown++, new FunctionCall.Argument(param,
                        render(expr, r, depth + 1), part.attr("category"), part.attr("expander"),
                        part.attr("transformer"), "false".equals(part.attr("enabled")) ? Boolean.FALSE : null)));
            }
        });
        return args.stream().sorted(Comparator.comparingInt(Arg::order)).map(Arg::argument).toList();
    }

    /** Symbol notation of an operator if the editor text uses it, else null. */
    private static String symbolic(String symbol, List<String> args, String hint) {
        if (symbol == null) {
            return null;
        }
        List<String> candidates = new ArrayList<>();
        if (BINARY_OPERATORS.contains(symbol) && args.size() == 2) {
            candidates.add(args.get(0) + " " + symbol + " " + args.get(1));
            candidates.add(wrap(args.get(0)) + " " + symbol + " " + wrap(args.get(1)));
        } else if (UNARY_OPERATORS.contains(symbol) && args.size() == 1) {
            candidates.add(symbol + args.getFirst());
            candidates.add(symbol + wrap(args.getFirst()));
        }
        return candidates.stream().filter(c -> hint.contains(normalize(c))).findFirst().orElse(null);
    }

    private static String wrap(String arg) {
        return arg.contains(" ") ? "(" + arg + ")" : arg;
    }

    private String literal(MoinElement literal) {
        String value = literal.attr("value") == null ? "" : literal.attr("value");
        String type = ctx.index.first(Galaxy.LITERAL_TYPE, literal.id()).map(catalog::typeName).orElse(null);
        return QUOTED_TYPES.contains(type) ? "\"" + value + "\"" : value;
    }

    private static StringBuilder flush(StringBuilder chain, Render r) {
        if (chain != null) {
            r.reads.add(chain.toString());
        }
        return null;
    }

    /** Rendering state; {@code hint} = normalized editor text (or null) used to choose the notation. */
    private static final class Render {
        final String process;
        final String hint;
        final Set<String> reads = new LinkedHashSet<>();
        final List<FunctionCall> functions = new ArrayList<>();

        Render(String process, String hint) {
            this.process = process;
            this.hint = hint;
        }
    }
}
