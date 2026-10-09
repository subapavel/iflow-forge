package io.poxray.model;

import java.util.List;

/** One NW BPM process = one {@code src/bpmn/*.bpmn} partition; {@code id} is the Collaboration MOF ID. */
public record Process(
        String id,
        String name,
        String documentation,
        Pool pool,
        String source,
        List<Lane> lanes,
        List<Role> roles,
        List<Scope> scopes,
        List<DataObject> dataObjects,
        List<Node> nodes,
        List<Flow> flows,
        List<Annotation> annotations,
        EditorArtifacts editorArtifacts) {

    public record Pool(String id, String name, String identifier, String subject, String description) {
    }

    public record Lane(String id, String name, String documentation) {
    }

    /** {@code kind}: {@code process} (ProcessRole) or {@code lane} (LaneProcessRole). */
    public record Role(String id, String name, String kind, String documentation, Boolean dynamic, Mapping assignment) {
    }

    /** {@code kind}: {@code process} (active pool), {@code pool} (other pools), {@code embeddedSubprocess}. */
    public record Scope(String id, String kind, String name, String parent) {
    }

    /** {@code kind}: {@code dataObject} or {@code textVariable}. */
    public record DataObject(
            String id, String name, String kind, Ref scope, String documentation, TypeRef type, Mapping initialValue) {
    }

    public record TypeRef(String ref, String name, String namespace) {
    }

    public record Annotation(String id, String text, List<Ref> attachedTo) {
    }

    public record EditorArtifacts(List<Ref> contractedJoins) {
    }
}
