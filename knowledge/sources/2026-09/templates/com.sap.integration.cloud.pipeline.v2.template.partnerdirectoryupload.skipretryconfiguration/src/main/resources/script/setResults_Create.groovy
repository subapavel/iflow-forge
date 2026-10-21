import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {

    def properties = message.getProperties()

    def pd_pid = properties.get("PD_PID")
    def vm_id = properties.get("VM_ID")
    def number = properties.get("NumberOfEntries")
    message.setProperty("customHeader_Result", "${number} Entries created in Partner Directory for PID ${pd_pid}")

    return message;
}