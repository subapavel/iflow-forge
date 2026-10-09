package io.poxray.model;

import java.util.List;

public record DevelopmentComponent(
        String name,
        String vendor,
        String caption,
        String type,
        Archives archives,
        List<DcRef> dependencies,
        List<DcRef> deployDependencies) {

    public record Archives(String source, String deploy) {
    }
}
