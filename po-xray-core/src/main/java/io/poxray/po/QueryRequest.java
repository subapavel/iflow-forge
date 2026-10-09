package io.poxray.po;

import java.util.List;

/**
 * SimpleQuery request ({@code generalQueryRequest}), see docs/SOURCES-ESR-DIRECTORY.md §1.2. A condition is
 * mandatory (the server rejects requests without one); {@code IS_NOT_NULL} caused HTTP 500, use {@code LIKE *}.
 *
 * @param area      ESR or Directory
 * @param types     object types, e.g. {@code AllInOne}, {@code MAPPING}
 * @param key       condition key, e.g. {@code NAME}, {@code FROMSRVC}
 * @param value     condition value, may contain {@code *}
 * @param operator  {@code LIKE}, {@code EQ}, ...
 * @param caseInsensitive case-insensitive comparison
 * @param attributes result columns
 */
public record QueryRequest(Area area, List<String> types, String key, String value, String operator,
        boolean caseInsensitive, List<String> attributes) {

    /** Result columns useful for discovery: reference, name/namespace, IDs, change info, description, links. */
    public static final List<String> DEFAULT_ATTRIBUTES =
            List.of("RA_XILINK", "NAME", "NAMESPACE", "OBJECTID", "VERSIONID", "MODIFYDATE", "MODIFYUSER", "TEXT");

    public static QueryRequest like(Area area, List<String> types, String key, String pattern) {
        return new QueryRequest(area, types, key, pattern, "LIKE", true, DEFAULT_ATTRIBUTES);
    }

    public QueryRequest withAttributes(List<String> attributes) {
        return new QueryRequest(area, types, key, value, operator, caseInsensitive, attributes);
    }

    /** The request XML; {@code user} is the change list context user. */
    public String toXml(String user) {
        StringBuilder sb = new StringBuilder("<generalQueryRequest><types>");
        for (String t : types) {
            sb.append("<type id=\"").append(escape(t)).append("\"/>");
        }
        sb.append("</types>");
        if (area == Area.REPOSITORY) {
            sb.append("<qc qcType=\"S\" delMode=\"N\" useSyncT=\"true\" clCtxOnly=\"false\">")
                    .append("<clCxt consider=\"A\" user=\"").append(escape(user)).append("\"/>")
                    .append("<swcListDef def=\"L\"/></qc>"); // L = all newest SWCVs
        } else {
            sb.append("<qc qcType=\"D\" delMode=\"N\" useSyncT=\"true\" clCtxOnly=\"false\">")
                    .append("<clCxt consider=\"A\" user=\"").append(escape(user)).append("\"/></qc>");
        }
        sb.append("<condition><complex><elementary><single><key>").append(escape(key)).append("</key>")
                .append("<val><simple><strg>").append(escape(value)).append("</strg></simple></val>")
                .append("<op isCaseIns=\"").append(caseInsensitive).append("\">").append(escape(operator))
                .append("</op></single></elementary></complex></condition><result>");
        for (String a : attributes) {
            sb.append("<attrib>").append(escape(a)).append("</attrib>");
        }
        return sb.append("</result></generalQueryRequest>").toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
