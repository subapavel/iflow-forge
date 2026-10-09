package io.poxray.integration;

import static org.assertj.core.api.Assertions.assertThat;

import io.poxray.model.IntegrationInventory.Condition;
import io.poxray.sca.XmlDocs;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

class ConditionsTest {

    private static final String ICO = """
            <AllInOne>
              <ReceiverAssignmentList>
                <ReceiverAssignment><ReceiverConditionId>A</ReceiverConditionId>
                  <ReceiverIds><ReceiverId>R1</ReceiverId><ReceiverId>R2</ReceiverId></ReceiverIds></ReceiverAssignment>
                <ReceiverAssignment><ReceiverConditionId>B</ReceiverConditionId>
                  <ReceiverIds><ReceiverId>R1</ReceiverId></ReceiverIds></ReceiverAssignment>
                <ReceiverAssignment><ReceiverConditionId/>
                  <ReceiverIds><ReceiverId>R2</ReceiverId></ReceiverIds></ReceiverAssignment>
              </ReceiverAssignmentList>
              <Conditions>
                <RDS_CONDSHORT><CONDITIONID>A</CONDITIONID>
                  <CONDLINE><COMPGROUP>1</COMPGROUP><COMPOP>EQ</COMPOP>
                    <LEXTRACTOR><TRD_EXTRACTOR><TYPE>XP</TYPE><VALUE>/a</VALUE></TRD_EXTRACTOR></LEXTRACTOR>
                    <REXTRACTOR><TRD_EXTRACTOR><TYPE>KO</TYPE><VALUE>1</VALUE></TRD_EXTRACTOR></REXTRACTOR></CONDLINE>
                  <CONDLINE><COMPGROUP>1</COMPGROUP><COMPOP>EX</COMPOP>
                    <LEXTRACTOR><TRD_EXTRACTOR><TYPE>XP</TYPE><VALUE>/b</VALUE></TRD_EXTRACTOR></LEXTRACTOR></CONDLINE>
                </RDS_CONDSHORT>
                <RDS_CONDSHORT><CONDITIONID>B</CONDITIONID>
                  <CONDLINE><COMPGROUP>1</COMPGROUP><COMPOP>NX</COMPOP>
                    <LEXTRACTOR><TRD_EXTRACTOR><TYPE>XP</TYPE><VALUE>/c</VALUE></TRD_EXTRACTOR></LEXTRACTOR></CONDLINE>
                </RDS_CONDSHORT>
              </Conditions>
            </AllInOne>
            """;

    private static Element ico() {
        return XmlDocs.parse(ICO.getBytes(StandardCharsets.UTF_8), "ico").getDocumentElement();
    }

    @Test
    void receiverAssignmentsKeepUnconditionalEntries() {
        Map<String, List<String>> assignments = Conditions.receiverAssignments(ico());

        assertThat(assignments.get("R1")).containsExactly("A", "B");
        assertThat(assignments.get("R2")).isEqualTo(Arrays.asList("A", null));
    }

    @Test
    void alternativeConditionsAreCombinedWithOr() {
        Map<String, Condition> conditions = Conditions.read(ico());

        Condition or = Conditions.or(List.of(conditions.get("A"), conditions.get("B")));

        assertThat(or.text()).isEqualTo("(/a = \"1\" AND exists /b) OR not exists /c");
        assertThat(or.lines()).extracting(l -> l.group()).containsExactly(1, 1, 3);
    }
}
