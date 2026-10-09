package io.poxray.moin;

import java.util.List;

/** A parsed MOIN partition file: flat elements and association links, both in file order. */
public record MoinPartition(String path, List<MoinElement> elements, List<MoinLink> links) {
}
