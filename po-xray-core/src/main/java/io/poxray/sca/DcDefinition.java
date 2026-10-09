package io.poxray.sca;

import io.poxray.model.DcRef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.w3c.dom.Element;

/**
 * Parsed {@code .dcdef} of a development component.
 *
 * @param type         component sub-type if present (e.g. {@code EJBModule}), else type (e.g. {@code Process Composer})
 * @param dependencies DC dependencies, de-duplicated (design/build/deploy time variants collapsed)
 */
public record DcDefinition(String vendor, String name, String caption, String type, List<DcRef> dependencies) {

    public static final String PROCESS_COMPOSER = "Process Composer";

    public static DcDefinition parse(byte[] content, String path) {
        Element root = XmlDocs.parse(content, path).getDocumentElement();
        Element ct = XmlDocs.child(root, "component-type");
        String type = XmlDocs.childText(ct, "sub-type");
        if (type == null) {
            type = XmlDocs.childText(ct, "type");
        }
        Set<DcRef> deps = new LinkedHashSet<>();
        Element depsEl = XmlDocs.child(root, "dependencies");
        if (depsEl != null) {
            for (Element dep : XmlDocs.children(depsEl, "dependency")) {
                Element ref = XmlDocs.child(dep, "dc-ref");
                if (ref != null) {
                    deps.add(new DcRef(XmlDocs.childText(ref, "vendor"), XmlDocs.childText(ref, "name")));
                }
            }
        }
        return new DcDefinition(
                XmlDocs.childText(root, "vendor"),
                XmlDocs.childText(root, "name"),
                XmlDocs.childText(root, "caption"),
                type,
                new ArrayList<>(deps));
    }

    /** {@code vendor/name}, e.g. {@code eon.com/bpm/digisign}. */
    public String fullName() {
        return vendor + "/" + name;
    }
}
