# ADR 0001: MIT-Lizenz, keine Monetarisierung

**Status:** Angenommen · **Datum:** 2026-08-10

## Kontext

Rezeptli ist ein Community-Projekt ohne kommerzielle Absicht. Es braucht eine Lizenz und
eine klare Haltung zur Finanzierung, bevor externe Beiträge dazukommen.

## Entscheidung

Die App steht unter der **MIT-Lizenz**. Es gibt keine Werbung, kein Tracking, keine
In-App-Käufe und keine Konten.

MIT ist permissiv und maximal einfach zu verstehen – das senkt die Hürde für Beiträge und
für Forks. Eine Copyleft-Lizenz wie die GPLv3 wäre die Alternative, wenn verhindert werden
soll, dass jemand eine geschlossene Variante veröffentlicht. Für ein Projekt dieser Grösse
wiegt die niedrigere Einstiegshürde schwerer als dieser Schutz.

## Konsequenzen

- Jede Abhängigkeit muss lizenzkompatibel und kostenlos sein.
- Keine Firebase-, Ads- oder Payment-SDKs, auch nicht "nur für Crash-Reports".
- Netzzugriffe nur dort, wo die Nutzerin sie ausdrücklich auslöst (siehe ADR 0005).
- Wer eine geschlossene Variante bauen will, darf das. Das ist bewusst in Kauf genommen.
- Wenn später doch Copyleft gewünscht ist, ist der Wechsel nur mit Zustimmung aller
  Beitragenden möglich – der Entscheid ist also faktisch dauerhaft.
