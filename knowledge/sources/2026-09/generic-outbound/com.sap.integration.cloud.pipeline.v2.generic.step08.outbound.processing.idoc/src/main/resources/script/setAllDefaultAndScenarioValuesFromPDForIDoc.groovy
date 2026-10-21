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
	String logMsg
	String prefixScenario = "SAP_Receiver_"
	def scenarioKeys = [
		"IDocCredentialName",
		"IDocPrivateKeyAlias",
		"IDocAuthenticationType",
		"IDocContentType"
	]

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
			String valFromBS = service.getParameter(key, Receiver, String.class)
			String val = valFromBS ?: service.getParameter("${key}_${Receiver}", ReceiverAlias, String.class)
			// if any parameter found - it will replace default value from Externalized Parameters
			if (val != null) {
				message.setProperty("default_Receiver_${key}", val?.trim())
				logMsg = valFromBS ? 
					"String parameter '${key}' for receiver '${Receiver}' found in PD." : 
					"String parameter '${key}_${Receiver}' for alias '${ReceiverAlias}' found in PD."
				logger.addEntry(location, "${logMsg} Value '${val}' stored as Property 'default_Receiver_${key}'.")
			}
		}
	}

	// 1.2 Determine default Logical System name for Sender and Receiver names (expected to be used when SNDPRN and RCVPRN are of type LS)
	if (ReceiverAlias && Receiver) {
		String recLsFromBS = service.getParameter(prefixLS, Receiver, String.class)
		String receiverLogicalSystem = recLsFromBS ?: service.getParameter("${prefixLS}_${Receiver}", ReceiverAlias, String.class)
		if (receiverLogicalSystem != null) { // override default_RCVPRN if parameter exists in PD
			message.setProperty("default_RCVPRN", receiverLogicalSystem.trim())
			logMsg = recLsFromBS ? 
				"String parameter ${prefixLS}' of receiver '${Receiver}' found in PD." : 
				"String parameter '${prefixLS}_${Receiver}' of alias '${ReceiverAlias}' found in PD."
			logger.addEntry(location, "Default RCVPRN is set to value '${receiverLogicalSystem}' from ${logMsg}")
		}
	}
	if (SenderAlias && Sender) {
		String sndLsFromBS = service.getParameter(prefixLS, Sender, String.class)
		String senderLogicalSystem = sndLsFromBS ?: service.getParameter("${prefixLS}_${Sender}", SenderAlias, String.class)
		if (senderLogicalSystem != null) { // override default_SNDPRN if parameter exists in PD
			message.setProperty("default_SNDPRN", senderLogicalSystem.trim())
			logMsg = sndLsFromBS ? 
				"String parameter ${prefixLS}' of sender '${Sender}' found in PD." : 
				"String parameter '${prefixLS}_${Sender}' of alias '${SenderAlias}' found in PD."
			logger.addEntry(location, "Default SNDPRN is set to value '${senderLogicalSystem}' from ${logMsg}")
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
	String Pid = headers.get("partnerID")
	String ifSufPd = null
	if (Pid) { // check if suffix is maintained in PD
		ifSufPd = service.getParameter("pidParameterReadSuffixPattern", Pid, String.class) ?: service.getParameter("PidParameterReadSuffixPattern", Pid, String.class)
	}
	// Priority chain: Header > Partner Directory > Property
	def interfaceSuffix = headers.get("SAP_IDoc_EDIDC_pidParameterReadSuffixPattern") ?: ifSufPd ?: properties.get("pidParameterReadSuffixPattern")
	if (!interfaceSuffix || interfaceSuffix in ['MESTYP.IDOCTYP.CIMTYP', 'SAP_ReceiverInterface']) { // default setting, read parameters with ReceiverInterface suffix
		interfaceSuffix = receiverInterface
		logger.addEntry(location, "Using default suffix = ReceiverInterface (${interfaceSuffix}).")
	} else { // suffix to be built dynamically using the supplied EDIDC segments concatenated with a dot if not empty value
		def values = []
		try {
			def body = message.getBody(java.io.Reader)
			def xml = new XmlSlurper().parse(body) // works with or without namespaces
			def params = interfaceSuffix.split("\\.") 
			def ediDc40 = xml.'*'.EDI_DC40
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
	String overrideEDIDCparams = headers.get("SAP_IDoc_EDIDC_overrideControlRecordParameters")
	if(!overrideEDIDCparams) { // check if overrideControlRecordParameters if configured in the PD
		if (Pid && interfaceSuffix) { // check if suffix is maintained in PD
			overrideEDIDCparams = service.getParameter("overrideControlRecordParameters_${interfaceSuffix}", Pid, String.class) ?: service.getParameter("OverrideControlRecordParameters_${interfaceSuffix}", Pid, String.class)
			if(overrideEDIDCparams){
				message.setHeader("SAP_IDoc_EDIDC_overrideControlRecordParameters", overrideEDIDCparams?.trim())
				logger.addEntry(location, "Header SAP_IDoc_EDIDC_overrideControlRecordParameters is set to value 'overrideEDIDCparams' from scenario-specific String parameter 'overrideControlRecordParameters_${interfaceSuffix}' found under PID '${Pid}' in PD.")
			}
		}
	}
	if (overrideEDIDCparams && Pid && interfaceSuffix) {
		overrideEDIDCparams.split(",").each { key ->
			key = key.trim()
			String headerName = "SAP_IDoc_EDIDC_${key}"
			if (!headers.containsKey(headerName)) {
				String pdValue = service.getParameter("${key}_${interfaceSuffix}", Pid, String.class)
				if (pdValue != null) {
					message.setHeader(headerName, pdValue?.trim())
					logger.addEntry(location, "Control Record segment ${key} is part of header SAP_IDoc_EDIDC_overrideControlRecordParameters and there is no ${headerName} from scenario-specific IFlow, that's why header value ${pdValue} is set from Partner Directory parameter ${key}_${interfaceSuffix} of Partner ID ${Pid}.")
				}
			}
		}
	} else if (!Pid) {
		logger.addEntry(location, "No PartnerID found in headers, cannot resolve SAP_IDoc_EDIDC_overrideControlRecordParameters from PD.")
	}

	// Read connectivity-related String Parameters linked to Scenario in Partner Directory
	Receiver = ReceiverAlias ?: Receiver
	scenarioKeys.each { key ->
		String headerName = "${prefixScenario}${key}".toString()
		if (!headers.get(headerName) && Pid && Receiver && interfaceSuffix){
			String val = service.getParameter("${key}_${Receiver}_${interfaceSuffix}", Pid, String.class)
			if (val != null) {
				message.setHeader(headerName, val.trim())
				logger.addEntry(location, "Scenario-specific String parameter '${key}_${Receiver}_${interfaceSuffix}' found for PID '${Pid}'. Value '${val}' stored in header '${headerName}'.")
			}
		}
	}

	if (logger.getLog()) { logger.saveToMessage(message) }
	return message
}