package io.poxray.xi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Objects held in memory (e.g. from TPZ exports), indexed by type + OID and by type + SWCV + key. */
public final class InMemoryObjectSource implements XiObjectSource {

    private final Map<String, XiObject> byOid = new LinkedHashMap<>();
    private final Map<String, XiObject> byKey = new LinkedHashMap<>();

    public InMemoryObjectSource add(XiObject o) {
        if (o.id() == null || o.id().typeId() == null) {
            return this;
        }
        if (o.id().oid() != null) {
            byOid.put(o.id().typeId() + "|" + o.id().oid(), o);
        }
        byKey.put(keyOf(o.id()), o);
        return this;
    }

    public InMemoryObjectSource addAll(Collection<XiObject> objects) {
        objects.forEach(this::add);
        return this;
    }

    @Override
    public Optional<XiObject> resolve(XiRef ref) {
        if (ref == null || ref.typeId() == null) {
            return Optional.empty();
        }
        if (ref.oid() != null) {
            XiObject o = byOid.get(ref.typeId() + "|" + ref.oid());
            if (o != null) {
                return Optional.of(o);
            }
        }
        return Optional.ofNullable(byKey.get(keyOf(ref)));
    }

    /** All objects of a type, in insertion order. */
    public List<XiObject> ofType(String typeId) {
        List<XiObject> out = new ArrayList<>();
        for (XiObject o : byKey.values()) {
            if (typeId.equals(o.typeId())) {
                out.add(o);
            }
        }
        return out;
    }

    public Collection<XiObject> all() {
        return byKey.values();
    }

    private static String keyOf(XiRef r) {
        String scope = r.isDirectory() ? "D" : String.valueOf(r.swcGuid());
        return r.typeId() + "|" + scope + "|" + r.keyString();
    }
}
