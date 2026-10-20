package io.poxray.bpm;

import static io.poxray.bpm.SyntheticNwdsPlugins.BPEMCONTENT_JAR;
import static io.poxray.bpm.SyntheticNwdsPlugins.F_BLANK;
import static io.poxray.bpm.SyntheticNwdsPlugins.F_MINUS;
import static io.poxray.bpm.SyntheticNwdsPlugins.F_PLUS;
import static io.poxray.bpm.SyntheticNwdsPlugins.SAP_PRINCIPAL;
import static io.poxray.bpm.SyntheticNwdsPlugins.T_DEADLINE;
import static io.poxray.bpm.SyntheticNwdsPlugins.T_FAULT;
import static io.poxray.bpm.SyntheticNwdsPlugins.T_MESSAGE;
import static io.poxray.bpm.SyntheticNwdsPlugins.UNKNOWN_TYPE;
import static io.poxray.bpm.SyntheticNwdsPlugins.XSD_INT;
import static io.poxray.bpm.SyntheticNwdsPlugins.XSD_STRING;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Java generator reproduces the output of the former Python generator (removed by KB-13, ISSUE-1) byte for byte
 * ({@code json.dump(out, fh, indent=1, ensure_ascii=False)}, LF, no trailing newline) on a synthetic plugins folder.
 */
class BuiltinFunctionGeneratorTest {

    private static final String TYPES_PLUGIN = "   \"sourcePlugin\": \"com.sap.tc.ap.servicecomp_1.0.0.jar\",";
    private static final String TYPES_DC = "   \"sourceDc\": \"sap.com/tc/bpem/content/predefs/types\"";
    private static final String DEFAULT_PLUGIN = "   \"sourcePlugin\": \"com.sap.tc.ap.bpemcontent_1.0.0.jar\",";
    private static final String DEFAULT_DC = "   \"sourceDc\": \"sap.com/tc/bpem/content/predefs/default\"";
    /** JSON form of {@link SyntheticNwdsPlugins#SAP_PRINCIPAL_NAME}: \b \f named, other controls as lower-case \\u00xx. */
    private static final String PRINCIPAL_JSON = "\"Principal\\b\\f\\u0001\\u001b\"";

    static String expectedJson() {
        return String.join("\n",
                "{",
                " \"source\": {",
                "  \"plugin\": \"com.sap.tc.ap.servicecomp_1.0.0.jar\",",
                "  \"dc\": \"sap.com/tc/bpem/content/predefs/types\"",
                " },",
                " \"additionalSources\": [",
                "  {",
                "   \"plugin\": \"com.sap.tc.ap.bpemcontent_1.0.0.jar\",",
                "   \"dc\": \"sap.com/tc/bpem/content/predefs/default\"",
                "  }",
                " ],",
                " \"functions\": [",
                // (operators, minus): default DC, read last, sorted first
                "  {",
                "   \"id\": \"" + F_MINUS + "\",",
                "   \"namespace\": \"sap.com/bpem/glx/built-in/operators\",",
                "   \"category\": \"operators\",",
                "   \"name\": \"minus\",",
                "   \"symbol\": \"-\",",
                "   \"infix\": false,",
                "   \"documentation\": null,",
                "   \"parameters\": [",
                "    {",
                "     \"name\": \"value\",",
                "     \"type\": \"string\"",
                "    }",
                "   ],",
                "   \"result\": " + PRINCIPAL_JSON + ",",
                "   \"javaClass\": \"com.example.bpm.MinusFunction\",",
                "   \"file\": \"src/functions/com/example/minus.function\",",
                DEFAULT_PLUGIN,
                DEFAULT_DC,
                "  },",
                // (operators, plus): parameters in particle order, not document or name order
                "  {",
                "   \"id\": \"" + F_PLUS + "\",",
                "   \"namespace\": \"sap.com/bpem/glx/built-in/operators\",",
                "   \"category\": \"operators\",",
                "   \"name\": \"plus\",",
                "   \"symbol\": \"+\",",
                "   \"infix\": true,",
                "   \"documentation\": \"Adds two numbers.\",",
                "   \"parameters\": [",
                "    {",
                "     \"name\": \"summand\",",
                "     \"type\": \"int\"",
                "    },",
                "    {",
                "     \"name\": \"addend\",",
                "     \"type\": \"" + UNKNOWN_TYPE + "\"",
                "    }",
                "   ],",
                "   \"result\": \"int\",",
                "   \"javaClass\": \"com.example.bpm.PlusFunction\",",
                "   \"file\": \"rep/functions/sap.com/bpem/glx/built-in/operators/plus.function\",",
                TYPES_PLUGIN,
                TYPES_DC,
                "  },",
                // (text, blank): first by name, last by category
                "  {",
                "   \"id\": \"" + F_BLANK + "\",",
                "   \"namespace\": \"sap.com/bpem/glx/built-in/text\",",
                "   \"category\": \"text\",",
                "   \"name\": \"blank\",",
                "   \"symbol\": \"blank\",",
                "   \"infix\": false,",
                "   \"documentation\": \"Say \\\"hi\\\" \\\\ C:\\\\temp\\nnext line\\ttab\\rcr \u00fc/ok\",",
                "   \"parameters\": [],",
                "   \"result\": null,",
                "   \"javaClass\": null,",
                "   \"file\": \"rep/functions/sap.com/bpem/glx/built-in/text/blank.function\",",
                TYPES_PLUGIN,
                TYPES_DC,
                "  }",
                " ],",
                " \"triggers\": [",
                "  {",
                "   \"id\": \"" + T_FAULT + "\",",
                "   \"kind\": \"error\",",
                "   \"name\": \"MessageTriggerFault\",",
                "   \"documentation\": \"Fault of the message trigger\",",
                "   \"isCritical\": false,",
                "   \"isTechnical\": true,",
                "   \"parameter\": null,",
                "   \"file\": \"src/triggers/MessageTrigger.eventdef\",",
                DEFAULT_PLUGIN,
                DEFAULT_DC,
                "  },",
                "  {",
                "   \"id\": \"" + T_MESSAGE + "\",",
                "   \"kind\": \"message\",",
                "   \"name\": \"MessageTrigger\",",
                "   \"documentation\": \"Default message trigger\",",
                "   \"isCritical\": true,",
                "   \"isTechnical\": false,",
                "   \"parameter\": {",
                "    \"name\": \"MessageTrigger\",",
                "    \"namespace\": \"urn:example:bpm:trigger\",",
                "    \"type\": \"string\"",
                "   },",
                "   \"file\": \"src/triggers/MessageTrigger.eventdef\",",
                DEFAULT_PLUGIN,
                DEFAULT_DC,
                "  },",
                "  {",
                "   \"id\": \"" + T_DEADLINE + "\",",
                "   \"kind\": \"timer\",",
                "   \"name\": \"Deadline\",",
                "   \"documentation\": null,",
                "   \"isCritical\": false,",
                "   \"isTechnical\": false,",
                "   \"parameter\": null,",
                "   \"file\": \"src/triggers/Deadline.eventdef\",",
                DEFAULT_PLUGIN,
                DEFAULT_DC,
                "  }",
                " ],",
                " \"xsdTypes\": {",
                "  \"" + XSD_STRING + "\": \"string\",",
                "  \"" + XSD_INT + "\": \"int\"",
                " },",
                " \"sapTypes\": {",
                "  \"" + SAP_PRINCIPAL + "\": " + PRINCIPAL_JSON,
                " }",
                "}");
    }

    @Test
    void renderMatchesPythonOutputByteForByte(@TempDir Path dir) throws IOException {
        Path plugins = SyntheticNwdsPlugins.write(dir.resolve("plugins"));
        Path out = dir.resolve("bpm-builtin-functions.json");
        String expected = expectedJson();

        assertThat(BuiltinFunctionGenerator.render(plugins)).isEqualTo(expected);

        BuiltinFunctionGenerator.Result result = BuiltinFunctionGenerator.generate(plugins, out);
        assertThat(Files.readAllBytes(out)).isEqualTo(expected.getBytes(UTF_8));
        assertThat(result).isEqualTo(new BuiltinFunctionGenerator.Result(3, 3, 2, 1));
        assertThat(result.summary()).isEqualTo("3 functions, 3 triggers, 2 XSD types, 1 SAP types");
    }

    @Test
    void missingJarIsReported(@TempDir Path dir) throws IOException {
        Path plugins = SyntheticNwdsPlugins.write(dir.resolve("plugins"));
        Files.delete(plugins.resolve(BPEMCONTENT_JAR));

        assertThatThrownBy(() -> BuiltinFunctionGenerator.render(plugins))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("com.sap.tc.ap.bpemcontent_*.jar");
        assertThatThrownBy(() -> BuiltinFunctionGenerator.generate(plugins, dir.resolve("out.json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("com.sap.tc.ap.bpemcontent_*.jar");
        assertThat(dir.resolve("out.json")).doesNotExist();
    }

    @Test
    void missingServicecompJarIsReported(@TempDir Path dir) throws IOException {
        Path plugins = SyntheticNwdsPlugins.write(dir.resolve("plugins"));
        Files.delete(plugins.resolve(SyntheticNwdsPlugins.SERVICECOMP_JAR));
        Files.delete(plugins.resolve(SyntheticNwdsPlugins.OLD_SERVICECOMP_JAR));

        assertThatThrownBy(() -> BuiltinFunctionGenerator.render(plugins))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("com.sap.tc.ap.servicecomp_*.jar");
    }

    @Test
    void summaryLineFormat() {
        assertThat(new BuiltinFunctionGenerator.Result(224, 11, 91, 78).summary())
                .isEqualTo("224 functions, 11 triggers, 91 XSD types, 78 SAP types");
    }
}
