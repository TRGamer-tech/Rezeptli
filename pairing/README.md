# Rezeptli-Pairing

Der einzige Server, den Rezeptli kennt. Er tut genau eine Sache: zwei Telefonen
erlauben, dieselbe Swipe-Runde zu teilen.

## Was er speichert

- einen kurzen Code (6 Zeichen, ohne I, O, 0 und 1 - die verwechselt man beim Abtippen)
- die Rezepte der Runde: Titel, Quelladresse und Bildlink, sofern vorhanden
- zufaellige Kennungen der Beteiligten, die das jeweilige Geraet erzeugt
- wer welches Rezept mit Ja oder Nein bewertet hat

## Was er nicht speichert

Keine Konten, keine Namen, keine Zutaten, keine Zubereitung, keine
Einkaufslisten, keine Adressen, keine Geraetekennungen. Er kennt keine E-Mail
und kann niemanden wiedererkennen: Die Kennung einer Person gilt nur innerhalb
einer Sitzung.

Eine Sitzung verfaellt nach zwoelf Stunden. Ein taeglicher Auftrag loescht, was
abgelaufen ist, und der Gastgeber kann sie jederzeit sofort schliessen. Dann
verschwinden Rezepte, Beteiligte und Stimmen mit - dafuer sorgen die
Fremdschluessel, und genau das prueft ein Test.

## Warum Rezepttitel ueberhaupt den Server sehen

Rezept-Kennungen sind auf jedem Geraet andere. Damit zwei Personen ueber
dieselben Rezepte abstimmen koennen, muss die Runde selbst geteilt werden - der
Titel ist das Mindeste, damit die zweite Person weiss, worueber sie entscheidet.

Das ist eine bewusste Abweichung vom sonstigen Grundsatz, dass nichts das Geraet
verlaesst. Sie gilt nur fuer Rezepte einer laufenden Mehrspieler-Runde, nur
solange diese laeuft, und die App sagt es vor dem Start deutlich.

## Schnittstelle

| Methode | Pfad | Zweck |
|---|---|---|
| `POST` | `/sitzung` | Runde eroeffnen, liefert den Code |
| `POST` | `/sitzung/{code}/beitreten` | Beitreten, liefert die Rezepte |
| `POST` | `/sitzung/{code}/stimmen` | Stimmen senden, wiederholbar |
| `GET`  | `/sitzung/{code}` | Stand und - wenn alle fertig sind - die Treffer |
| `POST` | `/sitzung/{code}/schliessen` | Sofort beenden, nur der Gastgeber |
| `GET`  | `/gesundheit` | Lebt der Dienst? |

Die Treffer gibt der Dienst erst heraus, wenn alle entschieden haben. Sonst
liesse sich am Zwischenstand ablesen, was die andere Person gewischt hat - und
das ist beim gemeinsamen Aussuchen der halbe Reiz.

## Entwickeln

```bash
cd pairing
npm install
npm run migrate:lokal   # Schema in die lokale Datenbank
npm run dev             # Dienst lokal starten
python3 tests/treffer_test.py
```

Der Test laeuft gegen SQLite mit dem echten Schema und liest die Treffer-Abfrage
woertlich aus `src/queries.ts`. Damit kann die geprueefte Abfrage nicht von der
ausgefuehrten abweichen.
