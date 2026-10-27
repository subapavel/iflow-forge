package io.poxray.integration;

import io.poxray.SyntheticTpz;
import io.poxray.xi.InMemoryObjectSource;
import io.poxray.xi.TpzArchive;
import java.nio.file.Path;
import java.util.List;

/** Synthetic envelopes only; content does not claim a SAP external-resource wrapper convention. */
final class SyntheticExternalContracts {
    static final List<String> A = List.of("Contract", "urn:esr", "Same", "urn:message:a", "");
    static final List<String> B = List.of("Contract", "urn:esr", "Same", "urn:message:b", "tail");
    static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="urn:wire">
              <xs:element name="WireRoot" type="xs:string"/>
            </xs:schema>
            """;

    static String object(String type, String oid, List<String> key, String links, String content, String inner) {
        return "<p:xiObj xmlns:p=\"urn:sap-com:xi\"><p:idInfo><p:vc vcType=\"S\" swcGuid=\"aaaa\" caption=\"Synthetic\"/>"
                + key(type, oid, key) + "</p:idInfo><p:generic><p:lnks>" + links
                + "</p:lnks></p:generic><p:content>" + content + "</p:content><p:inner>" + inner
                + "</p:inner></p:xiObj>";
    }

    static String key(String type, String oid, List<String> key) {
        return "<p:key typeID=\"" + type + "\"" + (oid == null ? "" : " oid=\"" + oid + "\"") + ">"
                + key.stream().map(k -> "<p:elem>" + k + "</p:elem>").reduce("", String::concat) + "</p:key>";
    }

    static String link(String role, String type, List<String> key) {
        return "<p:lnkRole role=\"" + role + "\"><p:lnk><p:vc vcType=\"S\" swcGuid=\"aaaa\"/>"
                + key(type, null, key) + "</p:lnk></p:lnkRole>";
    }

    static String definition(String content) {
        return object("ifmextdef", "definition-oid", List.of("Contract", "urn:esr"), "", content,
                object("ifmextmes", "message-a", A, "", "", "")
                        + object("ifmextmes", "message-b", B, "", "", ""));
    }

    static InMemoryObjectSource source(Path path, String... objects) {
        return new InMemoryObjectSource().addAll(TpzArchive.open(
                SyntheticTpz.write(path, "", List.of(objects))).objects());
    }
}
