package io.poxray.cpi;

import io.poxray.json.Json;

/** Text forms of an {@link IFlow}: a compact Markdown listing and JSON. */
public final class IFlowDump {

    private IFlowDump() {
    }

    /**
     * Markdown: a {@code ## <id> (<name>, <version>)} header, one
     * {@code - participant: <name> <componentType> <direction> -> <endpoint>} line per participant (its properties
     * are in the JSON form only), the steps numbered continuously and indented two spaces per depth
     * ({@code [unreached]} when no flow leads to the step), one {@code - parameter:} line per parameter
     * ({@code [configured]} when a configured value exists) and one {@code - resource:} line per resource; ends with
     * a line break.
     */
    public static String markdown(IFlow f) {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(f.id()).append(" (").append(f.name()).append(", ").append(f.version()).append(")\n");
        for (Participant p : f.participants()) {
            sb.append("- participant: ").append(p.name()).append(' ').append(p.componentType()).append(' ')
                    .append(p.direction()).append(" ->");
            if (!p.endpoint().isEmpty()) {
                sb.append(' ').append(p.endpoint());
            }
            sb.append('\n');
        }
        int n = 0;
        for (Step s : f.steps()) {
            sb.append("  ".repeat(Math.max(0, s.depth()))).append(++n).append(". ");
            if (!s.activityType().isEmpty()) {
                sb.append(s.activityType()).append(' ');
            }
            sb.append('"').append(s.name()).append('"');
            if (!s.reached()) {
                sb.append(" [unreached]");
            }
            sb.append('\n');
        }
        for (Param p : f.parameters()) {
            sb.append("- parameter: ").append(p.name()).append(" =");
            if (!p.defaultValue().isEmpty()) {
                sb.append(' ').append(p.defaultValue());
            }
            if (p.configured()) {
                sb.append(" [configured]");
            }
            sb.append('\n');
        }
        for (String r : f.resources()) {
            sb.append("- resource: ").append(r).append('\n');
        }
        return sb.toString();
    }

    /** JSON via {@link Json}: keys in record component order, empty values omitted. */
    public static String json(IFlow f) {
        return Json.write(f);
    }
}
