import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper
import groovy.xml.StreamingMarkupBuilder

def Message processData(Message message) {
    // Parse the incoming XML body
    def headers = message.getHeaders()
    def body = message.getBody(java.io.Reader)
    def xml = new XmlSlurper().parse(body)
    boolean rebuildXml = false
    
    // fetch receiver interface name
    def receiverInterface = headers.get("SAP_ReceiverInterface") ?: headers.get("SAP_MessageType")

    // Find each IDOC node
    xml.IDOC.each { idocNode ->
        // Check if the EDI_DC40 child node is missing.
        if (idocNode.EDI_DC40.size() == 0) {
            
            rebuildXml = true
            // Get all other existing children from the original IDOC node
            def existingChildren = idocNode.children()

            // Use 'replaceNode' to rebuild the IDOC node completely.
            // This gives us full control over the order of child elements.
            idocNode.replaceNode {
                // Recreate the IDOC node, preserving its original attributes (like BEGIN="1")
                IDOC(idocNode.attributes()) {
                    // Add the new EDI_DC40 node and its children TABNAM and DIRECT first.
                    EDI_DC40(SEGMENT: '1', '')
                    {
                        TABNAM('EDI_DC40')
                        DIRECT('2')
                        MESTYP(determineIdocMessageType(receiverInterface).MESTYP)
                        IDOCTYP(determineIdocMessageType(receiverInterface).IDOCTYP)
                        CIMTYP(determineIdocMessageType(receiverInterface).CIMTYP)
                    }
                    // 'mkp.yield' inserts the collection of original children after the new node.
                    mkp.yield(existingChildren)
                }
            }
        }
    }
    
    if (rebuildXml) {
    
        // Rebuild the final XML from the modified structure
        def updatedXml = new StreamingMarkupBuilder().bind {
            // 'mkp.yield' renders the entire modified 'xml' object.
            mkp.yield(xml)
        }
    
        // Set the modified XML as the new message body
        message.setBody(updatedXml.toString())
    
    }    
    
    return message

}

// Determine MESTYP, IDOCTYP, and CIMTYP
Map determineIdocMessageType(String input) {
    String CIMTYP = null
	String MESTYP = null
	String IDOCTYP = null

    if (input?.contains(".")) {
        def kv = input.split(/\./, 2)
        MESTYP  = kv[0]?.trim()
        IDOCTYP = kv[1]?.trim()
        if (IDOCTYP?.contains(".")) {
            kv = IDOCTYP.split(/\./, 2)
            IDOCTYP  = kv[0]?.trim()
            CIMTYP = kv[1]?.trim()
        }
    }

    return [ MESTYP: MESTYP, IDOCTYP: IDOCTYP, CIMTYP: CIMTYP ]
}