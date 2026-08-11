/**
 * Rezeptli-Pairing: der einzige Server, den die App kennt.
 *
 * Er tut genau eine Sache: zwei Telefonen erlauben, dieselbe Swipe-Runde zu
 * teilen. Er kennt keine Konten, speichert keine Namen und behaelt nichts
 * laenger als ein paar Stunden.
 *
 * Rezepte selbst bleiben auf den Geraeten. Der Dienst sieht nur, was noetig
 * ist, damit die zweite Person weiss, worueber sie abstimmt: Titel, eine
 * Quelladresse falls vorhanden, und ein Bildlink.
 */

import { TREFFER_QUERY } from "./queries";

export interface Env {
    DB: D1Database;
}

/** So lange lebt eine Sitzung. Danach raeumt der taegliche Auftrag sie weg. */
const SITZUNG_STUNDEN = 12;

/** Ohne I, O, 0 und 1 - die verwechselt man beim Abtippen. */
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_LAENGE = 6;

const MAX_REZEPTE = 200;
const MAX_TITEL_LAENGE = 200;
const MAX_TEILNEHMER = 8;

interface RezeptEingabe {
    rezeptId: number;
    titel: string;
    quelleUrl?: string | null;
    bildUrl?: string | null;
    zubereitungszeit?: number | null;
}

function jetzt(): number {
    return Date.now();
}

function code_erzeugen(): string {
    const zufall = new Uint8Array(CODE_LAENGE);
    crypto.getRandomValues(zufall);
    return Array.from(zufall)
        .map((wert) => CODE_ALPHABET[wert % CODE_ALPHABET.length])
        .join("");
}

function antwort(daten: unknown, status = 200): Response {
    return new Response(JSON.stringify(daten), {
        status,
        headers: { "content-type": "application/json; charset=utf-8" },
    });
}

function fehler(grund: string, status: number): Response {
    return antwort({ fehler: grund }, status);
}

/** Kuerzt und saeubert, was von aussen kommt - nichts wird ungeprueft gespeichert. */
function text(wert: unknown, maxLaenge: number): string | null {
    if (typeof wert !== "string") return null;
    const sauber = wert.trim().slice(0, maxLaenge);
    return sauber.length > 0 ? sauber : null;
}

function teilnehmerId(wert: unknown): string | null {
    const kennung = text(wert, 64);
    if (kennung === null) return null;
    // Nur harmlose Zeichen - die Kennung erzeugt das Geraet selbst.
    return /^[A-Za-z0-9_-]+$/.test(kennung) ? kennung : null;
}

async function sitzungLaden(env: Env, code: string) {
    return env.DB.prepare(
        "SELECT code, gastgeber, verfaellt_am, max_teilnehmer FROM sitzungen WHERE code = ?1",
    )
        .bind(code)
        .first<{ code: string; gastgeber: string; verfaellt_am: number; max_teilnehmer: number }>();
}

/**
 * Legt eine Sitzung an.
 *
 * Der Code wird hier erzeugt und nicht vom Geraet vorgegeben: Sonst koennte
 * jemand einen Code erraten und ihn vor der eigentlichen Person belegen.
 */
async function sitzungAnlegen(request: Request, env: Env): Promise<Response> {
    const rumpf = await request.json().catch(() => null);
    if (rumpf === null || typeof rumpf !== "object") return fehler("ungueltige Anfrage", 400);

    const { gastgeber, rezepte } = rumpf as { gastgeber?: unknown; rezepte?: unknown };

    const gastgeberId = teilnehmerId(gastgeber);
    if (gastgeberId === null) return fehler("gastgeber fehlt", 400);
    if (!Array.isArray(rezepte) || rezepte.length === 0) return fehler("rezepte fehlen", 400);
    if (rezepte.length > MAX_REZEPTE) return fehler("zu viele rezepte", 413);

    const gepruefte: RezeptEingabe[] = [];
    for (const eintrag of rezepte) {
        if (typeof eintrag !== "object" || eintrag === null) continue;
        const roh = eintrag as Record<string, unknown>;
        const titel = text(roh.titel, MAX_TITEL_LAENGE);
        const rezeptId = typeof roh.rezeptId === "number" ? Math.trunc(roh.rezeptId) : null;
        if (titel === null || rezeptId === null) continue;

        gepruefte.push({
            rezeptId,
            titel,
            quelleUrl: text(roh.quelleUrl, 500),
            bildUrl: text(roh.bildUrl, 500),
            zubereitungszeit:
                typeof roh.zubereitungszeit === "number" ? Math.trunc(roh.zubereitungszeit) : null,
        });
    }
    if (gepruefte.length === 0) return fehler("keine brauchbaren rezepte", 400);

    const code = code_erzeugen();
    const zeitpunkt = jetzt();
    const verfall = zeitpunkt + SITZUNG_STUNDEN * 60 * 60 * 1000;

    const anweisungen = [
        env.DB.prepare(
            "INSERT INTO sitzungen (code, erstellt_am, verfaellt_am, gastgeber, max_teilnehmer) " +
                "VALUES (?1, ?2, ?3, ?4, ?5)",
        ).bind(code, zeitpunkt, verfall, gastgeberId, 2),
        env.DB.prepare(
            "INSERT INTO teilnehmer (code, teilnehmer_id, beigetreten_am) VALUES (?1, ?2, ?3)",
        ).bind(code, gastgeberId, zeitpunkt),
        ...gepruefte.map((rezept, index) =>
            env.DB.prepare(
                "INSERT INTO rezepte (code, rezept_id, position, titel, quelle_url, bild_url, " +
                    "zubereitungszeit) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)",
            ).bind(
                code,
                rezept.rezeptId,
                index,
                rezept.titel,
                rezept.quelleUrl,
                rezept.bildUrl,
                rezept.zubereitungszeit,
            ),
        ),
    ];

    await env.DB.batch(anweisungen);

    return antwort({ code, verfaelltAm: verfall, rezepte: gepruefte.length }, 201);
}

/** Tritt einer Sitzung bei und bekommt die Rezepte, ueber die abgestimmt wird. */
async function beitreten(code: string, request: Request, env: Env): Promise<Response> {
    const rumpf = await request.json().catch(() => null);
    const kennung = teilnehmerId((rumpf as { teilnehmer?: unknown } | null)?.teilnehmer);
    if (kennung === null) return fehler("teilnehmer fehlt", 400);

    const sitzung = await sitzungLaden(env, code);
    if (sitzung === null) return fehler("sitzung unbekannt", 404);
    if (sitzung.verfaellt_am < jetzt()) return fehler("sitzung abgelaufen", 410);

    const bisher = await env.DB.prepare(
        "SELECT COUNT(*) AS anzahl FROM teilnehmer WHERE code = ?1",
    )
        .bind(code)
        .first<{ anzahl: number }>();

    const istBekannt = await env.DB.prepare(
        "SELECT 1 FROM teilnehmer WHERE code = ?1 AND teilnehmer_id = ?2",
    )
        .bind(code, kennung)
        .first();

    // Ein erneuter Beitritt derselben Person ist kein Fehler - das Netz bricht
    // schon mal ab, und dann versucht es die App noch einmal.
    if (istBekannt === null) {
        if ((bisher?.anzahl ?? 0) >= Math.min(sitzung.max_teilnehmer, MAX_TEILNEHMER)) {
            return fehler("sitzung ist voll", 409);
        }
        await env.DB.prepare(
            "INSERT INTO teilnehmer (code, teilnehmer_id, beigetreten_am) VALUES (?1, ?2, ?3)",
        )
            .bind(code, kennung, jetzt())
            .run();
    }

    const rezepte = await env.DB.prepare(
        "SELECT rezept_id, titel, quelle_url, bild_url, zubereitungszeit FROM rezepte " +
            "WHERE code = ?1 ORDER BY position",
    )
        .bind(code)
        .all<{
            rezept_id: number;
            titel: string;
            quelle_url: string | null;
            bild_url: string | null;
            zubereitungszeit: number | null;
        }>();

    return antwort({
        code,
        verfaelltAm: sitzung.verfaellt_am,
        rezepte: rezepte.results.map((zeile) => ({
            rezeptId: zeile.rezept_id,
            titel: zeile.titel,
            quelleUrl: zeile.quelle_url,
            bildUrl: zeile.bild_url,
            zubereitungszeit: zeile.zubereitungszeit,
        })),
    });
}

/** Nimmt Stimmen entgegen. Mehrfach dasselbe zu senden ist unschaedlich. */
async function stimmenSenden(code: string, request: Request, env: Env): Promise<Response> {
    const rumpf = await request.json().catch(() => null);
    if (rumpf === null || typeof rumpf !== "object") return fehler("ungueltige Anfrage", 400);

    const { teilnehmer, stimmen, fertig } = rumpf as {
        teilnehmer?: unknown;
        stimmen?: unknown;
        fertig?: unknown;
    };

    const kennung = teilnehmerId(teilnehmer);
    if (kennung === null) return fehler("teilnehmer fehlt", 400);
    if (!Array.isArray(stimmen)) return fehler("stimmen fehlen", 400);

    const sitzung = await sitzungLaden(env, code);
    if (sitzung === null) return fehler("sitzung unbekannt", 404);
    if (sitzung.verfaellt_am < jetzt()) return fehler("sitzung abgelaufen", 410);

    const gehoertDazu = await env.DB.prepare(
        "SELECT 1 FROM teilnehmer WHERE code = ?1 AND teilnehmer_id = ?2",
    )
        .bind(code, kennung)
        .first();
    if (gehoertDazu === null) return fehler("nicht Teil dieser Sitzung", 403);

    const zeitpunkt = jetzt();
    const anweisungen = stimmen
        .filter(
            (eintrag): eintrag is { rezeptId: number; mag: boolean } =>
                typeof eintrag === "object" &&
                eintrag !== null &&
                typeof (eintrag as Record<string, unknown>).rezeptId === "number" &&
                typeof (eintrag as Record<string, unknown>).mag === "boolean",
        )
        .slice(0, MAX_REZEPTE)
        .map((eintrag) =>
            env.DB.prepare(
                "INSERT INTO stimmen (code, teilnehmer_id, rezept_id, mag, entschieden_am) " +
                    "VALUES (?1, ?2, ?3, ?4, ?5) " +
                    "ON CONFLICT (code, teilnehmer_id, rezept_id) DO UPDATE SET " +
                    "mag = excluded.mag, entschieden_am = excluded.entschieden_am",
            ).bind(code, kennung, Math.trunc(eintrag.rezeptId), eintrag.mag ? 1 : 0, zeitpunkt),
        );

    if (fertig === true) {
        anweisungen.push(
            env.DB.prepare(
                "UPDATE teilnehmer SET fertig_am = ?3 WHERE code = ?1 AND teilnehmer_id = ?2",
            ).bind(code, kennung, zeitpunkt),
        );
    }

    if (anweisungen.length > 0) await env.DB.batch(anweisungen);

    return antwort({ gespeichert: anweisungen.length });
}

/**
 * Stand der Sitzung: wer ist da, wer ist fertig, und - wenn alle fertig sind -
 * die gemeinsamen Treffer.
 *
 * Die Treffer werden bewusst erst herausgegeben, wenn alle entschieden haben.
 * Sonst koennte man am Zwischenstand ablesen, was die andere Person gewischt
 * hat, und das ist beim gemeinsamen Aussuchen der halbe Reiz.
 */
async function stand(code: string, env: Env): Promise<Response> {
    const sitzung = await sitzungLaden(env, code);
    if (sitzung === null) return fehler("sitzung unbekannt", 404);
    if (sitzung.verfaellt_am < jetzt()) return fehler("sitzung abgelaufen", 410);

    const teilnehmende = await env.DB.prepare(
        "SELECT teilnehmer_id, fertig_am FROM teilnehmer WHERE code = ?1 ORDER BY beigetreten_am",
    )
        .bind(code)
        .all<{ teilnehmer_id: string; fertig_am: number | null }>();

    const anzahl = teilnehmende.results.length;
    const fertige = teilnehmende.results.filter((zeile) => zeile.fertig_am !== null).length;
    const alleFertig = anzahl > 1 && fertige === anzahl;

    let treffer: Array<{ rezeptId: number; titel: string; quelleUrl: string | null }> = [];
    if (alleFertig) {
        // Ein Treffer ist ein Rezept, dem alle zugestimmt haben - dieselbe Regel
        // wie im Alleingang, nur mit mehr Beteiligten.
        const ergebnis = await env.DB.prepare(TREFFER_QUERY)
            .bind(code, anzahl)
            .all<{ rezept_id: number; titel: string; quelle_url: string | null }>();

        treffer = ergebnis.results.map((zeile) => ({
            rezeptId: zeile.rezept_id,
            titel: zeile.titel,
            quelleUrl: zeile.quelle_url,
        }));
    }

    return antwort({
        code,
        verfaelltAm: sitzung.verfaellt_am,
        teilnehmer: anzahl,
        fertig: fertige,
        alleFertig,
        treffer,
    });
}

/** Beendet eine Sitzung sofort. Nur die Person, die sie eroeffnet hat, darf das. */
async function schliessen(code: string, request: Request, env: Env): Promise<Response> {
    const rumpf = await request.json().catch(() => null);
    const kennung = teilnehmerId((rumpf as { teilnehmer?: unknown } | null)?.teilnehmer);
    if (kennung === null) return fehler("teilnehmer fehlt", 400);

    const sitzung = await sitzungLaden(env, code);
    if (sitzung === null) return fehler("sitzung unbekannt", 404);
    if (sitzung.gastgeber !== kennung) return fehler("nur der Gastgeber darf schliessen", 403);

    // Die Fremdschluessel raeumen Rezepte, Teilnehmer und Stimmen mit weg.
    await env.DB.prepare("DELETE FROM sitzungen WHERE code = ?1").bind(code).run();
    return antwort({ geschlossen: true });
}

const SITZUNG_PFAD = /^\/sitzung\/([A-Z0-9]{4,12})(\/[a-z]+)?$/;

export default {
    async fetch(request: Request, env: Env): Promise<Response> {
        const url = new URL(request.url);

        if (url.pathname === "/gesundheit") {
            return antwort({ bereit: true });
        }

        if (url.pathname === "/sitzung" && request.method === "POST") {
            return sitzungAnlegen(request, env);
        }

        const treffer = SITZUNG_PFAD.exec(url.pathname);
        if (treffer !== null) {
            const code = treffer[1];
            const aktion = treffer[2] ?? "";

            if (aktion === "" && request.method === "GET") return stand(code, env);
            if (aktion === "/beitreten" && request.method === "POST") {
                return beitreten(code, request, env);
            }
            if (aktion === "/stimmen" && request.method === "POST") {
                return stimmenSenden(code, request, env);
            }
            if (aktion === "/schliessen" && request.method === "POST") {
                return schliessen(code, request, env);
            }
        }

        return fehler("nicht gefunden", 404);
    },

    /**
     * Taeglicher Auftrag: alles Abgelaufene loeschen.
     *
     * Damit haelt der Dienst nie mehr als einen Tag an Daten - auch dann nicht,
     * wenn niemand seine Sitzung ordentlich beendet hat.
     */
    async scheduled(_ereignis: ScheduledEvent, env: Env): Promise<void> {
        await env.DB.prepare("DELETE FROM sitzungen WHERE verfaellt_am < ?1").bind(jetzt()).run();
    },
};
