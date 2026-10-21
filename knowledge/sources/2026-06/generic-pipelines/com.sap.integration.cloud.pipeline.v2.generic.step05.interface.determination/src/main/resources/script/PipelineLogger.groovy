package src.main.resources.script
import com.sap.gateway.ip.core.customdev.util.Message
import org.apache.camel.Exchange
import org.apache.camel.builder.SimpleBuilder

class PipelineLogger {
    private static final String NEW_SEPARATOR = "<EOR>"
    private static final String OLD_SEPARATOR = ";;"
    final List entries
    final String logLevel
    final String iFlow
    final String logTypeName
    private final String headerKey
    private String separator  // dynamic separator

    static PipelineLogger newLogger(Message message, String logTypeName = "auditLog") {
        return new PipelineLogger(message, logTypeName)
    }

    private PipelineLogger() {}

    private PipelineLogger(Message message, String logTypeName) {
        this.entries = []
        this.logTypeName = logTypeName
        this.headerKey = logTypeName + "Header"

        def headers = message.getHeaders()
        if (headers.containsKey(headerKey) && headers.get(headerKey)) {
            String existingLog = headers.get(headerKey).toString().trim()
            // Detect which separator is used
            if (existingLog.contains(OLD_SEPARATOR)) {
                this.separator = OLD_SEPARATOR
            } else {
                this.separator = NEW_SEPARATOR
            }
            // Keep existing header log content as starting point, strip trailing separator
            this.entries.add(existingLog.replaceAll("${this.separator}\$", ""))
        } else {
            this.separator = NEW_SEPARATOR  // default for new logs
        }

        Exchange ex = message.exchange
        this.iFlow = SimpleBuilder.simple('${camelId}').evaluate(ex, String)
    }

    void addEntry(String location, String entry) {
        def timeNow = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", TimeZone.getTimeZone('GMT'))
        this.entries.add(
            "${logTypeName.toUpperCase()}|" + timeNow + "|" + location + "|" + entry
        )
    }

    void addLongEntry(String location, String entry) {
        def timeNow = new Date().format("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", TimeZone.getTimeZone('GMT'))
        this.entries.add(
            "${logTypeName.toUpperCase()}|" + timeNow + "|" + this.iFlow + "|" + location + "|" + entry
        )
    }

    String getLog() {
        return entries.join(separator)
    }

    String getLog4Print() {
        return getLog().replaceAll(separator, '\n')
    }

    void saveToMessage(Message message) {
        message.setHeader(headerKey, getLog())
    }

    // For backwards compatibility
    String getAuditLog() { getLog() }
    String getAuditLog4Print() { getLog4Print() }
}