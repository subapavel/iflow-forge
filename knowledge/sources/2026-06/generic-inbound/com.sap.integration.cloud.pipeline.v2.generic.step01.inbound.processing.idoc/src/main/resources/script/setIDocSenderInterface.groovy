import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {
    // headers
    def map = message.getHeaders()

    // Set sender interface name
    message.setHeader("SAP_SenderInterface", map.get("SAP_IDoc_EDIDC_MESTYP") + "." + map.get("SAP_IDoc_EDIDC_IDOCTYP") + (map.get("SAP_IDoc_EDIDC_CIMTYP") ? "." +map.get("SAP_IDoc_EDIDC_CIMTYP") : ""))
    // Set sender interface namespace
    message.setHeader("SAP_SenderInterfaceNamespace", "urn:sap-com:document:sap:idoc:messages")
    // Set QoS 
    message.setHeader("SapQualityOfService", map.get("SapPlainSoapQoS") ?: "ExactlyOnce")
    message.setProperty("customHeader_QualityOfService", map.get("SapQualityOfService"))

    return message;
}