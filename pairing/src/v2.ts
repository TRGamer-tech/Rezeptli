/**
 * Die gemeinsame Runde, Fassung 2.
 *
 * Der Ablauf, wie ihn zwei Leute am Tisch erwarten:
 *
 *   lobby    Eine Person eroeffnet und bekommt sofort den Code. Die zweite tritt bei. Wer
 *            eroeffnet hat, legt fest, wie viele Gerichte gesucht werden und ob es um Essen,
 *            Getraenke oder beides geht - die andere Person sieht das mit.
 *   sammeln  Beide Geraete steuern Karten bei: zuerst die eigenen gespeicherten Rezepte,
 *            spaeter neue aus dem Verzeichnis. Der Dienst mischt beides zu EINEM Stapel.
 *   laeuft   Beide wischen denselben Stapel in derselben Reihenfolge. Jede Entscheidung
 *            kommt sofort hier an; sobald genug gemeinsame Treffer da sind, ist Schluss.
 *   frage    Der Stapel ist durch, das Ziel nicht erreicht: Wer eroeffnet hat, entscheidet,
 *            ob es mit neuen Rezepten weitergeht oder ob die bisherigen Treffer genuegen.
 *   beendet  Die Treffer stehen fest.
 *
 * Was nicht auf den Tisch darf, gilt fuer beide: Die Angaben beider Personen werden
 * vereinigt und stehen in der Sitzung. Jedes Geraet steuert nur Karten bei, die dazu
 * passen - deshalb sehen beide exakt denselben Stapel, und niemand muss still
 * ueberspringen, was die andere Person ausgeschlossen hat.
 *
 * Der Dienst kennt weiterhin keine Namen und keine Konten, und nach zwoelf Stunden ist
 * alles weg.
 */

import {
    antwort,
    codeErzeugen,
    type Env,
    fehler,
    ganzzahl,
    jetzt,
    MAX_ADRESSE_LAENGE,
    MAX_TITEL_LAENGE,
    rumpfLesen,
    SITZUNG_STUNDEN,
    teilnehmerId,
    text,
    veraltet,
} from "./basis";
import {
    BEITRAG_VERMERKEN,
    KARTE_EINFUEGEN,
    NICHTS_MEHR_DA,
    OHNE_GESPEICHERTE_WEITER,
    STAND_ZAHLEN,
    STAPEL_DURCH,
    STAPEL_STEHT,
    STIMME_SPEICHERN,
    STIMME_ZURUECKNEHMEN,
    TREFFER_LISTE,
    ZIEL_ERREICHT,
} from "./queries";

const FASSUNG = 2;
const TEILNEHMER = 2;

/** So viele Karten darf eine Person je Runde beisteuern. */
const MAX_BEITRAG = 80;
const MAX_ZIEL = 20;
const MAX_STIMMEN_JE_ANFRAGE = 200;

/** Grenzen fuer das, was nicht auf den Tisch darf - grosszuegig, aber nicht beliebig. */
const MAX_ANGABEN = 40;
const MAX_EIGENE = 24;
const MAX_EIGENE_LAENGE = 24;
const MAX_SCHLUESSEL_LAENGE = 80;

const ARTEN = ["essen", "getraenke", "beides"];
const QUELLEN = ["gespeichert", "neu"];

type Zustand = "lobby" | "sammeln" | "laeuft" | "frage" | "beendet";

interface Sitzung {
    code: string;
    gastgeber: string;
    verfaellt_am: number;
    max_teilnehmer: number;
    version: number;
    zustand: Zustand;
    ziel: number;
    art: string;
    quelle: string;
    runde: number;
    einschraenkungen: string;
    ende_grund: string | null;
    stand_nr: number;
}

/** Was fuer die Runde nicht auf den Tisch darf - die Angaben aller, vereinigt. */
interface Einschraenkungen {
    /** Ernaehrungsformen, die jedes Rezept erfuellen muss. */
    ernaehrung: string[];
    /** Unvertraeglichkeiten und Allergien. */
    unvertraeglich: string[];
    /** Was jemand nicht mag. */
    ohne: string[];
    /** Eigene Stichworte, etwa "rosinen". */
    eigene: string[];
}

interface Karte {
    schluessel: string;
    titel: string;
    quelleUrl: string | null;
    bildUrl: string | null;
    zubereitungszeit: number | null;
}

interface KartenZeile {
    rezept_id: number;
    titel: string;
    quelle_url: string | null;
    bild_url: string | null;
    zubereitungszeit: number | null;
    schluessel: string | null;
    von: string | null;
}

// --------------------------------------------------------------------------- Eingaben lesen

function marken(wert: unknown): string[] {
    if (!Array.isArray(wert)) return [];
    const gelesen = wert.filter((eintrag): eintrag is string => typeof eintrag === "string" && /^[A-Z_]{1,32}$/.test(eintrag));
    return [...new Set(gelesen)].slice(0, MAX_ANGABEN);
}

function stichworte(wert: unknown): string[] {
    if (!Array.isArray(wert)) return [];
    const gelesen = wert
        .map((eintrag) => text(eintrag, MAX_EIGENE_LAENGE))
        .filter((eintrag): eintrag is string => eintrag !== null);
    return vereint([], gelesen).slice(0, MAX_EIGENE);
}

function einschraenkungenLesen(wert: unknown): Einschraenkungen {
    const roh = (typeof wert === "object" && wert !== null ? wert : {}) as Record<string, unknown>;
    return {
        ernaehrung: marken(roh.ernaehrung),
        unvertraeglich: marken(roh.unvertraeglich),
        ohne: marken(roh.ohne),
        eigene: stichworte(roh.eigene),
    };
}

/** Vereinigt zwei Listen, ohne Gross- und Kleinschreibung doppelt zu zaehlen. */
function vereint(eins: string[], zwei: string[]): string[] {
    const gesehen = new Set<string>();
    const ergebnis: string[] = [];
    for (const eintrag of [...eins, ...zwei]) {
        const schluessel = eintrag.toLowerCase();
        if (gesehen.has(schluessel)) continue;
        gesehen.add(schluessel);
        ergebnis.push(eintrag);
    }
    return ergebnis;
}

/**
 * Beide Wunschlisten zusammen: Was eine Person nicht isst, kommt fuer beide nicht auf den
 * Tisch. Bei der Ernaehrung gewinnt damit von selbst die strengere Form, denn ein Rezept
 * muss jede genannte erfuellen.
 */
function zusammenlegen(eins: Einschraenkungen, zwei: Einschraenkungen): Einschraenkungen {
    return {
        ernaehrung: vereint(eins.ernaehrung, zwei.ernaehrung).slice(0, MAX_ANGABEN),
        unvertraeglich: vereint(eins.unvertraeglich, zwei.unvertraeglich).slice(0, MAX_ANGABEN),
        ohne: vereint(eins.ohne, zwei.ohne).slice(0, MAX_ANGABEN),
        eigene: vereint(eins.eigene, zwei.eigene).slice(0, MAX_EIGENE),
    };
}

function gespeicherteEinschraenkungen(sitzung: Sitzung): Einschraenkungen {
    try {
        return einschraenkungenLesen(JSON.parse(sitzung.einschraenkungen));
    } catch {
        return einschraenkungenLesen(null);
    }
}

function kartenLesen(wert: unknown): Karte[] {
    if (!Array.isArray(wert)) return [];

    const gelesen: Karte[] = [];
    const schluessel = new Set<string>();
    for (const eintrag of wert) {
        if (typeof eintrag !== "object" || eintrag === null) continue;
        const roh = eintrag as Record<string, unknown>;
        const titel = text(roh.titel, MAX_TITEL_LAENGE);
        const kennung = text(roh.schluessel, MAX_SCHLUESSEL_LAENGE);
        if (titel === null || kennung === null || !/^[A-Za-z0-9:_-]+$/.test(kennung)) continue;
        if (schluessel.has(kennung)) continue;
        schluessel.add(kennung);

        gelesen.push({
            schluessel: kennung,
            titel,
            quelleUrl: adresse(roh.quelleUrl),
            bildUrl: adresse(roh.bildUrl),
            zubereitungszeit: ganzzahl(roh.zubereitungszeit),
        });
        if (gelesen.length >= MAX_BEITRAG) break;
    }
    return gelesen;
}

/** Nur Web-Adressen - ein Dateipfad vom Telefon der einen Person nuetzt der anderen nichts. */
function adresse(wert: unknown): string | null {
    const gelesen = text(wert, MAX_ADRESSE_LAENGE);
    return gelesen !== null && /^https?:\/\//i.test(gelesen) ? gelesen : null;
}

// --------------------------------------------------------------------------- Sitzung laden

async function laden(env: Env, code: string): Promise<Sitzung | null> {
    return env.DB.prepare(
        "SELECT code, gastgeber, verfaellt_am, max_teilnehmer, version, zustand, ziel, art, quelle, runde, " +
            "einschraenkungen, ende_grund, stand_nr FROM sitzungen WHERE code = ?1",
    )
        .bind(code)
        .first<Sitzung>();
}

/**
 * Laedt die Sitzung und prueft, ob die anfragende Person dazugehoert.
 *
 * Auch der Stand ist nur fuer Beteiligte: Er nennt, was in dieser Runde nicht auf den
 * Tisch darf, und das geht niemanden an, der bloss einen Code geraten hat.
 */
async function ladenFuer(
    env: Env,
    code: string,
    kennung: string | null,
): Promise<{ sitzung: Sitzung } | { abbruch: Response }> {
    if (kennung === null) return { abbruch: fehler("teilnehmer fehlt", 400) };

    const sitzung = await laden(env, code);
    if (sitzung === null) return { abbruch: fehler("sitzung unbekannt", 404) };
    if (sitzung.verfaellt_am < jetzt()) return { abbruch: fehler("sitzung abgelaufen", 410) };
    if (sitzung.version !== FASSUNG) return { abbruch: veraltet() };

    const dabei = await env.DB.prepare("SELECT 1 FROM teilnehmer WHERE code = ?1 AND teilnehmer_id = ?2")
        .bind(code, kennung)
        .first();
    if (dabei === null) return { abbruch: fehler("nicht Teil dieser Sitzung", 403) };

    return { sitzung };
}

// --------------------------------------------------------------------------- Stand

function karteAusgeben(zeile: KartenZeile, kennung: string) {
    return {
        rezeptId: zeile.rezept_id,
        titel: zeile.titel,
        quelleUrl: zeile.quelle_url,
        bildUrl: zeile.bild_url,
        zubereitungszeit: zeile.zubereitungszeit,
        schluessel: zeile.schluessel,
        vonMir: zeile.von === kennung,
    };
}

/**
 * Der Stand der Runde, wie ihn EINE Person sieht.
 *
 * Der Stapel haengt nur dran, wenn das Geraet ihn noch nicht hat ([kenntRunde] weicht ab) -
 * er aendert sich waehrend einer Runde nicht, und alle paar Sekunden hundert Karten zu
 * schicken waere Verschwendung. Mit dem Stapel kommen die eigenen Stimmen: So setzt ein
 * Geraet nach einem Unterbruch genau dort fort, wo es war.
 */
async function standBauen(env: Env, code: string, kennung: string, kenntRunde: number | null): Promise<Response> {
    const sitzung = await laden(env, code);
    if (sitzung === null) return fehler("sitzung unbekannt", 404);

    const teilnehmende = await env.DB.prepare(
        "SELECT teilnehmer_id, beitrag_runde FROM teilnehmer WHERE code = ?1 ORDER BY beigetreten_am",
    )
        .bind(code)
        .all<{ teilnehmer_id: string; beitrag_runde: number }>();
    const anzahl = teilnehmende.results.length;
    const ich = teilnehmende.results.find((zeile) => zeile.teilnehmer_id === kennung);

    const zahlen = await env.DB.prepare(STAND_ZAHLEN)
        .bind(code, sitzung.runde, kennung)
        .first<{ karten: number; entschieden: number; andere: number; treffer: number }>();
    const karten = zahlen?.karten ?? 0;

    const stand: Record<string, unknown> = {
        version: FASSUNG,
        code,
        verfaelltAm: sitzung.verfaellt_am,
        standNr: sitzung.stand_nr,
        zustand: sitzung.zustand,
        gastgeber: sitzung.gastgeber === kennung,
        teilnehmer: anzahl,
        ziel: sitzung.ziel,
        art: sitzung.art,
        quelle: sitzung.quelle,
        runde: sitzung.runde,
        einschraenkungen: gespeicherteEinschraenkungen(sitzung),
        beigetragen: (ich?.beitrag_runde ?? -1) >= sitzung.runde,
        wartetAuf: teilnehmende.results.filter((zeile) => zeile.beitrag_runde < sitzung.runde).length,
        karten,
        entschieden: zahlen?.entschieden ?? 0,
        andereFertig: karten > 0 && anzahl > 1 && (zahlen?.andere ?? 0) >= karten * (anzahl - 1),
        trefferAnzahl: zahlen?.treffer ?? 0,
        endeGrund: sitzung.ende_grund,
    };

    if (sitzung.zustand === "laeuft" && kenntRunde !== sitzung.runde) {
        const stapel = await env.DB.prepare(
            "SELECT rezept_id, titel, quelle_url, bild_url, zubereitungszeit, schluessel, von FROM rezepte " +
                "WHERE code = ?1 AND runde = ?2 ORDER BY position, rezept_id",
        )
            .bind(code, sitzung.runde)
            .all<KartenZeile>();
        const stimmen = await env.DB.prepare(
            "SELECT s.rezept_id, s.mag FROM stimmen s JOIN rezepte r ON r.code = s.code AND r.rezept_id = s.rezept_id " +
                "WHERE s.code = ?1 AND s.teilnehmer_id = ?2 AND r.runde = ?3 ORDER BY s.entschieden_am, r.position",
        )
            .bind(code, kennung, sitzung.runde)
            .all<{ rezept_id: number; mag: number }>();

        stand.stapel = stapel.results.map((zeile) => karteAusgeben(zeile, kennung));
        stand.stimmen = stimmen.results.map((zeile) => ({ rezeptId: zeile.rezept_id, mag: zeile.mag === 1 }));
    }

    // Welche Rezepte Treffer sind, steht erst fest, wenn nicht mehr gewischt wird. Waehrend
    // der Runde gibt es nur die Zahl - am einzelnen Treffer liesse sich sonst ablesen, wie
    // die andere Person gerade gewischt hat.
    if (sitzung.zustand === "frage" || sitzung.zustand === "beendet") {
        const treffer = await env.DB.prepare(TREFFER_LISTE).bind(code).all<KartenZeile>();
        stand.treffer = treffer.results.map((zeile) => karteAusgeben(zeile, kennung));
    }

    return antwort(stand);
}

// --------------------------------------------------------------------------- Aktionen

/**
 * Eroeffnet eine Runde - noch ohne Rezepte. Der Code steht sofort fest, damit er
 * weitergegeben werden kann, waehrend die Einzelheiten noch offen sind.
 */
async function anlegen(request: Request, env: Env): Promise<Response> {
    const rumpf = await rumpfLesen(request);
    if (rumpf === null) return fehler("ungueltige Anfrage", 400);

    const kennung = teilnehmerId(rumpf.teilnehmer);
    if (kennung === null) return fehler("teilnehmer fehlt", 400);

    const angaben = JSON.stringify(einschraenkungenLesen(rumpf.einschraenkungen));
    const zeitpunkt = jetzt();
    const verfall = zeitpunkt + SITZUNG_STUNDEN * 60 * 60 * 1000;

    // Der Code wird hier erzeugt und nicht vom Geraet vorgegeben: Sonst koennte jemand
    // einen Code erraten und ihn vor der eigentlichen Person belegen. Trifft der Zufall
    // einen vergebenen Code, wird neu gewuerfelt.
    for (let versuch = 0; versuch < 4; versuch += 1) {
        const code = codeErzeugen();
        const belegt = await env.DB.prepare("SELECT 1 FROM sitzungen WHERE code = ?1").bind(code).first();
        if (belegt !== null) continue;

        await env.DB.batch([
            env.DB.prepare(
                "INSERT INTO sitzungen (code, erstellt_am, verfaellt_am, gastgeber, max_teilnehmer, version, " +
                    "zustand, einschraenkungen) VALUES (?1, ?2, ?3, ?4, ?5, ?6, 'lobby', ?7)",
            ).bind(code, zeitpunkt, verfall, kennung, TEILNEHMER, FASSUNG, angaben),
            env.DB.prepare("INSERT INTO teilnehmer (code, teilnehmer_id, beigetreten_am) VALUES (?1, ?2, ?3)").bind(
                code,
                kennung,
                zeitpunkt,
            ),
        ]);
        const stand = await standBauen(env, code, kennung, null);
        return new Response(stand.body, { status: 201, headers: stand.headers });
    }
    return fehler("kein freier Code", 503);
}

/**
 * Tritt einer Runde bei - oder kehrt in sie zurueck.
 *
 * Neu dazukommen kann man nur in der Lobby: Sobald gesammelt wird, steht fest, wessen
 * Wuensche gelten und wessen Rezepte in den Stapel kommen. Wer schon dabei ist, darf
 * jederzeit wieder herein (das Netz bricht ab, die App wird geschlossen).
 */
async function beitreten(code: string, rumpf: Record<string, unknown>, env: Env): Promise<Response> {
    const kennung = teilnehmerId(rumpf.teilnehmer);
    if (kennung === null) return fehler("teilnehmer fehlt", 400);

    const sitzung = await laden(env, code);
    if (sitzung === null) return fehler("sitzung unbekannt", 404);
    if (sitzung.verfaellt_am < jetzt()) return fehler("sitzung abgelaufen", 410);
    if (sitzung.version !== FASSUNG) return veraltet();

    const bekannt = await env.DB.prepare("SELECT 1 FROM teilnehmer WHERE code = ?1 AND teilnehmer_id = ?2")
        .bind(code, kennung)
        .first();

    if (bekannt === null) {
        const zusammen = zusammenlegen(gespeicherteEinschraenkungen(sitzung), einschraenkungenLesen(rumpf.einschraenkungen));

        // Platz und Zustand werden in der Anweisung selbst geprueft: Zwei Leute, die im selben
        // Augenblick beitreten, koennen so nicht beide den letzten Platz bekommen.
        const [eintritt] = await env.DB.batch([
            env.DB.prepare(
                "INSERT INTO teilnehmer (code, teilnehmer_id, beigetreten_am) SELECT ?1, ?2, ?3 " +
                    "WHERE (SELECT COUNT(*) FROM teilnehmer WHERE code = ?1) < ?4 " +
                    "AND (SELECT zustand FROM sitzungen WHERE code = ?1) = 'lobby'",
            ).bind(code, kennung, jetzt(), Math.min(sitzung.max_teilnehmer, TEILNEHMER)),
            env.DB.prepare(
                "UPDATE sitzungen SET einschraenkungen = ?2, stand_nr = stand_nr + 1 WHERE code = ?1 " +
                    "AND EXISTS (SELECT 1 FROM teilnehmer WHERE code = ?1 AND teilnehmer_id = ?3)",
            ).bind(code, JSON.stringify(zusammen), kennung),
        ]);
        if ((eintritt.meta.changes ?? 0) === 0) {
            return fehler(sitzung.zustand === "lobby" ? "sitzung ist voll" : "runde laeuft schon", 409);
        }
    }

    return standBauen(env, code, kennung, ganzzahl(rumpf.kenntRunde));
}

/** Legt fest, was gesucht wird. Nur wer eroeffnet hat, und nur vor dem Start. */
async function einstellen(sitzung: Sitzung, kennung: string, rumpf: Record<string, unknown>, env: Env): Promise<Response> {
    if (sitzung.gastgeber !== kennung) return fehler("nur wer eroeffnet hat, legt das fest", 403);

    const ziel = ganzzahl(rumpf.ziel);
    const art = text(rumpf.art, 16);
    const quelle = text(rumpf.quelle, 16);
    if (ziel !== null && (ziel < 1 || ziel > MAX_ZIEL)) return fehler("ziel ausserhalb", 400);
    if (art !== null && !ARTEN.includes(art)) return fehler("art unbekannt", 400);
    if (quelle !== null && !QUELLEN.includes(quelle)) return fehler("quelle unbekannt", 400);

    await env.DB.prepare(
        "UPDATE sitzungen SET ziel = ?2, art = ?3, quelle = ?4, stand_nr = stand_nr + 1 " +
            "WHERE code = ?1 AND zustand = 'lobby'",
    )
        .bind(sitzung.code, ziel ?? sitzung.ziel, art ?? sitzung.art, quelle ?? sitzung.quelle)
        .run();
    return standBauen(env, sitzung.code, kennung, ganzzahl(rumpf.kenntRunde));
}

/** Schliesst die Lobby: Ab jetzt steuern beide ihre Karten bei. */
async function starten(sitzung: Sitzung, kennung: string, rumpf: Record<string, unknown>, env: Env): Promise<Response> {
    if (sitzung.gastgeber !== kennung) return fehler("nur wer eroeffnet hat, startet", 403);

    const ergebnis = await env.DB.prepare(
        "UPDATE sitzungen SET zustand = 'sammeln', runde = 0, stand_nr = stand_nr + 1 " +
            "WHERE code = ?1 AND zustand = 'lobby' AND (SELECT COUNT(*) FROM teilnehmer WHERE code = ?1) >= ?2",
    )
        .bind(sitzung.code, TEILNEHMER)
        .run();

    // Ein zweiter Tipp auf "Start" ist kein Fehler - nur allein laesst sich nicht beginnen.
    if ((ergebnis.meta.changes ?? 0) === 0 && sitzung.zustand === "lobby") {
        return fehler("es fehlt noch jemand", 409);
    }
    return standBauen(env, sitzung.code, kennung, ganzzahl(rumpf.kenntRunde));
}

/**
 * Nimmt den Beitrag eines Geraets zur laufenden Runde entgegen.
 *
 * Haben alle beigetragen, steht der Stapel: Die Karten beider liegen abwechselnd - gerade
 * Plaetze fuer die eine Person, ungerade fuer die andere -, damit nicht erst der ganze
 * Vorschlag der einen und dann der der anderen kommt.
 *
 * Ein verspaeteter oder doppelter Beitrag aendert nichts; die Antwort ist dann einfach der
 * aktuelle Stand, an dem sich das Geraet neu ausrichtet.
 */
async function beitragen(sitzung: Sitzung, kennung: string, rumpf: Record<string, unknown>, env: Env): Promise<Response> {
    const runde = ganzzahl(rumpf.runde);
    if (runde === null) return fehler("runde fehlt", 400);

    if (sitzung.zustand === "sammeln" && runde === sitzung.runde) {
        const karten = kartenLesen(rumpf.rezepte);
        const versatz = sitzung.gastgeber === kennung ? 0 : 1;
        const code = sitzung.code;

        await env.DB.batch([
            ...karten.map((karte, index) =>
                env.DB.prepare(KARTE_EINFUEGEN).bind(
                    code,
                    index * 2 + versatz,
                    karte.titel,
                    karte.quelleUrl,
                    karte.bildUrl,
                    karte.zubereitungszeit,
                    runde,
                    kennung,
                    karte.schluessel,
                ),
            ),
            env.DB.prepare(BEITRAG_VERMERKEN).bind(code, kennung, runde),
            env.DB.prepare(STAPEL_STEHT).bind(code),
            env.DB.prepare(OHNE_GESPEICHERTE_WEITER).bind(code),
            env.DB.prepare(NICHTS_MEHR_DA).bind(code),
            env.DB.prepare("UPDATE sitzungen SET stand_nr = stand_nr + 1 WHERE code = ?1").bind(code),
        ]);
    }
    return standBauen(env, sitzung.code, kennung, ganzzahl(rumpf.kenntRunde));
}

/**
 * Nimmt Stimmen entgegen - eine je Wisch, oder mehrere auf einmal nach einem Funkloch.
 *
 * `mag: null` nimmt eine Entscheidung zurueck. Danach prueft der Dienst im selben Zug, ob
 * das Ziel erreicht oder der Stapel durch ist. Mehrfach dasselbe zu senden ist unschaedlich.
 */
async function abstimmen(sitzung: Sitzung, kennung: string, rumpf: Record<string, unknown>, env: Env): Promise<Response> {
    if (!Array.isArray(rumpf.stimmen)) return fehler("stimmen fehlen", 400);

    const code = sitzung.code;
    const zeitpunkt = jetzt();
    const anweisungen: D1PreparedStatement[] = [];
    for (const eintrag of rumpf.stimmen.slice(0, MAX_STIMMEN_JE_ANFRAGE)) {
        if (typeof eintrag !== "object" || eintrag === null) continue;
        const roh = eintrag as Record<string, unknown>;
        const rezeptId = ganzzahl(roh.rezeptId);
        if (rezeptId === null) continue;

        if (roh.mag === null) {
            anweisungen.push(env.DB.prepare(STIMME_ZURUECKNEHMEN).bind(code, kennung, rezeptId));
        } else if (typeof roh.mag === "boolean") {
            // Die Reihenfolge der Stimmen bleibt erhalten, auch wenn sie im selben Augenblick ankommen.
            anweisungen.push(
                env.DB.prepare(STIMME_SPEICHERN).bind(code, kennung, rezeptId, roh.mag ? 1 : 0, zeitpunkt + anweisungen.length),
            );
        }
    }

    if (anweisungen.length > 0) {
        await env.DB.batch([
            ...anweisungen,
            env.DB.prepare(ZIEL_ERREICHT).bind(code),
            env.DB.prepare(STAPEL_DURCH).bind(code),
            env.DB.prepare("UPDATE sitzungen SET stand_nr = stand_nr + 1 WHERE code = ?1").bind(code),
        ]);
    }
    return standBauen(env, code, kennung, ganzzahl(rumpf.kenntRunde));
}

/** Der Stapel ist durch - wer eroeffnet hat, laesst mit neuen Rezepten weitermachen. */
async function weitermachen(sitzung: Sitzung, kennung: string, rumpf: Record<string, unknown>, env: Env): Promise<Response> {
    if (sitzung.gastgeber !== kennung) return fehler("nur wer eroeffnet hat, entscheidet das", 403);

    await env.DB.prepare(
        "UPDATE sitzungen SET zustand = 'sammeln', runde = runde + 1, quelle = 'neu', stand_nr = stand_nr + 1 " +
            "WHERE code = ?1 AND zustand = 'frage'",
    )
        .bind(sitzung.code)
        .run();
    return standBauen(env, sitzung.code, kennung, ganzzahl(rumpf.kenntRunde));
}

/**
 * Beendet die Runde mit dem, was bis dahin gefunden wurde.
 *
 * Beide duerfen das - niemand soll in einer Runde festsitzen. Der Grund haelt fest, wer
 * es war, damit die andere Person nicht raetseln muss, warum Schluss ist.
 */
async function beenden(sitzung: Sitzung, kennung: string, rumpf: Record<string, unknown>, env: Env): Promise<Response> {
    const grund = sitzung.gastgeber === kennung ? "gastgeber" : "verlassen";
    await env.DB.prepare(
        "UPDATE sitzungen SET zustand = 'beendet', ende_grund = ?2, stand_nr = stand_nr + 1 " +
            "WHERE code = ?1 AND zustand != 'beendet'",
    )
        .bind(sitzung.code, grund)
        .run();
    return standBauen(env, sitzung.code, kennung, ganzzahl(rumpf.kenntRunde));
}

/** Loescht die Runde sofort und restlos. Nur die Person, die sie eroeffnet hat, darf das. */
async function schliessen(sitzung: Sitzung, kennung: string, env: Env): Promise<Response> {
    if (sitzung.gastgeber !== kennung) return fehler("nur wer eroeffnet hat, darf schliessen", 403);

    // Die Fremdschluessel raeumen Rezepte, Teilnehmer und Stimmen mit weg.
    await env.DB.prepare("DELETE FROM sitzungen WHERE code = ?1").bind(sitzung.code).run();
    return antwort({ geschlossen: true });
}

// --------------------------------------------------------------------------- Verteiler

const PFAD = /^\/v2\/sitzung\/([A-Z0-9]{4,12})(?:\/([a-z]+))?$/;

/** Bearbeitet alles unter `/v2/`. Gibt `null` zurueck, wenn die Adresse nicht dazugehoert. */
export async function v2(request: Request, env: Env): Promise<Response | null> {
    const url = new URL(request.url);

    if (url.pathname === "/v2/sitzung") {
        return request.method === "POST" ? anlegen(request, env) : fehler("nicht gefunden", 404);
    }

    const teile = PFAD.exec(url.pathname);
    if (teile === null) return null;
    const code = teile[1];
    const aktion = teile[2] ?? "";

    if (request.method === "GET" && aktion === "") {
        const kennung = teilnehmerId(url.searchParams.get("teilnehmer"));
        const geladen = await ladenFuer(env, code, kennung);
        if ("abbruch" in geladen) return geladen.abbruch;

        // Hat sich nichts getan, genuegt eine Zeile - das Nachfragen alle paar Sekunden
        // soll den Dienst nicht jedes Mal den ganzen Stand zusammensuchen lassen.
        const bekannt = Number.parseInt(url.searchParams.get("stand") ?? "", 10);
        if (bekannt === geladen.sitzung.stand_nr) {
            return antwort({ version: FASSUNG, code, standNr: bekannt, unveraendert: true });
        }
        const kenntRunde = Number.parseInt(url.searchParams.get("runde") ?? "", 10);
        return standBauen(env, code, kennung as string, Number.isNaN(kenntRunde) ? null : kenntRunde);
    }

    if (request.method !== "POST") return fehler("nicht gefunden", 404);

    const rumpf = await rumpfLesen(request);
    if (rumpf === null) return fehler("ungueltige Anfrage", 400);
    if (aktion === "beitreten") return beitreten(code, rumpf, env);

    const kennung = teilnehmerId(rumpf.teilnehmer);
    const geladen = await ladenFuer(env, code, kennung);
    if ("abbruch" in geladen) return geladen.abbruch;
    const { sitzung } = geladen;
    const ich = kennung as string;

    switch (aktion) {
        case "einstellungen":
            return einstellen(sitzung, ich, rumpf, env);
        case "start":
            return starten(sitzung, ich, rumpf, env);
        case "beitrag":
            return beitragen(sitzung, ich, rumpf, env);
        case "stimmen":
            return abstimmen(sitzung, ich, rumpf, env);
        case "weiter":
            return weitermachen(sitzung, ich, rumpf, env);
        case "beenden":
            return beenden(sitzung, ich, rumpf, env);
        case "schliessen":
            return schliessen(sitzung, ich, env);
        default:
            return fehler("nicht gefunden", 404);
    }
}
