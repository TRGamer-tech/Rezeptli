# ADR 0004: Regelbasierter Zutaten-Parser statt Machine Learning

**Status:** Angenommen · **Datum:** 2026-08-10

## Kontext

Beim Import eines kopierten Rezepttexts müssen Zeilen wie `200 g Mehl`, `1 ½ dl Rahm` oder
`Salz nach Belieben` in strukturierte Zutaten umgewandelt werden. Die App ist offline, also
scheidet ein Netzwerkdienst aus.

## Entscheidung

Ein **regelbasierter Parser** in der Domain-Schicht, ohne Modell und ohne Netzwerk.

Der Parser gibt zu jeder Zeile eine `ParseConfidence` (`HIGH`, `MEDIUM`, `LOW`) zurück.
Die Import-UI zeigt jede erkannte Zeile editierbar an und hebt unsichere hervor. Gespeichert
wird erst auf ausdrückliche Bestätigung – der Parser übernimmt nie stillschweigend Daten.

Was der Parser nicht sicher als Zutat erkennt, landet in der Zubereitung statt im Nichts.
Lieber eine Zeile an der falschen Stelle, die man sieht und verschieben kann, als eine
verlorene Zeile.

## Konsequenzen

- Der Parser ist deterministisch und damit vollständig durch Unit-Tests abgedeckt.
- Schweizer Schreibweisen (`dl`, `KL`, `Rüebli`) sind explizit hinterlegt statt gelernt –
  jede Ergänzung ist ein Eintrag in einer Liste plus ein Testfall.
- Ungewöhnliche Formate erkennt er nicht. Das ist akzeptabel, weil die Korrektur direkt in
  der Import-Vorschau möglich ist.
- Er wächst mit gemeldeten Beispielen: Ein Bug-Report zum Import besteht idealerweise aus
  dem Originaltext und wird zu einem neuen Testfall.
