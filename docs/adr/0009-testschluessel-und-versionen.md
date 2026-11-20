# ADR 0009: Ein fester Testschlüssel und steigende Build-Nummern

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Wer die App testete, musste sie für jeden neuen Build deinstallieren und neu installieren –
und verlor dabei jedes Mal Rezepte, Wochenplan und Vorlieben. Gemeldet wurde es als
"mach eine Versionsänderung, damit ich nicht dauernd neu installieren muss".

Die Versionsnummer war nur die halbe Ursache. Android nimmt ein Update unter zwei
Bedingungen an:

1. **Dieselbe Unterschrift.** Die Debug-APK wurde mit dem Schlüssel unterschrieben, den das
   Android-Gradle-Plugin auf der jeweiligen Maschine anlegt. Jeder CI-Lauf startet auf einer
   frischen Maschine – also bekam jeder Build einen neuen, zufälligen Schlüssel. Für Android
   waren das lauter fremde Apps mit demselben Namen.
2. **Kein Rückschritt.** `versionCode` stand fest auf 1. Das allein hätte ein Update nicht
   verhindert, aber auch nicht erkennen lassen, welcher Build der neuere ist.

Die Aussage im Bericht zur vorigen Runde, die neue APK lasse sich über die alte
installieren, war deshalb falsch.

## Entscheidung

### Ein Testschlüssel im Repository

`app/rezeptli-test.keystore` liegt im Repository und unterschreibt jeden Debug-Build – in
der CI und auf jedem Entwicklerrechner. Damit trägt jeder Build dieselbe Unterschrift, und
ein selbst gebauter lässt sich über einen heruntergeladenen installieren und umgekehrt.

Ein Schlüssel im Repository ist sonst ein Fehler. Hier ist er richtig, weil er nichts
schützt, was geheim sein müsste:

- Er unterschreibt ausschliesslich **Debug-Builds**. Das sind Testbuilds, als solche
  beschriftet und nicht für eine Verbreitung über einen Store gedacht.
- Das Repository ist öffentlich und die App quelloffen: Wer eine veränderte Fassung bauen
  will, kann das ohnehin. Der Schlüssel erlaubt zusätzlich nur, sie *über* einen
  bestehenden Testbuild zu installieren – und das setzt voraus, dass jemand eine APK aus
  fremder Quelle von Hand installiert.
- Das Passwort steht im Klartext daneben. Es gibt nichts vor, was es nicht ist.

Eine Veröffentlichung im Play Store bekäme einen **eigenen, geheimen** Schlüssel, der nie
im Repository liegt. Sie wäre für Android eine andere App-Fassung als die Testbuilds – der
Wechsel dorthin kostet eine Neuinstallation, und die ist dann gewollt.

Die CI prüft nach dem Bauen, dass die APK wirklich mit diesem Schlüssel unterschrieben ist
(Fingerabdruck aus dem Schlüsselspeicher gegen den der APK). Ohne diese Prüfung fiele ein
Rückfall auf einen Zufallsschlüssel erst wieder auf einem Telefon auf.

### Die Build-Nummer kommt aus der CI

`versionCode` ist die Laufnummer des Workflows (`github.run_number`), lokal 1.
`versionName` wird von Hand gepflegt (jetzt 1.1.0). Die Laufnummer steigt mit jedem Lauf
von selbst – niemand muss daran denken, und ein älterer Build lässt sich nicht versehentlich
über einen neueren installieren.

Welcher Build läuft, steht im Tab *Vorlieben* ganz unten ("Rezeptli 1.1.0 · Build 212"); die
Version steht im Namen der APK, die Build-Nummer im Titel des Releases.

## Folgen

- **Ein letztes Mal deinstallieren.** Die bisher verteilten Builds tragen Schlüssel, die es
  nicht mehr gibt. Über sie lässt sich nichts installieren – auch dieser Build nicht. Wer
  einen Build von vor 1.1.0 auf dem Telefon hat, muss ihn einmal entfernen; die Daten auf
  dem Telefon gehen dabei verloren, und retten lassen sie sich nicht: Der Export der
  Sammlung kam erst mit 1.1.0, und Androids eigene Sicherung stellt Daten nur für dieselbe
  Unterschrift wieder her. Ab 1.1.0 ist das vorbei.
- Was ein Update übersteht, ist alles, was die App speichert: die Datenbank (Rezepte, Plan,
  Einkaufsliste) und die Einstellungsdateien (Vorlieben, Aussehen, Gedächtnis des Plans).
  Ändert sich das Schema der Datenbank, führt eine Migration die Daten hinüber; die CI prüft
  auf einem Emulator, dass sie das tut, und dass das exportierte Schema zum Code passt.
- Der Schlüssel darf nicht ersetzt werden. Ein neuer Testschlüssel hiesse wieder: alle
  einmal deinstallieren.
