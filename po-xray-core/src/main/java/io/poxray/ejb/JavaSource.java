package io.poxray.ejb;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight facts about one Java source file, extracted without compiling: package, imports, the top-level
 * type with its annotations, supertypes, string constants and selected call arguments. Comments are stripped
 * first; string literals are kept. Good enough for classifying EJBs, not a Java parser.
 */
final class JavaSource {

    private static final Pattern PACKAGE = Pattern.compile("\\bpackage\\s+([\\w.]+)\\s*;");
    private static final Pattern IMPORT = Pattern.compile("\\bimport\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?)\\s*;");
    private static final Pattern TYPE = Pattern.compile("\\b(class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern CONSTANT = Pattern.compile(
            "\\bString\\s+(\\w+)\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*;");
    private static final Pattern STRING_ATTR = Pattern.compile("(\\w+)\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern SINGLE_STRING = Pattern.compile("^\\s*\"((?:[^\"\\\\]|\\\\.)*)\"\\s*$");
    private static final Pattern CLASS_LITERAL = Pattern.compile("([\\w.]+)\\.class\\b");
    private static final Pattern EJB_FIELD = Pattern.compile(
            "@EJB(?:\\s*\\([^)]*\\))?\\s+(?:(?:private|protected|public|static|final|transient)\\s+)*([\\w.]+)\\s+\\w+\\s*;");

    record Annotation(String name, String args) {

        /** String attribute {@code key = "value"}; {@code value} also matches a single unnamed string. */
        Optional<String> attr(String key) {
            if (args == null) {
                return Optional.empty();
            }
            Matcher m = STRING_ATTR.matcher(args);
            while (m.find()) {
                if (m.group(1).equals(key)) {
                    return Optional.of(unescape(m.group(2)));
                }
            }
            if ("value".equals(key)) {
                Matcher s = SINGLE_STRING.matcher(args);
                if (s.matches()) {
                    return Optional.of(unescape(s.group(1)));
                }
            }
            return Optional.empty();
        }

        /** Class literals in the arguments ({@code X.class}), in order. */
        List<String> classes() {
            List<String> out = new ArrayList<>();
            if (args != null) {
                Matcher m = CLASS_LITERAL.matcher(args);
                while (m.find()) {
                    out.add(m.group(1));
                }
            }
            return out;
        }
    }

    final String path;
    final String text;
    final String packageName;
    final List<String> imports = new ArrayList<>();
    final String kind;
    final String simpleName;
    final List<Annotation> annotations;
    final String superclass;
    final List<String> interfaces;
    final Map<String, String> constants = new LinkedHashMap<>();

    private JavaSource(String path, String source) {
        this.path = path;
        this.text = stripComments(source);
        Matcher pm = PACKAGE.matcher(text);
        this.packageName = pm.find() ? pm.group(1) : "";
        Matcher im = IMPORT.matcher(text);
        while (im.find()) {
            imports.add(im.group(1));
        }
        Matcher cm = CONSTANT.matcher(text);
        while (cm.find()) {
            constants.putIfAbsent(cm.group(1), unescape(cm.group(2)));
        }
        Matcher tm = findTopLevelType(text);
        if (tm == null) {
            kind = null;
            simpleName = null;
            annotations = List.of();
            superclass = null;
            interfaces = List.of();
            return;
        }
        kind = tm.group(1);
        simpleName = tm.group(2);
        int headerStart = Math.max(text.lastIndexOf(';', tm.start()), 0);
        annotations = parseAnnotations(text.substring(headerStart, tm.start()));
        int brace = text.indexOf('{', tm.end());
        String decl = text.substring(tm.end(), brace < 0 ? text.length() : brace);
        superclass = clause(decl, "extends", "implements").stream().findFirst().orElse(null);
        interfaces = "interface".equals(kind) ? clause(decl, "extends", "implements") : clause(decl, "implements", null);
    }

    static JavaSource parse(String path, String source) {
        return new JavaSource(path, source);
    }

    String qualifiedName() {
        return packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    boolean isType() {
        return simpleName != null;
    }

    Optional<Annotation> annotation(String simpleName) {
        return annotations.stream().filter(a -> a.name().equals(simpleName)).findFirst();
    }

    boolean hasAnnotation(String simpleName) {
        return annotation(simpleName).isPresent();
    }

    boolean imports(String prefix) {
        return imports.stream().anyMatch(i -> i.startsWith(prefix));
    }

    boolean contains(String regex) {
        return Pattern.compile(regex).matcher(text).find();
    }

    /** Resolves a simple type name via imports, else assumes the own package; qualified names are kept. */
    String resolve(String typeName) {
        if (typeName.contains(".")) {
            return typeName;
        }
        for (String i : imports) {
            if (i.endsWith("." + typeName)) {
                return i;
            }
        }
        return packageName.isEmpty() ? typeName : packageName + "." + typeName;
    }

    /**
     * Arguments of calls to {@code method(...)}: string literals as is, identifiers resolved through string
     * constants of the file, unresolved identifiers as {@code {NAME}}. Distinct, in order.
     */
    List<String> callArguments(String method) {
        Pattern p = Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[\\w.]+)\\s*\\)");
        Set<String> out = new LinkedHashSet<>();
        Matcher m = p.matcher(text);
        while (m.find()) {
            String arg = m.group(1);
            if (arg.startsWith("\"")) {
                out.add(unescape(arg.substring(1, arg.length() - 1)));
            } else {
                String simple = arg.substring(arg.lastIndexOf('.') + 1);
                out.add(constants.getOrDefault(simple, "{" + arg + "}"));
            }
        }
        return List.copyOf(out);
    }

    /** Declared types of {@code @EJB} injected fields (simple or qualified as written). */
    List<String> injectedEjbs() {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = EJB_FIELD.matcher(text);
        while (m.find()) {
            out.add(m.group(1));
        }
        return List.copyOf(out);
    }

    // ------------------------------------------------------------------------------------------ parsing helpers

    /** First {@code class|interface|enum|record} keyword at brace depth 0, outside string literals. */
    private static Matcher findTopLevelType(String text) {
        Matcher m = TYPE.matcher(text);
        while (m.find()) {
            if (depthAt(text, m.start()) == 0 && !isAnnotationTypeOrLiteral(text, m.start())) {
                return m;
            }
        }
        return null;
    }

    private static boolean isAnnotationTypeOrLiteral(String text, int pos) {
        // ".class" literal or "@interface"
        return (pos > 0 && text.charAt(pos - 1) == '.') || (pos > 0 && text.charAt(pos - 1) == '@');
    }

    private static int depthAt(String text, int pos) {
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < pos; i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
        }
        return depth;
    }

    static List<Annotation> parseAnnotations(String header) {
        List<Annotation> out = new ArrayList<>();
        int i = 0;
        while ((i = header.indexOf('@', i)) >= 0) {
            int j = i + 1;
            while (j < header.length() && (Character.isJavaIdentifierPart(header.charAt(j)) || header.charAt(j) == '.')) {
                j++;
            }
            String name = header.substring(i + 1, j);
            name = name.substring(name.lastIndexOf('.') + 1);
            int k = j;
            while (k < header.length() && Character.isWhitespace(header.charAt(k))) {
                k++;
            }
            String args = null;
            if (k < header.length() && header.charAt(k) == '(') {
                int end = matchingParen(header, k);
                args = header.substring(k + 1, end);
                j = end + 1;
            }
            if (!name.isEmpty() && !"interface".equals(name)) {
                out.add(new Annotation(name, args));
            }
            i = Math.max(j, i + 1);
        }
        return out;
    }

    private static int matchingParen(String s, int open) {
        int depth = 0;
        boolean inString = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return s.length() - 1;
    }

    /** Type names after {@code keyword} up to {@code stop} (or end), generics removed. */
    private static List<String> clause(String decl, String keyword, String stop) {
        Matcher m = Pattern.compile("\\b" + keyword + "\\b(.*?)" + (stop == null ? "$" : "(?=\\b" + stop + "\\b|$)"),
                Pattern.DOTALL).matcher(decl);
        if (!m.find()) {
            return List.of();
        }
        String list = removeGenerics(m.group(1));
        List<String> out = new ArrayList<>();
        for (String t : list.split(",")) {
            String s = t.trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private static String removeGenerics(String s) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (char c : s.toCharArray()) {
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Removes line and block comments; string, text block and char literals are preserved. */
    static String stripComments(String s) {
        StringBuilder out = new StringBuilder(s.length());
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            char next = i + 1 < n ? s.charAt(i + 1) : '\0';
            if (c == '"' && s.startsWith("\"\"\"", i)) {
                int end = s.indexOf("\"\"\"", i + 3);
                end = end < 0 ? n : end + 3;
                out.append(s, i, end);
                i = end;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && s.charAt(j) != c) {
                    if (s.charAt(j) == '\\') {
                        j++;
                    }
                    if (s.charAt(Math.min(j, n - 1)) == '\n') {
                        break; // unterminated literal, stop at line end
                    }
                    j++;
                }
                j = Math.min(j + 1, n);
                out.append(s, i, j);
                i = j;
            } else if (c == '/' && next == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && next == '*') {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
                out.append(' ');
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static String unescape(String s) {
        return s.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
