package io.poxray.sca;

import io.poxray.model.DcRef;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** {@code META-INF/SAP_MANIFEST.MF} of an SCA (standard manifest syntax, SAP specific keys). */
public final class SapManifest {

    private static final Pattern DEPENDENCY =
            Pattern.compile("<dependency\\s+keyname=\"([^\"]+)\"\\s+keyvendor=\"([^\"]+)\"");

    private final Manifest manifest;

    private SapManifest(Manifest manifest) {
        this.manifest = manifest;
    }

    public static SapManifest parse(byte[] content) {
        try {
            return new SapManifest(new Manifest(new ByteArrayInputStream(content)));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot parse SAP_MANIFEST.MF", e);
        }
    }

    public String main(String key) {
        return manifest.getMainAttributes().getValue(key);
    }

    /** Deploy-time dependencies declared in the section of the given archive entry ({@code dependencylist}). */
    public List<DcRef> deployDependencies(String archiveEntry) {
        Attributes a = section(archiveEntry);
        List<DcRef> out = new ArrayList<>();
        String list = a == null ? null : a.getValue("dependencylist");
        if (list != null) {
            Matcher m = DEPENDENCY.matcher(list);
            while (m.find()) {
                out.add(new DcRef(m.group(2), m.group(1)));
            }
        }
        return out;
    }

    private Attributes section(String archiveEntry) {
        for (Map.Entry<String, Attributes> e : manifest.getEntries().entrySet()) {
            String key = e.getKey().endsWith("/") ? e.getKey().substring(0, e.getKey().length() - 1) : e.getKey();
            if (key.equals(archiveEntry)) {
                return e.getValue();
            }
        }
        return null;
    }
}
