package io.poxray.cpi;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unpacks an SAP Integration Suite package export into the layout of {@code knowledge/sources/}: every deployable
 * artifact ({@code IFlow}, {@code ScriptCollection}, {@code ValueMapping}) into {@code <out>/<symbolic-name>/}, the
 * change log as {@code <out>/CHANGELOG.txt} with LF line ends. Tenant content is left behind: {@code parameters.prop}
 * (configured values) and {@code Configured_Custom_Tags.txt} are never written, {@code Url}, {@code ContentPackage}
 * and other entries and {@code contentmetadata.md} are ignored, and the given tokens (a tenant suffix) are removed
 * from the symbolic name, {@code MANIFEST.MF} and {@code .project}. All other files are written byte for byte.
 */
public final class PackageUnpacker {

    /** File names never written, wherever they occur in an artifact. */
    private static final Set<String> SKIPPED_FILES = Set.of("parameters.prop", "Configured_Custom_Tags.txt");

    private static final String PROJECT = ".project";
    private static final String CHANGELOG = "CHANGELOG.txt";
    private static final int MANIFEST_LINE_BYTES = 72;

    private PackageUnpacker() {
    }

    /**
     * Unpacks the package export folder or zip {@code packageZipOrDir} into {@code outDir} (created if needed;
     * existing files with the same names are overwritten).
     *
     * @param stripTokens removed (plain text) from the symbolic name, {@code MANIFEST.MF} and {@code .project}
     * @throws IOException when the input is no package export, an artifact has no symbolic name, two artifacts would
     *                     share a folder or an entry would be written outside its folder
     */
    public static void unpack(Path packageZipOrDir, Path outDir, List<String> stripTokens) throws IOException {
        String source = packageZipOrDir.toString();
        Map<String, byte[]> files = IFlowReader.contents(packageZipOrDir);
        PackageExport export = PackageExport.parse(files, source);
        List<String> tokens = new ArrayList<>();
        for (String t : stripTokens == null ? List.<String>of() : stripTokens) {
            if (t != null && !t.isEmpty()) {
                tokens.add(t);
            }
        }
        Path out = outDir.toAbsolutePath().normalize();
        Files.createDirectories(out);
        Set<String> folders = new HashSet<>();
        for (PackageExport.Entry e : export.entries()) {
            byte[] content = PackageExport.content(files, e);
            if (IFlowReader.ARTIFACT_TYPES.contains(e.resourceType())) {
                if (content == null) {
                    throw new IOException("Package export " + source + " has no content for " + e.resourceType()
                            + " '" + e.displayName() + "' (" + e.id() + "_content)");
                }
                unpackArtifact(IFlowReader.unzip(content, source + "!" + e.content()), source + "!" + e.content(),
                        out, tokens, folders);
            } else if ("ChangeLog".equals(e.resourceType()) && content != null) {
                Files.write(out.resolve(CHANGELOG), toLf(content));
            }
        }
    }

    private static void unpackArtifact(Map<String, byte[]> artifact, String source, Path out, List<String> tokens,
            Set<String> folders) throws IOException {
        byte[] manifest = artifact.get(IFlowReader.MANIFEST);
        String symbolic = manifest == null ? null : IFlowReader.manifestHeaders(manifest).get("Bundle-SymbolicName");
        if (symbolic == null) {
            throw new IOException("No Bundle-SymbolicName in " + IFlowReader.MANIFEST + " of " + source);
        }
        String folder = strip(symbolic.split(";", 2)[0].strip(), tokens);
        Path base = out.resolve(folder).normalize();
        if (folder.isEmpty() || !out.equals(base.getParent())) {
            throw new IOException("Symbolic name '" + folder + "' of " + source + " is not a folder name");
        }
        if (!folders.add(folder)) {
            throw new IOException("Two artifacts of the package share the symbolic name '" + folder + "'");
        }
        for (Map.Entry<String, byte[]> f : artifact.entrySet()) {
            String path = f.getKey();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (SKIPPED_FILES.contains(fileName)) {
                continue;
            }
            Path target = base.resolve(path).normalize();
            if (!target.startsWith(base) || target.equals(base)) {
                throw new IOException("Entry '" + path + "' of " + source + " points outside its folder");
            }
            byte[] bytes = f.getValue();
            if (!tokens.isEmpty() && path.equals(IFlowReader.MANIFEST)) {
                bytes = stripManifest(bytes, tokens);
            } else if (!tokens.isEmpty() && path.equals(PROJECT)) {
                bytes = strip(new String(bytes, StandardCharsets.UTF_8), tokens).getBytes(StandardCharsets.UTF_8);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        }
    }

    private static String strip(String text, List<String> tokens) {
        String s = text;
        for (String t : tokens) {
            s = s.replace(t, "");
        }
        return s;
    }

    /**
     * Removes the tokens from the logical (unfolded) lines of a manifest. A changed line is folded again at 72 bytes
     * (continuation lines start with one space), as {@link java.util.jar.Manifest} writes; unchanged lines are kept
     * byte for byte.
     */
    static byte[] stripManifest(byte[] manifest, List<String> tokens) {
        String text = new String(manifest, StandardCharsets.UTF_8);
        String eol = text.contains("\r\n") ? "\r\n" : "\n";
        List<String[]> lines = physicalLines(text);
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < lines.size()) {
            int j = i + 1;
            while (j < lines.size() && lines.get(j)[0].startsWith(" ")) {
                j++;
            }
            StringBuilder logical = new StringBuilder(lines.get(i)[0]);
            for (int k = i + 1; k < j; k++) {
                logical.append(lines.get(k)[0], 1, lines.get(k)[0].length());
            }
            String original = logical.toString();
            String stripped = strip(original, tokens);
            if (stripped.equals(original)) {
                for (int k = i; k < j; k++) {
                    sb.append(lines.get(k)[0]).append(lines.get(k)[1]);
                }
            } else {
                fold(stripped, eol, sb);
                sb.append(lines.get(j - 1)[1]);
            }
            i = j;
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Lines as {content, line terminator}; the terminator is empty for a last line without one. */
    private static List<String[]> physicalLines(String text) {
        List<String[]> out = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                int end = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
                out.add(new String[] {text.substring(start, i), text.substring(i, end)});
                start = end;
                i = end;
            } else {
                i++;
            }
        }
        if (start < text.length()) {
            out.add(new String[] {text.substring(start), ""});
        }
        return out;
    }

    /** Appends {@code line} folded at 72 UTF-8 bytes per physical line, never inside a character. */
    private static void fold(String line, String eol, StringBuilder sb) {
        int bytes = 0;
        for (int i = 0; i < line.length(); ) {
            int cp = line.codePointAt(i);
            int len = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
            if (bytes + len > MANIFEST_LINE_BYTES) {
                sb.append(eol).append(' ');
                bytes = 1;
            }
            sb.appendCodePoint(cp);
            bytes += len;
            i += Character.charCount(cp);
        }
    }

    /** CRLF and lone CR become LF (byte level; CR and LF never occur inside a UTF-8 multi-byte sequence). */
    private static byte[] toLf(byte[] text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length);
        for (int i = 0; i < text.length; i++) {
            if (text[i] == '\r') {
                out.write('\n');
                if (i + 1 < text.length && text[i + 1] == '\n') {
                    i++;
                }
            } else {
                out.write(text[i]);
            }
        }
        return out.toByteArray();
    }
}
