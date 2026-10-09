Ahoj, začínáme nový lokální projekt: reader SAP PO obsahu pro migraci do SAP Integration Suite.
Fáze 1 = BPM reader (NW BPM Galaxy model z NWDI SCA) + EJB reader (BPM funkce, adapter moduly).

Kontext z průzkumu je v docs/HANDOFF.md, přečti ho celý jako první. Podrobnosti ke čtení ESR/Directory
(SimpleQuery, read/ext, xiObj, TPZ) jsou v docs/SOURCES-ESR-DIRECTORY.md, pro fázi 1 jen jako kontext. Python prototypy
v docs/prototype/ jsou jen reference (formát MOIN/Galaxy je v nich ověřený), ne produktový kód.
Testovací SCA a BPMN 2.0 exporty jsou v <CESTA_K_TESTDATUM> (důvěrná data klienta,
necommitovat do veřejného repa).

Rozhodnutí: Java 21, Maven, knihovna jako jádro (CLI/MCP později).

Postup:
1. Navrhni JSON výstupní model procesu a EJB inventáře (HANDOFF, sekce 4, bod 1) a počkej na mou revizi.
   Zatím neimplementuj.
2. Pak kostra Maven projektu a BPM reader s testy proti DigiSign SCA a BPMN 2.0 exportům.

Odpovídej česky, stručně a po krocích. Chyby nalezené ve zdrojích hlas explicitně.
