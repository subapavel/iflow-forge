package io.poxray.xi;

import java.util.List;

/**
 * Identity of an ESR or Integration Directory object, as used in the {@code p1:xiObj} envelope (identical for
 * online {@code read/ext}, SimpleQuery results and TPZ exports).
 *
 * @param vcType    {@code S} = ESR (software component version), {@code D} = Integration Directory
 * @param swcGuid   SWCV GUID (ESR only), without dashes
 * @param swcCaption SWCV caption, e.g. {@code PPAS_AEM, 1.0 of ppas.cz} (may be null)
 * @param typeId    object type, e.g. {@code AllInOne}, {@code MAPPING}, {@code ifmmessif}
 * @param oid       object ID (stable across versions; may be null in some links)
 * @param key       key elements in order (meaning depends on the type)
 */
public record XiRef(String vcType, String swcGuid, String swcCaption, String typeId, String oid, List<String> key) {

    public static final String ESR = "S";
    public static final String DIRECTORY = "D";

    /** Key elements joined with {@code |} (the format used by {@code read/ext}). */
    public String keyString() {
        return String.join("|", key);
    }

    /** Key element at {@code index}, or empty string if absent. */
    public String key(int index) {
        return index < key.size() ? key.get(index) : "";
    }

    public boolean isDirectory() {
        return DIRECTORY.equals(vcType);
    }

    /** Human-readable label: {@code typeId name|namespace...} without empty key elements. */
    public String label() {
        return typeId + " " + String.join("|", key.stream().filter(k -> !k.isEmpty()).toList());
    }
}
