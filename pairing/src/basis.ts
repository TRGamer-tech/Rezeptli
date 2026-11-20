/**
 * Was beide Fassungen der Schnittstelle gemeinsam brauchen: Antworten bauen, Eingaben
 * saeubern, Codes erzeugen.
 */

export interface Env {
    DB: D1Database;
}

/** So lange lebt eine Sitzung. Danach raeumt der taegliche Auftrag sie weg. */
export const SITZUNG_STUNDEN = 12;

/** Ohne I, O, 0 und 1 - die verwechselt man beim Abtippen. */
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
const CODE_LAENGE = 6;

export const MAX_TITEL_LAENGE = 200;
export const MAX_ADRESSE_LAENGE = 500;

export function jetzt(): number {
    return Date.now();
}

export function codeErzeugen(): string {
    const zufall = new Uint8Array(CODE_LAENGE);
    crypto.getRandomValues(zufall);
    return Array.from(zufall)
        .map((wert) => CODE_ALPHABET[wert % CODE_ALPHABET.length])
        .join("");
}

export function antwort(daten: unknown, status = 200): Response {
    return new Response(JSON.stringify(daten), {
        status,
        headers: { "content-type": "application/json; charset=utf-8" },
    });
}

export function fehler(grund: string, status: number): Response {
    return antwort({ fehler: grund }, status);
}

/**
 * Die Antwort, wenn App und Runde nicht dieselbe Fassung sprechen.
 *
 * Eine aeltere App kennt die neue Runde nicht (Lobby, gemeinsames Ziel, laufende Treffer)
 * und umgekehrt. Statt halb zu funktionieren, sagt der Dienst klar: bitte aktualisieren.
 */
export function veraltet(): Response {
    return fehler("app aktualisieren", 426);
}

/** Kuerzt und saeubert, was von aussen kommt - nichts wird ungeprueft gespeichert. */
export function text(wert: unknown, maxLaenge: number): string | null {
    if (typeof wert !== "string") return null;
    const sauber = wert.trim().slice(0, maxLaenge);
    return sauber.length > 0 ? sauber : null;
}

export function teilnehmerId(wert: unknown): string | null {
    const kennung = text(wert, 64);
    if (kennung === null) return null;
    // Nur harmlose Zeichen - die Kennung erzeugt das Geraet selbst.
    return /^[A-Za-z0-9_-]+$/.test(kennung) ? kennung : null;
}

export function ganzzahl(wert: unknown): number | null {
    return typeof wert === "number" && Number.isFinite(wert) ? Math.trunc(wert) : null;
}

export async function rumpfLesen(request: Request): Promise<Record<string, unknown> | null> {
    const rumpf = await request.json().catch(() => null);
    return rumpf !== null && typeof rumpf === "object" && !Array.isArray(rumpf)
        ? (rumpf as Record<string, unknown>)
        : null;
}
