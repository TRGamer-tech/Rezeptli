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
