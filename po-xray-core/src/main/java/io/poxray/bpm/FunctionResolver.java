package io.poxray.bpm;

import io.poxray.model.Issue;
import io.poxray.moin.MoinElement;
import java.util.List;

/**
 * Resolves the function interface of a {@code Rule.FunctionInvocation}: first a function of the DC
 * ({@code src/functions/**}/*.function, customer EJB/SDO functions), then the SAP built-in catalog.
 * Unknown IDs are reported and kept, never guessed.
 */
final class FunctionResolver {

    static final String BUILTIN = "builtin";
    static final String DC_FUNCTION = "dc-function";

    /**
     * @param category       built-in type category (operand type), null for DC functions
     * @param symbol         editor notation of built-ins ({@code <}, {@code =}, ...), null for DC functions
     * @param parameterNames formal parameters in declaration order (argument order)
     */
    record Resolved(String id, String name, String category, String source, String symbol, List<String> parameterNames) {
    }

    private final BpmContext ctx;
    private final BuiltinFunctionCatalog catalog;

    FunctionResolver(BpmContext ctx, BuiltinFunctionCatalog catalog) {
        this.ctx = ctx;
        this.catalog = catalog;
    }

    /** Resolved function, or null if the ID is neither in the DC nor in the catalog. */
    Resolved resolve(String functionId) {
        if (functionId == null) {
            return null;
        }
        MoinElement custom = ctx.index.get(functionId).filter(e -> e.is(Galaxy.FUNCTION_INTERFACE)).orElse(null);
        if (custom != null) {
            List<String> params = ctx.index.firstElements(Galaxy.FUNCTION_PARAMETER, functionId).stream()
                    .map(p -> p.attr("name")).toList();
            return new Resolved(functionId, custom.name(), null, DC_FUNCTION, null, params);
        }
        return catalog.function(functionId)
                .map(f -> new Resolved(f.id(), f.name(), f.category(), BUILTIN, f.symbol(),
                        f.parameters().stream().map(BuiltinFunctionCatalog.Parameter::name).toList()))
                .orElse(null);
    }

    /** Reports an unknown function ID (once per process). */
    void reportUnknown(String functionId, String process, MoinElement where) {
        ctx.issues.add(Issue.WARNING, "UNKNOWN_BUILTIN_FUNCTION",
                "Function interface " + functionId + " is neither in the DC nor in the built-in function catalog",
                ctx.at(process, null, null), where == null ? null : ctx.source(where.partition()));
    }
}
