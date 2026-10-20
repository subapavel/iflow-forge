# Benchmark questions

Part-1 benchmark questions (plan Task 12); a fresh Claude Code session is told: "Load skill sap-is-pipeline. Answer every question in knowledge/pipeline/benchmark/questions.md in a file /tmp/benchmark-answers.md. Then follow knowledge/pipeline/migration/po-ico-to-pipeline.md for knowledge/pipeline/benchmark/sample-ico.json up to and including the proposal, into /tmp/benchmark-proposal.md."
The owner scores the answers; the owner holds the key.

1. **Q:** In what order do iFlows and queues carry an async XI → IDoc message (integrated-async)?
2. **Q:** On the sync path, who calls the receiver and how does the reply return?
3. **Q:** What do the `runtime` values decoupled, integrated-async, integrated-sync mean?
4. **Q:** Which queue does each Step01 copy write, and when?
5. **Q:** Where does each Step02 copy send the message, and which call the inbound conversion?
6. **Q:** What do Step04, Step05, Step06 and Step08 IDoc do?
7. **Q:** Which SAP_Sender, SAP_SenderInterface and namespace values does Step01 set, from what, in each copy?
8. **Q:** How is the partner ID derived for XI and IDoc senders?
9. **Q:** Which Partner Directory keys does Step04 read, and how is "no receiver" handled per copy?
10. **Q:** What does an async XI → IDoc scenario contribute?
11. **Q:** Which queues does that message pass, and what is SAP's default prefix?
12. **Q:** What do inbound conversion and custom exits contribute, and where?
13. **Q:** What does the IDoc receiver side need: connection, logical systems, control record?
14. **Q:** Which route does Step01 XI give a BestEffort message?
15. **Q:** What differs for a sync scenario, in Step02 and in its contribution?
16. **Q:** Where is a determination XSLT stored, how is it referenced, and which iFlows apply it?
17. **Q:** Where does a message mapping go for async XI → IDoc, and for sync?
18. **Q:** Which templates serve one-to-many and ordered delivery, and what is their limit?
19. **Q:** What does 2026-09-current change for a migration (Step01 queue fallback, new templates, error handling)?
20. **Q:** Which values on the async XI → IDoc path are tenant-specific, and how do pages write them?
