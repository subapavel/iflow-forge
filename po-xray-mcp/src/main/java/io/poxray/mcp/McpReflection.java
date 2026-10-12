package io.poxray.mcp;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Native image: the MCP-specific result records serialised by Jackson (the output model: po-xray-native). */
@RegisterForReflection(targets = {
    Views.IcoOverview.class, Views.ChannelSummary.class, Views.DataTypeDetail.class, Views.ScaOverview.class,
    Views.ProcessSummary.class, Views.EjbSummary.class, Views.ProcessData.class, Views.StepDetail.class,
    Views.CatalogOverview.class, Views.InterfaceSummary.class, Views.FunctionSummary.class,
    Views.DataTypeSummary.class, Views.DataTypeView.class,
    Inventories.Components.class, PoXrayTools.Ambiguous.class, PoXrayTools.WithIssues.class,
    PoXrayTools.ResultFile.class
})
final class McpReflection {

    private McpReflection() {
    }
}
