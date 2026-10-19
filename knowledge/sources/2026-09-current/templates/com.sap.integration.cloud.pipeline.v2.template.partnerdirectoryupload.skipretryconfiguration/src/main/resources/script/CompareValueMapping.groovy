import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper
import groovy.xml.MarkupBuilder

def Message processData(Message message) {

    // ── 1. Parse deployed StringParameters (message body) ────────────────────
    def deployed = new XmlSlurper()
                        .parse(message.getBody(Reader))

    // Map: exceptionClass -> value
    def deployedMap = [:]
    deployed.parameter.each { p ->
        def id = p.Id.text().trim()
        if (id) deployedMap[id] = p.Value.text().trim()
    }

    // ── 2. Parse value_mapping.xml content (exchange property) ───────────────
    def vmXml   = message.getProperty("ValueMappingContent")
    if (!vmXml) {
        throw new RuntimeException("Exchange property 'ValueMappingContent' is missing or empty")
    }

    def vm = new XmlSlurper().parse(new StringReader(vmXml))

    // Map: exceptionClass -> value  (source value -> target value)
    // Works for any schema grouping — iterates all groups regardless of schemaGroup
    def mappingMap = [:]
    vm.'**'.findAll { it.name() == 'group' }.each { g ->
        def entries = g.entry.list()
        if (entries.size() >= 2) {
            def srcValue = entries.first().value.text().trim()
            def tgtValue = entries.last().value.text().trim()
            if (srcValue) mappingMap[srcValue] = tgtValue
        }
    }

    // ── 3. Diff ───────────────────────────────────────────────────────────────
    // Only the mapping keys matter — extra deployed keys are ignored (different purpose)
    def onlyInMapping = []
    def valueMismatch = []

    mappingMap.each { key, expectedValue ->
        if (!deployedMap.containsKey(key)) {
            onlyInMapping << [id: key, expectedValue: expectedValue]
        } else if (deployedMap[key] != expectedValue) {
            valueMismatch << [id: key, expectedValue: expectedValue, deployedValue: deployedMap[key]]
        }
    }

    // ── 4. Set result ─────────────────────────────────────────────────────────
    if (!onlyInMapping && !valueMismatch) {
        message.setProperty("ConfigurationMatch", true)
        return message
    }

    message.setProperty("ConfigurationMatch", false)

    def writer  = new StringWriter()
    def builder = new MarkupBuilder(writer)
    builder.omitNullAttributes = true

    builder.differences {
        if (onlyInMapping) {
            delegate.onlyInMapping(count: onlyInMapping.size()) {
                onlyInMapping.each { e ->
                    parameter {
                        Id(e.id)
                        expectedValue(e.expectedValue)
                    }
                }
            }
        }
        if (valueMismatch) {
            delegate.valueMismatch(count: valueMismatch.size()) {
                valueMismatch.each { e ->
                    parameter {
                        Id(e.id)
                        expectedValue(e.expectedValue)
                        deployedValue(e.deployedValue)
                    }
                }
            }
        }
    }

    message.setBody(writer.toString())

    return message
}
