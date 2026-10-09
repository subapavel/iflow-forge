package io.poxray.ejb;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JavaSourceTest {

    private static final String MODULE = """
            package com.acme.po;

            import java.util.List;
            import javax.ejb.Local;
            import javax.ejb.Stateless;
            import com.sap.aii.af.lib.mp.module.Module;
            import com.sap.aii.af.lib.mp.module.ModuleLocal;
            import com.sap.conn.jco.JCoDestinationManager;

            /**
             * Not a class: public class Fake {}
             */
            @Stateless(name = "AcmeModule")
            @Local(value = { ModuleLocal.class })
            public class AcmeModuleBean implements Module, java.io.Serializable {
                private static final String DEST = "ERP";
                // getContextData("commented") must be ignored
                @EJB
                OtherBeanLocal other;
                @EJB(beanName = "x") private com.acme.po.ThirdLocal third;

                public Object process(ModuleContext ctx) {
                    String a = ctx.getContextData("param1");
                    String b = ctx.getContextData(KEY);
                    JCoDestinationManager.getDestination(DEST).getRepository().getFunction("Z_ACME");
                    String s = "class Inside { }";
                    return null;
                }
            }
            """;

    @Test
    void extractsTypeAnnotationsAndSupertypes() {
        JavaSource js = JavaSource.parse("AcmeModuleBean.java", MODULE);

        assertThat(js.qualifiedName()).isEqualTo("com.acme.po.AcmeModuleBean");
        assertThat(js.kind).isEqualTo("class");
        assertThat(js.annotations).extracting(JavaSource.Annotation::name).containsExactly("Stateless", "Local");
        assertThat(js.annotation("Stateless").flatMap(a -> a.attr("name"))).contains("AcmeModule");
        assertThat(js.annotation("Local").orElseThrow().classes()).containsExactly("ModuleLocal");
        assertThat(js.interfaces).containsExactly("Module", "java.io.Serializable");
        assertThat(js.resolve("Module")).isEqualTo("com.sap.aii.af.lib.mp.module.Module");
        assertThat(js.resolve("OtherBeanLocal")).isEqualTo("com.acme.po.OtherBeanLocal");
        assertThat(js.imports("com.sap.conn.jco")).isTrue();
    }

    @Test
    void extractsCallArgumentsResolvingConstantsAndIgnoringComments() {
        JavaSource js = JavaSource.parse("AcmeModuleBean.java", MODULE);

        assertThat(js.callArguments("getContextData")).containsExactly("param1", "{KEY}");
        assertThat(js.callArguments("getDestination")).containsExactly("ERP");
        assertThat(js.callArguments("getFunction")).containsExactly("Z_ACME");
        assertThat(js.injectedEjbs()).containsExactly("OtherBeanLocal", "com.acme.po.ThirdLocal");
    }

    @Test
    void parsesNestedAnnotationArgumentsAndInterfaceExtends() {
        JavaSource mdb = JavaSource.parse("Job.java", """
                package p;
                @MessageDriven(activationConfig = {
                    @ActivationConfigProperty(propertyName = "messageSelector", propertyValue = "JobDefinition='J1'") })
                public class Job extends MDBJobImplementation { }
                """);
        assertThat(mdb.annotation("MessageDriven").orElseThrow().args()).contains("JobDefinition='J1'");
        assertThat(mdb.superclass).isEqualTo("MDBJobImplementation");
        assertThat(mdb.interfaces).isEmpty();

        JavaSource iface = JavaSource.parse("L.java", "package p; @Local public interface L extends A, B<String> {}");
        assertThat(iface.kind).isEqualTo("interface");
        assertThat(iface.hasAnnotation("Local")).isTrue();
        assertThat(iface.interfaces).containsExactly("A", "B");
    }
}
