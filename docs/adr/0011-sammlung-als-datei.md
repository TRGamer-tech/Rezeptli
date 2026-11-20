# ADR 0011: Die Sammlung als Datei – Export und Import

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Die Rezepte lagen nur in der Datenbank der App. Wer das Telefon wechselte oder die App neu
installieren musste, fing bei null an. Gewünscht war, die Rezepte exportieren und
importieren zu können.

## Entscheidung

### Eine Datei, die man auch ohne die App lesen kann

Der Export ist ein gewöhnliches ZIP:

```
rezepte.json      alle Rezepte der Sammlung, lesbares JSON mit deutschen Feldnamen
fotos/1.jpg …     die eigenen Fotos
```

Kein eigenes Binärformat, keine Datenbankkopie: Die Rezepte gehören der Person, die sie
gesammelt hat. Sie soll sie auch in zehn Jahren mit einem Texteditor öffnen können. Das
Format trägt eine Versionsnummer; eine Datei aus einer neueren App wird abgelehnt statt halb
verstanden.

Mit gehen die Rezepte aus "Meine Rezepte" samt Zutaten, Schritten, Timern, Schlagworten,
Quelle und dem Vermerk "zuletzt gekocht". **Nicht** mit gehen Wochenplan, Einkaufsliste und
Vorlieben – das ist der Stand einer Woche auf einem Telefon, keine Sammlung. Fotos, die im
Netz liegen (bei Rezepten aus dem Web), bleiben eine Adresse: Sie mitzunehmen hiesse,
fremde Bilder zu kopieren.

### Der Dateidialog von Android, keine Speicher-Berechtigung

Wohin die Datei kommt und welche gelesen wird, wählt die Person im Dateidialog des
Telefons. Android gibt der App genau diese eine Datei frei. Die App braucht dafür keine
Berechtigung und sieht sonst nichts vom Speicher.

### Einlesen fügt hinzu, es ersetzt nichts

Ein Rezept gibt es schon, wenn es dieselbe Quelladresse hat – oder, bei selbst erfassten,
denselben Titel und dieselben Zutaten. Solche werden übergangen. Damit lässt sich dieselbe
Datei gefahrlos zweimal einlesen, und ein Rezept, das seit dem Export bearbeitet wurde, wird
nicht überschrieben. Die App sagt danach, wie viele dazugekommen sind und wie viele es
schon gab.

### Die Datei kann von irgendwoher kommen

Eine Exportdatei ist fremde Eingabe, auch wenn sie meist die eigene ist:

- **Namen aus dem Archiv werden nie zu Pfaden.** Jedes Foto bekommt beim Ablegen einen Namen,
  den die App vergibt. Ein Eintrag `fotos/../../databases/…` ist damit wirkungslos.
- **Als Foto zählt nur, was wie ein Bild anfängt** (JPEG, PNG, GIF, WebP, HEIF).
- **Alles hat eine Obergrenze** – der Text, jedes Foto, die Fotos zusammen, fremde Einträge,
  die Länge jedes Felds. Ein Archiv kann beim Auspacken beliebig gross werden; ein
  übergrosser Eintrag bricht das Einlesen ab, statt den Speicher zu füllen.
- **Adressen müssen ins Web zeigen.** Eine Quelle `javascript:…` oder ein Foto
  `file:///…` wird verworfen.
- Scheitert das Einlesen, bleibt nichts zurück: Schon abgelegte Fotos werden wieder
  weggeräumt. Scheitert das Schreiben, bleibt keine halbe Datei liegen – sie sähe aus wie
  eine Sicherung und wäre keine.

## Folgen

- Die Datei ist nicht verschlüsselt. Wer sie hat, kann die Rezepte lesen. Das steht so in
  der Datenschutzerklärung.
- Der Export ist kein Abbild des Telefons. Für "alles genau so wie vorher" bräuchte es auch
  Plan und Vorlieben – das steht unter "Geplant".
