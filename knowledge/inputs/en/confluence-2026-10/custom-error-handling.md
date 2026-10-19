<!-- Translated from Confluence page "Custom Error Handling" (PDF export 2026-10-19, Czech original, generation b). Draft, not a source of facts. -->

# Custom Error Handling

A skeleton for a custom error flow (called when CustomXError_Enabled=true from any generic step, endpoint /pip/custom/errorHandling). The logic imitates the standard retry handling: router "Max retries exceeded?" (SAPJMSRetries > maxJMSRetries and maxJMSRetries != 'unlimited'):

- Yes → Set dead letter queue name (${header.incomingQueue}_DLQ) → Send to DLQ (JMS ${property.deadLetterQueue}) → returns the headers customXError_Status = MaxRetriesExceeded, customXError_EndEvent = Escalated
- No → returns customXError_Status = AutomaticRetry, customXError_EndEvent = Failed (the caller ends with an Error end → JMS retry)

The returned headers customXError_Status and customXError_EndEvent are the contract with the generic steps (router "End event?": Completed/Escalated/Failed). Between the DLQ and the retry you can insert your own steps (notifications, creating tickets…).
