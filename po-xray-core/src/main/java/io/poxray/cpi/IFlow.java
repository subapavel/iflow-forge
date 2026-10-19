package io.poxray.cpi;

import java.util.List;

/**
 * One SAP Integration Suite artifact as read by {@link IFlowReader}: an integration flow, or a script collection or
 * value mapping (which have no {@code .iflw} and therefore no participants, steps or parameters).
 *
 * @param id           {@code Bundle-SymbolicName} of {@code META-INF/MANIFEST.MF} up to the first {@code ;}
 * @param name         {@code Bundle-Name}
 * @param version      {@code Bundle-Version}
 * @param participants one per {@code bpmn2:messageFlow} (sender and receiver channels), document order
 * @param steps        flow steps in sequence-flow order (see {@link IFlowReader})
 * @param parameters   externalized parameters of {@code parameters.propdef}, document order
 * @param resources    files under {@code src/main/resources/} except the {@code .iflw}, {@code parameters.propdef}
 *                     and {@code parameters.prop}; relative paths with forward slashes, sorted
 */
public record IFlow(String id, String name, String version, List<Participant> participants, List<Step> steps,
        List<Param> parameters, List<String> resources) {

    public IFlow {
        participants = participants == null ? List.of() : List.copyOf(participants);
        steps = steps == null ? List.of() : List.copyOf(steps);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}
