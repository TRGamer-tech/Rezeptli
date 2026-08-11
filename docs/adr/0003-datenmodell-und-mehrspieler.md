# ADR 0003: Datenmodell mit Blick auf den Mehrspieler-Modus

**Status:** Angenommen · **Datum:** 2026-08-10

## Kontext

Der Mehrspieler-Modus – zwei Geräte swipen durch denselben Rezept-Pool, angezeigt werden
nur die gemeinsamen Treffer – ist nicht Teil von Version 1.0. Er soll aber später ohne
Umbau der Kernlogik ergänzt werden können, und ohne dass dafür ein kostenpflichtiger
Cloud-Dienst nötig wird.

## Entscheidung

1. **`SwipeResult` hat von Anfang an eine `participantId`** und diese ist Teil des
   Primärschlüssels (`sessionId`, `participantId`, `recipeId`). Im Solo-Modus steht dort
   immer `"local"`. Entscheidungen einer zweiten Person landen später in derselben
   Session – ohne Schema-Migration.

2. **Die Auswertung ist bereits mehrspielerfähig.** `SwipeMatcher.commonMatches()`
   definiert einen Treffer als "alle beteiligten Personen haben Ja gesagt". Bei einer
   Person ist das identisch mit deren Ja-Liste. Der spätere Mehrspieler-Modus muss nur
   noch Entscheidungen zusammenführen; die Auswertung bleibt unverändert.

3. **Kein Server im Datenmodell.** Es gibt keine Felder für Konten, Tokens oder
   Server-IDs. Der Austausch zwischen zwei Geräten kann später über QR-Code, Nearby
   Connections oder einen selbst gehosteten Minimal-Dienst laufen – alle drei Wege
   arbeiten mit genau denselben `SwipeDecision`-Datensätzen.

4. **Zutaten kennen einen kanonischen Namen.** `Ingredient.canonicalName` hält die
   hochdeutsche Normalform eines regionalen Begriffs fest ("Rüebli" → "Karotte"). Angezeigt
   wird immer die Eingabe der Nutzerin; der kanonische Name dient dem Zusammenführen. Das
   ist die Grundlage für Vorrats-Abgleich und Einkaufsliste, ohne dass diese heute schon
   existieren müssen.

## Konsequenzen

- Ein Rezept gilt erst als Treffer, wenn *alle* Beteiligten entschieden haben. Im
  Solo-Modus fällt das nicht auf, im Mehrspieler-Modus ist es das erwartete Verhalten.
- Die Tabelle `swipe_results` ist minimal breiter als für Solo nötig – ein Textfeld pro
  Zeile. Das ist der Preis dafür, später keine Migration zu brauchen.
- Tags liegen normalisiert in einer eigenen Tabelle, damit nach mehreren Tags gleichzeitig
  gefiltert werden kann (UND-Semantik) und die Filter-Chips direkt aus der Datenbank kommen.
