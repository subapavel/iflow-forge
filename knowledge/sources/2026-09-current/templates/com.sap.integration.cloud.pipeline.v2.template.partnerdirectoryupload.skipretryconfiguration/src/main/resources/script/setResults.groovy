import com.sap.gateway.ip.core.customdev.util.Message;

def Message processData(Message message) {

    def properties = message.getProperties()

    def pd_pid = properties.get("PD_PID")
    def vm_id = properties.get("VM_ID")
    def number = properties.get("NumberOfEntries")
    def matchstatus = properties.get("ConfigurationMatch")?.toString()?.equalsIgnoreCase('true')
    if (matchstatus)
        message.setProperty("customHeader_Result", "No entries updated. Configration between Partner Directory for PID ${pd_pid} and used Value Mapping is a match")
    else
        message.setProperty("customHeader_Result", "${number} Entries updated. Configration between Partner Directory for PID ${pd_pid} and used Value Mapping is not a match")

    return message;
}