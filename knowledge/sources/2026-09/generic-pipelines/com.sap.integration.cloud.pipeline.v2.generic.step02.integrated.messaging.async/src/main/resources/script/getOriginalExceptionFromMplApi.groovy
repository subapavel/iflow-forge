import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.SecureStoreService
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def headers = message.getHeaders()
    def properties = message.getProperties()

    // Default Values from Externalized configuration to filter out
    final String def_apiTokenUrl = "https://<token_url_from_ProcessIntegrationRuntime_api_plan>/oauth/token"
    final String def_apiHostname = "https://<tenant_url_from_ProcessIntegrationRuntime_api_plan>"
    final String def_apiOAuthCredentialAlias = "<Alias_of_UserCredentials_from_api_plan>"

    // 1. Inputs & Targets
    def maxRetries = headers.get("maxJMSRetries")
    def mplId = headers.get("SAP_MessageProcessingLogID")
    def hostname = properties.get("apiHostname")
    def tokenUrl = properties.get("apiTokenUrl")
    def oauthCredential = properties.get("apiOAuthCredentialAlias")

    // Static target allocations 
    message.setProperty("exceptionType", "RuntimeException")
    message.setProperty("location", "MaxRetriesExceeded")

    String originalException = ""
    boolean apiCallSuccessful = false

    // 2. Controlled API Execution Block
    try {
        // Safe Check: Verify properties exist AND ensure they aren't using the default placeholders
        if (hostname && hostname != def_apiHostname &&
            tokenUrl && tokenUrl != def_apiTokenUrl &&
            oauthCredential && oauthCredential != def_apiOAuthCredentialAlias &&
            mplId) {
            
            // Fetch OAuth Credential properties safely
            def secureStorageService = ITApiFactory.getService(SecureStoreService.class, null)
            def credential = secureStorageService.getUserCredential(oauthCredential)
            String clientId = credential.getUsername()
            String clientSecret = credential.getPassword().toString()

            // Retrieve Access Token via POST
            URL authUrl = new URL(tokenUrl)
            HttpURLConnection authConn = (HttpURLConnection) authUrl.openConnection()
            authConn.requestMethod = "POST"
            authConn.doOutput = true
            authConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

            def authBody = "grant_type=client_credentials" +
                           "&client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                           "&client_secret=" + URLEncoder.encode(clientSecret, "UTF-8")

            authConn.outputStream.withWriter("UTF-8") { writer -> writer << authBody }

            if (authConn.responseCode == HttpURLConnection.HTTP_OK) {
                def authJson = new JsonSlurper().parse(authConn.inputStream)
                String accessToken = authJson.access_token

                if (accessToken) {
                    // Normalize Base Service URL formatting
                    def baseEndpoint = hostname.endsWith("/") ? hostname : "${hostname}/"
                    def apiPath = "api/v1/MessageProcessingLogs('${mplId}')/ErrorInformation/\$value"
                    
                    // Fire raw text fetch request
                    URL apiEndpoint = new URL(baseEndpoint + apiPath)
                    HttpURLConnection apiConn = (HttpURLConnection) apiEndpoint.openConnection()
                    apiConn.requestMethod = "GET"
                    apiConn.setRequestProperty("Authorization", "Bearer ${accessToken}")
                    apiConn.setRequestProperty("Accept", "*/*")

                    if (apiConn.responseCode == HttpURLConnection.HTTP_OK) {
                        originalException = apiConn.inputStream.text?.trim()
                        if (originalException) {
                            apiCallSuccessful = true
                        }
                    }
                }
            }
        }
    } catch (Exception e) {
        // Absorb exceptions cleanly to safeguard flow execution continuity
    }

    // 3. Dynamic Message Structuring
    String baseMessage = "Runtime Exception is thrown due to current retry count exceeding maximum number of JMS retries of '${maxRetries}' times"
    
    if (apiCallSuccessful) {
        message.setProperty("exceptionMessage", "${baseMessage}: Original Exception: ${originalException}")
    } else {
        message.setProperty("exceptionMessage", baseMessage)
    }

    return message
}