package io.poxray.po;

import java.util.List;
import java.util.Map;

/**
 * Key element names per object type as reported by SimpleQuery {@code typeInfo} (PO 7.50). Online results carry
 * them with every response; a TPZ export does not, so {@link TpzGateway} answers with these. Types not listed
 * fall back to generic {@code KEY<i>} names.
 */
public final class TypeKeys {

    private static final List<String> NAME_NAMESPACE = List.of("NAME", "NAMESPACE");

    public static final Map<String, List<String>> NAMES = Map.ofEntries(
            Map.entry("RoutingRelation",
                    List.of("FROMPARTY", "FROMSRVC", "FROMACTIONNS", "FROMACTION", "TOPARTY", "TOSRVC")),
            Map.entry("MappingRelation",
                    List.of("FROMACTIONNS", "FROMACTION", "FROMPARTY", "FROMSRVC", "TOPARTY", "TOSRVC")),
            Map.entry("InboundBinding",
                    List.of("FROMPARTY", "FROMSRVC", "ACTIONNS", "ACTIONNAME", "TOPARTY", "TOSRVC", "DIRECTION")),
            Map.entry("OutboundBinding",
                    List.of("TOPARTY", "TOSRVC", "ACTIONNS", "ACTIONNAME", "FROMPARTY", "FROMSRVC", "DIRECTION")),
            Map.entry("AllInOne",
                    List.of("FROMPARTY", "FROMSRVC", "FROMACTIONNS", "FROMACTION", "TOPARTY", "TOSRVC")),
            Map.entry("XI_TRAFO", NAME_NAMESPACE),
            Map.entry("MAPPING", NAME_NAMESPACE),
            Map.entry("FUNC_LIB", NAME_NAMESPACE),
            Map.entry("TRAFO_JAR", NAME_NAMESPACE),
            Map.entry("MAP_ARCHIVE_PRG", NAME_NAMESPACE),
            Map.entry("ifmmessif", NAME_NAMESPACE),
            Map.entry("ifmoper", NAME_NAMESPACE),
            Map.entry("ifmmessage", NAME_NAMESPACE),
            Map.entry("ifmfaultm", NAME_NAMESPACE),
            Map.entry("ifmtypedef", NAME_NAMESPACE),
            Map.entry("ifmextdef", NAME_NAMESPACE));

    private TypeKeys() {
    }
}
