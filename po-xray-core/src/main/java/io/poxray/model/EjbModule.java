package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * One EJB module DC: its beans classified by role (BPM function, adapter module, XI proxy, scheduler job, ...),
 * other classes, JPA and scheduler descriptors. Sources are referenced, not embedded.
 *
 * @param ear         EAR DC wrapping the module ({@code vendor/name})
 * @param application deployed application name as used in BPM function lookup names ({@code vendor/ear~name})
 */
public record EjbModule(
        String dc,
        String ear,
        String application,
        List<Bean> beans,
        List<JavaClass> classes,
        List<JpaEntity> jpaEntities,
        List<PersistenceUnit> persistenceUnits,
        List<JobDefinition> jobDefinitions,
        List<String> technologies) {

    /**
     * @param type      stateless | stateful | singleton | messageDriven
     * @param roles     bpmFunction | adapterModule | xiProxyProvider | webService | schedulerJob | other
     * @param injects   bean names (or types) injected via {@code @EJB}
     */
    public record Bean(
            String name,
            @JsonProperty("class") String className,
            String type,
            String jndiName,
            Interfaces interfaces,
            List<String> roles,
            List<String> technologies,
            List<String> moduleParameters,
            WebService webService,
            JobDefinition job,
            Map<String, String> activationConfig,
            Map<String, String> properties,
            List<String> rfcFunctions,
            List<String> jcoDestinations,
            List<String> injects,
            String persistenceUnit,
            List<Usage> usedBy,
            SourceRef source) {
    }

    public record Interfaces(List<String> local, List<String> remote) {
    }

    public record WebService(
            String serviceName, String portName, String endpointInterface, String targetNamespace, String wsdlLocation) {
    }

    /** {@code kind}: bpmFunction (with the referencing catalog function). */
    public record Usage(String kind, String dc, Ref function) {
    }

    public record SourceRef(String path, String sha256) {
    }

    /** {@code kind}: class | interface | enum | record. */
    public record JavaClass(@JsonProperty("class") String className, String kind, SourceRef source) {
    }

    public record JpaEntity(@JsonProperty("class") String className, String table) {
    }

    public record PersistenceUnit(String name, String dataSource, List<String> classes) {
    }

    public record JobDefinition(String name, String description) {
    }
}
