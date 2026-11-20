# ADR 0015: Platz für die Tastatur – an einer Stelle

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Gemeldet wurde: Beim Tippen von Vorname, Abneigung oder Runden-Code liegt die Tastatur über
dem Feld; man sieht nicht, was man schreibt.

Die App zeichnet bis unter die Systemleisten (`enableEdgeToEdge`, ab Android 15 ohnehin
Pflicht). In diesem Modus verkleinert `adjustResize` das Fenster nicht mehr – der Eintrag
im Manifest steht noch da, bewirkt aber nichts. Den Platz für die Tastatur muss der Inhalt
selbst frei machen, und das tat kein Bildschirm.

Nachgestellt ist der Fehler im Fenster der App selbst, auf Emulatoren mit Android 14 und
Android 15: Ohne eigens gemachten Platz ist das Eingabefeld am unteren Rand bei offener
Tastatur nicht zu sehen (`ohneDenPlatzIstDasFeldBeiOffenerTastaturNichtZuSehen`).

### Gemessen wird im Fenster der App

Die ersten Messungen liefen in einem nachgebauten Fenster – einer leeren Test-Activity – und
führten in die Irre:

- Auf Android 14 schob das System dieses Fenster als Ganzes nach oben, bis das Feld mit dem
  Cursor zu sehen war (Fenster 640 px hoch, Tastatur 235 px, das Feld endete bei 405 px). Es
  sah aus, als gäbe es den Fehler dort nicht. Im Fenster der App gibt es ihn.
- Das nachgebaute Fenster hatte eine Titelleiste, die die App nicht hat; oben meldete es
  80 px Abstand statt der 24 px der Statusleiste.

Darum laufen die Gerätetests in `MainActivity`: ihr Thema, ihr Eintrag im Manifest, ihr
Zeichnen bis unter die Systemleisten. Nur der Inhalt wird für den Test ersetzt.

## Entscheidung

Der Inhaltsbereich der App weicht der Tastatur aus – einmal, an der Stelle, an der er auch
der unteren Leiste ausweicht (`KeyboardAwareScaffold` um den `NavHost`), statt Feld für Feld
oder Bildschirm für Bildschirm.

- **Der Inhalt endet über der Tastatur.** Rollbare Seiten werden dadurch kürzer und holen das
  Feld mit dem Cursor von selbst ins Bild; ein Feld am unteren Rand (Einkaufsliste) sitzt
  direkt über der Tastatur.
- **Die untere Leiste verschwindet, solange getippt wird.** Hinter der Tastatur nützt sie
  nichts. Und sie kann ihr im Weg stehen: Im nachgebauten Fenster mit Android 15 wuchs ihr
  unterer Abstand mit der Tastatur mit, sie stieg über die Tastatur und nahm dem Inhalt
  80 px – genau den Platz, um den es geht. Schliesst die Tastatur, kommt die Leiste zurück.
- **Was das Gerüst freihält, gilt für alles darin als erledigt – auf jeder Seite**
  (`Modifier.aboveBarsAndKeyboard`).
  - Unten: Die Tastatur überdeckt die Leiste, also zählt nur, was sie darüber hinaus braucht.
    Sonst stünde der Inhalt um die Höhe der Leiste zu hoch über der Tastatur.
  - Oben: Sonst zieht jede Kopfzeile die Statusleiste ein zweites Mal ab. So war es, seit die
    App bis unter die Systemleisten zeichnet – im Fenster der App gemessen, auf beiden
    Android-Versionen: Die Kopfzeile begann 24 px unter dem oberen Rand und war selbst noch
    einmal 24 px höher. Auf jedem Bildschirm blieb ein leerer Streifen in Höhe der
    Statusleiste.
- Neue Bildschirme müssen nichts tun. Ein Eingabefeld in einem Dialog oder Blatt läge
  ausserhalb dieses Bereichs und bräuchte eigenen Platz – derzeit gibt es keines.

## Folgen

- `KeyboardSpaceTest` prüft das mit der echten Tastatur, auf Android 14 und Android 15
  (beide laufen in der CI):
  - Runden-Code, Vorname und eigene Abneigung auf einer langen Seite, das Feld am unteren
    Rand: Die wirkliche Unterkante des Felds liegt über der Oberkante der Tastatur, und es
    lässt sich hineinschreiben.
  - Der Platz wird nur einmal gemacht: Zwischen dem unteren Feld und der Tastatur liegt nur
    der Rand der Zeile, und der Titel des Bildschirms bleibt, wo er war.
  - Die Kopfzeile steht direkt unter der Statusleiste.
  - Die Gegenprobe: Ohne den Platz ist das Feld nicht zu sehen. Schlägt sie eines Tages
    fehl, macht Android den Platz wieder selbst.
- Sichtbar anders als vorher:
  - Alle Kopfzeilen stehen um die Höhe der Statusleiste weiter oben; der leere Streifen
    darüber ist weg.
  - Beim Tippen verschwindet die untere Leiste und kommt danach zurück.
  - Wo über der Leiste ein Streifen in Höhe der Navigationsleiste frei blieb, reicht der
    Inhalt jetzt bis an die Leiste.
