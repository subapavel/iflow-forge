package io.poxray.model;

import java.util.List;

/** Sequence flow; {@code via} lists contracted artificial joins the flow passed through. */
public record Flow(
        String id, Ref source, Ref target, String name, Expression condition, Boolean isDefault, List<Ref> via) {
}
