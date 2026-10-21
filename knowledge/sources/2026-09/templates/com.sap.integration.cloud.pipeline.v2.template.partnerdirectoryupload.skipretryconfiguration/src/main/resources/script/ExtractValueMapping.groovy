import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper
import groovy.xml.MarkupBuilder
import java.util.zip.ZipInputStream

def Message processData(Message message) {

    // Extract value_mapping.xml from ZIP
    def zipIn    = new ZipInputStream(message.getBody(InputStream))
    def vm       = null
    def zipEntry

    while ((zipEntry = zipIn.nextEntry) != null) {
        if (zipEntry.name == "value_mapping.xml") {
            vm = new XmlSlurper().parse(new InputStreamReader(zipIn, "UTF-8"))
            break
        }
        zipIn.closeEntry()
    }
    zipIn.close()

    if (!vm) {
        throw new RuntimeException("value_mapping.xml not found in ZIP")
    }

    // Group by target schema (generalised — works for any schema values)
    def schemaKey = { group ->
        group.entry.list().last().schema.text()
    }

    def grouped = new LinkedHashMap()   // preserves insertion order
    vm.group.each { group ->
        def schema = schemaKey(group)
        if (!grouped.containsKey(schema)) grouped[schema] = []
        grouped[schema] << group
    }

    // Rebuild XML
    def writer  = new StringWriter()
    def builder = new MarkupBuilder(writer)
    builder.omitNullAttributes = true

    builder.vm(version: vm.@version.text()) {
        grouped.each { schema, groups ->
            schemaGroup(schema: schema) {
                groups.each { g ->
                    group(id: g.@id.text()) {
                        g.entry.each { e ->
                            def attrs = [:]
                            if (e.@isDefault.text()) attrs.isDefault = e.@isDefault.text()
                            entry(attrs) {
                                agency(e.agency.text())
                                delegate.schema(e.schema.text())
                                value(e.value.text())
                            }
                        }
                    }
                }
            }
        }
    }

    message.setBody("")
    message.setProperty("ValueMappingContent", writer.toString())

    return message
}
