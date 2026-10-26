package io.poxray.migration;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Read-only projection of a versioned decision ledger, not a migration readiness verdict. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DossierReport(int formatVersion, String migrationId, String ledgerSha256, boolean valid,
        String historyStatus, List<Decision> decisions, List<Risk> risks, List<DerivedDocument> derivedDocuments,
        String nextAction, List<Diagnostic> diagnostics) {

    public DossierReport {
        decisions = List.copyOf(decisions);
        risks = List.copyOf(risks);
        derivedDocuments = List.copyOf(derivedDocuments);
        diagnostics = List.copyOf(diagnostics);
    }

    public record Scope(String sectionId, String revision, String path, String sha256) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Decision(String id, String status, String text, String source, List<Scope> proposedScopes,
            List<Scope> activeScopes, List<String> approvalEventIds) {
        public Decision {
            proposedScopes = List.copyOf(proposedScopes);
            activeScopes = List.copyOf(activeScopes);
            approvalEventIds = List.copyOf(approvalEventIds);
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Risk(String id, String text, String source, List<String> sections) {
        public Risk {
            sections = List.copyOf(sections);
        }
    }

    public record Dependency(String decisionId, String sectionId, String revision, String sha256) {
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record DerivedDocument(String id, String path, String status, boolean required, List<Dependency> consumes) {
        public DerivedDocument {
            consumes = List.copyOf(consumes);
        }
    }

    public record Diagnostic(String severity, String code, String eventId, String message) {
    }
}
