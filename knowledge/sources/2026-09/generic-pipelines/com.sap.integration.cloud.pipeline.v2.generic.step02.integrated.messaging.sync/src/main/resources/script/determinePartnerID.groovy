import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.pd.PartnerDirectoryService
import src.main.resources.script.PipelineLogger

def Message processData(Message message) {
    def service = getPartnerDirectoryService()
    PipelineLogger logger = PipelineLogger.newLogger(message, "auditLog")
	def tracker = new HeaderStateTracker(message, logger)
	def headers = message.getHeaders()
	String stage = headers.get("tenantStage")
	boolean stageVirtual = headers.get("tenantStageVirtual") == "true"
	String partnerID = headers.get("partnerID")
	def properties = message.getProperties()
	
	if (stageVirtual) logger.addEntry("determinePartnerID", "Virtual Stages are already enabled with header 'tenantStageVirtual'.")
	// Partner ID and Tenant Stage can be known from Generic XI / IDoc Inbound Step01
	if(stage && partnerID) { // skip execution - required headers are known
		logger.addEntry("determinePartnerID", "Skipped execution of determinePartnerID script because 'tenantStage' and 'partnerID' headers are already known.")
		if(!stageVirtual) {
			def listCurStage = stage.split(",")*.trim() ?: []
	//		String landscapeValue = service.getParameter(System.getenv("TENANT_NAME"), "SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
			String landscapeValue = service.getParameter(properties.get("TenantName"), "SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
			def listVirtual = landscapeValue.split(",")*.trim() ?: []
			def merged = (listCurStage + listVirtual).unique()
			if (merged.size() > 1) { // if multiple Virtual Stages - enable Virtual Stages logic for Receiver
				tracker.setHeader("tenantStageVirtual", "true")
				String mergedValue = merged.join(",")
				logger.addEntry("determinePartnerID", "Virtual Stages are enabled for the tenant: '${mergedValue}'.")
			}
		}
	} else { // execute script logic
		try {
			// 1. Read Stage configuration
			def tenantContext = determineTenantStage(message, service, logger, tracker)
			
			// 2. Set the base for Sender Context
			def senderContext = determineSenderContext(message, service, tenantContext, logger, tracker)
			
			// 3. Stage & System resolution (Verification): only for Virtual Stages
			determineSenderStage(service, tenantContext, senderContext, logger, tracker)
			
			// 4. Partner ID determination
			determinePartnerId(message, service, senderContext, logger, tracker)

		} catch (Exception e) {
			// ROLLBACK: Restore headers to original state before the script started
			tracker.restoreOriginalState()
			
			// Save Log and Rethrow
			if (logger.getLog()) logger.saveToMessage(message)
			throw e
		}
	}

    // 5. Finalize Log
    finalizeAuditLog(message, logger)
    
    return message
}

PartnerDirectoryService getPartnerDirectoryService() {
    def service = ITApiFactory.getApi(PartnerDirectoryService.class, null)
    if (!service) throw new IllegalStateException("Partner Directory Service not found")
    return service
}

// --- 1. TENANT STAGE ---
Map determineTenantStage(Message message, PartnerDirectoryService service, PipelineLogger logger, HeaderStateTracker tracker) {
    def properties = message.getProperties()
    def headers = message.getHeaders()
    String stage = headers.get("tenantStage")
	String headerSender = headers.get("SAP_Sender")
	headerSender = determineSenderParty(headerSender) ? determineSenderParty(headerSender).sender : headerSender
	String authPotSender = null
	boolean authSenderWithStage = false

    // 0) Check SAP_Sender header for landscapeStage if not yet found via Auth User
    if (!stage && headerSender) {
        stage = service.getParameter("landscapeStage", headerSender, String.class)
        if (stage) {
            logger.addEntry("determineTenantStage", "Stage '${stage}' assigned from Landscape Stage of Sender System '${headerSender}' supplied with header.")
        }
    } else if (stage) {
        logger.addEntry("determineTenantStage", "Tenant Stage provided via incoming header: '${stage}'.")
    }
	
    // 1) Identity Determination via Auth User - it can be at Tenant Level or at Business System Level
    def auth = authenticatedUserDetermination(message, service)
    if (auth) {
        if (auth.location == "tenant") { // Tenant Stage by User (example: Landscape_Stage~DEV~PRJ)
            logger.addEntry("determineTenantStage", "Tenant Stage resolved via Authenticated User: '${auth.pid}'.")
			if (!stage) { // Tenant Stage header supplied from Step01 has priority
                stage = auth.pid
            } else {
				if(stage != auth.pid) logger.addEntry("determineTenantStage", "WARNING: Tenant Stage Mismatch: Known tenant stage value '${stage}', while Tenant Stage resolved via Auth User is '${auth.pid}'.")
				logger.addEntry("determineTenantStage", "Tenant Stage overridden with '${stage}'.")
			}
        } else { // BS does not have specific pattern/prefix in PD, so it can be differentiated only if landscapeStage is configured
            if(!headerSender) {
				authPotSender = auth.pid // It's a potential Business System PID
				logger.addEntry("determineTenantStage", "Authenticated User is mapped to PID: '${auth.pid}'.")
				
				// If (optional) Stage is defined, then it is definitely BS
				String confStage = service.getParameter("landscapeStage", auth.pid, String.class)
				if (confStage) {
					authSenderWithStage = true // if Stage assigned, then it is Business System
					logger.addEntry("determineTenantStage", "Stage '${confStage}' assigned from Landscape Stage of Business System '${authPotSender}' that is found using Authenticated User.")
					if(confStage.split(",")*.trim().size() > 1) { // validation that only single stage is assigned for BS
						throw new IllegalStateException("Partner Directory Configuration Error: Single Landscape Stage can be assigned to Business System: '${auth.pid}' have multiple configured '${confStage}'.")
					}
					if (!stage) { // Known Tenant Stage header has priority (like supplied from Step01)
						stage = confStage
					} else {
						if(stage != confStage) logger.addEntry("determineTenantStage", "WARNING: Tenant Stage Mismatch: Known tenant stage value '${stage}', while Tenant Stage resolved via Landscape Stage of Business System resolved via Auth User is '${auth.pid}'.")
						logger.addEntry("determineTenantStage", "WARNING: Sender System Tenant Stage overridden with '${stage}'.")
					}
				}
			} else {
				logger.addEntry("determineTenantStage", "Sender System from header '${headerSender}'; Authenticated User is mapped to PID: '${auth.pid}' but this PID is ignored due to sender is already known.")
			}	
        }
    } else {
		logger.addEntry("determineTenantStage", "Used Authenticated User is not mapped to any PID in PD.")
    }
	
	// 2) Landscape Configuration
	//String landscapeValue = service.getParameter(System.getenv("TENANT_NAME"), "SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
	String landscapeValue = service.getParameter(properties.get("TenantName"), "SAP_Integration_Suite_Landscape", String.class) ?: "PRD"
    def listVirtual = landscapeValue.split(",")*.trim()
    boolean isVirtual = listVirtual.size() > 1
	String virtualStages = isVirtual ? landscapeValue : null
	String defaultStage = isVirtual ? null : listVirtual[0]
	if (isVirtual) logger.addEntry("determineTenantStage", "Virtual Stages are enabled for the tenant: '${virtualStages}'.")
	
	// 3) Stage assignment for Non-Virtual case
	if(!stage && defaultStage) {
		stage = defaultStage
		logger.addEntry("determineTenantStage", "Single stage tenant configuration and set to value: '${stage}'.")
	}
	
	// 4) Stage Promotion Logic
    if (stage && !(stage in listVirtual)) {
        isVirtual = true
        virtualStages = "${landscapeValue},${stage}" // Promotion to Virtual Context
        logger.addEntry("determineTenantStage", "Resolved Stage '${stage}' promoted to Virtual Context as it was not in the list of tenant's virtual stages '${landscapeValue}'. Active Virtual Stages: '${virtualStages}'.")
    }

    if (isVirtual) tracker.setHeader("tenantStageVirtual", "true")
    if (stage) tracker.setHeader("tenantStage", stage)

    return [stage: stage, isVirtual: isVirtual, viStages: virtualStages, authSender: [hasStage : authSenderWithStage, value : authPotSender]]	
}

// --- 2. SENDER CONTEXT ---
Map determineSenderContext(Message message, PartnerDirectoryService service, Map ctxTenant, PipelineLogger logger, HeaderStateTracker tracker) {
    def headers = message.getHeaders()
    String alias = headers.get("SAP_SenderAlias")
    String idocSndPrn = headers.get("SAP_IDoc_EDIDC_SNDPRN")  // Detect IDoc
    boolean isIdoc = idocSndPrn != null
    String party = null
	
	// 1) Resolve Sender
	def authInfo = ctxTenant.authSender
	String potSender = authInfo?.value
	boolean hasStage = authInfo?.hasStage ?: false
	// priority is header; then if Sender was found with User at determineTenantStage with Landscape Stage - then Sender is known:
	String sender = headers.get("SAP_Sender") ?: (hasStage ? potSender : null) 

    // 2) Party Determination (only for non-IDoc scenarios)
    if (!isIdoc && sender) {
        def partyResult = determineSenderParty(sender)
        sender = partyResult.sender
        party = partyResult.party
        if (party) {
			tracker.setHeader("SAP_SenderParty", party)
			message.setProperty("customHeader_SenderParty", party)
			message.setProperty("customHeader_SenderSystem", sender)
			logger.addEntry("determineSenderContext", "Party detected in SAP_Sender header: '${party}' and '${sender}'.")
        }
    }
    
    // 3) Sender Business System Determination via Auth User
    if (!sender && potSender && !hasStage) { // Partner ID was found with User at determineTenantStage but without Landscape Stage
        // Fetch ALL possible Aliases for the Business System across all stages/tracks
		def aliasMatches = findInPD(service, ctxTenant, "BusinessSystemName", potSender, false, true, false) ?: 
                       findInPD(service, ctxTenant, "LogicalSystemName", potSender, false, true, false)
		
		if (aliasMatches) {
            def uniqueAliases = aliasMatches.collect { it.value }.unique()
			if (uniqueAliases.size() > 1) {
				throw new IllegalStateException("Partner Directory Configuration Error: Business System '${potSender}' is linked to multiple System Aliases ${uniqueAliases}. A Business System must map to exactly one System Alias.")
            }
			String potAlias = uniqueAliases[0]
			sender = potSender
			if (alias && alias != potAlias) {
				throw new IllegalStateException("Sender Alias Determination Error: Alias '${alias}' supplied in 'SAP_SenderAlias' header does not match Alias '${potAlias}' associated with Sender System '${potSender}' resolved via Authenticated User Alias Determination. Authenticated User mapped to a specific Business System cannot be used to authenticate other sender systems.")
			} else {
				alias = potAlias
				logger.addEntry("determineSenderContext", "Sender Alias '${alias}' resolved via Authenticated User associated with Business System.")
			}
            logger.addEntry("determineSenderContext", "Sender '${sender}' resolved via Authenticated User associated with Business System.")
        }
	}
		
	// 4) Sender Determination if Alias is present (optional - overall known alias is sufficient)
    if (alias && !sender) {
        def matches = findInPD(service, ctxTenant, "BusinessSystemName", alias, true, true, false) ?: 
                      findInPD(service, ctxTenant, "LogicalSystemName", alias, true, true, false)
        if (matches) {
            sender = resolveSenderFromAliasMatches(matches, alias, ctxTenant, logger)
        }
    }
	
	// 5) Standard Alias Lookup (main logic if Auth User is not used)
    if (!alias) {
        String scheme = isIdoc ? "LogicalSystemName" : "BusinessSystemName"
        String pid = isIdoc ? idocSndPrn : sender
		
		if (!pid) { // At this time if no Alias - either Sender Business System or IDoc SNDPRN must be known or Partner ID is already known
            if (headers.get("partnerID")) {
            // partnerID is already known — sender identification is not required for routing
                logger.addEntry("determineSenderContext", "SAP_Sender and SAP_SenderAlias not provided. Sender resolution skipped — partnerID is already known.")
                message.setProperty("customHeader_partnerID", headers.get("partnerID"))
                return [alias: null, sender: null, party: null, isAlias: false]
            }
            throw new IllegalStateException("Mandatory sender identification (SAP_Sender or IDoc SNDPRN) is missing.")
        }
	//	if (!pid) { // At this time if no Alias - either Sender Business System or IDoc SNDPRN must be known
	//		throw new IllegalStateException("Mandatory sender identification (SAP_Sender or IDoc SNDPRN) is missing.")
	//	}
		def aliasList = findInPD(service, ctxTenant, scheme, pid, false, true, false)
		if(aliasList) {
			def uniqueList = aliasList.collect { it.value }.unique()
			if (uniqueList.size() > 1) {
				throw new IllegalStateException("Partner Directory Configuration Error: Multiple System Aliases ${uniqueList} found for Scheme '${scheme}' and PID '${pid}'.")
			}
			alias = uniqueList[0]
		}
		if (alias) logger.addEntry("determineSenderContext", "Alias '${alias}' resolved via standard PD lookup for Scheme '${scheme}' and PID '${pid}'.")
    }

	boolean isAliasFound = alias as boolean  // set flag that Alias is found in Partner Directory (not a fallback: alias = sender). It is true only if alias is non-null and non-empty

    // IDoc Mapping: set header SAP_Sender (map alias name to business system name otherwise keep logical system name)
    if (isIdoc) {
        String idocBS = isAliasFound ? findInPD(service, ctxTenant, "BusinessSystemName", alias, true) : null
        String finalIdocSender = idocBS ?: idocSndPrn
		tracker.setHeader("SAP_Sender", finalIdocSender)
		logger.addEntry("determineSenderContext", "IDoc specific: Header SAP_Sender set to '${finalIdocSender}'. Internal sender context remains '${sender}'.")
    }

    // Final Sync & Fallbacks
    alias = alias ?: (isIdoc ? idocSndPrn : sender)
    tracker.setHeader("SAP_SenderAlias", alias)
    message.setProperty("customHeader_SenderAlias", alias)
	if(!headers.get("SAP_Sender")) { // set SAP_Sender if missing
		if (sender) {
			tracker.setHeader("SAP_Sender", sender)
		} else {
			tracker.setHeader("SAP_Sender", alias)
		}
	}

    return [alias: alias, sender: sender, party: party, isAlias: isAliasFound]
}

// --- 3. SENDER STAGE VERIFICATION ---
void determineSenderStage(PartnerDirectoryService service, Map ctxTenant, Map ctxSender, PipelineLogger logger, HeaderStateTracker tracker) {
    if (!ctxTenant.isVirtual) return // not relevant if Tenant does not have virtual stages

    String currentStage = ctxTenant.stage
    String alias = ctxSender.alias
    String sender = ctxSender.sender
    boolean isAliasFromPD = ctxSender.isAlias
	
	// --- Scenario 1: Tenant Stage is KNOWN ---
    if (currentStage) {
		// 1.a If No Alias from PD, then keep As-Is (no change of header tenantStage is required)
		// 1.b if Alias exists, then validate Alias against the known Stage:
        if (isAliasFromPD) { // search for BS by Alias and Stage
            String foundSender = findInPD(service, ctxTenant, "BusinessSystemName", alias, true, false, true)
                                 
            if (!foundSender) {
				throw new IllegalStateException("Virtual Stage Routing Error: Sender System for Alias '${alias}' not maintained for stage '${currentStage}'")
            }
            if (sender && foundSender != sender) {
				throw new IllegalStateException("Virtual Stage Routing Error: Mismatch. Configured Sender System '${foundSender}' in System Alias '${alias}' does not match actual sender '${sender}' for tenant stage '${currentStage}'")
            } // else if(foundSender) && foundSender == sender, then expected normal path with correct and verfied tenantStage
        }
    } else { // --- Scenario 2: Tenant Stage is UNKNOWN ---
        if (!isAliasFromPD) { // 2.a If No Alias from PD (like sender business component without System Alias): keep stage ambiguous with possible virtual stages
            tracker.setHeader("tenantStage", ctxTenant.viStages)
            logger.addEntry("determineSenderStage", "Tenant Stage is unknown and no Sender System Alias found for sender '${sender}. Set header for tenant stage to ambiguous value with possible virtual stages: '${ctxTenant.viStages}'.")
        } else { // 2.b if Alias exists, then resolve Stage via Alias + Sender
            if (!sender) { // if no Sender, proceed with unknown Stage & Sender
				 tracker.setHeader("tenantStage", ctxTenant.viStages)
				 logger.addEntry("determineSenderStage", "For Alias '${alias}' sender system 'SAP_Sender' is unknown: Tenant Stage cannot be determined and header set to ambiguous value with possible virtual stages: '${ctxTenant.viStages}'.")
			} else { // Retrieve ALL matches across ALL virtual stages
				def matches = findInPD(service, ctxTenant, "BusinessSystemName", alias, true, true, false)
				// Filter list to find matches where the found value equals the actual sender
				def validMatches = matches?.findAll { it.value == sender } 

				if (!validMatches) {
					throw new IllegalStateException("Virtual Stage Routing Error: Sender '${sender}' with System Alias '${alias}' not found in any virtual stage '${ctxTenant.viStages}'.")
				}
				if (validMatches.size() == 1) { // One unique occurrence - set tenant stage from Sender System stage (expected path)
					tracker.setHeader("tenantStage", validMatches[0].stage)
					logger.addEntry("determineSenderStage", "Tenant Stage match found: '${validMatches[0].stage}'")
				} else { // Multiple matches found
					String foundInStages = validMatches.collect { "[${it.stage}]" }.join(",")
					tracker.setHeader("tenantStage", foundInStages)
					logger.addEntry("determineSenderStage", "Ambiguity detected: Sender '${sender}' with Alias '${alias}' matches multiple virtual stages: '${foundInStages}'. Tenant Stage is unknown and allowed landscape: '${ctxTenant.viStages}'. Set tenant stage header to ambiguous value with matching virtual stages: '${foundInStages}'.")
				}
			}
        }
    }
}

// --- 4. PARTNER ID ---
void determinePartnerId(Message message, PartnerDirectoryService service, Map ctxSender, PipelineLogger logger, HeaderStateTracker tracker) {
    def headers = message.getHeaders()
	def properties = message.getProperties()
	
    if (headers.get("partnerID")) return
    
    def messageLog = messageLogFactory.getMessageLog(message)
    String Stage = headers.get("tenantStage")
    if (Stage && Stage.split(",")*.trim().size() == 1) messageLog?.addCustomHeaderProperty("tenantStage", Stage)
    
    String agency = ctxSender.party ? "${ctxSender.party}.${ctxSender.alias}" : ctxSender.alias
    String iface = headers.get("SAP_SenderInterface")
    String ns = headers.get("SAP_SenderInterfaceNamespace")
    String pid = null
	// Zero attempt: Determine partner ID via alternative partner based on sender service, sender interface and interface namespace
    if (iface && ns) pid = service.getPartnerId(agency, iface, ns)
	if (pid) logger.addEntry("determinePartnerId", "Partner ID '${pid}' determined from: agency '${agency}', interface '${iface}', namespace '${ns}'.")
	// First attempt: Determine partner ID via alternative partner if maintained
    if (!pid && iface) {
		pid = service.getPartnerId(agency, "SenderInterface", iface)
		if (pid) logger.addEntry("determinePartnerId", "Partner ID '${pid}' determined from: agency '${agency}' and sender interface '${iface}'.")
	}
    // Second attempt: Otherwise use a combination of sender component and sender interface unless you switched off the tilde option   
    if (!pid) {
        if (properties.get("CustomXPid_SwitchOffTilde") == "true") {
			throw new IllegalStateException("Partner ID Missing Error: No matching Partner ID found in Partner Directory: " + 
					"agency = '${agency ?: 'n/a'}', " +
					"interface = '${iface ?: 'n/a'}', " +
					"namespace = '${ns ?: 'n/a'}'")
		}
        pid = agency + "~" + (iface ?: "Unknown").replaceAll("[&%/]", "_")
        logger.addEntry("determinePartnerId", "Partner ID '${pid}' determined with Tilde PID determination option.")
    }

    tracker.setHeader("partnerID", pid)
    message.setProperty("customHeader_partnerID", pid)
}

// --- 5. PIPELINE AUDIT LOG ---
void finalizeAuditLog(Message message, PipelineLogger logger) {
    def h = message.getHeaders()
	def p = message.getProperties()
    logger.addEntry("determinePartnerID_FinalHeaders", "tenantStage=${h.tenantStage}, tenantStageVirtual=${h.tenantStageVirtual}, SAP_Sender=${h.SAP_Sender}, SAP_SenderAlias=${h.SAP_SenderAlias}, partnerID=${h.partnerID}")
	logger.addEntry("determinePartnerID_Additional", "Headers: SAP_SenderParty=${h.SAP_SenderParty ?: 'n/a'}, SAP_SenderInterface=${h.SAP_SenderInterface ?: 'n/a'}, SAP_ApplicationID=${h.SAP_ApplicationID ?: 'n/a'}, testMode=${h.testMode ?: 'n/a'}, Property CustomXError_Enabled=${p.CustomXError_Enabled ?: 'n/a'}")
    if (logger.getLog()) logger.saveToMessage(message)
}
/**
* --- HELPERS ---
* 5 helpers are used: determineSenderParty, authenticatedUserDetermination, findInPD, resolveSenderFromAliasMatches, HeaderStateTracker
*/
// Check and Exract Sender Party
Map determineSenderParty(String senderHeader) {
    String party = null
    String sender = senderHeader
    if (senderHeader?.contains(".")) {
        def kv = senderHeader.split(/\./, 2)
        party = kv[0]?.trim()
        sender = kv[1]?.trim()
    }
    return [sender: sender, party: party]
}

// Check if AuthenticatedUser (header SapAuthenticatedUserName) is maintained in Partner Directory
Map authenticatedUserDetermination(Message message, PartnerDirectoryService service) {
    String user = message.getHeaders().get("SapAuthenticatedUserName")
    if (!user) return null
    String partnerId = service.getPartnerIdOfAuthorizedUser(user)
    if (!partnerId) return null
    
    String prefTenant = "Landscape_Stage~"
	String prefTpm = "SAP_TPM"
	
    if (partnerId.startsWith(prefTenant)) {
        return [pid: partnerId.substring(prefTenant.length()), location: "tenant"]
    }
    if (partnerId.startsWith(prefTpm)) return null // Ignore TPM
    
    return [pid: partnerId, location: "other"]
}

/**
 * Helper to find a value in PD by checking all valid virtual stages.
 * isAlt = true (getAlternativePartnerId: Scheme + Alias -> Bus.System)
 * isAlt = false (getPartnerId: Scheme + Bus.System -> Alias)
 * fetchAll = true (search through all possible combinations and return a list [value, stage])
 * fetchAll = false (exit fucntion at first found entry in PD)
 * perStage = true (search only by ctxTenant.stage)
 * perStage = false (search by all virtual stages)
 */
def findInPD(PartnerDirectoryService service, Map ctxTenant, String scheme, String value, boolean isAlt = false, boolean fetchAll = false, boolean perStage = false) {
    // 1) Determine the exact stages to search from the landscape
	def searchStages = perStage ? [ctxTenant.stage] : (ctxTenant.isVirtual ? (ctxTenant.viStages?.split(",")*.trim() ?: []) : [ctxTenant.stage])
    def results = []
    for (stage in searchStages.unique()) { // 2) Iterate through unique stages only
        if (!stage) continue  
        String found = isAlt ? service.getAlternativePartnerId(stage, scheme, value) : service.getPartnerId(stage, scheme, value)  
        if (found) {
            if (!fetchAll) return found // Exit early for single match
            results << [value: found, stage: stage]
        }
    }
    return fetchAll ? results : null
}

/**
 * Helper to resolve a technical Sender System from a list of Alias matches in PD.
 * Handles stage-based resolution, and ambiguous logging.
 */
String resolveSenderFromAliasMatches(List matches, String alias, Map ctxTenant, PipelineLogger logger) {
    def uniqueSenders = matches.collect { it.value }.unique()
    String senderList = uniqueSenders.join(", ")
    String resolvedSender = null

    if (uniqueSenders.size() == 1) { // 1. Unique Match Case
        String potSender = uniqueSenders[0]
        resolvedSender = potSender
        logger.addEntry("determineSenderContext", "Sender '${resolvedSender}' resolved as unique match for Alias '${alias}'.")
    } else {
        // 2. Try Stage-based resolution
        if (!resolvedSender && ctxTenant.stage) {
            def match = matches.find { it.stage == ctxTenant.stage }        
            if (match) {
                resolvedSender = match.value
                logger.addEntry("determineSenderContext", "Sender '${resolvedSender}' resolved via Stage Match (${match.stage}) for Alias '${alias}'.")
            }
        }
        // 3. If still not resolved, keep sender unknown
        if (!resolvedSender) {
            logger.addEntry("determineSenderContext", "System Alias '${alias}' has multiple possible Sender Systems: '${senderList}'. Specific system could not be determined for the current landscape context as Tenant Stage is unknown '${ctxTenant.viStages}'.")
        }
    }
    return resolvedSender
}
/**
 * Helper to restore original state of headers before throwing an exception.
 * This prevents "distortion" of message headers in case of moving to DLQ due to configuration mistakes.
 */
class HeaderStateTracker {
    private Map originalState = [:]
    private Set modifiedKeys = []
    private def message
    private def logger

    HeaderStateTracker(def message, def logger) {
        this.message = message
        this.logger = logger
    }

    // Sets a header and tracks the original value if not already tracked.
    void setHeader(String name, Object value) {
        if (!originalState.containsKey(name)) {
            // Store the true original value (even if null)
            originalState[name] = message.getHeaders().get(name) ?: ""
        }
        modifiedKeys.add(name)
        message.setHeader(name, value)
    }

    // Reverts all modified headers to their state prior to this script's execution.
    void restoreOriginalState() {
        originalState.each { name, oldValue ->
            message.setHeader(name, oldValue)
        }
        logger.addEntry("HeaderStateTracker", "Rollback performed: Reverted headers ${modifiedKeys} to original values.")
    }
}