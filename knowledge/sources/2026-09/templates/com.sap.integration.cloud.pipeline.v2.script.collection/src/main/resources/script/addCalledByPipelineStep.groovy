import com.sap.gateway.ip.core.customdev.util.Message;
def Message processData(Message message) {
    //body
    def body = message.getBody();
    def messageLog = messageLogFactory.getMessageLog(message)
    //fetch pipeline step from header customXPipelineStep
    def headers = message.getHeaders();
    def step = headers.get("customXPipelineStep");
    //add step to Custom Header Property
    if (step) {
            messageLog.addCustomHeaderProperty('calledByStep', step)
    }
    return message;
}