# Migrační proces: audit a návrh změn

Datum: 2026-10-26. Stav: **P1–P2 IMPLEMENTOVÁNO A OVĚŘENO; P3–P7 schváleno k postupné realizaci**.
Schválení vlastníka 2026-10-26: „Schvaluji“ v odpovědi na návrh a zahájení pouze P1.
Historie: dokument byl předložen jako návrh ke schválení; schválení není důkaz implementace.
Code review P1: PASS; dokumentační review: PASS po opravě popisu JSON diagnostiky.
Nezávislý reactor verify: 371 testů, 0 chyb, 26 původních skips; po poslední
dokumentační opravě 16 cílených kontrol PASS. Evidence: PROGRESS, MIG-23 P1 acceptance.
Navazující pokyn vlastníka D-048 povoluje pokračovat P2–P7 až do stop;
budoucí části zůstávají neimplementované, dokud nemají vlastní důkazy a review.
P2/MIG-24: code review r2 a docs review PASS; nezávislý reactor 420/0/0/26,
49 nových testů a 10/10 kontrol čerstvého CLI. Evidence: PROGRESS, MIG-24 P2 acceptance.
Tento dokument sám nemění současná pravidla ani neznamená schválení zákaznického návrhu.
Navazuje na [fázový plán](../plans/2026-10-26-migration-process-improvements.md).

## 1. Stručný závěr

Největší systémové mezery jsou globální uzavírání otázek před plánem, chybějící
jednoznačný aktuální stav s rozsahem schválení a chybějící propojení zdrojových dat
do doložených cest procesu. Reader už poskytuje podstatně více než inventář uzlů;
rozšíření parseru není správná odpověď na každou chybějící informaci.

Doporučené tři změny: **P1 stav a schválení → P2 závislosti otázek → P3 cesty
procesu**. Nejmenší užitečný první krok je P1: jeden soukromý strukturovaný
záznam, odvozený aktuální přehled a malý Java validátor. Na syntetickém příkladu
musí odhalit nahrazené rozhodnutí, změnu schváleného podkladu a neoprávněné
rozšíření souhlasu z umístění validace na její pravidla.

## 2. Stav checkoutu a hranice auditu

- Pozorováno `main`, HEAD `1a258867118d5418f334017114d279627c9e6a01`, jediný worktree.
- Před auditem existovaly změny `AGENTS.md`, `CLAUDE.md`, `PROGRESS.md`,
  `agent/BACKLOG.md`, `agent/HANDOFF.md`, `docs/DECISIONS.md`, `docs/WORKFLOW.md`
  a untracked `docs/CODEX-WORKFLOW.md`. Návrh je zachovává a používá skutečný
  lokální override; samotný HEAD proto nepopisuje všechna platná pravidla.
- MIG-20 má core discovery; MIG-21 CLI je ready, MIG-22 intake pending.
  Jejich historické výsledky nejsou znovu spuštěné testy tohoto auditu.
- Prohlédnuty canonical skill, reference a šablony, migrační spec, workflow,
  relevantní backlog/notes, implementace a testy readerů/helperů/CLI/MCP a dossier M-1.
- Audit je statický. Build, Maven, generování, deployment a provozní akceptace
  zde nebyly provedeny. Pilot dokládá analýzu a návrh, nikoli výsledek celé migrace.
- Soukromé citace `M-1/E01` až `E07` se rozlišují přes `local/migrations.md`;
  přesné lokátory jsou v soukromém indexu auditu. Veřejný návrh neobsahuje
  zákaznické identifikátory, hodnoty ani skutečné zákaznické cesty.

## 3. Audit: problém, důkaz, dopad a změna

Klasifikace: **R** problém pravidla, **A** použití pravidel agentem,
**T** chybějící schopnost nástroje, **V** už řešeno alespoň v daném rozsahu.
Soukromý záznam rozhodnutí je důkazem zaznamenaného rozhodnutí, nikoli runtime.

| Problém a klasifikace | Konkrétní důkaz současného stavu | Dopad | Navržená změna a místo |
|---|---|---|---|
| Všechny otázky před celým plánem — R | `skills/sap-po-migration/SKILL.md:31–37`; `references/questions.md:22,52–60`; spec 2026-10-20 §7 a P-4: při čekání žádná analýza, neznámé se nikdy neodkládá | Lokální nejistota zastaví nezávislý návrh | P2: SKILL, questions/plan/intake/attributes/bpm reference, question template a původní spec; blokovat podle závislosti a milníku |
| Automatický souhlas s předpokladem — A a rozpor R | `references/questions.md:47` už zakazuje zapsat návrh jako odpověď; `docs/CODEX-WORKFLOW.md:61–63` ponechává nejistoty otevřené, zatímco questions:52–55 vyžaduje předpoklad; M-1/E01 zachycuje opravu vlastníka | Domněnka může působit jako schválení či zjištěný fakt | P1/P2: souhlas vždy s lokátorem odpovědi a konkrétním rozsahem; neznámá skutečnost zůstává neznámá |
| Historie bez kontrolovaného aktuálního stavu — T/R; stale handoff — A | `references/questions.md:3` a `references/plan.md:36,44` chrání historii; žádný dossier validátor nebyl nalezen. M-1/E02: Q-23 nahrazuje pojmenování, continuation a shared design ještě nesou staré. Hlavička spec 2026-10-20 říká „nothing here is built yet“, přesto MIG-01–06 mají done | Agent může znovu použít nahrazenou volbu nebo znovu otevřít uzavřenou diskusi | P1: append-only události, explicitní nahrazení, revize sekcí, odvozený stav a hlášení stale podkladů |
| Souhlas s celým kolem místo jednotlivé části — R | `references/plan.md:43–45`: souhlas platí pro všechny scénáře kola; `templates/scenario.md:76`: specifikace není schválena samostatně | Souhlas s umístěním validace nelze bezpečně přenášet na obsah pravidel | P1/P2: schválení seznamu částí/revizí; integrační milník zvlášť |
| Inventář zaměněn za chování — T/A; část podkladů již V | `references/bpm.md:14,65–71` nařizuje číst uzly, podmínky a chyby; veřejný model obsahuje Flow, initialValue i assignments. Chybí souvislý report dosažitelnosti, původu hodnot a hranic chyb. M-1/E03 rozlišuje pre-process větev a procesní fault; runtime větve s optional boolean zůstává neznámý | Přítomný artefakt je zaměněn za zapojený; missing může být nepravdivě vyložen jako false | P3: `po-xray-core` bpm/model/integration/migration, výstupní model, CLI/MCP, syntetické testy; parsovat pouze prokázané mezery |
| Více implementací bez společné osy — T | Helpery poskytují inventory/attributes/discovery, žádná srovnávací matice po kroku nebyla nalezena | Rozdíly se hledají v oddělených dlouhých výstupech | P5: normalizovaná matice s explicitním přiřazením kroků, podklady z P3/P4 |
| Kontrakty a inventář artefaktů se konsolidují pozdě — R/T, ne úplné opomenutí | `references/plan.md:11,22` uvádí artefakty/data až v plánu; `templates/scenario.md:39–47` je převážně seznam parametrů kopií. M-1/E04: společný typ existoval už v časném návrhu, katalog se sjednotil později | Nejasný producent, konzument, čas vzniku hodnot a druh artefaktu | P4 + časná inventarizace v P3: contracts/artifacts reference a šablona, rozšíření zdrojových dat jen tam, kde opravdu chybí |
| Provenience a verze — V částečně, T/R pro jednotný model | `templates/profile.md:23–34`, `references/questions.md:29` už oddělují rozhodnutý update od pozorované instalace; D-029 rozlišuje artifact/external/owner. M-1/E05 správně uvádí export ≠ deployment a úspěšné volání ≠ cache | Volné texty nebrání záměně důkazních kategorií napříč dokumenty | P6: zdroj, verze, scope a omezení důkazu jako samostatná pole; schválení není technický důkaz |
| Nový balíček není ve staré KB — R | `SKILL.md:8,25`, `references/questions.md:48`, `references/plan.md:30–31` dovolují pipeline tvrzení jen přes KB/pattern, jinak globálně znovu otázky | Doložená nová vlastnost nemá přímou lokální cestu do návrhu | P6: přezkoumaný soukromý evidence packet jako další citace; generalizaci do KB zvlášť a beze změny původní evidence |
| Cílený PoC a strop složitosti — R/T; vlastní přijaté riziko již V | M-1/E06: doložený malý PoC, vlastník omezuje obnovování stavu a další blokující průzkum; systém nemá pole pro trigger znovuotevření rizika | Diskuse může znovu navrhovat již odmítnutou provozní odpovědnost | P2/P6: samostatná přijatá rizika, limit složitosti a karta experimentu |
| Delegování každého zápisu — výklad A, výkon NEPROKÁZÁN | `docs/CODEX-WORKFLOW.md:14–20` dává architektovi ledgery/rozhodnutí/findings, autorovi migrační evidenci. M-1/E07 neobsahuje časové měření | Zbytečné předávky jsou plausibilní, audit neprokázal jejich cenu | P1/P7: vyjasnit hranici ledger versus odborný obsah; bez změny rolí pro kód, testy a KB |
| Povinný cloud/field branch pro lokální opravu — V | `docs/CODEX-WORKFLOW.md:44–50,90–91`, D-046 | Toto není důvod pro další výjimku | Zachovat lokální override; neobnovovat již zrušenou překážku |

`references/…` a `templates/…` v tabulce jsou relativní ke `skills/sap-po-migration/`.
Rozsah „nenalezeno“ je prohlédnutý checkout, ne tvrzení o všech externích nástrojích.

## 4. Co zachovat

Zdrojová evidence s lokátorem; raw zdroj vedle parsovaných dat; explicitní unknown
a unresolved references; syntetické fixtures ve verzovaném projektu; zákaznické
podklady pouze v povoleném soukromém umístění; jeden zapisující agent; cílené
packety; nezávislá kontrola významných změn; TDD a reálné výstupy kontrol;
oddělené schválení před specifikací; human-task stop; poctivý placeholder generátoru.
Souhlas vlastníka se nikdy nevyvozuje z mlčení, doporučení agenta ani úspěšného testu.

## 5. Rozhodnutí návrhu: malý soubor vedle dokumentů

Zvažované varianty: pouze sjednotit Markdown (nejmenší změna, ale slabá kontrola
rozsahu souhlasu a zastarání); malý JSON vedle dokumentů (**doporučeno**, projekt
už JSON používá); databáze nebo nový workflow engine (neúměrný rozsah).

### Historie a aktuální stav

- Soukromý `dossier.json` obsahuje `formatVersion`, neutrální migration ID a
  seřazené události s unikátními ID. Událost obsahuje druh, čas nebo explicitní
  unknown, původní zdroj, dotčenou část a její revizi. Žádná zákaznická evidence
  se z něj nekopíruje do tracked fixtures.
- Události zaznamenávají návrh, souhlas, zamítnutí, odklad, explicitní nahrazení
  a přijetí rizika. Přijetí rizika není odpověď na neznámou skutečnost.
- Část návrhu má stabilní ID a revizi. Revize odkazuje na konkrétní archivovaný
  text/snapshot a SHA-256, ne pouze na pohyblivý nadpis. Rozhodnutí může zasáhnout
  více částí, ale všechny musí vyjmenovat. Schválení fixuje přesný seznam
  `sectionId + revision + digest`; žádná implicitní dědičnost na sousední části.
- Nový návrh sám neruší staré schválení. Nahrazení vyžaduje explicitní odkaz
  na staré rozhodnutí, nový schválený obsah a rozsah. Neslučitelné současné
  schválení stejné části je konflikt, nikoli automatické „vyhrává nejnovější“.
- Aktuální pohled `current-design.md` je odvozený přehled: platná schválení,
  vedle nich neschválené návrhy, otevřené závislosti, rizika a další krok.
  Jiné dokumenty jsou podklady nebo odvozené výstupy, nikoli další autorita.
- Stávající `questions.md` a schválená kola `plan.md` zůstávají nedotčené.
  Pro další schválení se ukládá snapshot dotčené části; draft soubor se smí dál
  měnit, ale starší souhlas tím nelze přenést na novou revizi.

### Kontrola a její meze

Java helper v existujícím `io.poxray.migration` načte dossier, sestaví přehled a
vrátí diagnostiky. Ověří ID/reference, nahrazení/cykly, rozsah schválení, hash
schváleného podkladu a vazby odvozených dokumentů na použitou revizi. Změna
závislosti označí registrovaný odvozený dokument jako stale. Kontrola neslibuje
sémantické rozpoznání rozporu v libovolném neregistrovaném Markdownu.

Neměnnost historie kontroluje vůči předchozímu snapshotu: předchozí události
musí být stejným prefixem nového seznamu. Bez baseline nelze prokázat, že někdo
historii zpětně nepřepsal; report to označí `HISTORY_NOT_CHECKED`. Hash sám
nepotvrzuje pravost zdroje ani souhlas vlastníka.

Read-only příkaz `po-xray dossier-check <dossier.json> [--baseline <previous.json>]
[--json]` je **navržený, dnes neexistuje**. Vrací JSON nebo Markdown včetně
aktuálního přehledu a diagnostik; nemění dossier ani podklady. Chybná struktura,
konflikt, neplatné schválení nebo stale povinný podklad znemožní kladný výsledek.
Otevřená otázka sama není chyba souboru; připravenost milníku je samostatná osa P2.

### Převzetí starého dossieru

Opt-in sidecar, žádná hromadná migrace. Každý převzatý záznam má původní
lokátor, datum pokud je známo a původní stav. Nejasné historické „souhlasím“
zůstává `approval-unresolved`, dokud nelze doložit rozsah; nevyžaduje se znovu
schválit něco, co původní doklad jednoznačně schvaluje. Dossier bez sidecaru
je podporován v původním režimu a označen jako nekontrolovaný novým validátorem.
Původní evidence a historie se nepřepisuje; parser veřejných starých dumpů
se tímto krokem nemění.

## 6. Otázky a milníky podle závislostí

Otázka má ID, neznámou skutečnost/volbu, vlastníka, zdroj, očekávaný důkaz,
dotčené části a `neededBy`: návrh konkrétní části / implementace / nasazení /
ověření. Odklad nese důvod a událost či datum přezkoumání. Topic jako version,
conversion nebo naming není totéž co blokující závislost.

Při čekání pokračuje pouze práce, která na odpovědi nezávisí. Jedna otázka
v konverzaci najednou zůstává. `deferred` nemaže závislost: po dosažení potřebného
milníku opět blokuje daný rozsah. Změna odpovědi zneplatní závislé revize/pohledy.

Explicitně odsouhlasený předpoklad smí podpořit vyjmenovanou část návrhu;
nezmění evidenční stav neznámé skutečnosti. Technická verifikace zůstane otevřená.
Přijaté provozní riziko má vlastní ID, vlastníka, rozsah, schválení, omezení a
trigger znovuotevření (nový důkaz nebo změna požadavku). Samotný přechod fáze
není důvod znovu navrhovat odmítnutou obnovu stavu.

Před každým milníkem report uvede požadované části, jejich schválené revize,
nevyřešené závislosti a platné výjimky. Dílčí souhlas není finální souhlas s celým
plánem; finální kontrola zkontroluje také propojení částí. Nasazovací otázka
nemusí blokovat návrh, ale nasazení nepovolí. Specifikace se vytváří pouze pro
schválený rozsah s vyřešenými relevantními závislostmi.

## 7. Rekonstrukce chování a kontraktů

### Co již existuje a co skutečně chybí

V následující tabulce `core/` znamená `po-xray-core/src/main/java/io/poxray/`.
Jde o staticky přečtený kód, nikoli nové runtime ověření.

| Oblast | Již dostupné | Mezery a typ práce |
|---|---|---|
| Hrany a podmínky | `core/model/Flow.java:7`, `core/bpm/ProcessReader.java:181`; Markdown je již ukazuje v `core/doc/DocumentBuilder.java:122,311` | Analýza dosažitelnosti a propojení; nikoli nový parser základních hran |
| Hodnoty a mapping | `core/model/Process.java:36` initialValue, `core/model/Mapping.java:17` flags/assignments; `core/bpm/ExpressionReader.java:72` reads/functions | Výstup: `DocumentBuilder.java:143` vynechává initialValue, `:330` strukturální assignment bez expr. Analýza: producenti a všechna přiřazení podle větví/scope |
| MCP stručnost | `po-xray-mcp/src/main/java/io/poxray/mcp/Views.java:156,292` zkracuje process; processData/step na `:157,169` vrací detail | Použít detail nebo přidat behavior view; neprohlašovat zkrácený výstup za chybějící zdroj |
| Smyčky | Zpětné hrany se mohou zachovat jako Flow; unknown uzel nese metaclass/attributes (`ProcessReader.java:262,271`) | Chybí vlastní model/parser režimu sequential/parallel/multi-instance a ukončení; rozšířit jen podle doložených konstrukcí |
| Unknown | Expression je text/metadata (`core/model/Expression.java:12`), není evaluator; `ScenarioAttributes.java:280–287` řeší jiný druh unknown | Konzervativní analýza, ne nepravdivé false. `IntegrationReader.java:281,286` může neznámou podmínku vynechat z Condition a uvést ji jen v issues; report musí číst obojí |
| Fault a scope | `core/model/Process.java:32`, `core/model/Node.java:25`, `ProcessReader.java:228,338` uchovávají scope/attachment/fault refs | Chybí propojení propagace a priorita handlerů; nevyvozovat runtime catch tam, kde zdroj nestačí |
| Async/ack a kanály | `core/model/Interface.java:11`, `IntegrationInventory.java:104,139,208` poskytují pattern/QoS/kanály/moduly | Explicitní hranice ack/transakce chybí; povolit unknown, ne odhad podle samotného async |
| Unresolved | Warnings pro mapping/expression/trigger/ESR existují | `core/bpm/MappingReader.java:51` přeskočí chybějící part, `ProcessReader.java:458` dovolí endpoint s null name; doplnit diagnostiky |
| Kontrakty | `core/integration/IntegrationReader.java:614,629` MT→DT/XSD; BPM QNameRef oddělen od ESR ObjRef | ExternalDefinition má jen id/messages/description (`core/model/IntegrationInventory.java:287`); externí message key se ztrácí v `IntegrationReader.java:694`, raw zůstává v `core/xi/XiRef.java:16` / `XiObject.java:22` |
| Discovery | `core/migration/ScenarioDiscovery.java:68,130,314`; exact name+namespace `:284` | CLI/MCP discover chybí. `core/integration/BpmIntegration.java:110` má jiný name-only fallback s poznámkou; report musí zachovat způsob a jistotu spojení |
| Provenience | `core/PoXray.java:68–72` uchovává SHA-256, verzi komponenty a čas; `core/migration/Reports.java:24` generator | Neexistuje úplný build/skill/source manifest ani dossier validátor |

Syntetické testy již pokrývají např. `ScenarioDiscoveryTest.matchingRequiresExactCaseSensitiveNameAndNamespace`,
`interfaceWithoutIcoIsReportedOnceAndKeepsAnEmptyRow`, `ConditionsTest.receiverAssignmentsKeepUnconditionalEntries`
a `alternativeConditionsAreCombinedWithOr`. Naopak parserové testy
`graphMatchesBpmn2Export`, `conditionsMatchBpmn2Export`, `mappingsCarryAllPartFlags`
a některé MCP Views testy závisejí na privátních fixtures. Bez nich mohou být
skipped (`po-xray-core/src/test/java/io/poxray/TestData.java:20`). Inventory test
nad hotovým JSON neprokazuje správné parsování zdrojové MOIN konstrukce.

### Požadovaný výsledek

P3 nejprve využije existující model a oddělí strukturální dosažitelnost od
splnitelnosti podmínky a pozorovaného běhu. „Dosažitelný v exportu“ neznamená
„provedený v produkci“. Při chybějící referenci nelze větev označit za mrtvou.

Požadovaný výstup pro každý krok: zdrojový uzel/rozsah, vstupní hrany a podmínky,
výchozí hrana, data před/po, čtení a všechna známá přiřazení s původem,
počáteční hodnoty, smyčka a její režim/ukončení, volání a kanál, chybová hrana
a její scope, asynchronní předání a doložená hranice potvrzení, unresolved položky.

Trojhodnotové vyhodnocení `true / false / unknown` se používá pouze pro
podporované výrazy s doloženými vstupy. Optional položka schématu neurčuje
runtime hodnotu. Není cílem spouštět Java moduly, plně interpretovat XPath ani
modelovat celý SAP runtime; nepodporovaná konstrukce zůstane explicitní unknown.

Od začátku se zachytí předprocesní modul → ICO → BPM vstup a odděleně fault
scope uvnitř BPM. Třída v archivu, nakonfigurovaný modul, napojená hrana a runtime
pozorování jsou různé skutečnosti. Konkrétní pilotní volba se nestává pravidlem
všech migrací.

P4 katalog zaznamená vstup/výstup, skutečný XML QName odděleně od ESR identity,
producenta/konzumenty, okamžik dostupnosti hodnot, mapping a defaulty, zachovaný
externí versus nový interní kontrakt, chybu, vlastnictví a dependencies importu.
Klasifikace dovolí External Definition/resource, nativní MT/DT, externí sdílený
Message Mapping, mapping uvnitř iFlow, Script Collection, lokální skript a
mappingovou funkci. Zařazení musí mít důkaz; ProcessDirect sám neurčí sdílení
mappingu. Hodnota vznikající až odpovědí API není povinný údaj vstupního mappingu.

P5 srovná implementace po doloženě přiřazených krocích: přítomnost a zapojení,
podmínky, data/defaulty, smyčky, volání, chyby a kontrakty. Automaticky lze
navrhnout kandidáta shody, ne prohlásit stejné jméno za stejnou sémantiku.
Nepřiřazené kroky a rozdíly zůstanou viditelné.

## 8. Provenience, nové balíčky a cílené PoC

Každé tvrzení odlišuje druh zdroje (`export`, `documentation`, `derived`,
`owner-confirmed`, `tenant-observed`), lokátor, hash/verzi, čas pozorování,
rozsah a omezení. Není to žebříček jistoty: uživatel může rozhodnout design,
ale jeho souhlas není trace technického chování. Návrh/schválení a
implementováno/ověřeno se evidují samostatně.

Verze: label exportu, release balíčku, Bundle-Version artefaktu, cílová verze,
verze pozorovaná v návrhovém prostředí a prokázaná nasazená verze jsou oddělené.
Staré pole „installed“ se bez lokátoru a scope nepřeklasifikuje jako deployed.

Nový balíček může doložit nový fakt i mimo aktuální KB: specialista připraví
ohraničený evidence packet ze zdroje, nezávislá kontrola ověří tvrzení a návrh
cituje přesně tento lokální doklad. Zůstane označen rozdíl oproti KB.
Generalizace do `knowledge/pipeline/` je samostatná anonymizovaná, citovaná a
reviewovaná změna s původními KB kontrolami; zdrojová data se do ní nekopírují.

Při intake a změně zásadní nejistoty vybrat nejvýše tři nejistoty s největším
dopadem. Karta PoC: otázka, dotčená část, nejmenší experiment, očekávané
pozorování, co výsledek neprokáže, limit času/rozsahu a způsob návratu do návrhu.
Úspěšná odpověď API neprokáže cache, idempotenci ani odolnost při retry.
Test takové vlastnosti musí pozorovat právě ji. Tento plán nepovoluje tenantové
změny ani provádění nových externích experimentů; použije existující důkazy a
lokální syntetické případy.

## 9. Orchestrace a ověření postupu

Architekt už vlastní rozhodnutí, ledgery a findings. Zápis otázky, odkazu na
souhlas, aktuálního stavu a dalšího kroku do ledgeru proto nepotřebuje novou
výjimku. Vyjasní se hranice: odborná analýza a formální migrační výstup patří
autorovi, kód/testy/KB stávajícím specialistům. Pokud je aktivní zapisující autor,
architekt nic současně nezapisuje; čeká na jeho handback.

Specialisté dostávají omezené packety pro zdrojové chování, kontrakty/API,
verze balíčků, nezávislý review uceleného návrhu a implementaci/testy. Drobné
aktualizace rozhodnutí nepodléhají celému code-task cyklu. Validátor a kontrola
aktuálního přehledu proběhnou při obnově práce a před milníkem; nezávislý review
se soustředí na významnou změnu a milník, ne na každou větu ledgeru.

P7 ověří postup na odděleném soukromém pohledu M-1 a na syntetických případech.
Původní soubory zůstanou beze změny. Výsledky uvést jako pozorované
VERIFIED_PASS / EXECUTED_FAIL / NOT_RUN / ENVIRONMENT_BLOCKED, s konkrétním
vstupem a logem. Neměřené úspory času či procenta úspěšnosti se neuvádějí.

## 10. Rozsah rozhodnutí vlastníka

Ke schválení je navržená reprezentace, změna globálních fází na závislosti
a milníky a pořadí P1–P7. Doporučený první realizační rozsah je **pouze P1**;
ostatní části zatím zůstávají plánem. Podrobné task packety, veřejné Java
signatury a red testy vzniknou po schválení příslušné části.

Výslovně mimo rozsah: dokončení M-1, změna zákaznických kontraktů, generator,
ručně psané iFlow BPMN, deployment, push, změna upstream PO, databáze, workflow
framework a obecný runtime simulátor. Návrh nevyžaduje oslabení důkazního
standardu ani zrušení nezávislého review.
