package io.poxray.moin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MoinParserTest {

    private static final String PARTITION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Partition xmlns:m='http://xml.sap.com/moin'>
            <C n='sap.com/glx/ide' i='1' v='2.0.0'/>
            <M c='1' q='Galaxy.Workflow.AutomatedActivity' i='AAAA'/>
            <Galaxy.Workflow.AutomatedActivity m:c='1' m:i='A1' nameTranslatable='true'>
            <name maxWidth='255' originalText='Send Envelope' resType='generaltext'/>
            <documentation originalText='Odeslání obálky'/>
            </Galaxy.Workflow.AutomatedActivity>
            <XSD1.XsdComplexTypeDefinition m:c='2' m:i='T1' name='response'>
            <attributes v='X1'/>
            <attributes v='X2'/>
            </XSD1.XsdComplexTypeDefinition>
            <properties.PropertyBag m:c='4' m:i='P1' id='PartitionCreationInfo'>
            <properties key='creatorId' value='com.sap.glx.ide'/>
            </properties.PropertyBag>
            <Galaxy.Workflow.SourceOfIncomingConnectors c='1'>
            <L f='A1' s='C1'/>
            <L f='A1' s='local:C2'/>
            </Galaxy.Workflow.SourceOfIncomingConnectors>
            </Partition>
            """;

    @Test
    void parsesElementsTextsValuesAndLinks() {
        MoinPartition p = MoinParser.parse("src/bpmn/x.bpmn", PARTITION.getBytes(StandardCharsets.UTF_8));

        assertThat(p.elements()).hasSize(3);
        MoinElement a = p.elements().getFirst();
        assertThat(a.id()).isEqualTo("A1");
        assertThat(a.metaclass()).isEqualTo("Galaxy.Workflow.AutomatedActivity");
        assertThat(a.simpleType()).isEqualTo("AutomatedActivity");
        assertThat(a.name()).isEqualTo("Send Envelope");
        assertThat(a.text("documentation")).isEqualTo("Odeslání obálky");
        assertThat(a.attributes()).containsOnlyKeys("nameTranslatable");
        assertThat(a.partition()).isEqualTo("src/bpmn/x.bpmn");

        assertThat(p.elements().get(1).values()).containsEntry("attributes", java.util.List.of("X1", "X2"));
        assertThat(p.elements().get(2).properties()).containsEntry("creatorId", "com.sap.glx.ide");

        assertThat(p.links()).containsExactly(
                new MoinLink("Galaxy.Workflow.SourceOfIncomingConnectors", "A1", "C1"),
                new MoinLink("Galaxy.Workflow.SourceOfIncomingConnectors", "A1", "C2"));
    }

    @Test
    void indexResolvesBothDirectionsInFileOrder() {
        MoinIndex index = new MoinIndex();
        index.add(MoinParser.parse("x", PARTITION.getBytes(StandardCharsets.UTF_8)));

        assertThat(index.seconds("Galaxy.Workflow.SourceOfIncomingConnectors", "A1")).containsExactly("C1", "C2");
        assertThat(index.firsts("Galaxy.Workflow.SourceOfIncomingConnectors", "C2")).containsExactly("A1");
        assertThat(index.firsts("Galaxy.Workflow.SourceOfIncomingConnectors", "nope")).isEmpty();
        assertThat(index.get("A1")).isPresent();
        assertThat(index.elementsOf("XSD1.XsdComplexTypeDefinition")).extracting(MoinElement::id).containsExactly("T1");
    }

    @Test
    void detectsPartitionsAndRejectsOtherXml() {
        assertThat(MoinParser.isPartition(PARTITION.getBytes(StandardCharsets.UTF_8))).isTrue();
        byte[] other = "<?xml version='1.0'?><definitions/>".getBytes(StandardCharsets.UTF_8);
        assertThat(MoinParser.isPartition(other)).isFalse();
        assertThatThrownBy(() -> MoinParser.parse("y", other)).isInstanceOf(MoinException.class);
    }
}
