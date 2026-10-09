package io.poxray;

import io.poxray.model.Issue;
import io.poxray.model.Ref;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Collects issues found while reading; duplicates (same code, element and path) are reported once. */
public final class IssueLog {

    private final List<Issue> issues = new ArrayList<>();
    private final Set<String> seen = new LinkedHashSet<>();

    public void add(String severity, String code, String message, Issue.Location location, String source) {
        String key = code + '|' + message + '|' + (location == null ? "" : location.toString());
        if (seen.add(key)) {
            issues.add(new Issue(severity, code, message, location, source));
        }
    }

    public void warn(String code, String message, Issue.Location location, String source) {
        add(Issue.WARNING, code, message, location, source);
    }

    public void info(String code, String message, Issue.Location location, String source) {
        add(Issue.INFO, code, message, location, source);
    }

    public void error(String code, String message, Issue.Location location, String source) {
        add(Issue.ERROR, code, message, location, source);
    }

    public static Issue.Location at(String dc, String process, Ref element, String path) {
        return new Issue.Location(dc, process, element, path);
    }

    public List<Issue> issues() {
        return List.copyOf(issues);
    }
}
