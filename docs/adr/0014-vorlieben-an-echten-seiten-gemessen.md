# ADR 0014: Vorlieben gelten überall – gemessen an echten Rezeptseiten

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Gemeldet wurde: Rezepte mit Pilzen, obwohl "keine Pilze" gewählt war – allein und in der
gemeinsamen Runde. Verlangt war, es für jede Abneigung zu prüfen und zu beheben.

Die Einstufung (`RecipeClassifier`) arbeitet mit Stichworten. Ob sie etwas übersieht,
entscheidet sich nicht an ausgedachten Beispielen, sondern daran, wie die Quellen ihre
Zutaten wirklich schreiben. Die Entwicklungsumgebung erreicht die Rezeptseiten nicht; also
läuft die Messung in der CI (Aufgabe `fehler-pruefen`): `FilterAuditTest` zieht Karten aus
dem Verzeichnis, liest ihre Seiten mit den Bausteinen der App – samt robots.txt und
Wartezeiten – und schreibt auf, an welchen Wörtern jede Einstufung hängt, was verdächtig
aussieht und nicht erkannt wurde, und welche Zutatenwörter gar nichts auslösen.

Der erste Lauf (281 Seiten aus den acht deutschsprachigen Quellen, 245 davon lesbar) zeigte:
An der Erkennung von Pilzen in Zutatenlisten lag es kaum – dort fehlte ein einziger Begriff
("Parasole"). Die Meldung hatte andere, gröbere Ursachen. Bei fast jeder anderen Abneigung
gab es Lücken und Fehlalarme. Und eine der acht Quellen lieferte kein einziges Rezept.

## Entscheidung

### 1. Die Vorlieben gelten an jeder Stelle, die Rezepte vorschlägt

Drei Stellen kannten sie nicht oder nicht mehr:

- **Wischen durch die eigene Sammlung.** Dort wurde gar nicht gefiltert. Wer Rezepte einer
  anderen Person übernommen hatte (Import, gemeinsame Treffer), bekam deren Gerichte
  vorgelegt. Jetzt gilt `DietaryFilter` auch hier; vor dem Start steht, wie viele Rezepte
  deshalb draussen bleiben – sonst wäre der kleinere Stapel ein Rätsel.
- **Eine laufende Wischrunde.** Die Regeln wurden beim Start gelesen. Der Stapel bleibt aber
  liegen, wenn man zum Tab "Vorlieben" wechselt (die Navigation bewahrt jeden Tab). Wer
  mitten in der Runde etwas ausschloss, sah es danach weiter. Jetzt beobachtet der Stapel
  die Vorlieben: Bei einer Änderung, die das Essen betrifft, werden die offenen Karten nach
  den neuen Regeln neu gezogen und geprüft. Was schon gefallen hat, bleibt.
- **Der Bildschirm "Zu zweit".** Er holte die Vorlieben einmal beim Öffnen. Aus demselben
  Grund – der Bildschirm bleibt liegen – eröffnete oder betrat man die nächste Runde mit
  dem alten Stand, und das Gegenüber erfuhr von der neuen Abneigung nichts. Jetzt liest er
  mit. Die Vereinigung der Angaben beider im Dienst war in Ordnung; am echten Dienst
  nachgeprüft.

Der **Wochenplan** bleibt bei einer Änderung stehen – das war und bleibt Absicht, sonst
zieht das Antippen einer Abneigung einem die Woche unter den Füssen weg. Neu sagt die
Plan-Seite bei jeder Mahlzeit, die nicht mehr passt, dass sie nicht mehr passt; tauschen
lässt sie sich dort mit einem Tipp.

### 2. Stichworte sagen, wie sie passen müssen

Bisher traf ein Stichwort irgendwo im Wort. Deutsche Zutaten sind Zusammensetzungen, und
nicht jede meint, was in ihr steckt: An den echten Seiten wurde aus Flammkuchen ein
Lammgericht, aus Zitronenrinde Rind, aus "al dente" eine Ente, aus "garnieren" eine Niere,
aus Austernpilzen Meeresfrüchte und aus gehackten Tomaten Hackfleisch.

`WordSet` kennt deshalb vier Arten zu passen – ganzes Wort (`=ente`), Wortanfang
(`lamm*`), Wortende (`*leber`), irgendwo (`champignon`) – und Ausnahmen. Zwei
aufeinanderfolgende Wörter gelten auch als eines ("Ziegen-Frischkäse", "Crème fraîche");
eine Ausnahme, die auf ein Wortpaar passt, nimmt beide Wörter aus.

Die Listen wurden um das ergänzt, was die Seiten wirklich schreiben (Schwammerl, Funghi,
Thon, Dorade, Matjes, Truten, Hendl, Faschiertes, Cipollata, Schlagobers, Topfen …) und
gegen ein Wörterbuch deutscher Nomen auf zufällige Treffer geprüft. Die Fälle aus den
echten Seiten stehen in `RealWorldClassificationTest`.

Neu gelesen werden die Notizen zu Zutaten ("mit Speck umwickelt").

### 3. Die Zubereitung liest mit – aber nur für Bestimmtes

Die Zutatenliste einer Seite ist nicht immer vollständig. Am deutlichsten bei einer Seite
von ichkoche.at: "Gebackene Champignons" nennt als Zutaten "Pfeffer, Ehl, 2 Eier,
Emmelbroesel" – so steht es dort –, während die Zubereitung Champignons, Zwiebel und
Sardellen verarbeitet. Gezählt über 281 Seiten: Bei rund jeder fünfzigsten stand etwas
Ausgeschlossenes nur in der Zubereitung (Schmalz zum Ausbacken, Innereien für die Füllung).

Einfach alles mitzulesen wäre aber falsch. Mit denselben Listen auf die Zubereitung
angewandt, wäre ein Mehrfaches an Seiten zu Unrecht herausgefallen: "scharf anbraten" (11
Seiten), Serviervorschläge wie "dazu passt Baguette" (9), "in speckwürfelgrosse Stücke",
"wie ein Schnitzel panieren". Darum liest die Zubereitung nur mit Bedacht mit:

- **Nur Fleisch, Fisch, Meeresfrüchte und die Lebensmittel unter "Mag ich nicht"** (Pilze,
  Zwiebeln, Knoblauch, Koriander, Oliven, Aubergine, Kohl). Nicht Gluten, Milch, Eier oder
  Nüsse – dort stand fast immer ein Serviervorschlag – und nicht "scharf".
- **Nur bestimmte Wörter.** Gerichte ("Schnitzel", "Gulasch"), Geräte ("Knoblauchpresse",
  "Fischheber"), Vergleiche ("walnussgross", "lachsfarben") und Redensarten ("zergeht auf
  der Zunge") zählen nicht.
- **Nicht bei Rezepten, die sich als fleischlos ausweisen** oder auf Fleischersatz bauen:
  Dort meint "die Würstchen anbraten" den Ersatz. Die Pflanzen zählen auch dort.

Eigene Stichworte werden weiterhin nur an Titel und Zutaten geprüft: Sie treffen irgendwo
im Wort, und in einem langen Text träfe ein kurzes Stichwort fast alles.

### 4. Eigene Stichworte werden grosszügig verglichen

Ein eigenes Stichwort traf nur Buchstabe für Buchstabe: "Pilze" traf keine Champignons,
"Nüsse" keine Haselnuss, "Rüebli" kein Rezept mit Karotten. Jetzt gilt (`CustomDislike`):

- Einzahl und Mehrzahl sind dasselbe.
- Schweizer und hochdeutsche Namen sind dasselbe.
- Wer eine ganze Gruppe eintippt, für die es einen Knopf gibt ("Pilze", "Fisch",
  "Geflügel"), meint die Gruppe.

### 5. Wo es keine eindeutige Antwort gibt, ist sie festgelegt

- **Bärlauch zählt zu Knoblauch.** Er schmeckt danach und heisst nicht umsonst wilder
  Knoblauch.
- **Hackfleisch ohne Tierangabe zählt als Rind und Schwein.** Wer eines davon nicht isst,
  soll kein "Gehacktes gemischt" bekommen.
- **Blumenkohl, Brokkoli und Kohlrabi zählen nicht zu "Kohl".** Gemeint sind mit der
  Abneigung die Kohlköpfe und ihr Geschmack: Weisskabis, Rotkohl, Wirz, Rosenkohl,
  Sauerkraut. Wer Blumenkohl nicht mag, trägt ihn als eigenes Stichwort ein.
- **Trüffel sind Pilze** – ausser es ist erkennbar Konfekt (Schokolade, Praliné).
- **"Scharf" hängt an Zutaten und am Titel, nicht an Notizen.** "Nach Belieben mit Chili"
  macht ein Gericht nicht scharf.

### 6. Eine Quelle, die sich nicht lesen liess

ichkoche.at stellt ein Achtel der deutschsprachigen Karten – und für 36 von 36 Seiten fand
der Leser kein Rezept. Mit aktivem Filter fiel damit jede dieser Karten nach dem Laden
still heraus. Die Seiten haben kein JSON-LD, nur Microdata, und zwar in zwei Schreibweisen,
die der Leser nicht kannte: mehrere Namen in einem `itemprop` und Werte im `content` eines
unsichtbaren `meta`-Elements. Beides wird jetzt gelesen; der nächste Lauf las 36 von 36.

### 7. Die Messung bleibt

`fehler-pruefen` ist eine Aufgabe der CI, kein Teil der täglichen Prüfung: Sie braucht das
Netz und die Seiten anderer Leute. Sie gehört gelaufen, wenn an den Stichworten gedreht
wird oder eine Quelle dazukommt. Ihr Bericht nennt auch die Seiten, aus denen sich kein
Rezept lesen liess, und was sie stattdessen enthalten.

## Folgen

- Stichworte bleiben Stichworte. Die App liest Zutatenlisten und Zubereitung, sie versteht
  keine Rezepte: Ein Gericht, das seine Pilze hinter einem Fertigprodukt versteckt
  ("Jägersauce"), kommt durch. Unverträglichkeiten werden nur an Titel und Zutaten
  geprüft; der Hinweis in der App, sich bei Allergien auf die Zutatenliste zu verlassen,
  gilt weiter.
- Die Zubereitung mitzulesen kostet Karten: Ein Rezept, das Speck nur als Möglichkeit
  nennt ("oder Würstchen"), fällt für alle heraus, die kein Schwein essen. Das ist gewollt –
  eine Karte zu wenig ist billiger als eine falsche.
- Mit aktivem Filter kommen nur deutschsprachige Quellen in den Stapel – nur dort lassen
  sich die Zutaten prüfen. Das war schon so und bleibt.
- Strenger heisst auch: Es fällt mehr heraus. "Hackfleisch" ohne Tierangabe fehlt jetzt
  allen, die Rind *oder* Schwein ausgeschlossen haben.
- Der Plan zeigt nach einer Änderung der Vorlieben Hinweise, ersetzt aber nichts von
  selbst.
