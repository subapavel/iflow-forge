import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper
import groovy.json.JsonBuilder

def Message processData(Message message) {

    def differences = new XmlSlurper().parse(message.getBody(Reader))
    def pid         = message.getProperty("PD_PID")

    def parameter = differences.'**'.find { it.name() == 'parameter' }

    def payload = [
        Pid  : pid,
        Id   : parameter.Id.text(),
        Value: parameter.expectedValue.text()
    ]

    message.setProperty("PD_ID",parameter.Id.text())
    message.setBody(new JsonBuilder(payload).toPrettyString())
    message.setHeader("Content-Type", "application/json")

    return message
}
