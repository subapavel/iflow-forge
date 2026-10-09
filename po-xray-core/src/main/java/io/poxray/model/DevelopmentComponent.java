package io.poxray.model;

import java.util.List;

/** {@code libraries}: jar files of an External Library DC (third-party dependencies, e.g. MVEL). */
public record DevelopmentComponent(
        String name,
        String vendor,
        String caption,
        String type,
        Archives archives,
        List<DcRef> dependencies,
        List<DcRef> deployDependencies,
        List<String> libraries) {

    public record Archives(String source, String deploy) {
    }
}
