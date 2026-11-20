# ADR 0013: Kalorien und Eiweiss – erst die Quelle, dann die Schätzung

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Gewünscht war zu jedem Rezept eine Angabe zu Kalorien (kcal) und Eiweiss (g), unter der
Zutatenliste – im Rezept und beim Antippen einer Wischkarte. Nennt die Quelle die Werte,
sollen ihre gelten; nur sonst soll geschätzt werden.

## Entscheidung

### Zuerst, was die Rezeptseite sagt

Viele Seiten liefern die Nährwerte in denselben strukturierten Daten, aus denen die App
ohnehin Titel und Zutaten liest (schema.org `nutrition`: `calories`, `proteinContent`). Sie
werden beim Laden der Seite mitgelesen – ein zusätzlicher Abruf entsteht nicht – und beim
Rezept gespeichert (zwei Spalten, Datenbank-Version 7).

Gelesen wird grosszügig ("512 kcal", "512 calories", "2143 kJ", "23,5 g"), geglaubt nur, was
plausibel ist. Eine Null, ein leeres Feld oder ein Wert pro 100 g gilt als "nicht genannt":
Als "laut Quelle" gezeigt, wäre eine falsche Zahl schlimmer als eine ehrliche Schätzung.

### Sonst eine Schätzung aus den Zutaten

Jede Zutat wird in einer Tabelle nachgeschlagen (rund zweihundert Lebensmittel, wie sie in
Rezepten aus der Schweiz und Deutschland vorkommen) und in Gramm umgerechnet – auch
"2 Eier", "1 EL Öl", "1 Bund Peterli", "1 Dose Kichererbsen" oder "4 cl Rum". Die Summe wird
durch die Portionen geteilt.

Was dabei zählt:

- **Die Tabelle rechnet, wie man einkauft.** Teigwaren und Reis trocken, Fleisch roh.
  Hülsenfrüchte aus der Dose und "gekochter Reis vom Vortag" haben eigene Werte.
- **Deutsche Zutaten sind Zusammensetzungen.** In "Schweinefleisch" steckt "wein", in
  "mehligkochend" steckt "mehl", und "Kokosmilch" ist keine Milch. Jedes Stichwort sagt
  deshalb, wie es passen muss (ganzes Wort, Wortanfang, Wortende), und das Genauere steht
  vor dem Allgemeinen. Diese Fallen sind einzeln getestet.
- **Unbekanntes bekommt den Wert seiner Warengruppe** – dieselbe Einteilung, nach der die
  Einkaufsliste sortiert. Ein Stück Fleisch, das die Tabelle nicht kennt, zählt als Fleisch.
- **Bleibt mehr als ein Viertel unerkannt, gibt es keine Zahl.** Eine Schätzung, der die
  Hauptzutat fehlt, ist schlechter als keine; die App sagt dann, dass die Angaben nicht
  reichen.
- **Fett ohne Menge zählt als knapper Esslöffel**, Frittieröl nur zu einem Zehntel.

### Immer dabei: woher die Zahl kommt

Geschätzte Werte tragen "ca.", sind auf Zehner gerundet, und darunter steht "Geschätzt aus
den Zutaten". Werte der Quelle stehen ohne "ca." mit "Angaben von …". Nennt die Quelle nur
einen der beiden Werte, wird der andere geschätzt und das so beschriftet.

Sagt ein Rezept nicht, für wie viele es reicht, gilt die Schätzung für das ganze Rezept –
und steht so da. Durch eine erfundene Portionszahl zu teilen, wäre geraten.

### Gerechnet wird beim Anzeigen, gespeichert nur die Angabe der Quelle

Die Schätzung steht nicht in der Datenbank. So folgt sie jeder Änderung an den Zutaten, und
eine bessere Tabelle wirkt sofort auf alle Rezepte. Auch die Exportdatei enthält nur, was
die Quelle genannt hat.

## Folgen

- Die Zahlen sind Richtwerte, keine Laborwerte. README und Datenschutzerklärung sagen, dass
  sie keine Grundlage für eine Diät oder Therapie sind.
- Fremdsprachige Rezeptseiten werden nicht geschätzt – ihre Zutaten liefen an den deutschen
  Stichworten vorbei. Dort zählt nur, was die Seite selbst nennt.
- Rezepte, die vor Version 7 gespeichert wurden, haben keine Angabe der Quelle; für sie
  wird geschätzt. Nachgeladen wird nichts.
- Das Bearbeitungsformular reicht Portionszahl und Quellenwerte jetzt durch. Vorher ging die
  Portionszahl bei jedem Speichern verloren – aufgefallen ist es erst hier, weil "pro
  Portion" sie braucht.
