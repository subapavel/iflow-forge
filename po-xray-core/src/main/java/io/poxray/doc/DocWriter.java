package io.poxray.doc;

import java.util.List;

/** Renders the format-neutral document model into a concrete markup. */
public interface DocWriter {

    String write(List<Doc.Block> blocks);
}
