# Zlepšení migračního procesu — fázový implementační plán

> **For agentic workers:** Po schválení používat `superpowers:subagent-driven-development`
> s rolemi a omezeními `docs/CODEX-WORKFLOW.md`. Realizace je omezena na výslovně
> schválené části. Detailní task packety vznikají pouze pro ně.

**Stav:** Návrh schválen vlastníkem 2026-10-26; po dokončení P1 vlastník povolil
průběžnou realizaci zbývajících P2–P7, dokud neřekne stop (D-048).
Následně rozsah omezil na dokončení P3 a ukončení práce (D-053); P4–P7 se
v tomto běhu neimplementují. Dosavadní souhlas a historie zůstávají zachované.
P1 je implementováno a ověřeno jako MIG-23: code/doc review PASS, nezávislý
reactor verify 371/0/0/26 a 16 cílených kontrol po dokumentační opravě PASS.
P1 commit: `461ae13`. P2 je dokončeno jako MIG-24: code review r2 a docs review PASS,
nezávislý reactor 420/0/0/26, 49 nových testů a 10/10 kontrol čerstvého CLI balíčku.
P3a/MIG-25 je dokončeno jako `c8b4f86`: code/docs review PASS, reactor440/0/0/26
a čerstvé CLI3/3. MIG-26 dokončilo strukturální cesty: code r2/docs PASS,
nezávislý reactor483/0/0/26 a43 nových testů. MIG-27 dokončilo datové kandidáty:
code/docs r2 PASS, nezávislý reactor533/0/0/26 a50 nových testů včetně7 regresí.
MIG-28 dokončilo fault deklarace v commitu `3e2b716`: code r2/docs PASS,
nezávislý reactor569/0/0/26 a36 nových testů. MIG-29 dokončilo společný report,
konfigurované ICO/kanálové kandidáty, CLI `behavior` a MCP `bpm_behavior`:
code r2/finaldocs PASS, nezávislý reactor623/0/0/26,54 nových testů a čerstvé CLI8/8.
**P3 je dokončeno a práce tímto končí (2026-10-27, D-053).** P4–P7 se neimplementovaly.
26 stávajících vynechání zůstává neověřeno; report nedokazuje skutečný běh ani doručení.
Přesná evidence a omezení jsou v PROGRESS.
Historie: původně předloženo ke schválení; odpověď vlastníka „Schvaluji“ navazuje
na otázku o schválení návrhu a zahájení pouze P1.

**Goal:** Doložit aktuální návrh a skutečné zdrojové chování bez globálního
blokování práce otázkami, které se jí netýkají.

**Architecture:** Opt-in soukromý JSON ledger, stávající Markdown jako historie
a podklady, odvozený aktuální pohled. Malé Java helpery ve stávajících modulech;
nejprve využít data readerů, potom doplnit skutečně chybějící parsování.

**Tech Stack:** Stávající Java 25, Maven, JSON infrastruktura, Quarkus/Aesh CLI,
JUnit/AssertJ dle projektu; žádná nová databáze, jazyk ani framework.

**Spec:** [audit a návrh](../specs/2026-10-26-migration-process-retrospective-design.md).

## Globální omezení a pořadí

- Původní historie a zákaznické kontrakty se nepřepisují.
- Zákaznické podklady pouze v povoleném soukromém umístění; tracked příklady syntetické.
- Jeden zapisující agent, nejvýše dva subagenti, žádná vnořená delegace.
- Kód a testy píší příslušní specialisté; architekt schvaluje přečtené red testy.
- Před knihovně závislým kódem Context7 resolve-library-id a get-library-docs.
  V tomto auditu se knihovní kód nepíše; lookup bude součástí code tasku,
  podle aktuálně dostupného názvu nástroje pro načtení dokumentace.
- Každý code task: observed red, bounded implementace, verifier, reactor
  `mvn -B -ntp verify`, nezávislý reviewer. Skips uvést konkrétně, nepovažovat za pokrytí.
- Změny KB/skills vyžadují příslušné citační, budget, mirror a privacy kontroly;
  KB obsah navíc nezávislý fact check. Kontrola nadpisů sama není akceptace chování.
- Žádné tenantové změny, deployment, push, generator ani ruční iFlow BPMN.

Doporučené pořadí: **P1 → P2 → P3 → P4 → P5 → P6 → P7**.
Katalog kontraktů P4 předchází srovnávací matici P5, protože bez kontraktů nelze
spolehlivě porovnat význam kroků. Základní soupis kontraktů vzniká už v P3,
zdrojové lokátory v P1 a výběr PoC v P2; nejde o odložení evidence až do P6.
Výklad přímého zápisu ledgeru se vyjasní už v P1, P7 ověří celý pracovní postup.
MIG-21/22 zůstávají samostatné existující položky, nevytváří se jejich duplikát.

## P1 — Aktuální návrh a přesný rozsah schválení

**Přínos:** Jeden dohledatelný stav bez přepisování historie; první samostatně
použitelný krok, nezávislý na rozšíření BPM readeru.

**Rozsah a soubory:**

- Nové `skills/sap-po-migration/references/dossier.md` a `templates/dossier.json`;
  aktualizovat `references/plan.md`, `templates/scenario.md` a routing v SKILL.
- Nové `po-xray-core/src/main/java/io/poxray/migration/DossierReader.java`,
  `DossierValidator.java`, `DossierReport.java` jako navržené umístění malého helperu.
- Nové `po-xray-cli/src/main/java/io/poxray/cli/DossierCheckCommand.java`,
  registrace v `PoXrayCommand.java`, popis v `docs/OUTPUT-MODEL.md`.
- Syntetické `po-xray-core/src/test/java/io/poxray/migration/DossierValidatorTest.java`
  a `po-xray-cli/src/test/java/io/poxray/cli/DossierCheckCommandTest.java`.
- `po-xray-native` registrace nových report records a její test (doplněno při
  přípravě P1 podle existujícího CLI/native kontraktu; bez nové funkčnosti).
- Vyjasnit ledger ownership v nové dossier reference odkazem na existující
  `AGENTS.md#Roles`; Codex override nepotřebuje další výjimku. Původní migrační spec
  §7/P-8 změnit pouze v odsouhlaseném rozsahu, s changelogem; historická
  D-034 se nemaže, nová decision row výslovně pojmenuje nahrazené části.

**Rozhraní:** Vstup je versioned ledger + lokální read-only podklady + volitelný
baseline. Výstup je current state a diagnostiky v JSON/Markdown. CLI návrh je
`dossier-check`, viz spec §5. Přesné Java signatury uzavře acceptance packet P1,
ne tento fázový plán. Autoritativní obsah zůstává v událostech a jejich podkladech.

**Akceptace a smysluplné testy:**

1. `replacementRequiresApprovedSuccessor`: návrh D2 neruší schválené D1;
   explicitně schválená náhrada jej vyřadí pouze z uvedeného rozsahu.
2. `approvalDoesNotCoverNeighbourSection`: schválené umístění validace nezpůsobí
   schválení jejích pravidel ani jiné revize téže části.
3. `changedSourceAndDerivedRevisionAreStale`: změna hashe schváleného snapshotu
   a zastaralá consumed revision pokračovacího dokumentu mají odlišné diagnostiky.
4. `historyRequiresUnchangedBaselinePrefix`: změna staré události je odmítnuta;
   bez baseline výsledek říká HISTORY_NOT_CHECKED, nikdy ověřená neměnnost.
5. `conflictingApprovalAndDanglingSupersessionAreRejected`: konflikt, cyklus,
   neexistující reference a neznámá formatVersion se nesmí potichu přijmout.
6. CLI smoke ověří skutečné ID platného rozhodnutí, rozsah a diagnostiky v JSON
   i Markdownu; žádný vstupní soubor se nezmění. Generated current view nese
   digest ledgeru, ze kterého vznikl; není to další autoritativní vstup.

**Testování:** Syntetické soubory v dočasném adresáři, focused core/CLI testy,
reactor verify a kontrola skill citací/portability; nezávislý review schválení
a historie. Testy nesmějí jen hledat názvy nových nadpisů.

**Realizace MIG-23:** Přesný formát a rozhraní stanoví
[packet P1](2026-10-26-mig-23-p1-packet.md). Native registrace a čtyři dodatečné
testy atomicity patří do stejné části. Konečný reactor verify zahrnuje knowledge
kontroly; samostatný duplicitní běh stejného modulu není potřebný.

**Kompatibilita:** Sidecar opt-in; původní Q a plan rounds beze změny.
Nejednoznačné staré schválení se importuje jako unresolved. Známý původní
souhlas se nepřemění na nový požadavek schválení jen kvůli změně formátu.
Bez sidecaru platí dosavadní režim s viditelným omezením.

**Mimo P1:** Změna blokování otázek, analýza BPM, matice, automatické
rozpoznávání sémantických rozporů v próze, MCP wrapper. P1 nezměkčí stávající
fázové gate; dílčí souhlas zatím nenahrazuje závěrečné schválení plánu.

## P2 — Otázky, rizika a připravenost konkrétního milníku

**Přínos:** Odložená otázka nezastaví nezávislou práci a přesto se neztratí
před implementací či nasazením.

**Rozsah:** Rozšířit dossier helper a testy; `references/questions.md`,
`attributes.md`, `intake.md`, `bpm.md`, `plan.md`; `templates/question.md`,
`scenario.md`; SKILL; původní spec §3 G2, §7, P-4. Oddělit téma otázky od
blokovaného scope/milníku; aktuální přehled drží závislosti i přijatá rizika.
Karta priority nejistot zavede včasný výběr malého PoC.

**Akceptace/testování:** `deferredQuestionBlocksOnlyDependentMilestone` nechá
navrhovat část B při otázce o implementaci A, ale implementaci A nepovolí.
`deferralDoesNotRemoveDependency` znovu blokuje při neededBy události.
`assumptionNeedsOwnerEvidenceAndKeepsUnknown` odmítne automatické uzavření.
`acceptedRiskNeedsNewTriggerToReopen` respektuje scope i změnu požadavku.
Test finálního milníku ověří všechny požadované části a jejich propojení;
samotný dílčí souhlas jej nepovolí. Struktura validní ≠ milník připravený.

**Kompatibilita:** Staré typy blocking/version/conversion/naming ponechat jako
topic. Chybějící dependencies se nesmějí automaticky vyložit jako „nic neblokuje“;
vyžádají klasifikaci a report omezí připravenost. Staré odpovědi ani rizika se nemažou.

**Mimo rozsah:** Scheduler, kalendář, automatické dotazy kolegům, změna human-task
stop a oprávnění provádět tenantové operace. Verifikace/review jako P1.

## P3 — Doložené zdrojové cesty

**Implementační nález F-14 (MIG-26):** Schválené syntetické exporty odhalily dvě
starší NPE ještě před analýzou cest. Packet výslovně povoluje úzkou opravu
`MoinIndex.first` při null cíli a `ExpressionReader.literal` při neznámém typu.
Zdrojové vazby a nejistota zůstanou zachované; očekávání testů se nemění.
Podrobnosti a pozorované chyby: [F-14](../../findings/F-14.md).

**Přínos:** Odlišit zapojení od přítomnosti a oddělit chybové cesty před procesem
od fault scope procesu. Získat podklady pro kontrakty a porovnání.

**Rozsah:** `po-xray-core` balíky `bpm`, `model`, `integration`, `migration`,
`doc/DocumentBuilder.java`; navržené `migration/BehaviorAnalysis.java` a
`BehaviorReport.java`; CLI command a registrace, MCP `Views.java`/`PoXrayTools.java`;
`references/bpm.md`, `intake.md`, `docs/OUTPUT-MODEL.md`.
Nejdříve report z existujících Flow/scope/assignment/reads/kanálů, potom
synteticky doložené rozšíření pro loops, missing refs a fault vazby.
Discovery znovu neimplementovat; používat MIG-20, MIG-21 připojit, až existuje.

**Akceptace/testování:** Na syntetickém zdrojovém exportu a ICO rozlišit
pre-process odmítnutí a fault handler; nedosažitelnou větev oddělit od větve
s unknown podmínkou; chybějící boolean nevyhodnotit jako false; zobrazit
initialValue i strukturální assignment; při joinu zachovat různé možné
producenty; scope stínění neztotožnit podle názvu; zachytit sequential/parallel
loop a ukončení, nepodporovanou variantu označit unknown; unresolved mapping
part/channel nedovolí výrok o jistém cíli. Async bez dokladu ack zůstane unknown.

Parserové testy musí číst syntetické MOIN/SCA/TPZ struktury, ne pouze ručně
naplněné records. Samostatné report testy ověří JSON/Markdown/MCP obsah;
reactor verify a nezávislý review. Customer-gated skips nejsou náhrada za tyto testy.

**Kompatibilita:** Raw input zachovat, dosavadní output fields neměnit významem.
Přidat report a verzi rozšířeného formátu; starší dump bez potřebného pole vrátí
explicitní unknown. Chybějící údaje může řešit nový export, ne LLM odhad.

**Mimo rozsah:** Kompletní XPath/Java interpreter, simulátor transakcí, tvrzení
o současné produkci. Přesný podporovaný soubor loop/fault konstrukcí určí
packet z konkrétních zdrojů před kódem, nikoli seznam marketingových slibů.

## P4 — Katalog kontraktů a artefaktů

**Přínos:** Ujasnit hranice a producenty dat před návrhem cílových artefaktů.

**Rozsah:** Nové `references/contracts.md`, `templates/contracts.md` ve skillu;
intake/analysis/plan/scenario návaznosti. `IntegrationReader.java`,
`model/IntegrationInventory.java` pro chybějící ExternalDefinition/resource
a QName údaje; navržené `migration/ContractCatalog.java` + syntetické testy.
`knowledge/pipeline/conversion/README.md` a případné nové reference upravit
jen tam, kde vznikne obecné doložené tvrzení; `knowledge/pipeline/contracts.md`
je dnes kontrakt headerů/properties pipeline, nenahrazovat jej zákaznickými XSD.

**Akceptace/testování:** Externí XSD resource se nestane MT; MT→DT a závislosti
importu zůstanou dohledatelné. XML QName a ESR identita mohou být odlišné.
ProcessDirect nesmí sám přepnout mapping na shared. Script Collection,
lokální skript a mappingová funkce zůstávají odlišné. Každý krok zná dostupnost
dat před/po a producenta defaultu; výstup API není požadovaný vstup.
Chybějící dependency je diagnostika, ne úspěšně kompletní import.

**Kompatibilita:** Existing catalog/proposal lze připojit jako podklad, ne jako
schválený kontrakt. Legacy ExternalDefinition neumožní domyslet QName;
chybějící metadata zůstanou unknown. Syntetické reader/helper testy, reactor
verify a případné KB citace/budget/fact check.

**Mimo rozsah:** Vytváření/import do tenantu, změna zákaznických rozhraní,
univerzální povinnost sdílených mappingů. Katalog zaznamenává volbu a důkaz.

## P5 — Srovnávací matice implementací

**Přínos:** Rozdíly jsou vidět po krocích, ne jen čtením několika dlouhých reportů.

**Rozsah:** Nové `migration/BehaviorComparison.java` a testy; CLI/MCP
report využívající P3/P4; nová skill reference `comparison.md`, routing.
Jednotná řádková osa a explicitní alignment kroků s evidencí a stavem potvrzení.

**Akceptace/testování:** Syntetické tři implementace — přejmenovaný ekvivalent,
stejné jméno s jinou podmínkou, odpojená větev. Výstup nesmí zaměnit jméno
za ekvivalenci a nesmí zahodit unmatched kroky. Zobrazí odlišný default, loop
režim, chybový scope, kanál a kontrakt; opakovaný běh má stabilní pořadí.

**Kompatibilita:** Původní reporty zůstávají zdroji buněk. Ruční alignment
se ukládá jako doložená volba, automatický kandidát není schválení.
Focused syntetické testy, reactor verify, nezávislý review s chybně spárovaným případem.

**Mimo rozsah:** Univerzální semantic diff nebo automatické rozhodování o
sloučení procesů. Neprovádí změny cílového ani zdrojového procesu.

## P6 — Verze, důkazy a návrat výsledku PoC do návrhu

**Přínos:** Nová evidence může návrh zpřesnit bez předstírání podpory ve staré KB;
úspěch jednoho testu nemění nesouvisející tvrzení na ověřená.

**Rozsah:** Rozšíření dossier provenance z P1; `templates/profile.md`,
`references/retrospective.md`, nová `references/evidence.md` a
`templates/experiment.md`; `migration/Reports.java` a export metadata;
SKILL pravidlo 1, Start here a routing, `references/questions.md` pravidlo
pipeline citací a `references/plan.md` Cite rule musí výslovně povolit
přezkoumaný lokální packet v jeho doloženém rozsahu;
spec P-3/§10, citační resolver v
`iflow-forge-knowledge/src/test/java/io/iflowforge/knowledge/Checks.java`
pro případnou novou veřejnou cite formu. Lokální evidence links kontroluje
dossier validator; veřejný checker nikdy nepřistupuje k privátním podkladům.

**Akceptace/testování:** Stažený release s novým Bundle-Version nepřepíše
deployed evidence; export-date není package-version. Owner-approved se
nepřemění na tenant-observed. Úspěšné API volání nedoloží cache/idempotenci.
Nový packet uvede vlastní hash/lokátor/verzi a omezení a označí rozdíl oproti KB;
starou KB nesmí citovat jako důkaz nového chování. PoC upraví jen rozhodnutí,
které na jeho pozorování závisí. Schválený strop složitosti a riziko zůstanou
platné bez konkrétního triggeru změny.
Test ověří přijetí přezkoumaného packetu pouze pro přesný scope tvrzení;
chybějící packet, neověřený review a tvrzení nad jeho rámec zůstanou otevřené.

**Kompatibilita:** Zachovat sedm sekcí profilu, přidat typ zdroje a scope;
staré installed záznamy bez důkazu nepovýšit na deployed. Existující PoC
záznam se připojí s jeho omezeními. Syntetické testy, cílený citační check,
reactor verify a nezávislý fact check příslušných nových tvrzení.

**Mimo rozsah:** Automatické tenant API dotazy, tajné údaje, univerzální
obnova stavu, nová živá PoC bez vlastního oprávnění, hromadný přepis KB.

## P7 — Orchestrace a pilotní ověření postupu

**Přínos:** Ověřit změny spolu na skutečném dossieru, zachovat důkazy a získat
pozorování o režii místo nepodložených odhadů.

**Rozsah:** Vyjasnění `docs/CODEX-WORKFLOW.md`, odpovídajících role boundaries
a skill routing; oddělený soukromý auditní pohled M-1, ne editace originálů.
Source behavior, contract/API a pipeline-version specialisté dostanou vždy
jen potřebný packet. Významný návrh kontroluje jiný agent než jeho autor.

**Akceptace/testování:** Přehrát následující případy se vstupem, očekáváním
a skutečným výsledkem; žádné tvrzení o úspěchu bez logu:

| Případ | Ověřovaná vlastnost / hlavní část |
|---|---|
| Odklad otázky | Nezávislá část pokračuje, potřebný milník zůstává blokován / P2 |
| Náhrada rozhodnutí a částečný souhlas | Staré není aktuální, sousední část není schválená / P1 |
| Pre-process chyba versus BPM fault | Dvě rozdílné cesty a scope / P3 |
| Odpojená větev a missing boolean | Unreachable a unknown se nezamění za aktivní/false / P3 |
| API output a externí XSD | Nevznikne fiktivní vstup ani nativní MT / P4 |
| Nový export a úspěšný request | Nevznikne deployed/cache/idempotence důkaz / P6 |
| Kontrola původní evidence | Hashe původních souborů před/po jsou stejné |
| Běžný zápis otázky/rozhodnutí | Provede architekt bez code-task cyklu a bez souběhu s writerem |

**Kompatibilita:** Ověření pracuje s read-only originály a odděleným sidecarem/
snapshoty. Importní report ukáže i unresolved schválení, ne pouze úspěšně
převedené záznamy. Výsledek s chybějícími provozními daty uvede omezení.

**Mimo rozsah:** Dokončení zákaznické migrace a provozní akceptace. Délky běhů,
počty předávek a dotazů lze měřit od tohoto bodu; srovnání s pilotem nelze
vydávat za zrychlení, pokud jeho původní baseline chybí.

## Review focus a rozhodnutí před implementací

- Sousední část, částečná náhrada a následný nový draft: pokrývá P1.
- Chybějící baseline, starší formát a nejasný souhlas: P1, P2 a P7.
- Neúplný export, chybějící reference a nepodporovaná konstrukce: P3.
- Stejné jméno s jiným QName/kontraktem nebo chováním: P4 a P5.
- Zaměnění důkazu, přijatého rizika a schválení: P2 a P6.

Původní doporučení bylo zahájit pouze P1 a vyhodnotit výstup. P1 je dokončeno;
navazující pokyn vlastníka D-048 nyní povoluje P2–P7 bez dalších průběžných
potvrzení. Detailní packety a backlog řádky vznikají postupně pro nejbližší
část; budoucí testy se neoznačují jako spuštěné.
