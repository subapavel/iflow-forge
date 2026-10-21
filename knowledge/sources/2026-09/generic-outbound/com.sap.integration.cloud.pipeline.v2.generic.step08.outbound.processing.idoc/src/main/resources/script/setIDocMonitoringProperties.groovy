import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {
    def properties = message.getProperties()
    def edidcKeys = [
        "EDIDC_MESCOD",
        "EDIDC_MESFCT",
        "EDIDC_ARCKEY",
        "EDIDC_RCVPRT",
        "EDIDC_RCVPFC",
        "EDIDC_RCVPRN",
        "EDIDC_RCVPOR",
        "EDIDC_SNDPRT",
        "EDIDC_SNDPFC",
        "EDIDC_SNDPRN",
        "EDIDC_SNDPOR",
        "EDIDC_CIMTYP",
        "EDIDC_IDOCTYP",
        "EDIDC_MESTYP",
        "EDIDC_TABNAM",
        "EDIDC_DIRECT"
    ]

    // Read EDIDC properties into a map, removing prefix
    def edidcValues = edidcKeys.collectEntries { key ->
        def val = properties.get(key)
        [(key.replace("EDIDC_", "")) : val?.trim()] // trim whitespace
    }

    // Collect all mandatory keys where the value is empty
    def emptyMandatoryValueKeys = ["TABNAM","DIRECT","MESTYP","IDOCTYP","SNDPOR","SNDPRT","SNDPRN","RCVPOR","RCVPRT","RCVPRN"].findAll { !edidcValues[it] }
    //if (emptyMandatoryValueKeys) message.setProperty("customHeader_EmptyMandatoryControlRecords", emptyMandatoryValueKeys.join(","))
    // Raise an error in case of an empty mandatory IDoc control record field
    if (emptyMandatoryValueKeys) throw new IllegalStateException("The following mandatory IDoc control record fields are empty which would result into an error in the receiving system: " + emptyMandatoryValueKeys.join(","))

    // Build new monitoring properties, skipping empty segments
    def senderParts = ["SNDPRT","SNDPFC","SNDPRN","SNDPOR"].collect { edidcValues[it] }.findAll { it }
    if (senderParts) message.setProperty("customHeader_IDocSenderPartner", senderParts.join("."))

    def receiverParts = ["RCVPRT","RCVPFC","RCVPRN","RCVPOR"].collect { edidcValues[it] }.findAll { it }
    if (receiverParts) message.setProperty("customHeader_IDocReceiverPartner", receiverParts.join("."))

    def typeParts = ["MESTYP","MESCOD","MESFCT"].collect { edidcValues[it] }.findAll { it }
    def idocTypeParts = ["IDOCTYP","CIMTYP"].collect { edidcValues[it] }.findAll { it }
    def allTypeParts = []
    if (typeParts) allTypeParts << typeParts.join("-")
    if (idocTypeParts) allTypeParts << idocTypeParts.join(".")
    if (allTypeParts) message.setProperty("customHeader_IDocTypeDetails", allTypeParts.join("."))

    if (edidcValues.ARCKEY) message.setProperty("customHeader_ARCKEY", edidcValues.ARCKEY)

    return message
}