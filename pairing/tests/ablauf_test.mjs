#!/usr/bin/env node
/**
 * Spielt ganze gemeinsame Runden gegen den echten Dienst durch.
 *
 * Der Dienst laeuft dafuer lokal in derselben Laufzeit wie bei Cloudflare (workerd, ueber
 * Miniflare), mit einer echten D1-Datenbank und den echten Migrationen. Geprueft wird also
 * nicht eine Nachbildung, sondern genau der Code, der veroeffentlicht wird - samt der
 * Frage, ob zwei Telefone wirklich denselben Stapel sehen und die Runde aufhoert, sobald
 * genug Treffer da sind.
 *
 * Aufruf: node tests/ablauf_test.mjs   (im Ordner pairing, nach npm install)
 */

import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { build } from "esbuild";
import { Miniflare } from "miniflare";

const WURZEL = join(dirname(fileURLToPath(import.meta.url)), "..");

/** Die Frist fuer fehlende Beitraege in diesem Testlauf. */
const FRIST_MS = 600;

const warte = (ms) => new Promise((fertig) => setTimeout(fertig, ms));

let bestanden = 0;

function pruefe(bedingung, beschreibung, anhang) {
    if (!bedingung) {
        console.error(`FEHLGESCHLAGEN: ${beschreibung}`);
        if (anhang !== undefined) console.error(JSON.stringify(anhang, null, 2));
        process.exitCode = 1;
        throw new Error(beschreibung);
    }
    bestanden += 1;
    console.log(`bestanden: ${beschreibung}`);
}

async function dienstStarten() {
    const gebaut = await build({
        entryPoints: [join(WURZEL, "src", "index.ts")],
        bundle: true,
        format: "esm",
        target: "es2022",
        write: false,
        logLevel: "silent",
    });

    const mf = new Miniflare({
        modules: true,
        script: gebaut.outputFiles[0].text,
        compatibilityDate: "2025-01-01",
        d1Databases: { DB: "pruefung" },
        // Die Frist fuer fehlende Beitraege dauert im Betrieb eine Minute - so lange wartet
        // hier niemand. Alle anderen Ablaeufe sind in Millisekunden durch und merken nichts davon.
        bindings: { SAMMEL_FRIST_MS: String(FRIST_MS) },
    });

    // Die Migrationen in ihrer Reihenfolge - so, wie wrangler sie auch anwendet.
    const db = await mf.getD1Database("DB");
    const ordner = join(WURZEL, "migrations");
    for (const datei of readdirSync(ordner).filter((name) => name.endsWith(".sql")).sort()) {
        const sql = readFileSync(join(ordner, datei), "utf-8")
            .split("\n")
            .filter((zeile) => !zeile.trim().startsWith("--"))
            .join("\n");
        for (const anweisung of sql.split(";").map((teil) => teil.trim()).filter((teil) => teil.length > 0)) {
            await db.prepare(anweisung).run();
        }
    }
    return { mf, db };
}

/** Eine Anfrage an den Dienst; die Antwort kommt als { status, daten } zurueck. */
function anfragen(mf) {
    return async (methode, pfad, rumpf) => {
        const antwort = await mf.dispatchFetch(`http://dienst${pfad}`, {
            method: methode,
            headers: rumpf === undefined ? {} : { "content-type": "application/json" },
            body: rumpf === undefined ? undefined : JSON.stringify(rumpf),
        });
        const textinhalt = await antwort.text();
        let daten = null;
        try {
            daten = JSON.parse(textinhalt);
        } catch {
            daten = textinhalt;
        }
        return { status: antwort.status, daten };
    };
}

const ANNA = "anna-0123456789";
const BEAT = "beat-0123456789";
const CEM = "cem-0123456789";

const karte = (schluessel, titel, extra = {}) => ({ schluessel, titel, ...extra });

/** Bringt eine Runde bis in die Lobby mit zwei Personen. */
async function lobby(ruf, angabenAnna = {}, angabenBeat = {}) {
    const eroeffnet = await ruf("POST", "/v2/sitzung", { teilnehmer: ANNA, einschraenkungen: angabenAnna });
    const code = eroeffnet.daten.code;
    const beigetreten = await ruf("POST", `/v2/sitzung/${code}/beitreten`, {
        teilnehmer: BEAT,
        einschraenkungen: angabenBeat,
    });
    return { code, eroeffnet, beigetreten };
}

const stand = (ruf, code, person, zusatz = "") => ruf("GET", `/v2/sitzung/${code}?teilnehmer=${person}${zusatz}`);

const stimme = (ruf, code, person, stimmen) => ruf("POST", `/v2/sitzung/${code}/stimmen`, { teilnehmer: person, stimmen });

async function fassungEins(ruf) {
    console.log("\n-- Fassung 1 laeuft weiter (alte App) --");
    const eroeffnet = await ruf("POST", "/sitzung", {
        gastgeber: ANNA,
        rezepte: [
            { rezeptId: 1, titel: "Rösti" },
            { rezeptId: 2, titel: "Risotto" },
        ],
    });
    pruefe(eroeffnet.status === 201 && /^[A-Z2-9]{6}$/.test(eroeffnet.daten.code), "alte App: Runde eröffnen liefert einen Code");
    const code = eroeffnet.daten.code;

    const beigetreten = await ruf("POST", `/sitzung/${code}/beitreten`, {
        teilnehmer: BEAT,
        rezepte: [{ rezeptId: 3, titel: "Älplermagronen" }],
    });
    pruefe(
        beigetreten.status === 200 && beigetreten.daten.rezepte.map((r) => r.titel).join() === "Rösti,Älplermagronen,Risotto",
        "alte App: Beitreten mischt die Vorschläge abwechselnd",
        beigetreten.daten,
    );

    await ruf("POST", `/sitzung/${code}/stimmen`, {
        teilnehmer: ANNA,
        fertig: true,
        stimmen: [
            { rezeptId: 1, mag: true },
            { rezeptId: 2, mag: true },
            { rezeptId: 3, mag: false },
        ],
    });
    const halb = await ruf("GET", `/sitzung/${code}`);
    pruefe(halb.daten.alleFertig === false && halb.daten.treffer.length === 0, "alte App: keine Treffer, solange jemand wischt");

    await ruf("POST", `/sitzung/${code}/stimmen`, {
        teilnehmer: BEAT,
        fertig: true,
        stimmen: [
            { rezeptId: 1, mag: false },
            { rezeptId: 2, mag: true },
            { rezeptId: 3, mag: true },
        ],
    });
    const fertig = await ruf("GET", `/sitzung/${code}`);
    pruefe(
        fertig.daten.alleFertig === true && fertig.daten.treffer.map((t) => t.titel).join() === "Risotto",
        "alte App: am Ende steht der gemeinsame Treffer",
        fertig.daten,
    );
    return code;
}

async function fassungenMischen(ruf, alterCode) {
    console.log("\n-- Alte und neue App treffen aufeinander --");
    const neu = await ruf("POST", "/v2/sitzung", { teilnehmer: ANNA });
    pruefe(neu.status === 201, "neue App: Runde eröffnen");

    const altInNeu = await ruf("POST", `/sitzung/${neu.daten.code}/beitreten`, { teilnehmer: BEAT });
    pruefe(altInNeu.status === 426, "alte App in neuer Runde: wird gebeten zu aktualisieren");

    const altStand = await ruf("GET", `/sitzung/${neu.daten.code}`);
    pruefe(altStand.status === 426, "alte App fragt Stand einer neuen Runde ab: aktualisieren");

    const neuInAlt = await ruf("POST", `/v2/sitzung/${alterCode}/beitreten`, { teilnehmer: CEM });
    pruefe(neuInAlt.status === 426, "neue App in alter Runde: wird gebeten zu aktualisieren");
}

async function lobbyUndEinstellungen(ruf) {
    console.log("\n-- Lobby: Code zuerst, dann legt fest, wer eröffnet hat --");
    const eroeffnet = await ruf("POST", "/v2/sitzung", {
        teilnehmer: ANNA,
        einschraenkungen: { ernaehrung: [], unvertraeglich: ["NUESSE"], ohne: ["FISCH"], eigene: ["Rosinen"] },
    });
    const code = eroeffnet.daten.code;
    pruefe(
        eroeffnet.status === 201 && eroeffnet.daten.zustand === "lobby" && eroeffnet.daten.gastgeber === true,
        "der Code steht sofort, noch ohne Rezepte und ohne zweite Person",
        eroeffnet.daten,
    );
    pruefe(eroeffnet.daten.teilnehmer === 1, "in der Lobby ist zunächst nur eine Person");

    const zuFrueh = await ruf("POST", `/v2/sitzung/${code}/start`, { teilnehmer: ANNA });
    pruefe(zuFrueh.status === 409, "allein lässt sich die Runde nicht starten");

    const fremd = await stand(ruf, code, CEM);
    pruefe(fremd.status === 403, "wer nicht dazugehört, sieht den Stand nicht");

    const beigetreten = await ruf("POST", `/v2/sitzung/${code}/beitreten`, {
        teilnehmer: BEAT,
        einschraenkungen: { ernaehrung: ["VEGETARISCH"], unvertraeglich: ["NUESSE", "GLUTEN"], ohne: ["VEGAN"], eigene: ["rosinen", "Kapern"] },
    });
    pruefe(beigetreten.status === 200 && beigetreten.daten.gastgeber === false, "die zweite Person tritt bei und ist nicht Gastgeber");

    // Der Kern von Punkt 19: Was EINE Person ausschliesst, gilt fuer beide.
    const zusammen = beigetreten.daten.einschraenkungen;
    pruefe(
        zusammen.ernaehrung.join() === "VEGETARISCH" &&
            zusammen.unvertraeglich.join() === "NUESSE,GLUTEN" &&
            zusammen.ohne.join() === "FISCH,VEGAN" &&
            zusammen.eigene.join() === "Rosinen,Kapern",
        "die Angaben beider werden vereinigt - ohne Dubletten, ohne Gross-/Kleinschreibung doppelt zu zählen",
        zusammen,
    );
    const beiAnna = await stand(ruf, code, ANNA);
    pruefe(
        JSON.stringify(beiAnna.daten.einschraenkungen) === JSON.stringify(zusammen) && beiAnna.daten.teilnehmer === 2,
        "beide sehen dieselben vereinigten Angaben",
    );

    const dritte = await ruf("POST", `/v2/sitzung/${code}/beitreten`, { teilnehmer: CEM });
    pruefe(dritte.status === 409, "eine dritte Person passt nicht mehr hinein");

    const gastStellt = await ruf("POST", `/v2/sitzung/${code}/einstellungen`, { teilnehmer: BEAT, ziel: 7 });
    pruefe(gastStellt.status === 403, "die eingeladene Person kann die Anzahl nicht festlegen");

    const gestellt = await ruf("POST", `/v2/sitzung/${code}/einstellungen`, {
        teilnehmer: ANNA,
        ziel: 2,
        art: "beides",
        quelle: "gespeichert",
    });
    pruefe(gestellt.daten.ziel === 2 && gestellt.daten.art === "beides", "wer eröffnet hat, legt Anzahl und Art fest");

    const beiBeat = await stand(ruf, code, BEAT);
    pruefe(beiBeat.daten.ziel === 2 && beiBeat.daten.art === "beides", "die eingeladene Person sieht, was festgelegt wurde");

    const unsinn = await ruf("POST", `/v2/sitzung/${code}/einstellungen`, { teilnehmer: ANNA, ziel: 0 });
    pruefe(unsinn.status === 400, "eine unsinnige Anzahl wird abgewiesen");

    const gastStartet = await ruf("POST", `/v2/sitzung/${code}/start`, { teilnehmer: BEAT });
    pruefe(gastStartet.status === 403, "die eingeladene Person kann nicht starten");

    const unveraendert = await stand(ruf, code, BEAT, `&stand=${beiBeat.daten.standNr}`);
    pruefe(unveraendert.daten.unveraendert === true, "hat sich nichts getan, kommt nur 'unverändert' zurück");

    return code;
}

async function gespeicherteDannNeue(ruf, code) {
    console.log("\n-- Gespeicherte Rezepte beider, dann die Frage, dann neue --");
    const gestartet = await ruf("POST", `/v2/sitzung/${code}/start`, { teilnehmer: ANNA });
    pruefe(
        gestartet.daten.zustand === "sammeln" && gestartet.daten.runde === 0 && gestartet.daten.quelle === "gespeichert",
        "nach dem Start wird gesammelt - zuerst aus den gespeicherten Rezepten",
        gestartet.daten,
    );

    const zuSpaet = await ruf("POST", `/v2/sitzung/${code}/beitreten`, { teilnehmer: CEM });
    pruefe(zuSpaet.status === 409, "nach dem Start kann niemand Neues mehr dazukommen");

    const annas = await ruf("POST", `/v2/sitzung/${code}/beitrag`, {
        teilnehmer: ANNA,
        runde: 0,
        rezepte: [
            karte("u1", "Rösti", { quelleUrl: "https://beispiel.ch/roesti", bildUrl: "https://beispiel.ch/roesti.jpg" }),
            karte("u2", "Risotto", { quelleUrl: "https://beispiel.ch/risotto" }),
            karte("e:anna:7", "Omas Gratin", { bildUrl: "content://media/7" }),
        ],
    });
    pruefe(
        annas.daten.zustand === "sammeln" && annas.daten.beigetragen === true && annas.daten.wartetAuf === 1,
        "nach dem ersten Beitrag wartet der Dienst auf die zweite Person",
        annas.daten,
    );

    const beats = await ruf("POST", `/v2/sitzung/${code}/beitrag`, {
        teilnehmer: BEAT,
        runde: 0,
        kenntRunde: -1,
        rezepte: [
            karte("u2", "Risotto (bei Beat)", { quelleUrl: "https://beispiel.ch/risotto" }),
            karte("u3", "Älplermagronen", { quelleUrl: "https://beispiel.ch/aelpler" }),
            karte("e:beat:7", "Beats Eintopf"),
        ],
    });
    pruefe(beats.daten.zustand === "laeuft" && beats.daten.karten === 5, "mit dem zweiten Beitrag steht der Stapel - das doppelte Rezept nur einmal", beats.daten);

    const titel = beats.daten.stapel.map((eintrag) => eintrag.titel);
    pruefe(
        titel.join() === "Rösti,Risotto,Älplermagronen,Omas Gratin,Beats Eintopf",
        "die Karten beider liegen abwechselnd im Stapel",
        titel,
    );
    pruefe(
        beats.daten.stapel.find((eintrag) => eintrag.titel === "Omas Gratin").bildUrl === null,
        "ein Bild, das nur auf einem Telefon liegt, wird nicht geteilt",
    );
    pruefe(
        new Set(beats.daten.stapel.map((eintrag) => eintrag.rezeptId)).size === 5,
        "eigene Rezepte beider mit derselben Nummer überschreiben sich nicht",
    );

    // Punkt 20: Beide sehen denselben Stapel in derselben Reihenfolge.
    const beiAnna = await stand(ruf, code, ANNA, "&runde=-1");
    pruefe(
        JSON.stringify(beiAnna.daten.stapel.map((e) => [e.rezeptId, e.titel])) ===
            JSON.stringify(beats.daten.stapel.map((e) => [e.rezeptId, e.titel])),
        "beide Telefone bekommen denselben Stapel in derselben Reihenfolge",
    );
    pruefe(
        beiAnna.daten.stapel.filter((e) => e.vonMir).map((e) => e.titel).join() === "Rösti,Risotto,Omas Gratin",
        "jedes Telefon erkennt die Karten, die es selbst beigesteuert hat",
    );

    const ohneStapel = await stand(ruf, code, ANNA, "&runde=0");
    pruefe(ohneStapel.daten.stapel === undefined, "wer den Stapel schon hat, bekommt ihn nicht noch einmal geschickt");

    const [roesti, risotto, aelpler, gratin, eintopf] = beats.daten.stapel.map((eintrag) => eintrag.rezeptId);

    // Anna mag Rösti und Risotto, Beat nur Rösti: ein Treffer, Ziel (2) nicht erreicht.
    await stimme(ruf, code, ANNA, [{ rezeptId: roesti, mag: true }]);
    const nachAnna = await stimme(ruf, code, ANNA, [{ rezeptId: risotto, mag: true }]);
    pruefe(nachAnna.daten.trefferAnzahl === 0 && nachAnna.daten.entschieden === 2, "eine Zustimmung allein ist noch kein Treffer");

    const ersterTreffer = await stimme(ruf, code, BEAT, [{ rezeptId: roesti, mag: true }]);
    pruefe(ersterTreffer.daten.trefferAnzahl === 1 && ersterTreffer.daten.zustand === "laeuft", "stimmen beide zu, zählt der Treffer sofort");
    pruefe(ersterTreffer.daten.treffer === undefined, "während des Wischens nennt der Dienst nur die Zahl der Treffer, nicht welche");

    // Zuruecknehmen (Punkt 18): Beats Ja zu Rösti verschwindet, der Treffer auch.
    const zurueck = await stimme(ruf, code, BEAT, [{ rezeptId: roesti, mag: null }]);
    pruefe(zurueck.daten.trefferAnzahl === 0 && zurueck.daten.entschieden === 0, "eine zurückgenommene Entscheidung nimmt auch den Treffer zurück");
    await stimme(ruf, code, BEAT, [{ rezeptId: roesti, mag: true }]);

    await stimme(ruf, code, ANNA, [
        { rezeptId: aelpler, mag: false },
        { rezeptId: gratin, mag: true },
        { rezeptId: eintopf, mag: false },
    ]);
    const annaDurch = await stand(ruf, code, BEAT);
    pruefe(
        annaDurch.daten.zustand === "laeuft" && annaDurch.daten.andereFertig === true,
        "ist eine Person durch, läuft die Runde für die andere weiter",
        annaDurch.daten,
    );

    // Fortsetzen nach einem Unterbruch: Beat kommt zurueck und bekommt Stapel und eigene Stimmen.
    const zurueckgekehrt = await ruf("POST", `/v2/sitzung/${code}/beitreten`, { teilnehmer: BEAT, kenntRunde: -1 });
    pruefe(
        zurueckgekehrt.status === 200 &&
            zurueckgekehrt.daten.stapel.length === 5 &&
            JSON.stringify(zurueckgekehrt.daten.stimmen) === JSON.stringify([{ rezeptId: roesti, mag: true }]),
        "wer zurückkehrt, bekommt den Stapel und die eigenen bisherigen Entscheidungen",
        zurueckgekehrt.daten.stimmen,
    );

    const durch = await stimme(ruf, code, BEAT, [
        { rezeptId: risotto, mag: false },
        { rezeptId: aelpler, mag: true },
        { rezeptId: gratin, mag: false },
        { rezeptId: eintopf, mag: true },
    ]);
    // Punkt 23: Der Stapel ist durch, das Ziel nicht erreicht - jetzt wird gefragt.
    pruefe(
        durch.daten.zustand === "frage" && durch.daten.trefferAnzahl === 1,
        "sind die gespeicherten Rezepte durch und das Ziel nicht erreicht, wird gefragt",
        durch.daten,
    );
    pruefe(durch.daten.treffer.map((t) => t.titel).join() === "Rösti", "zur Frage gehören die bisherigen Treffer");

    const gastWeiter = await ruf("POST", `/v2/sitzung/${code}/weiter`, { teilnehmer: BEAT });
    pruefe(gastWeiter.status === 403, "die Frage beantwortet nur, wer eröffnet hat");

    const spaeteStimme = await stimme(ruf, code, BEAT, [{ rezeptId: risotto, mag: true }]);
    pruefe(spaeteStimme.daten.trefferAnzahl === 1, "während der Frage ändert eine nachgereichte Stimme nichts mehr");

    const weiter = await ruf("POST", `/v2/sitzung/${code}/weiter`, { teilnehmer: ANNA });
    pruefe(
        weiter.daten.zustand === "sammeln" && weiter.daten.runde === 1 && weiter.daten.quelle === "neu" && weiter.daten.beigetragen === false,
        "weitermachen heisst: eine neue Runde mit neuen Rezepten",
        weiter.daten,
    );

    const alterBeitrag = await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: ANNA, runde: 0, rezepte: [karte("u9", "Verspätet")] });
    pruefe(alterBeitrag.daten.beigetragen === false && alterBeitrag.daten.karten === 0, "ein Beitrag für eine vergangene Runde wird nicht in die neue gemischt");

    await ruf("POST", `/v2/sitzung/${code}/beitrag`, {
        teilnehmer: ANNA,
        runde: 1,
        rezepte: [karte("u1", "Rösti (noch einmal)"), karte("u10", "Lasagne"), karte("u11", "Shakshuka")],
    });
    const zweiteRunde = await ruf("POST", `/v2/sitzung/${code}/beitrag`, {
        teilnehmer: BEAT,
        runde: 1,
        kenntRunde: 0,
        rezepte: [karte("u12", "Pad Thai")],
    });
    pruefe(
        zweiteRunde.daten.zustand === "laeuft" && zweiteRunde.daten.stapel.map((e) => e.titel).join() === "Pad Thai,Lasagne,Shakshuka",
        "der neue Stapel enthält nichts, was in dieser Sitzung schon dran war",
        zweiteRunde.daten.stapel,
    );
    pruefe(zweiteRunde.daten.entschieden === 0 && zweiteRunde.daten.trefferAnzahl === 1, "die neue Runde beginnt bei null, die Treffer bleiben");

    const [padThai, lasagne] = zweiteRunde.daten.stapel.map((eintrag) => eintrag.rezeptId);
    const alteKarte = await stimme(ruf, code, ANNA, [{ rezeptId: risotto, mag: false }]);
    pruefe(alteKarte.daten.entschieden === 0, "über Karten vergangener Runden lässt sich nicht mehr abstimmen");

    await stimme(ruf, code, ANNA, [{ rezeptId: padThai, mag: true }]);
    // Punkt 24: Mit dem zweiten Treffer ist Schluss - obwohl noch Karten offen sind.
    const ziel = await stimme(ruf, code, BEAT, [{ rezeptId: padThai, mag: true }]);
    pruefe(
        ziel.daten.zustand === "beendet" && ziel.daten.endeGrund === "ziel" && ziel.daten.trefferAnzahl === 2,
        "sobald die gewünschte Anzahl gefunden ist, endet die Runde - auch mit offenen Karten",
        ziel.daten,
    );
    pruefe(ziel.daten.treffer.map((t) => t.titel).join() === "Rösti,Pad Thai", "die Treffer stehen in der Reihenfolge, in der sie im Stapel lagen");

    const danach = await stimme(ruf, code, ANNA, [{ rezeptId: lasagne, mag: true }]);
    pruefe(danach.daten.zustand === "beendet" && danach.daten.entschieden === 1, "nach dem Ende zählt keine weitere Stimme");

    const beiAnnaAmEnde = await stand(ruf, code, ANNA);
    pruefe(
        beiAnnaAmEnde.daten.zustand === "beendet" && beiAnnaAmEnde.daten.treffer.length === 2,
        "auch das andere Telefon erfährt beim nächsten Nachfragen vom Ende",
    );
}

async function ohneGespeicherte(ruf) {
    console.log("\n-- Niemand hat passende gespeicherte Rezepte --");
    const { code } = await lobby(ruf);
    await ruf("POST", `/v2/sitzung/${code}/einstellungen`, { teilnehmer: ANNA, ziel: 1, art: "getraenke" });
    await ruf("POST", `/v2/sitzung/${code}/start`, { teilnehmer: ANNA });

    await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: ANNA, runde: 0, rezepte: [] });
    const leer = await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: BEAT, runde: 0, rezepte: [] });
    pruefe(
        leer.daten.zustand === "sammeln" && leer.daten.runde === 1 && leer.daten.quelle === "neu" && leer.daten.beigetragen === false,
        "ohne gespeicherte Rezepte geht es ohne Rückfrage mit neuen weiter",
        leer.daten,
    );

    await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: ANNA, runde: 1, rezepte: [] });
    const nichts = await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: BEAT, runde: 1, rezepte: [] });
    pruefe(
        nichts.daten.zustand === "beendet" && nichts.daten.endeGrund === "leer" && nichts.daten.treffer.length === 0,
        "findet auch bei den neuen niemand etwas, endet die Runde ehrlich statt ewig zu laden",
        nichts.daten,
    );
}

async function nurNeue(ruf) {
    console.log("\n-- Gleich mit neuen Rezepten beginnen --");
    const { code } = await lobby(ruf);
    const gestellt = await ruf("POST", `/v2/sitzung/${code}/einstellungen`, { teilnehmer: ANNA, ziel: 1, quelle: "neu" });
    pruefe(gestellt.daten.quelle === "neu" && gestellt.daten.art === "essen", "die Quelle lässt sich festlegen, die übrigen Einstellungen bleiben");
    const gestartet = await ruf("POST", `/v2/sitzung/${code}/start`, { teilnehmer: ANNA });
    pruefe(gestartet.daten.quelle === "neu" && gestartet.daten.runde === 0, "die Runde beginnt dann direkt mit neuen Rezepten");

    await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: BEAT, runde: 0, rezepte: [karte("u1", "Curry")] });
    const laeuft = await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: ANNA, runde: 0, kenntRunde: -1, rezepte: [] });
    pruefe(laeuft.daten.zustand === "laeuft" && laeuft.daten.karten === 1, "es genügt, wenn eine Person Karten beisteuert");

    const curry = laeuft.daten.stapel[0].rezeptId;
    await stimme(ruf, code, ANNA, [{ rezeptId: curry, mag: false }]);
    const durch = await stimme(ruf, code, BEAT, [{ rezeptId: curry, mag: true }]);
    pruefe(durch.daten.zustand === "frage", "auch ein Stapel aus neuen Rezepten endet mit der Frage, wenn das Ziel fehlt");

    const schluss = await ruf("POST", `/v2/sitzung/${code}/beenden`, { teilnehmer: ANNA });
    pruefe(
        schluss.daten.zustand === "beendet" && schluss.daten.endeGrund === "gastgeber",
        "wer eröffnet hat, kann aufhören und behalten, was gefunden wurde",
        schluss.daten,
    );
}

async function verlassenUndSchliessen(ruf, db) {
    console.log("\n-- Verlassen und schliessen --");
    const { code } = await lobby(ruf);
    await ruf("POST", `/v2/sitzung/${code}/start`, { teilnehmer: ANNA });
    await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: ANNA, runde: 0, rezepte: [karte("u1", "Rösti"), karte("u2", "Risotto")] });
    const laeuft = await ruf("POST", `/v2/sitzung/${code}/beitrag`, { teilnehmer: BEAT, runde: 0, kenntRunde: -1, rezepte: [] });
    const roesti = laeuft.daten.stapel[0].rezeptId;
    await stimme(ruf, code, ANNA, [{ rezeptId: roesti, mag: true }]);
    await stimme(ruf, code, BEAT, [{ rezeptId: roesti, mag: true }]);

    const gegangen = await ruf("POST", `/v2/sitzung/${code}/beenden`, { teilnehmer: BEAT });
    pruefe(
        gegangen.daten.zustand === "beendet" && gegangen.daten.endeGrund === "verlassen",
        "auch die eingeladene Person kann aufhören - die Runde endet dann für beide",
    );
    const beiAnna = await stand(ruf, code, ANNA);
    pruefe(
        beiAnna.daten.endeGrund === "verlassen" && beiAnna.daten.treffer.map((t) => t.titel).join() === "Rösti",
        "die andere Person erfährt, warum Schluss ist, und behält die Treffer",
    );

    const gastSchliesst = await ruf("POST", `/v2/sitzung/${code}/schliessen`, { teilnehmer: BEAT });
    pruefe(gastSchliesst.status === 403, "löschen darf nur, wer eröffnet hat");

    const geschlossen = await ruf("POST", `/v2/sitzung/${code}/schliessen`, { teilnehmer: ANNA });
    pruefe(geschlossen.daten.geschlossen === true, "wer eröffnet hat, kann die Runde sofort löschen");

    const uebrig = await db
        .prepare(
            "SELECT (SELECT COUNT(*) FROM rezepte WHERE code = ?1) + (SELECT COUNT(*) FROM stimmen WHERE code = ?1) + " +
                "(SELECT COUNT(*) FROM teilnehmer WHERE code = ?1) AS anzahl",
        )
        .bind(code)
        .first();
    pruefe(uebrig.anzahl === 0, "nach dem Löschen bleibt nichts zurück - keine Rezepte, keine Stimmen, keine Beteiligten");

    const weg = await stand(ruf, code, ANNA);
    pruefe(weg.status === 404, "eine gelöschte Runde gibt es nicht mehr");
}

async function fristFuerFehlendeBeitraege(ruf) {
    console.log("\n-- Ein Telefon steuert nie etwas bei --");
    const { code } = await lobby(ruf);
    await ruf("POST", `/v2/sitzung/${code}/start`, { teilnehmer: ANNA });

    const vorher = await stand(ruf, code, ANNA);
    pruefe(vorher.daten.zustand === "sammeln" && vorher.daten.fristRestMs === null, "solange niemand beigetragen hat, läuft keine Frist");

    const beitrag = await ruf("POST", `/v2/sitzung/${code}/beitrag`, {
        teilnehmer: ANNA,
        runde: 0,
        rezepte: [karte("u1", "Rösti"), karte("u2", "Risotto")],
    });
    pruefe(
        beitrag.daten.zustand === "sammeln" && beitrag.daten.fristRestMs > 0 && beitrag.daten.fristRestMs <= FRIST_MS,
        "mit dem ersten Beitrag beginnt die Frist für die andere Person",
        beitrag.daten,
    );

    const gleich = await stand(ruf, code, ANNA, `&stand=${beitrag.daten.standNr}`);
    pruefe(gleich.daten.unveraendert === true, "vor Ablauf der Frist wartet die Runde weiter");

    await warte(FRIST_MS + 150);
    // Das Nachfragen selbst stoesst die Entscheidung an - auch wenn das Geraet den Stand schon kennt.
    const danach = await stand(ruf, code, ANNA, `&stand=${beitrag.daten.standNr}&runde=-1`);
    pruefe(
        danach.daten.zustand === "laeuft" && danach.daten.stapel.map((eintrag) => eintrag.titel).join() === "Rösti,Risotto",
        "ist die Frist um, beginnt die Runde mit den Karten, die da sind",
        danach.daten,
    );
    pruefe(danach.daten.fristRestMs === null && danach.daten.wartetAuf === 0, "danach wartet niemand mehr auf jemanden");

    const verspaetet = await ruf("POST", `/v2/sitzung/${code}/beitrag`, {
        teilnehmer: BEAT,
        runde: 0,
        kenntRunde: -1,
        rezepte: [karte("u3", "Zu spät")],
    });
    pruefe(
        verspaetet.status === 200 && verspaetet.daten.zustand === "laeuft" && verspaetet.daten.stapel.length === 2,
        "wer zu spät beisteuert, wischt den Stapel mit, der schon steht",
        verspaetet.daten,
    );

    // Wer als Einzige beisteuert und nichts Passendes hat: Dann geht es mit neuen Rezepten weiter.
    const leer = await lobby(ruf);
    await ruf("POST", `/v2/sitzung/${leer.code}/start`, { teilnehmer: ANNA });
    await ruf("POST", `/v2/sitzung/${leer.code}/beitrag`, { teilnehmer: BEAT, runde: 0, rezepte: [] });
    await warte(FRIST_MS + 150);
    const weiter = await stand(ruf, leer.code, BEAT);
    pruefe(
        weiter.daten.zustand === "sammeln" && weiter.daten.runde === 1 && weiter.daten.quelle === "neu" && weiter.daten.fristRestMs === null,
        "hat die einzige Person, die da ist, nichts Gespeichertes, geht es nach der Frist mit neuen Rezepten weiter",
        weiter.daten,
    );
    pruefe(weiter.daten.beigetragen === false, "für die neue Runde ist wieder jeder gefragt - die Frist beginnt von vorn");
}

async function selbstErfassteTreffer(ruf, db) {
    console.log("\n-- Ein selbst erfasstes Rezept wird zum Treffer --");
    const { code } = await lobby(ruf);
    await ruf("POST", `/v2/sitzung/${code}/einstellungen`, { teilnehmer: ANNA, ziel: 2 });
    await ruf("POST", `/v2/sitzung/${code}/start`, { teilnehmer: ANNA });
    await ruf("POST", `/v2/sitzung/${code}/beitrag`, {
        teilnehmer: ANNA,
        runde: 0,
        rezepte: [karte("e:anna:7", "Omas Hackbraten"), karte("e:anna:8", "Tante Rosas Eintopf")],
    });
    const laeuft = await ruf("POST", `/v2/sitzung/${code}/beitrag`, {
        teilnehmer: BEAT,
        runde: 0,
        kenntRunde: -1,
        rezepte: [karte("u1", "Rösti", { quelleUrl: "https://beispiel.ch/roesti" })],
    });
    const nachTitel = Object.fromEntries(laeuft.daten.stapel.map((eintrag) => [eintrag.titel, eintrag.rezeptId]));
    const hackbraten = nachTitel["Omas Hackbraten"];
    const eintopf = nachTitel["Tante Rosas Eintopf"];
    const roesti = nachTitel["Rösti"];

    const inhalt = {
        zubereitung: "Alles mischen, formen, eine Stunde im Ofen backen.",
        schritte: ["Mischen und formen.", "Eine Stunde backen."],
        zutaten: [
            { name: "Hackfleisch", menge: 800, einheit: "GRAMM" },
            { name: "Zwiebeln", menge: 2, einheit: "NONE", notiz: "fein gehackt" },
            { name: "Salz" },
        ],
        portionen: 4,
        zeit: 75,
        tags: ["Familie"],
    };

    const zuFrueh = await ruf("POST", `/v2/sitzung/${code}/inhalt`, { teilnehmer: ANNA, rezeptId: hackbraten, inhalt });
    pruefe(zuFrueh.status === 409, "solange gewischt wird, verlässt kein Rezeptinhalt das Gerät");

    await stimme(ruf, code, ANNA, [
        { rezeptId: hackbraten, mag: true },
        { rezeptId: eintopf, mag: true },
        { rezeptId: roesti, mag: true },
    ]);
    const ende = await stimme(ruf, code, BEAT, [
        { rezeptId: hackbraten, mag: true },
        { rezeptId: eintopf, mag: false },
        { rezeptId: roesti, mag: true },
    ]);
    pruefe(ende.daten.zustand === "beendet" && ende.daten.treffer.length === 2, "zwei Treffer: der Hackbraten und die Rösti", ende.daten);
    pruefe(
        ende.daten.treffer.every((treffer) => treffer.inhalt === false),
        "zu Beginn liegt zu keinem Treffer ein Inhalt beim Dienst",
        ende.daten.treffer,
    );

    const nochNicht = await ruf("GET", `/v2/sitzung/${code}/inhalt?teilnehmer=${BEAT}&rezept=${hackbraten}`);
    pruefe(
        nochNicht.status === 200 && nochNicht.daten.inhalt === null,
        "was noch nicht hinterlegt ist, ist kein Fehler - es ist nur noch nicht da",
        nochNicht.daten,
    );

    const keinTreffer = await ruf("POST", `/v2/sitzung/${code}/inhalt`, { teilnehmer: ANNA, rezeptId: eintopf, inhalt });
    pruefe(keinTreffer.status === 409, "ein Rezept, das nur eine Person mochte, bleibt auf ihrem Gerät");

    const fremdes = await ruf("POST", `/v2/sitzung/${code}/inhalt`, { teilnehmer: BEAT, rezeptId: hackbraten, inhalt });
    pruefe(fremdes.status === 409, "den Inhalt hinterlegt nur das Gerät, von dem das Rezept stammt");

    const mitAdresse = await ruf("POST", `/v2/sitzung/${code}/inhalt`, { teilnehmer: BEAT, rezeptId: roesti, inhalt });
    pruefe(mitAdresse.status === 409, "ein Rezept mit Adresse braucht keinen Inhalt beim Dienst - es wird von seiner Seite geholt");

    const leererInhalt = await ruf("POST", `/v2/sitzung/${code}/inhalt`, { teilnehmer: ANNA, rezeptId: hackbraten, inhalt: { tags: ["x"] } });
    pruefe(leererInhalt.status === 400, "ein Inhalt ohne Zutaten und Zubereitung ist keiner");

    const zuGross = await ruf("POST", `/v2/sitzung/${code}/inhalt`, {
        teilnehmer: ANNA,
        rezeptId: hackbraten,
        inhalt: { ...inhalt, schritte: Array.from({ length: 60 }, () => "x".repeat(2000)) },
    });
    pruefe(zuGross.status === 413, "ein übergrosser Inhalt wird abgelehnt statt gekürzt gespeichert");

    const hinterlegt = await ruf("POST", `/v2/sitzung/${code}/inhalt`, {
        teilnehmer: ANNA,
        rezeptId: hackbraten,
        inhalt: { ...inhalt, geheim: "gehört nicht dazu", zutaten: [...inhalt.zutaten, { menge: 3 }, "kaputt"] },
    });
    pruefe(hinterlegt.status === 200 && hinterlegt.daten.gespeichert === true, "der Inhalt eines gemeinsamen Treffers lässt sich hinterlegen");

    const geholt = await ruf("GET", `/v2/sitzung/${code}/inhalt?teilnehmer=${BEAT}&rezept=${hackbraten}`);
    pruefe(
        geholt.status === 200 &&
            geholt.daten.inhalt.zutaten.length === 3 &&
            geholt.daten.inhalt.zutaten[0].menge === 800 &&
            geholt.daten.inhalt.zutaten[1].notiz === "fein gehackt" &&
            geholt.daten.inhalt.zutaten[2].menge === null &&
            geholt.daten.inhalt.schritte.length === 2 &&
            geholt.daten.inhalt.portionen === 4 &&
            geholt.daten.inhalt.zeit === 75,
        "die andere Person bekommt Zutaten, Schritte, Portionen und Zeit",
        geholt.daten,
    );
    pruefe(!("geheim" in geholt.daten.inhalt), "gespeichert wird nur, was der Dienst kennt");

    const beiBeat = await stand(ruf, code, BEAT);
    const marke = Object.fromEntries(beiBeat.daten.treffer.map((treffer) => [treffer.titel, treffer.inhalt]));
    pruefe(marke["Omas Hackbraten"] === true && marke["Rösti"] === false, "der Stand zeigt, zu welchem Treffer der Inhalt da ist", beiBeat.daten.treffer);

    const fremd = await ruf("GET", `/v2/sitzung/${code}/inhalt?teilnehmer=${CEM}&rezept=${hackbraten}`);
    pruefe(fremd.status === 403, "wer nicht in der Runde ist, bekommt den Inhalt nicht");

    await ruf("POST", `/v2/sitzung/${code}/schliessen`, { teilnehmer: ANNA });
    const uebrig = await db.prepare("SELECT COUNT(*) AS anzahl FROM inhalte WHERE code = ?1").bind(code).first();
    pruefe(uebrig.anzahl === 0, "mit der Runde verschwindet auch der hinterlegte Inhalt");
}

async function eingabenAbsichern(ruf) {
    console.log("\n-- Eingaben von aussen --");
    const ohneKennung = await ruf("POST", "/v2/sitzung", { einschraenkungen: {} });
    pruefe(ohneKennung.status === 400, "ohne Kennung lässt sich nichts eröffnen");

    const eroeffnet = await ruf("POST", "/v2/sitzung", {
        teilnehmer: ANNA,
        einschraenkungen: {
            ernaehrung: ["VEGAN", "vegan", 7, "<script>"],
            unvertraeglich: "NUESSE",
            ohne: Array.from({ length: 90 }, (_, index) => `X${"A".repeat(index % 20)}`),
            eigene: ["  Rosinen  ", "", "x".repeat(60), null],
        },
    });
    const gelesen = eroeffnet.daten.einschraenkungen;
    pruefe(
        gelesen.ernaehrung.join() === "VEGAN" && gelesen.unvertraeglich.length === 0 && gelesen.ohne.length <= 40,
        "Angaben werden nur in der erwarteten Form angenommen",
        gelesen,
    );
    pruefe(
        gelesen.eigene[0] === "Rosinen" && gelesen.eigene[1].length === 24 && gelesen.eigene.length === 2,
        "eigene Stichworte werden gekürzt und gesäubert",
        gelesen.eigene,
    );

    const unbekannt = await ruf("POST", "/v2/sitzung/ZZZZZZ/beitreten", { teilnehmer: BEAT });
    pruefe(unbekannt.status === 404, "einen erfundenen Code gibt es nicht");

    const gesund = await ruf("GET", "/gesundheit");
    pruefe(gesund.daten.bereit === true && gesund.daten.fassungen.includes(2), "der Dienst nennt die Fassungen, die er spricht");
}

async function main() {
    const { mf, db } = await dienstStarten();
    const ruf = anfragen(mf);
    try {
        const alterCode = await fassungEins(ruf);
        await fassungenMischen(ruf, alterCode);
        const code = await lobbyUndEinstellungen(ruf);
        await gespeicherteDannNeue(ruf, code);
        await ohneGespeicherte(ruf);
        await nurNeue(ruf);
        await verlassenUndSchliessen(ruf, db);
        await fristFuerFehlendeBeitraege(ruf);
        await selbstErfassteTreffer(ruf, db);
        await eingabenAbsichern(ruf);
        console.log(`\nAlle ${bestanden} Prüfungen bestanden.`);
    } finally {
        await mf.dispose();
    }
}

main().catch((grund) => {
    console.error(grund);
    process.exit(1);
});
