package io.poxray.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import static io.poxray.model.BehaviorSnapshots.required;

/** Source and configured wiring candidates; never a runtime execution claim. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record BehaviorReport(int formatVersion, String analysisVersion, BpmDataReport data,
        BpmFaultReport faults, DiscoveryReport discovery, Integration integration,
        List<ProcessWiring> processes, List<Issue> issues) {
    public BehaviorReport {
        discovery = BehaviorSnapshots.discovery(discovery);
        processes = required(processes); issues = required(issues);
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Integration(String input, String schemaVersion, String availability, String selection,
            IntegrationInventory.Source source, IntegrationInventory.Query query, List<IcoSource> icos,
            List<ChannelSource> channels, List<ChannelUse> channelUses, List<Issue> issues, List<String> reasons) {
        public Integration {
            source = BehaviorSnapshots.source(source);
            icos = required(icos); channels = required(channels); channelUses = required(channelUses);
            issues = required(issues); reasons = required(reasons);
        }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IcoSource(int ordinal, IntegrationInventory.Ico configuration, List<String> reasons) {
        public IcoSource { configuration = BehaviorSnapshots.ico(configuration); reasons = required(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChannelSource(int ordinal, IntegrationInventory.Channel configuration, List<String> reasons) {
        public ChannelSource { configuration = BehaviorSnapshots.channel(configuration); reasons = required(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ChannelUse(int ordinal, int ico, Integer receiver, Integer assignment, String role,
            String parameter, Integer reference, IntegrationInventory.DirRef source, List<Integer> candidates,
            String resolution, String association, String direction, List<String> reasons) {
        public ChannelUse { candidates = required(candidates); reasons = required(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ProcessWiring(BpmPathReport.ProcessKey key, String state, List<InterfaceUse> interfaces,
            List<StepCall> calls, List<String> reasons) {
        public ProcessWiring { interfaces = required(interfaces); calls = required(calls); reasons = required(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record InterfaceUse(int ordinal, String direction, String context, DiscoveryReport.InterfaceRow source,
            List<InterfaceCandidate> candidates, List<Integer> steps, List<IcoMatch> matches, String state, List<String> reasons) {
        public InterfaceUse {
            source = BehaviorSnapshots.interfaceRow(source);
            candidates = required(candidates); steps = required(steps); matches = required(matches); reasons = required(reasons);
        }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record InterfaceCandidate(int ordinal, Interface source) {
        public InterfaceCandidate { source = BehaviorSnapshots.iface(source); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record IcoMatch(int discovery, Integer ico, List<Route> routes, String state, List<String> reasons) {
        public IcoMatch { routes = required(routes); reasons = required(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Route(Integer receiver, Integer assignment, Integer target, String relation, List<String> reasons) {
        public Route { reasons = required(reasons); }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record StepCall(int step, BpmSourceEvidence.Locator location, List<BpmSourceEvidence.Link> relations,
            List<BpmSourceEvidence.Element> elements, List<Integer> interfaces, List<ServiceCandidate> services,
            String state, String acknowledgment, String transaction, List<String> reasons) {
        public StepCall {
            relations = required(relations); elements = required(elements); interfaces = required(interfaces);
            services = required(services); reasons = required(reasons);
        }
    }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record ServiceCandidate(int ordinal, ServiceReference source, String state, List<String> reasons) {
        public ServiceCandidate { source = BehaviorSnapshots.service(source); reasons = required(reasons); }
    }
}
