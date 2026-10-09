package io.poxray.xi;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.w3c.dom.Element;

/**
 * A parsed {@code p1:xiObj}: identity, administrative data, typed links, texts and the type-specific content.
 *
 * @param id          identity incl. SWCV / Directory version context
 * @param vid         version ID
 * @param modifiedBy  last changed by
 * @param modifiedAt  last changed at (as in the source, e.g. {@code 2021-08-24 15:33:09.811})
 * @param description object description (text with empty label, master language)
 * @param texts       further texts by label (e.g. field descriptions referenced from XSD), master language
 * @param links       outgoing links in source order
 * @param content     root element inside {@code p1:content} (null if none)
 * @param inner       inner objects ({@code p1:inner}, e.g. operations of a service interface)
 * @param raw         the original XML (kept for lossless snapshots)
 */
public record XiObject(
        XiRef id,
        String vid,
        String modifiedBy,
        String modifiedAt,
        String description,
        Map<String, String> texts,
        List<XiLink> links,
        Element content,
        List<XiObject> inner,
        String raw) {

    /** Links with the given role, in source order. */
    public List<XiLink> links(String role) {
        return links.stream().filter(l -> l.role().equals(role)).toList();
    }

    /** Links whose role starts with {@code prefix} (e.g. {@code MAP} for MAP0, MAP1 ...). */
    public List<XiLink> linksStartingWith(String prefix) {
        return links.stream().filter(l -> l.role().startsWith(prefix)).toList();
    }

    public Optional<XiLink> link(String role) {
        return links.stream().filter(l -> l.role().equals(role)).findFirst();
    }

    public String typeId() {
        return id.typeId();
    }
}
