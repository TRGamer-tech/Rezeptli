# ADR 0015: Platz für die Tastatur – an einer Stelle

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Gemeldet wurde: Beim Tippen von Vorname, Abneigung oder Runden-Code liegt die Tastatur über
dem Feld; man sieht nicht, was man schreibt.

Die App zeichnet bis unter die Systemleisten (`enableEdgeToEdge`, ab Android 15 ohnehin
Pflicht). In diesem Modus verkleinert `adjustResize` das Fenster nicht mehr – der Eintrag
im Manifest steht noch da, bewirkt aber nichts. Den Platz für die Tastatur muss der Inhalt
selbst frei machen, und das tat kein Bildschirm.

Was man davon sieht, hängt von der Android-Version ab. Auf dem Emulator mit Android 14
schob das System ersatzweise das ganze Fenster nach oben, bis das Feld mit dem Cursor zu
sehen war (gemessen: Fenster 640 px hoch, Tastatur 235 px, das Feld endete bei 405 px) –
der obere Teil des Bildschirms rutscht dabei aus dem Bild, und verlassen kann man sich
darauf nicht. Die App zielt auf Android 15; die Gerätetests laufen deshalb auf beiden
Versionen.

## Entscheidung

Der Inhaltsbereich der App weicht der Tastatur aus – einmal, an der Stelle, an der er auch
der unteren Leiste ausweicht (`Modifier.aboveBarsAndKeyboard` am `NavHost`), statt Feld
für Feld oder Bildschirm für Bildschirm.

- Der Inhalt endet über der Tastatur. Rollbare Seiten werden dadurch kürzer und holen das
  Feld mit dem Cursor von selbst ins Bild; ein Feld am unteren Rand (Einkaufsliste) sitzt
  direkt über der Tastatur.
- Die untere Leiste bleibt, wo sie ist, und verschwindet hinter der Tastatur. Ihr Platz
  wird nicht doppelt abgezogen: Was die Leiste schon freihält, gilt für alles darin als
  erledigt.
- Neue Bildschirme müssen nichts tun. Ein Eingabefeld in einem Dialog oder Blatt läge
  ausserhalb dieses Bereichs und bräuchte eigenen Platz – derzeit gibt es keines.

## Folgen

- Gemessen wird am echten Fenster (`KeyboardSpaceTest`): die wirkliche Unterkante des Felds
  gegen die Oberkante der Tastatur, für den Runden-Code, für Vorname und Abneigung auf
  einer langen Seite und für das Feld am unteren Rand.
- Weil der untere Rand der Leiste jetzt als erledigt gilt, ziehen die Bildschirme ihn nicht
  noch einmal ab. Wo vorher über der Leiste ein Streifen in Höhe der Navigationsleiste frei
  blieb, reicht der Inhalt jetzt bis an die Leiste.
