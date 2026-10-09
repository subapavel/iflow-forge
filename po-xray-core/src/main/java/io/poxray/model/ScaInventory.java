package io.poxray.model;

import java.util.List;

/** Root of the JSON output: everything read from one SCA. See {@code docs/OUTPUT-MODEL.md}. */
public record ScaInventory(
        String schemaVersion,
        Generator generator,
        ScaSource source,
        List<DevelopmentComponent> developmentComponents,
        List<BpmComponent> bpm,
        List<EjbModule> ejb,
        List<Issue> issues,
        List<UnmappedElement> unmapped) {

    public static final String SCHEMA_VERSION = "0.1";

    public record Generator(String name, String version, String readAt) {
    }

    public record ScaSource(String file, String sha256, SoftwareComponent softwareComponent) {
    }

    public record SoftwareComponent(
            String name, String vendor, String release, String servicelevel, String patchlevel,
            String updateVersion, String location) {
    }
}
