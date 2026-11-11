package io.poxray.monitoring;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Splits a message payload in the XI 3.0 form (leading headers with {@code Content-Type: multipart/...; boundary=...},
 * a blank line, then MIME parts) into its parts, with the JDK only. Lines may end in CRLF or LF; header names are
 * case-insensitive and folded header lines are joined. A part's content is its bytes as they are (a
 * {@code Content-Transfer-Encoding} is not undone).
 */
public final class Mime {

    /** A header line: a token, a colon, the value (RFC 5322 field name). */
    private static final Pattern HEADER = Pattern.compile("[A-Za-z0-9!#$%&'*+.^_`|~-]+:.*");

    private Mime() {
    }

    /**
     * The MIME parts of {@code bytes} in order, split by the boundary of the leading headers; empty (never null) when
     * the bytes do not start with headers whose content type is of type {@code multipart} with a boundary. A part is
     * text when its media type is of type {@code text} or its subtype is {@code xml} or {@code json} or ends with
     * {@code +xml} or {@code +json}, and its bytes decode cleanly in the declared charset (UTF-8 if none is declared);
     * otherwise, also for a part without a content type or with an unknown charset, it is base64.
     */
    public static List<PayloadPart> parts(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return List.of();
        }
        Headers lead = Headers.read(bytes, 0, bytes.length);
        String type = lead == null ? null : lead.get("content-type");
        if (type == null || !mediaType(type).startsWith("multipart/")) {
            return List.of();
        }
        String boundary = parameter(type, "boundary");
        if (boundary == null || boundary.isEmpty()) {
            return List.of();
        }
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        List<PayloadPart> parts = new ArrayList<>();
        for (int d = delimiter(bytes, delimiter, lead.end()); d >= 0; ) {
            int after = d + delimiter.length;
            if (startsWith(bytes, after, "--")) {
                break; // close delimiter
            }
            int start = nextLine(bytes, after);
            if (start < 0) {
                break; // delimiter at the end of the data
            }
            int next = delimiter(bytes, delimiter, start);
            parts.add(part(parts.size(), bytes, start, next < 0 ? bytes.length : contentEnd(bytes, start, next)));
            d = next;
        }
        return parts;
    }

    /** One part from {@code [start, end)}: its headers up to the first blank line, then its content. */
    private static PayloadPart part(int index, byte[] bytes, int start, int end) {
        Headers h = Headers.read(bytes, start, end);
        int body = h == null ? start : h.end();
        String contentType = h == null ? null : h.get("content-type");
        String contentId = h == null ? null : h.get("content-id");
        if (contentId != null && contentId.startsWith("<") && contentId.endsWith(">")) {
            contentId = contentId.substring(1, contentId.length() - 1).strip();
        }
        String text = text(contentType, bytes, body, end);
        return new PayloadPart(index, contentId, contentType, end - body, text,
                text == null ? Base64.getEncoder().encodeToString(Arrays.copyOfRange(bytes, body, end)) : null);
    }

    /** The content decoded in the declared charset if the content type is textual and it decodes cleanly, else null. */
    private static String text(String contentType, byte[] bytes, int start, int end) {
        if (contentType == null) {
            return null;
        }
        String media = mediaType(contentType);
        int slash = media.indexOf('/');
        String type = slash < 0 ? media : media.substring(0, slash);
        String subtype = slash < 0 ? "" : media.substring(slash + 1);
        boolean textual = type.equals("text") || subtype.equals("xml") || subtype.equals("json")
                || subtype.endsWith("+xml") || subtype.endsWith("+json");
        if (!textual) {
            return null;
        }
        String name = parameter(contentType, "charset");
        Charset charset;
        try {
            charset = name == null || name.isBlank() ? StandardCharsets.UTF_8 : Charset.forName(name.strip());
        } catch (IllegalArgumentException e) {
            return null; // unknown charset: the bytes are kept as base64
        }
        try {
            return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, end - start)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    /** The media type of a content type value, lower case, without parameters. */
    private static String mediaType(String contentType) {
        int semi = contentType.indexOf(';');
        return (semi < 0 ? contentType : contentType.substring(0, semi)).strip().toLowerCase(Locale.ROOT);
    }

    /** The value of a parameter of a content type (quoted or not, name case-insensitive), null if absent. */
    static String parameter(String contentType, String name) {
        int n = contentType.length();
        int i = contentType.indexOf(';');
        while (i >= 0 && i < n) {
            int eq = contentType.indexOf('=', i + 1);
            if (eq < 0) {
                return null;
            }
            String key = contentType.substring(i + 1, eq).strip();
            int j = eq + 1;
            while (j < n && Character.isWhitespace(contentType.charAt(j))) {
                j++;
            }
            String value;
            if (j < n && contentType.charAt(j) == '"') {
                StringBuilder b = new StringBuilder();
                for (j++; j < n && contentType.charAt(j) != '"'; j++) {
                    if (contentType.charAt(j) == '\\' && j + 1 < n) {
                        j++;
                    }
                    b.append(contentType.charAt(j));
                }
                value = b.toString();
                i = contentType.indexOf(';', Math.min(j + 1, n));
            } else {
                int semi = contentType.indexOf(';', j);
                value = contentType.substring(j, semi < 0 ? n : semi).strip();
                i = semi;
            }
            if (key.equalsIgnoreCase(name)) {
                return value;
            }
        }
        return null;
    }

    /**
     * The index of the next delimiter line at or after {@code from}: {@code delimiter} at the start of the data or
     * after a line feed, followed by {@code --}, or by optional blanks and the end of the line or of the data.
     */
    private static int delimiter(byte[] bytes, byte[] delimiter, int from) {
        for (int i = from; i + delimiter.length <= bytes.length; i++) {
            if ((i == 0 || bytes[i - 1] == '\n') && startsWith(bytes, i, delimiter) && delimiterEnd(bytes,
                    i + delimiter.length)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean delimiterEnd(byte[] bytes, int i) {
        if (startsWith(bytes, i, "--")) {
            return true;
        }
        while (i < bytes.length && (bytes[i] == ' ' || bytes[i] == '\t')) {
            i++;
        }
        return i == bytes.length || bytes[i] == '\n' || bytes[i] == '\r' && i + 1 < bytes.length && bytes[i + 1] == '\n';
    }

    /** The end of a part's content before the delimiter at {@code next}: the line break before it belongs to it. */
    private static int contentEnd(byte[] bytes, int start, int next) {
        int end = next;
        if (end > start && bytes[end - 1] == '\n') {
            end--;
            if (end > start && bytes[end - 1] == '\r') {
                end--;
            }
        }
        return end;
    }

    /** The index after the line feed that ends the line containing {@code i}, -1 if there is none. */
    private static int nextLine(byte[] bytes, int i) {
        for (int j = i; j < bytes.length; j++) {
            if (bytes[j] == '\n') {
                return j + 1;
            }
        }
        return -1;
    }

    private static boolean startsWith(byte[] bytes, int at, String ascii) {
        return startsWith(bytes, at, ascii.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static boolean startsWith(byte[] bytes, int at, byte[] prefix) {
        if (at < 0 || at + prefix.length > bytes.length) {
            return false;
        }
        for (int k = 0; k < prefix.length; k++) {
            if (bytes[at + k] != prefix[k]) {
                return false;
            }
        }
        return true;
    }

    /** A header block: the headers by lower-case name (the first of a repeated name) and the index after it. */
    private record Headers(Map<String, String> values, int end) {

        String get(String name) {
            return values.get(name);
        }

        /**
         * The header block that starts at {@code start} and ends with a blank line before {@code end}; null if the
         * region does not start with header lines so terminated.
         */
        static Headers read(byte[] bytes, int start, int end) {
            Map<String, String> values = new LinkedHashMap<>();
            String name = null;
            StringBuilder value = null;
            int i = start;
            while (i < end) {
                int lf = i;
                while (lf < end && bytes[lf] != '\n') {
                    lf++;
                }
                if (lf == end) {
                    return null; // no blank line: not a header block
                }
                int lineEnd = lf > i && bytes[lf - 1] == '\r' ? lf - 1 : lf;
                String line = new String(bytes, i, lineEnd - i, StandardCharsets.ISO_8859_1);
                i = lf + 1;
                if (line.isEmpty()) {
                    if (name == null) {
                        return new Headers(values, i); // no headers: the content starts after the blank line
                    }
                    values.putIfAbsent(name, value.toString().strip());
                    return new Headers(values, i);
                }
                if (line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                    if (name == null) {
                        return null;
                    }
                    value.append(' ').append(line.strip());
                } else if (HEADER.matcher(line).matches()) {
                    if (name != null) {
                        values.putIfAbsent(name, value.toString().strip());
                    }
                    int colon = line.indexOf(':');
                    name = line.substring(0, colon).toLowerCase(Locale.ROOT);
                    value = new StringBuilder(line.substring(colon + 1));
                } else {
                    return null;
                }
            }
            return null;
        }
    }
}
