package io.poxray.bpm;

import io.poxray.IssueLog;
import io.poxray.model.Issue;
import io.poxray.model.Ref;
import io.poxray.moin.MoinElement;
import io.poxray.moin.MoinIndex;

/** Shared state while reading one BPM DC. */
final class BpmContext {

    final String dc;
    final String sourceArchive;
    final MoinIndex index;
    final IssueLog issues;

    BpmContext(String dc, String sourceArchive, MoinIndex index, IssueLog issues) {
        this.dc = dc;
        this.sourceArchive = sourceArchive;
        this.index = index;
        this.issues = issues;
    }

    /** {@code <archive>!/<entry>} notation for source locations. */
    String source(String partition) {
        return sourceArchive + "!/" + partition;
    }

    Ref ref(MoinElement e) {
        return e == null ? null : new Ref(e.id(), e.name());
    }

    Issue.Location at(String process, MoinElement element, String path) {
        return IssueLog.at(dc, process, ref(element), path);
    }

    /** Name of the {@code XSD1.key.QName} linked to {@code ownerId} via {@code association} (f=qname). */
    String qname(String association, String ownerId) {
        return index.firstElement(association, ownerId).map(q -> q.attr("name")).orElse(null);
    }
}
