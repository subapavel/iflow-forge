import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.xml.MarkupBuilder;

def Message processData(Message message) {

    String prefix = "SAP-DYNREC-";

    String[] propsKey = message.getProperties().keySet().findAll{ it.contains(prefix) };

    String operationSpecific = message.getProperty("SAP-OperationSpecific");
    if (operationSpecific) {
        operationSpecific = operationSpecific.toUpperCase();
    }
    String operation = message.getProperty("SAP-Operation");

    ArrayList<DynamicReceiverOperation> receivers = [];

    //Iterate the receivers that contains the prefix
    propsKey.each{ key ->
        int startIndex = key.lastIndexOf("-") + 1;
        String receiver   = key.substring(startIndex);
        def receiverCondition = message.getProperty(key);

        if (operationSpecific == "TRUE"){
            String receiverOperation = receiver.split("~")[0];
            receiver = receiver.split("~")[1];

            if (receiverOperation != operation){ //if operation is different, skip condition
                return true;
            }
        }

        if (receiverCondition || receiverCondition == ""){ //If boolean == true or String empty, include receiver
            DynamicReceiverOperation dynReceiver = new DynamicReceiverOperation();
            dynReceiver.Service  = receiver;
            dynReceiver.Operation = operation;

            receivers.push(dynReceiver);

            return true;
        }

    }

    //Generate Receivers XML
    StringWriter writer = new StringWriter();
    MarkupBuilder xmlBuilder = new MarkupBuilder(writer);

    xmlBuilder.with {
        "sys:Receivers"("xmlns:sys": "http://sap.com/xi/XI/System") {
            receivers.each { receiver ->
                Receiver{
                    "Party"(agency: receiver.agency, scheme: receiver.scheme, receiver.Party);
                    "Service"(receiver.Service);
                    "Operation"(receiver.Operation);
                }
            }
        }
    }

    String xmlString = writer.toString();

    message.setBody(xmlString);

    return message;
}

class DynamicReceiverOperation{
    DynamicReceiverOperation(){
        this.Party    = "";
        this.Service  = "";
        this.Operation   = "";
        this.agency   = "";
        this.scheme   = "";
    }

    public String Operation;
    public String Party;
    public String Service;
    public String agency;
    public String scheme;
}