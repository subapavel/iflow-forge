import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.pd.PartnerDirectoryService
import com.sap.it.api.ITApiFactory
import src.main.resources.script.PipelineLogger
import groovy.util.XmlSlurper

def Message processData(Message message) {
    
    def headers = message.getHeaders()
	def properties = message.getProperties()
    String ReceiverAlias = headers.get("SAP_ReceiverAlias")
	String Receiver = headers.get("SAP_Receiver")
	String SenderAlias = headers.get("SAP_SenderAlias")
	String Sender = headers.get("SAP_Sender")
	def location = properties.get("location") ?: "Set IDoc Parameters"
	PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	def aliasKeys = [
        "IDocEndpoint",
        "IDocCCLocationId",
        "IDocCredentialName",
        "IDocPrivateKeyAlias",
		"IDocProxyType",
		"IDocAuthenticationType",
		"IDocContentType"
    ]
	String prefixLS = 'IDocLogicalSystem'

    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
    if (service == null){
		// simply exit as parameters that are checked in this script are optional
        return message
    }
	
	//--------------- 1. Set Default Properties ---------------------
	// (i.e. parameters that can override Default values in IFlow Externalized Configuration) 
	
	// 1.1 Read String Parameters linked to System Alias in Partner Directory (connectivity settings)
	if (ReceiverAlias && Receiver) {
		aliasKeys.each { key ->
			String stringParameterValue = service.getParameter("${key}_${Receiver}", ReceiverAlias, String)
			// if any parameter found - it will replace default value from Externalized Parameters
			if (stringParameterValue != null) {
				logger.addEntry(location, "Parameter ${key}_${Receiver} for receiver alias ${ReceiverAlias} is found in Partner Directory and stored as Property default_Receiver_${key} with value ${stringParameterValue}.")
				message.setProperty("default_Receiver_${key}", stringParameterValue?.trim())
			}
		}
	}
	
	// 1.2 Determine default Logical System name for Sender and Receiver names (expected to be used when SNDPRN and RCVPRN are of type LS)
	if (ReceiverAlias && Receiver) {
		String receiverLogicalSystem = service.getParameter("${prefixLS}_${Receiver}", ReceiverAlias, String)
		if (receiverLogicalSystem != null) { // override default_RCVPRN if parameter exists in PD
			message.setProperty("default_RCVPRN", receiverLogicalSystem.trim())
			logger.addEntry(location, "Default RCVPRN is set to value ${receiverLogicalSystem} from String Parameter ${prefixLS}_${Receiver} of Receiver system alias ${ReceiverAlias} in Partner Directory.")
		}
	}		
	if (SenderAlias && Sender) {
		String senderLogicalSystem = service.getParameter("${prefixLS}_${Sender}", SenderAlias, String)
		if (senderLogicalSystem != null) { // override default_SNDPRN if parameter exists in PD
			message.setProperty("default_SNDPRN", senderLogicalSystem.trim())
			logger.addEntry(location, "Default SNDPRN is set to value ${senderLogicalSystem} from String Parameter ${prefixLS}_${Sender} of Sender system alias ${SenderAlias} in Partner Directory.")
		}
	}
	
	// 1.3 Determine Receiver Interface Name
	String receiverInterface = headers.get("SAP_ReceiverInterface")
	if (!receiverInterface) {
		receiverInterface = "${properties.get('SAP_IDoc_EDIDC_MESTYP')}.${properties.get('SAP_IDoc_EDIDC_IDOCTYP')}${properties.get('SAP_IDoc_EDIDC_CIMTYP') ? '.' + properties.get('SAP_IDoc_EDIDC_CIMTYP') : ''}"
		message.setHeader("SAP_ReceiverInterface", receiverInterface) 																									  
        logger.addEntry(location, "Derived SAP_ReceiverInterface = ${receiverInterface}.")
    } else {
        logger.addEntry(location, "Header SAP_ReceiverInterface already provided = ${receiverInterface}.")
    }
	
	// 1.4 Determine suffix to be used to extract Interface-Specific values from Partner Directory per Partner ID
	def interfaceSuffix = headers.get("SAP_IDoc_EDIDC_pidParameterReadSuffixPattern") ?: properties.get("pidParameterReadSuffixPattern")
	if (!interfaceSuffix || interfaceSuffix in ['MESTYP.IDOCTYP.CIMTYP', 'SAP_ReceiverInterface']) { // default setting, read parameters with ReceiverInterface suffix
		interfaceSuffix = receiverInterface
		logger.addEntry(location, "Using default suffix = ReceiverInterface (${interfaceSuffix}).")
	} else { // suffix to be built dynamically using the supplied EDIDC segments concatenated with a dot if not empty value
        def values = []
        try {
            def body = message.getBody(java.io.Reader)
            def xml = new XmlSlurper().parse(body) // works with or without namespaces
            def params = interfaceSuffix.split("\\.") 
            def ediDc40 = xml.IDOC.EDI_DC40
            if (ediDc40) {
                values = params.collect { p -> ediDc40."$p".text()?.trim() ?: "" }.findAll { it }
            }
        } catch (Exception e) {
            logger.addEntry(location, "Failed to parse IDoc XML body for interfaceSuffix building: ${e.message}")
        }
        interfaceSuffix = values ? values.join(".") : receiverInterface
        logger.addEntry(location, "Built dynamic suffix = ${interfaceSuffix}.")
	}
	
	// 1.5 Determine default values of EDIDC values provided in the Externalized Parameters and set them as individual properties
	def controlHeaderNames = properties.get("default_EDIDC_List_Names")
	def controlHeaderValues = properties.get("default_EDIDC_List_Values")
	if (controlHeaderNames && controlHeaderValues) {
		def names  = controlHeaderNames.split(",", -1) as List
		def values = controlHeaderValues.split(",", -1) as List
		if (names.size() == values.size()) {
			names.eachWithIndex { name, i ->
				def value = values[i]
				if (name) {
					message.setProperty("default_${name.trim()}", value?.trim())
				}
			}
		} else {
			logger.addEntry(location, "Externalized Parameters default_EDIDC_List_Names has value ${controlHeaderNames} and default_EDIDC_List_Values has value ${controlHeaderValues} - the sizes between names and values are not matching after splitting with supported comma delimiter.")
		} 
	}

	//--------------- 2. Set Scenario-Specific Properties ---------------------
	// (precedence for incoming headers with prefix  SAP_IDoc_EDIDC_, if no such header - check Partner Directory by Partner ID) 
	
	// For each parameter of Header SAP_IDoc_EDIDC_overrideControlRecordParameters - check if SAP_IDoc_EDIDC_ exists, if not - read partner directoty with PID searching for parameter with previously defined interfaceSuffix
	String Pid = headers.get("partnerID")
	String overrideEDIDCparams = headers.get("SAP_IDoc_EDIDC_overrideControlRecordParameters")
	if (overrideEDIDCparams && Pid && interfaceSuffix) {
        overrideEDIDCparams.split(",").each { key ->
            key = key.trim()
            String headerName = "SAP_IDoc_EDIDC_${key}"
            if (!headers.containsKey(headerName)) {
                String pdValue = service.getParameter("${key}_${interfaceSuffix}", Pid, String)
                if (pdValue != null) {
                    message.setHeader(headerName, pdValue?.trim())
					logger.addEntry(location, "Control Record segment ${key} is part of header SAP_IDoc_EDIDC_overrideControlRecordParameters and there is no ${headerName} from scenario-specific IFlow, that's why header value ${pdValue} is set from Partner Directory parameter ${key}_${interfaceSuffix} of Partner ID ${Pid}.")
                }
            }
        }		
	} else if (!Pid) {
		logger.addEntry(location, "No PartnerID found in headers, cannot resolve SAP_IDoc_EDIDC_overrideControlRecordParameters from PD.")
	}
	
	if (logger.getLog()) { logger.saveToMessage(message) }  
    return message
}