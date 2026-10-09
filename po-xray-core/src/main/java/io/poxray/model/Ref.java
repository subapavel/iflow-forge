package io.poxray.model;

/** Cross-reference: {@code ref} (MOF ID) is authoritative, {@code name} is denormalised for readability. */
public record Ref(String ref, String name) {
}
