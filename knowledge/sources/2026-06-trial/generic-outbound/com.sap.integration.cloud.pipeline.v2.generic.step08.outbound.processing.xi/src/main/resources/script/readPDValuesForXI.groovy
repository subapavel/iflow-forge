import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.pd.PartnerDirectoryService
import com.sap.it.api.ITApiFactory
import src.main.resources.script.PipelineLogger

def Message processData(Message message) {
    
    def headers = message.getHeaders()
	def properties = message.getProperties()
    String ReceiverAlias = headers.get("SAP_ReceiverAlias")
	String Receiver = headers.get("SAP_Receiver")
	String Sender = headers.get("SAP_Sender")
	
	// if SAP_Sender or SAP_Receiver are deleted, the values of Externalized Parameters are taken and set as headers
	if (!Receiver) {
		Receiver = properties.get("default_Receiver")
		message.setHeader("SAP_Receiver", Receiver) 
	}
	if (!Sender) {
		Sender = properties.get("default_Sender")
		message.setHeader("SAP_Sender", Sender) 
	}
	
	// check if Sender or Receiver contains Party in format Party.SystemName
	Receiver = determineParty(Receiver).system
	Sender = determineParty(Sender).system
	
	def location = properties.get("location") ?: "Read XI Parameters"
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	def aliasKeys = [
        "XIEndpoint",
        "XICCLocationId",
        "XICredentialName",
        "XIPrivateKeyAlias",
		"XIProxyType",
		"XIAuthenticationType"
    ]

    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
    if (service == null){
		// simply exit as parameters that are checked in this script are optional
        return message
    }
	
	// Read String Parameters linked to Alias in Partner Directory
	if (ReceiverAlias && Receiver) {
		aliasKeys.each { key ->
			String stringParameterValue = service.getParameter("${key}_${Receiver}", ReceiverAlias, String)
			// if any parameter found - it will replace default value from Externalized Parameters
			if (stringParameterValue != null) {
				logger.addEntry(location, "Parameter ${key}_${Receiver} for receiver alias ${ReceiverAlias} is found in Partner Directory and stored as Property default_Receiver_${key} with value ${stringParameterValue}.")
				message.setProperty("default_Receiver_${key}", stringParameterValue)
			}
		}
	}
	
	// Determine Receiver Interface Name
	String Pid = headers.get("partnerID")
	String inboundInterfaceScenarioSpecific = headers.get("SAP_ReceiverInterface")
	String messageType = headers.get("SAP_MessageType")
	Receiver = ReceiverAlias ?: Receiver //reassing receiver to Receiver Alias for reading Receiver Service Interface
	// Read Interface Name configured like "InterfaceNameDetermination_Receiver" for ByPassOption (other options can specify /Interface/Name for SAP_ReceiverInterface)
	if (!inboundInterfaceScenarioSpecific && Pid && Receiver) {
		 inboundInterfaceScenarioSpecific = service.getParameter("InterfaceNameDetermination_${Receiver}", Pid , String) // if No ReceiverInterface try to assign from Partner Directory
	}
	// finally - either use inboundInterfaceScenarioSpecific if found, or assign SAP_MessageType as interface name
	String inboundInterface = inboundInterfaceScenarioSpecific ?: messageType
	if (inboundInterface) { 
		logger.addEntry(location, "Scenario-specific Receiver Interface has value ${inboundInterfaceScenarioSpecific}; SAP Message Type has value ${messageType}; Result value of Header SAP_ReceiverInterface is ${inboundInterface}.")
		message.setHeader("SAP_ReceiverInterface", inboundInterface) 
	}
	
	// Determine Receiver Interface Namespace
	String interfaceNamespace = headers.get("SAP_ReceiverInterfaceNamespace")
	if (!interfaceNamespace && Pid && inboundInterface) { // if no header SAP_ReceiverInterfaceNamespace - check Partner Directory
		// determination scheme "interfaceNamespaceDetermination_ReceiverInterface" (can be interface-specific namespace)
		interfaceNamespace = service.getParameter("InterfaceNamespaceDetermination_${inboundInterface}", Pid , String)
	}	

	if (interfaceNamespace) { 
		logger.addEntry(location, "Result value of Header SAP_ReceiverInterfaceNamespace for interface ${inboundInterface} is set to value ${interfaceNamespace}.")
		message.setHeader("SAP_ReceiverInterfaceNamespace", interfaceNamespace) 
	}
	
	if (logger.getLog()) { logger.saveToMessage(message) }  
    return message
}

Map determineParty(String systemHeader) {
    String party = null
	String system = systemHeader

    if (systemHeader?.contains(".")) {
        def kv = systemHeader.split(/\./, 2)
        party  = kv[0]?.trim()
        system = kv[1]?.trim()
    }

    return [ system: system, party: party ]
}