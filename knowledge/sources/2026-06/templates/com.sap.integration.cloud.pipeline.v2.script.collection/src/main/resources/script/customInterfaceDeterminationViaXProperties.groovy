import com.sap.gateway.ip.core.customdev.util.Message;
import groovy.xml.MarkupBuilder;

def Message processData(Message message) {
    String prefix = "SAP-IFDET-";
	String prefixName = "SAP-IFNAME-";
	String prefixEndpoint = "SAP-IFENDPT-";
    String defaultKey = "Default"
    Boolean defaultNeeded = true;

    InputStream iSBody = new ByteArrayInputStream(message.getBody(java.lang.String).getBytes('UTF-8'));
    def receiversXML   = new XmlParser().parse(iSBody);
    
    //Fetch operation in case of multiple service interface operations
    def operation = message.getProperty("SAP-Operation");

    //Get all exchange properties that match the prefix
    String[] propsKey = message.getProperties().keySet().findAll{ it.contains(prefix) };

    //Read behaviour if no receiver could be determined
    String noReceiverType = message.getProperty("SAP-NOREC-Type");
    String noReceiverDefault = message.getProperty("SAP-NOREC-Default");

    HashMap<String, DynamicReceiver> dynamicReceivers = [:];
    //Iterate the receivers that contain the prefix
    propsKey.each{ key ->
        String receiverAndInterface = key.substring(10);
        String[] splittedKey = receiverAndInterface.split("~");
        String receiver;
        String interfaceID;
        String index;

    //Fetch receiver and interface ID
        if (splittedKey.size() == 3){ //Has Operation
            receiverOperation = splittedKey[0];
            receiver = splittedKey[1];
            interfaceID = splittedKey[2];
        }else{
            receiver = splittedKey[0];
            interfaceID = splittedKey[1];
        }
        
        if ((operation && receiverOperation) && operation != receiverOperation){ //Regardless if is operationSpecific, we need to check the operation if exists
            return true; //If operation is different, then skip
        }

        index = interfaceID.replace("IFIndex", "");
        Boolean receiverInterfaceCondition = message.getProperty(key);

        if (!dynamicReceivers.containsKey(receiver)) {
            dynamicReceivers[receiver] = new DynamicReceiver();
            dynamicReceivers[receiver].Found = receiversXML.Receiver.findAll{ it.Service.text() == receiver };
        }
        ArrayList<DynamicReceiverInterface> receiverInterfaces = dynamicReceivers[receiver].receiverInterfaces;
        DynamicReceiverInterface dynReceiverInterface = new DynamicReceiverInterface();
        dynReceiverInterface.Service = receiver;
        dynReceiverInterface.Index = index;

        String keyName = key.replace(prefix, prefixName);
        dynReceiverInterface.Name = message.getProperty(keyName);
        String keyEndpoint = key.replace(prefix, prefixEndpoint);
        dynReceiverInterface.Service = message.getProperty(keyEndpoint);
        dynReceiverInterface.Found = receiverInterfaceCondition || receiverInterfaceCondition == "";
        if (dynamicReceivers[receiver].Found && dynReceiverInterface.Found)
            defaultNeeded = false;
        receiverInterfaces.add(dynReceiverInterface);
        return true;
    }
    
    //Generate Receivers XML
    StringWriter writer = new StringWriter();
    MarkupBuilder xmlBuilder = new MarkupBuilder(writer);

    xmlBuilder.with {
        "sys:Receivers"("xmlns:sys": "http://sap.com/xi/XI/System") {
            if (defaultNeeded){
                ReceiverNotDetermined{
                    "Type"(noReceiverType);
                    if (noReceiverType != 'Default')
                        "DefaultReceiver"();
                    else{
                        DefaultReceiver{
                            "Service"(noReceiverDefault);
                            def receiverInterfaceList;
                            if (!dynamicReceivers.containsKey(noReceiverDefault))
                                receiverInterfaceList = dynamicReceivers[defaultKey].receiverInterfaces;
                            else
                                receiverInterfaceList = dynamicReceivers[noReceiverDefault].receiverInterfaces;
                            Interfaces{
                                receiverInterfaceList.each { receiverInterface ->
                                    if (receiverInterface.Found){
                                        Interface{
                                            "Index"(receiverInterface.Index);
                                            "Service"(receiverInterface.Service);
                                            "Name"(receiverInterface.Name);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }           
            }
            dynamicReceivers.each { key, value ->
                if (key != defaultKey && value.Found){
                    Receiver{
                        "Service"(key);
                        Interfaces{
                            value.receiverInterfaces.each { receiverInterface ->
                                if (receiverInterface.Found){
                                    Interface{
                                        "Index"(receiverInterface.Index);
                                        "Service"(receiverInterface.Service);
                                        "Name"(receiverInterface.Name);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    String xmlString = writer.toString();
    message.setBody(xmlString);
    return message;
}

class DynamicReceiverInterface {
    DynamicReceiverInterface(){
        this.Service  = "";
		this.Index = "";
		this.Name = "";
        this.Found = false;
    }

    public String Service;
	public String Index;
	public String Name;
    public Boolean Found;
}

class DynamicReceiver {
    DynamicReceiver(){
        this.Receiver  = "";
		this.Found = false;
		this.receiverInterfaces = [];
    }

    public String Receiver;
    public Boolean Found;
    public def receiverInterfaces;
}
