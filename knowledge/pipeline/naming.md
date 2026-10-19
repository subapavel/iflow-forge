# Naming

_Stub, filled in Task 8._

## Placeholders (draft from Pass A; Task 8 completes)

- `{{tenant.pipeline.prefix}}`: the pipeline queue and address prefix; parameters PipelinePrefix (Step01 XI), DefaultJMSQueuePrefix (Step01 IDoc), PipelineJMSQueuePrefix (Step02)
- `{{tenant.endpoint.xi.inbound}}`: address of the Step01 XI sender adapter (parameter XIEntryPoint)
- `{{tenant.endpoint.idoc.inbound}}`: address of the Step01 IDoc sender adapter (parameter IDocEntryPoint)
- `{{tenant.endpoint.custom.preprocessing.global}}`: ProcessDirect address of the global pre-processing exit (parameter CustomXPreGlobal_Endpoint)
- `{{tenant.endpoint.custom.error}}`: ProcessDirect address of the custom error flow (parameter CustomXError_Endpoint)
- `{{tenant.security.sender.role}}`: role required of senders (parameter UserRole)
- `{{tenant.security.cert.subjectdn}}`: subject DN of the sender client certificate (parameter SubjectDN)
- `{{tenant.security.cert.issuerdn}}`: issuer DN of the sender client certificate (parameter IssuerDN)
- `{{tenant.xi.communication.party}}`: XI receiver communication party of the Step01 XI sender adapter (parameter CommunicationParty)
- `{{tenant.idoc.logicalsystem.default}}`: value of parameter Default_LogicalSystemName (Step01 IDoc)
- `{{tenant.name}}`: the tenant name the runtime reads from the environment variable TENANT_NAME; Partner Directory parameter id of the landscape stages (PID SAP_Integration_Suite_Landscape) and part of the stageRule_<tenant name> keys (Step02 async and sync, Step04)
