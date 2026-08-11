#!/usr/bin/env python3
"""Prueft die Treffer-Abfrage des Pairing-Dienstes gegen eine echte Datenbank.

Die Abfrage wird woertlich aus `src/queries.ts` gelesen. Damit kann die
geprueefte nicht von der ausgefuehrten abweichen - genau der Fehler, an dem
solche Tests sonst wertlos werden.

D1 spricht SQLite, deshalb genuegt hier das sqlite3-Modul.
"""

import re
import sqlite3
import sys
from pathlib import Path

WURZEL = Path(__file__).resolve().parent.parent


def treffer_query() -> str:
    """Liest die Abfrage aus dem TypeScript und setzt die Zeichenketten zusammen."""
    quelltext = (WURZEL / "src" / "queries.ts").read_text(encoding="utf-8")
    zuweisung = re.search(
        r"export const TREFFER_QUERY\s*=\s*(.*?);", quelltext, re.S,
    )
    if zuweisung is None:
        raise AssertionError("TREFFER_QUERY nicht gefunden - wurde sie umbenannt?")

    teile = re.findall(r'"((?:[^"\\]|\\.)*)"', zuweisung.group(1))
    if not teile:
        raise AssertionError("TREFFER_QUERY enthaelt keine Zeichenketten")
    # D1 nutzt ?1, ?2 - sqlite3 in Python nummerierte Platzhalter ebenfalls.
    return "".join(teile)


def datenbank() -> sqlite3.Connection:
    # isolation_level=None: ohne offene Transaktion wirkt das Pragma sofort.
    # D1 hat Fremdschluessel von Haus aus an, sqlite3 in Python nicht - ohne das
    # hier wuerde der Test das Aufraeumen beim Schliessen gar nicht pruefen.
    con = sqlite3.connect(":memory:", isolation_level=None)
    con.execute("PRAGMA foreign_keys = ON")
    con.executescript((WURZEL / "migrations" / "0001_sitzungen.sql").read_text(encoding="utf-8"))
    aktiv = con.execute("PRAGMA foreign_keys").fetchone()[0]
    assert aktiv == 1, "Fremdschluessel muessen fuer diesen Test aktiv sein"
    return con


def sitzung_anlegen(con: sqlite3.Connection, code: str, rezepte: list[tuple[int, str]]) -> None:
    con.execute(
        "INSERT INTO sitzungen (code, erstellt_am, verfaellt_am, gastgeber) VALUES (?, 0, 9999999999999, 'a')",
        (code,),
    )
    for position, (rezept_id, titel) in enumerate(rezepte):
        con.execute(
            "INSERT INTO rezepte (code, rezept_id, position, titel) VALUES (?, ?, ?, ?)",
            (code, rezept_id, position, titel),
        )


def stimmen(con: sqlite3.Connection, code: str, person: str, entscheidungen: dict[int, bool]) -> None:
    con.execute(
        "INSERT OR IGNORE INTO teilnehmer (code, teilnehmer_id, beigetreten_am) VALUES (?, ?, 0)",
        (code, person),
    )
    for rezept_id, mag in entscheidungen.items():
        con.execute(
            "INSERT INTO stimmen (code, teilnehmer_id, rezept_id, mag, entschieden_am) "
            "VALUES (?, ?, ?, ?, 0)",
            (code, person, rezept_id, 1 if mag else 0),
        )


def treffer(con: sqlite3.Connection, code: str, anzahl: int) -> list[str]:
    return [zeile[1] for zeile in con.execute(treffer_query(), (code, anzahl))]


def pruefe(bedingung: bool, beschreibung: str) -> None:
    if not bedingung:
        print(f"FEHLGESCHLAGEN: {beschreibung}")
        sys.exit(1)
    print(f"bestanden: {beschreibung}")


def main() -> None:
    con = datenbank()
    sitzung_anlegen(con, "ABC123", [(1, "Rösti"), (2, "Risotto"), (3, "Älplermagronen")])

    # Beide sagen Ja zu Risotto, sonst gehen die Meinungen auseinander.
    stimmen(con, "ABC123", "anna", {1: True, 2: True, 3: False})
    stimmen(con, "ABC123", "beat", {1: False, 2: True, 3: True})

    pruefe(
        treffer(con, "ABC123", 2) == ["Risotto"],
        "nur was beide mögen, ist ein Treffer",
    )
    # Der haeufigste Zwischenzustand: eine Person ist fertig, die andere nicht.
    con_wartet = datenbank()
    sitzung_anlegen(con_wartet, "WAIT01", [(1, "Rösti"), (2, "Risotto")])
    stimmen(con_wartet, "WAIT01", "anna", {1: True, 2: True})
    pruefe(
        treffer(con_wartet, "WAIT01", 2) == [],
        "solange nur eine Person gewischt hat, gibt es keine Treffer",
    )
    stimmen(con_wartet, "WAIT01", "beat", {1: True, 2: False})
    pruefe(
        treffer(con_wartet, "WAIT01", 2) == ["Rösti"],
        "sobald die zweite Person fertig ist, bleibt das gemeinsame Rezept übrig",
    )

    # Eine dritte Person, die noch nichts entschieden hat, verhindert Treffer.
    con.execute(
        "INSERT INTO teilnehmer (code, teilnehmer_id, beigetreten_am) VALUES ('ABC123', 'cem', 0)",
    )
    pruefe(
        treffer(con, "ABC123", 3) == [],
        "solange jemand nicht entschieden hat, gibt es keinen Treffer",
    )

    stimmen(con, "ABC123", "cem", {1: True, 2: True, 3: True})
    pruefe(
        treffer(con, "ABC123", 3) == ["Risotto"],
        "mit der dritten Zustimmung bleibt genau das gemeinsame Rezept übrig",
    )

    # Reihenfolge: Treffer erscheinen in der Reihenfolge des Rezeptstapels.
    con2 = datenbank()
    sitzung_anlegen(con2, "XYZ999", [(10, "Zuerst"), (20, "Danach"), (30, "Zuletzt")])
    stimmen(con2, "XYZ999", "anna", {30: True, 10: True, 20: False})
    stimmen(con2, "XYZ999", "beat", {10: True, 30: True, 20: True})
    pruefe(
        treffer(con2, "XYZ999", 2) == ["Zuerst", "Zuletzt"],
        "die Reihenfolge folgt dem Stapel, nicht dem Zeitpunkt der Stimme",
    )

    # Eine doppelt gesendete Stimme darf nichts verdoppeln.
    con3 = datenbank()
    sitzung_anlegen(con3, "DUP001", [(1, "Rösti")])
    stimmen(con3, "DUP001", "anna", {1: True})
    con3.execute(
        "INSERT INTO stimmen (code, teilnehmer_id, rezept_id, mag, entschieden_am) "
        "VALUES ('DUP001', 'anna', 1, 1, 5) "
        "ON CONFLICT (code, teilnehmer_id, rezept_id) DO UPDATE SET mag = excluded.mag",
    )
    stimmen(con3, "DUP001", "beat", {1: True})
    pruefe(
        treffer(con3, "DUP001", 2) == ["Rösti"],
        "eine doppelt gesendete Stimme zählt trotzdem nur einmal",
    )

    # Ein spaeteres Nein ersetzt ein frueheres Ja (Rueckgaengig-Funktion).
    con3.execute(
        "INSERT INTO stimmen (code, teilnehmer_id, rezept_id, mag, entschieden_am) "
        "VALUES ('DUP001', 'beat', 1, 0, 9) "
        "ON CONFLICT (code, teilnehmer_id, rezept_id) DO UPDATE SET mag = excluded.mag",
    )
    pruefe(
        treffer(con3, "DUP001", 2) == [],
        "ein zurückgenommenes Ja entfernt den Treffer wieder",
    )

    # Loeschen der Sitzung raeumt alles mit weg.
    con3.execute("DELETE FROM sitzungen WHERE code = 'DUP001'")
    uebrig = con3.execute("SELECT COUNT(*) FROM stimmen WHERE code = 'DUP001'").fetchone()[0]
    pruefe(uebrig == 0, "das Schliessen einer Sitzung lässt keine Stimmen zurück")

    print("\nAlle Prüfungen bestanden.")


if __name__ == "__main__":
    main()
