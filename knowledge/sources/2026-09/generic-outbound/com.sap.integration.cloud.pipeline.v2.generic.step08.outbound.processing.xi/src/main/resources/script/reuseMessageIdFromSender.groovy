import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.pd.PartnerDirectoryService
import com.sap.it.api.ITApiFactory
 
def Message processData(Message message) {
    
    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
    if (service == null){
        throw new IllegalStateException("Partner Directory Service not found")
    }
    
    // Get pid
    def messageLog = messageLogFactory.getMessageLog(message)
    def headers = message.getHeaders()
    def Pid = headers.get("partnerID")
    if (Pid == null){
        throw new IllegalStateException("Partner ID not found in sent message")  
    }
    
    // Preset flag to false
    message.setProperty("MapUniqueIdToMessageIdEx", false)
    
    // Fetch headers
    def PassMessageIdThrough = service.getParameter("PassMessageIdFromE2E", Pid , String.class)
    def SapSenderMessageIdEx = headers.get("SAP_SenderMessageIdEx")
    def SapSplitExpression = headers.get("SapSplitExpression")
    
    // In case of split, add parent id to custom header properties
    if (SapSenderMessageIdEx != null && SapSenderMessageIdEx != '' && SapSplitExpression != null) {
        messageLog.addCustomHeaderProperty("ParentIdBeforeMessageSplit", SapSenderMessageIdEx)
    }
    
    // Message id should be passed through if all apply:
    // the header SAP_SenderMessageIdEx must not be empty
    // and the header SAP_SenderMessageIdEx must be a valid UUID
    // and the string parameter PassMessageIdFromE2E of the configuration scenario is set to true
    // and there is no split carried out in the predecessor flow #07
    if (SapSenderMessageIdEx != null && SapSenderMessageIdEx != '' && PassMessageIdThrough == 'true' && SapSplitExpression == null) {
        if (validateUUID(SapSenderMessageIdEx)) {
            message.setHeader("SapMessageIdEx", SapSenderMessageIdEx)
            return message    
        }
    }
    
    // Map Unique ID to SapSenderMessageIdEx
    def UniqueId = headers.get("UniqueID")
    if (UniqueId != null && UniqueId != '') {
        message.setProperty("MapUniqueIdToMessageIdEx", true)
    }

    // Otherwise Reuse of XI receiver adapter becomes Create
    return message
}

boolean validateUUID(String id) {
    id = id.toLowerCase()
    def splitId = id.split("-")
    return (splitId?.size() == 5
            && splitId[0]?.length() == 8
            && splitId[1]?.length() == 4
            && splitId[2]?.length() == 4
            && splitId[3]?.length() == 4
            && splitId[4]?.length() == 12
            && id.replaceAll("-", "").matches("^[a-fA-F0-9]{32}\$"))
}