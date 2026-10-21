import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper
import groovy.xml.MarkupBuilder

def Message processData(Message message) {

    def feed = new XmlSlurper()
                    .parse(message.getBody(Reader))
                    .declareNamespace(
                        m: "http://schemas.microsoft.com/ado/2007/08/dataservices/metadata",
                        d: "http://schemas.microsoft.com/ado/2007/08/dataservices"
                    )

    def entries = feed.entry
    def count   = entries.size()

    message.setProperty("stringParameterCount", count)

    if (count == 0) {
        message.setBody("<stringParameters/>")
        message.setHeader("Content-Type", "text/xml")
        return message
    }

    def writer  = new StringWriter()
    def builder = new MarkupBuilder(writer)

    builder.stringParameters {
        entries.each { e ->
            def props = e.content.properties
            parameter {
                Id(props.Id.text())
                Value(props.Value.text())
            }
        }
    }

    message.setBody(writer.toString())

    return message
}
