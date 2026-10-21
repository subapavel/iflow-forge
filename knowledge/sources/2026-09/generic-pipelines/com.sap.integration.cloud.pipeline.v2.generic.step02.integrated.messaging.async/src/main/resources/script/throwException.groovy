import com.sap.gateway.ip.core.customdev.util.Message
import java.io.IOException

def Message processData(Message message) {
    def properties = message.getProperties()
    String exceptionMessage = properties.get("exceptionMessage")
    String exceptionType = properties.get("exceptionType") ?: "Exception"
	String location = properties.get("location") ?: "Script Throwing an Exception"
	
    if (!exceptionMessage) {
        // default: take body as message
		exceptionMessage = "Throw an exception with message body: ${message.getBody(String) ?: "Unexpected error"}"
    }
	exceptionMessage = "${exceptionMessage} at ${location}."

    def ex
    switch (exceptionType) {
        case "IllegalArgumentException":
            ex = new IllegalArgumentException(exceptionMessage)
            break
        case "RuntimeException":
            ex = new RuntimeException(exceptionMessage)
            break
        case "IOException":
            ex = new IOException(exceptionMessage)
            break
        case "NullPointerException":
            ex = new NullPointerException(exceptionMessage)
            break
        default:
            ex = new Exception(exceptionMessage)
    }

    throw ex
}