package io.poxray.migration;

import io.poxray.PoXray;
import io.poxray.SyntheticBehavior;
import io.poxray.json.Json;
import io.poxray.model.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import tools.jackson.databind.node.ObjectNode;
import static org.junit.jupiter.api.Assertions.*;

/** Literal exports through real readers and P3/P4; JSON editing is only for malformed variants. */
final class BehaviorComparisonFixtures {
    static final String W = SyntheticBehavior.W, R = SyntheticBehavior.R, M = SyntheticBehavior.M;
    static final List<String> KINDS = List.of("presence", "conditions", "data", "loops", "faults", "calls", "channels", "contracts");
    record Source(BehaviorReport behavior, ContractCatalogReport contracts, Path archive, Path tpz) { }

    static Source source(Path dir, int variant) throws Exception {
        return source(dir, variant, "Operation-" + (variant == 2 ? 0 : variant));
    }

    static Source source(Path dir, int variant, String callName) throws Exception {
        Files.createDirectories(dir);
        var fixture = new SyntheticBehavior()
                .node(W + "AutomatedActivity", "island")
                .raw(W + "ConditionalSequenceConnector", "condition", "isDefault='false'", "")
                .relation(W + "ScopeContainsScopeObjects", "condition", "p-pool")
                .relation(W + "SourceOfIncomingConnectors", "call", "condition")
                .relation(W + "TargetOfOutgoingConnectors", "end", "condition")
                .raw(R + "Expression", "condition-expr", "stringExpression='condition-" + variant + "'", "")
                .relation(W + "ConditionalSequenceConnectorContainsExpression", "condition-expr", "condition")
                .raw(W + "MultiInstanceLoopCharacteristics", "loop", "sequential='" + (variant != 1) + "' flowCondition='all'", "")
                .relation(W + "EmbeddedScopeContainsLoopCharcateristics", "loop", "call")
                .raw(M + "Mapping", "initial", "", "")
                .relation(W + "ViewContainsMapping", "initial", "data")
                .raw(M + "MappingPart", "initial-part", "category='set' enabled='true'", "")
                .relation(M + "MappingContainsMappingParts", "initial-part", "initial")
                .relation(M + "MappingPartHasQName", "target-q", "initial-part")
                .raw(R + "Expression", "initial-expr", "", "")
                .relation(M + "MappingPartContainsExpression", "initial-expr", "initial-part")
                .raw(R + "Literal", "initial-value", "value='default-" + variant + "'", "")
                .relation(R + "ExpressionHasSteps", "initial-value", "initial-expr");
        if (variant == 1) fixture.raw(W + "Pool", "alternate-pool", "active='false'", "<name originalText='Alternate scope'/>")
                .relation(W + "CollaborationContainsPools", "alternate-pool", "p-collab");
        var pair = fixture.write(dir, "channel-" + variant);
        // Change literal source XML, never an analyzed record, to exercise renamed explicit alignment.
        byte[] archive = renameArchive(Files.readAllBytes(pair.archive()), "Call|external", callName);
        if (variant == 1) archive = renameArchive(archive,
                io.poxray.SyntheticBpmSca.link(W + "ScopeContainsScopeObjects", "call", "p-pool"),
                io.poxray.SyntheticBpmSca.link(W + "ScopeContainsScopeObjects", "call", "alternate-pool"));
        Files.write(pair.archive(), archive);
        var sca = PoXray.read(pair.archive());
        var behavior = BehaviorAnalysis.of(List.of(sca), List.of("synthetic"), pair.icos(), "synthetic-icos");
        var process = behavior.data().paths().processes().getFirst();
        var call = process.steps().stream().filter(s -> "call".equals(s.id())).findFirst().orElseThrow();
        var evidence = List.of(new ContractCatalogInput.Evidence("design-decision", "synthetic.md", "section-" + variant, "proposed"));
        var ref = new ContractCatalogInput.StepRef(process.key(), call.ordinal());
        var claim = new ContractCatalogInput.ContractClaim("payload", "script", "proposed-internal", null,
                new IntegrationInventory.XmlName("urn:synthetic", "Payload" + variant),
                List.of(new ContractCatalogInput.PortClaim("request", "Caller", "invoke", ref, evidence)),
                List.of(new ContractCatalogInput.ValueClaim("/flag", "default", ref, List.of(ref), "claim-default-" + variant, evidence)), evidence);
        var catalog = ContractCatalog.of(pair.icos(), behavior.data(), new ContractCatalogInput(
                List.of(new ContractCatalogInput.ArtifactClaim("script", "local-script", null, evidence)), List.of(claim), null));
        assertEquals(callName, call.name());
        assertTrue(process.steps().stream().anyMatch(s -> "island".equals(s.id())));
        if (variant != 1) assertTrue(process.steps().stream().anyMatch(s -> "island".equals(s.id()) && "disconnected".equals(s.connectivity())));
        assertFalse(process.loops().isEmpty());
        assertFalse(behavior.faults().processes().getFirst().declarations().isEmpty());
        assertTrue(Json.write(behavior).contains("condition-" + variant));
        assertTrue(Json.write(behavior.data()).contains("default-" + variant));
        assertEquals(behavior.data(), catalog.data());
        return new Source(behavior, catalog, pair.archive(), pair.tpz());
    }

    private static byte[] renameArchive(byte[] bytes, String old, String replacement) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var input = new ZipInputStream(new ByteArrayInputStream(bytes)); var zip = new ZipOutputStream(output)) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null;) {
                byte[] body = input.readAllBytes();
                if (body.length > 3 && body[0] == 'P' && body[1] == 'K') body = renameArchive(body, old, replacement);
                else if (entry.getName().endsWith(".bpmn") || entry.getName().endsWith(".xml"))
                    body = new String(body, StandardCharsets.UTF_8).replace(old, replacement).getBytes(StandardCharsets.UTF_8);
                zip.putNextEntry(new ZipEntry(entry.getName())); zip.write(body); zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    static ObjectNode tree(Object value) { return (ObjectNode) Json.mapper().valueToTree(value); }
    static BehaviorReport malformed(BehaviorReport original, java.util.function.Consumer<ObjectNode> edit) {
        ObjectNode node = tree(original); edit.accept(node); return Json.mapper().treeToValue(node, BehaviorReport.class);
    }
    static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
