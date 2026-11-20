/**
 * Die Abfragen, auf die es ankommt.
 *
 * Sie stehen hier einzeln, damit der Test sie woertlich aus dieser Datei liest
 * und gegen eine echte SQLite-Datenbank laufen laesst. So kann die gepruefte
 * Abfrage nicht von der ausgefuehrten abweichen.
 */

/**
 * Die gemeinsamen Treffer einer Sitzung.
 *
 * Ein Treffer ist ein Rezept, dem *alle* Beteiligten zugestimmt haben. Ein
 * einzelnes Nein genuegt, damit es keiner ist - deshalb zaehlt die Abfrage die
 * Zustimmungen und vergleicht sie mit der Anzahl Teilnehmender, statt einfach
 * alle Ja-Stimmen zu sammeln.
 */
export const TREFFER_QUERY =
    "SELECT r.rezept_id, r.titel, r.quelle_url FROM rezepte r " +
    "JOIN stimmen s ON s.code = r.code AND s.rezept_id = r.rezept_id " +
    "WHERE r.code = ?1 AND s.mag = 1 " +
    "GROUP BY r.rezept_id HAVING COUNT(DISTINCT s.teilnehmer_id) = ?2 " +
    "ORDER BY r.position";

// ---------------------------------------------------------------------------------------
// Fassung 2: Lobby, gemeinsames Ziel, Stimmen waehrend des Wischens.
//
// Der Dienst entscheidet hier mehr als frueher - wann der Stapel steht, wann das Ziel
// erreicht ist, wann er durch ist. Jede dieser Entscheidungen ist EINE Anweisung mit ihrer
// Bedingung im WHERE. Sie laufen im selben Stapel wie die Aenderung, die sie ausloest
// (D1 fuehrt einen Stapel am Stueck aus). So koennen zwei Telefone, die im selben
// Augenblick senden, den Zustand nicht in verschiedene Richtungen schieben.
// ---------------------------------------------------------------------------------------

/** Die Kennungen aller Treffer einer Sitzung - Baustein fuer Zaehlen und Auflisten. */
const TREFFER_KENNUNGEN =
    "SELECT s.rezept_id FROM stimmen s WHERE s.code = ?1 AND s.mag = 1 " +
    "GROUP BY s.rezept_id HAVING COUNT(DISTINCT s.teilnehmer_id) >= " +
    "(SELECT COUNT(*) FROM teilnehmer t WHERE t.code = ?1)";

/** Haben alle ihren Beitrag zur laufenden Runde abgegeben? (Bezieht sich auf die Zeile in `sitzungen`.) */
const ALLE_BEIGETRAGEN =
    "NOT EXISTS (SELECT 1 FROM teilnehmer t WHERE t.code = sitzungen.code AND t.beitrag_runde < sitzungen.runde)";

/** Liegt fuer die laufende Runde mindestens eine Karte vor? */
const RUNDE_HAT_KARTEN =
    "EXISTS (SELECT 1 FROM rezepte r WHERE r.code = sitzungen.code AND r.runde = sitzungen.runde)";

/**
 * Nimmt eine Karte in den Stapel der laufenden Runde auf.
 *
 * Die Kennung vergibt der Dienst - eine fortlaufende Zahl je Sitzung. Die Geraete koennen
 * das nicht: Ihre eigenen Rezeptnummern beginnen beide bei 1 und wuerden sich ueberschreiben.
 * Wiedererkannt wird ein Rezept am Schluessel (er kommt von der Adresse), und was schon in
 * der Sitzung liegt, kommt kein zweites Mal hinein.
 *
 * Gilt nur, solange gesammelt wird und diese Person fuer die Runde noch nichts abgegeben hat.
 *
 * ?1 code, ?2 position, ?3 titel, ?4 quelle_url, ?5 bild_url, ?6 zubereitungszeit,
 * ?7 runde, ?8 teilnehmer, ?9 schluessel
 */
export const KARTE_EINFUEGEN =
    "INSERT OR IGNORE INTO rezepte " +
    "(code, rezept_id, position, titel, quelle_url, bild_url, zubereitungszeit, runde, von, schluessel) " +
    "SELECT ?1, (SELECT COALESCE(MAX(rezept_id), 0) + 1 FROM rezepte WHERE code = ?1), " +
    "?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9 " +
    "WHERE EXISTS (SELECT 1 FROM sitzungen z JOIN teilnehmer t ON t.code = z.code " +
    "WHERE z.code = ?1 AND z.zustand = 'sammeln' AND z.runde = ?7 " +
    "AND t.teilnehmer_id = ?8 AND t.beitrag_runde < z.runde)";

/** Haelt fest, dass jemand seinen Beitrag zur Runde abgegeben hat. ?1 code, ?2 teilnehmer, ?3 runde */
export const BEITRAG_VERMERKEN =
    "UPDATE teilnehmer SET beitrag_runde = ?3 WHERE code = ?1 AND teilnehmer_id = ?2 AND beitrag_runde < ?3 " +
    "AND EXISTS (SELECT 1 FROM sitzungen z WHERE z.code = ?1 AND z.zustand = 'sammeln' AND z.runde = ?3)";

/**
 * Der erste Beitrag einer Sammelphase startet die Frist fuer alle, die noch fehlen.
 * ?1 code, ?2 zeitpunkt
 */
export const FRIST_STARTEN =
    "UPDATE sitzungen SET beitrag_seit = ?2 WHERE code = ?1 AND zustand = 'sammeln' AND beitrag_seit IS NULL " +
    "AND EXISTS (SELECT 1 FROM teilnehmer t WHERE t.code = sitzungen.code AND t.beitrag_runde >= sitzungen.runde)";

/**
 * Die Frist ist um: Wer bis jetzt nichts beigesteuert hat, steuert zu dieser Runde nichts
 * mehr bei. Danach greifen dieselben Entscheidungen wie sonst - der Stapel steht aus dem,
 * was vorliegt.
 *
 * ?1 code, ?2 stichzeit (jetzt minus Frist)
 */
export const FRIST_ABGELAUFEN =
    "UPDATE teilnehmer SET beitrag_runde = (SELECT z.runde FROM sitzungen z WHERE z.code = ?1) " +
    "WHERE code = ?1 AND EXISTS (SELECT 1 FROM sitzungen z WHERE z.code = ?1 AND z.zustand = 'sammeln' " +
    "AND z.beitrag_seit IS NOT NULL AND z.beitrag_seit <= ?2 AND teilnehmer.beitrag_runde < z.runde)";

/** Alle haben beigetragen, und es gibt Karten: Es wird gewischt. ?1 code */
export const STAPEL_STEHT =
    "UPDATE sitzungen SET zustand = 'laeuft', beitrag_seit = NULL, stand_nr = stand_nr + 1 " +
    "WHERE code = ?1 AND zustand = 'sammeln' AND " +
    ALLE_BEIGETRAGEN +
    " AND " +
    RUNDE_HAT_KARTEN;

/**
 * Alle haben beigetragen, aber niemand hat ein passendes gespeichertes Rezept: Dann geht
 * es ohne Rueckfrage mit neuen Rezepten weiter - es gibt ja nichts, wozwischen zu waehlen waere.
 */
export const OHNE_GESPEICHERTE_WEITER =
    "UPDATE sitzungen SET runde = runde + 1, quelle = 'neu', beitrag_seit = NULL, stand_nr = stand_nr + 1 " +
    "WHERE code = ?1 AND zustand = 'sammeln' AND quelle = 'gespeichert' AND " +
    ALLE_BEIGETRAGEN +
    " AND NOT " +
    RUNDE_HAT_KARTEN;

/** Auch neue Rezepte hat niemand mehr gefunden: Die Runde endet mit dem, was da ist. */
export const NICHTS_MEHR_DA =
    "UPDATE sitzungen SET zustand = 'beendet', ende_grund = 'leer', stand_nr = stand_nr + 1 " +
    "WHERE code = ?1 AND zustand = 'sammeln' AND quelle = 'neu' AND " +
    ALLE_BEIGETRAGEN +
    " AND NOT " +
    RUNDE_HAT_KARTEN;

/**
 * Speichert eine Stimme - aber nur fuer eine Karte der laufenden Runde und nur, solange
 * gewischt wird. Was nach dem Ende noch eintrifft, aendert nichts mehr.
 *
 * ?1 code, ?2 teilnehmer, ?3 rezept_id, ?4 mag, ?5 zeitpunkt
 */
export const STIMME_SPEICHERN =
    "INSERT INTO stimmen (code, teilnehmer_id, rezept_id, mag, entschieden_am) " +
    "SELECT ?1, ?2, ?3, ?4, ?5 WHERE EXISTS (SELECT 1 FROM rezepte r JOIN sitzungen z ON z.code = r.code " +
    "WHERE r.code = ?1 AND r.rezept_id = ?3 AND r.runde = z.runde AND z.zustand = 'laeuft') " +
    "ON CONFLICT (code, teilnehmer_id, rezept_id) DO UPDATE SET " +
    "mag = excluded.mag, entschieden_am = excluded.entschieden_am";

/** Nimmt eine Stimme zurueck - die Karte gilt wieder als unentschieden. ?1 code, ?2 teilnehmer, ?3 rezept_id */
export const STIMME_ZURUECKNEHMEN =
    "DELETE FROM stimmen WHERE code = ?1 AND teilnehmer_id = ?2 AND rezept_id = ?3 " +
    "AND EXISTS (SELECT 1 FROM rezepte r JOIN sitzungen z ON z.code = r.code " +
    "WHERE r.code = ?1 AND r.rezept_id = ?3 AND r.runde = z.runde AND z.zustand = 'laeuft')";

/** Genug gemeinsame Treffer: Die Runde ist vorbei, niemand muss weiterwischen. ?1 code */
export const ZIEL_ERREICHT =
    "UPDATE sitzungen SET zustand = 'beendet', ende_grund = 'ziel', stand_nr = stand_nr + 1 " +
    "WHERE code = ?1 AND zustand = 'laeuft' AND (SELECT COUNT(*) FROM (" +
    TREFFER_KENNUNGEN +
    ")) >= ziel";

/**
 * Alle haben alle Karten der Runde entschieden, das Ziel ist aber nicht erreicht: Jetzt
 * entscheidet die Person, die eroeffnet hat, ob es weitergeht. ?1 code
 */
export const STAPEL_DURCH =
    "UPDATE sitzungen SET zustand = 'frage', stand_nr = stand_nr + 1 " +
    "WHERE code = ?1 AND zustand = 'laeuft' AND " +
    "(SELECT COUNT(*) FROM stimmen s JOIN rezepte r ON r.code = s.code AND r.rezept_id = s.rezept_id " +
    "WHERE s.code = ?1 AND r.runde = sitzungen.runde) >= " +
    "(SELECT COUNT(*) FROM rezepte r WHERE r.code = ?1 AND r.runde = sitzungen.runde) * " +
    "(SELECT COUNT(*) FROM teilnehmer t WHERE t.code = ?1)";

/**
 * Die Treffer mit allem, was die App zum Anzeigen braucht - in der Reihenfolge, in der sie
 * im Stapel lagen. `hat_inhalt` sagt, ob zu einem selbst erfassten Rezept der Inhalt
 * hinterlegt ist. ?1 code
 */
export const TREFFER_LISTE =
    "SELECT r.rezept_id, r.titel, r.quelle_url, r.bild_url, r.zubereitungszeit, r.schluessel, r.von, " +
    "EXISTS (SELECT 1 FROM inhalte i WHERE i.code = r.code AND i.rezept_id = r.rezept_id) AS hat_inhalt " +
    "FROM rezepte r WHERE r.code = ?1 AND r.rezept_id IN (" +
    TREFFER_KENNUNGEN +
    ") ORDER BY r.runde, r.position, r.rezept_id";

/**
 * Die Zahlen zum Stand: Karten der Runde, eigene und fremde Entscheidungen, Treffer.
 * ?1 code, ?2 runde, ?3 teilnehmer
 */
export const STAND_ZAHLEN =
    "SELECT " +
    "(SELECT COUNT(*) FROM rezepte r WHERE r.code = ?1 AND r.runde = ?2) AS karten, " +
    "(SELECT COUNT(*) FROM stimmen s JOIN rezepte r ON r.code = s.code AND r.rezept_id = s.rezept_id " +
    "WHERE s.code = ?1 AND s.teilnehmer_id = ?3 AND r.runde = ?2) AS entschieden, " +
    "(SELECT COUNT(*) FROM stimmen s JOIN rezepte r ON r.code = s.code AND r.rezept_id = s.rezept_id " +
    "WHERE s.code = ?1 AND s.teilnehmer_id != ?3 AND r.runde = ?2) AS andere, " +
    "(SELECT COUNT(*) FROM (" +
    TREFFER_KENNUNGEN +
    ")) AS treffer";

/**
 * Hinterlegt den Inhalt eines selbst erfassten Rezepts - aber nur, wenn es ein gemeinsamer
 * Treffer ist, keine Adresse hat und von genau diesem Geraet stammt. Alles andere bleibt,
 * wo es ist: auf dem Geraet.
 *
 * ?1 code, ?2 rezept_id, ?3 inhalt (JSON), ?4 teilnehmer
 */
export const INHALT_SPEICHERN =
    "INSERT INTO inhalte (code, rezept_id, inhalt) SELECT ?1, ?2, ?3 " +
    "WHERE EXISTS (SELECT 1 FROM rezepte r JOIN sitzungen z ON z.code = r.code " +
    "WHERE r.code = ?1 AND r.rezept_id = ?2 AND r.von = ?4 AND r.quelle_url IS NULL " +
    "AND z.zustand IN ('frage', 'beendet')) AND ?2 IN (" +
    TREFFER_KENNUNGEN +
    ") ON CONFLICT (code, rezept_id) DO UPDATE SET inhalt = excluded.inhalt";
