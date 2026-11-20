# ADR 0008: Gemeinsame Runde über den Dienst, Vorlieben mit Wirkung

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Die App lief auf zwei Telefonen, und dabei fiel einiges auf – am deutlichsten in der
gemeinsamen Runde:

- "Was darf nicht auf den Tisch?" wirkte nicht für beide. Wählte eine Person dort "Vegan",
  sah die andere weiterhin vegane Karten. Schlimmer: Die Auswahl bedeutete gar nicht "kein
  Vegan", sondern "nur Vegan" – unter einer Überschrift, die das Gegenteil sagte.
- Die beiden Telefone standen an verschiedenen Karten. Jedes filterte für sich und stimmte
  für aussortierte Karten still mit "Nein"; dadurch begann das eine bei Karte 0, das andere
  bei Karte 1.
- Die Runde endete erst, wenn beide den ganzen Stapel durchhatten – auch wenn die
  gewünschte Anzahl längst gefunden war. Der Dienst erfuhr alle Stimmen erst am Schluss.
- Beide wählten die Anzahl der Gerichte, bevor überhaupt ein Code existierte.

Dazu kam eine Reihe von Einstellungen ohne Wirkung: fünf von neun Küchen, die
Postleitzahl, der Laden ausserhalb der Preisrechnung des Plans.

## Entscheidung

### 1. Die gemeinsame Runde führt der Dienst

Die drei ersten Punkte haben dieselbe Wurzel: Jedes Telefon entschied für sich, was es
zeigt und wann Schluss ist. Das lässt sich auf dem Gerät nicht reparieren, denn keines
kennt die Angaben und die Stimmen des anderen. Also wandert beides zum Dienst (Fassung 2
der Schnittstelle, `pairing/README.md`):

- **Angaben werden vereinigt.** Beim Eröffnen und Beitreten schickt jedes Telefon, was
  seine Person nicht will; der Dienst legt beides zusammen. Jedes Telefon steuert danach
  nur Karten bei, die zu den Angaben *beider* passen. Niemand muss mehr still überspringen
  – deshalb ist der Stapel für beide derselbe, ab der ersten Karte.
- **Stimmen kommen einzeln.** Der Dienst zählt die gemeinsamen Treffer nach jeder Stimme
  und beendet die Runde beim Ziel. Dass dadurch die *Zahl* der Treffer schon während des
  Wischens sichtbar ist, ist gewollt (sie ist der Fortschritt); *welche* Rezepte es sind,
  nennt er weiterhin erst am Ende.
- **Der Code steht zuerst.** Eine Lobby trennt "einladen" von "festlegen": Wer eröffnet,
  bestimmt Anzahl, Art (Essen, Getränke, beides) und Quelle; die eingeladene Person sieht
  es mit, kann es aber nicht ändern.
- **Erst die gespeicherten Rezepte beider, dann die Frage.** Ist der Stapel durch und das
  Ziel nicht erreicht, entscheidet die eröffnende Person: mit neuen Rezepten weiter oder
  aufhören und behalten, was gefunden wurde.

Jede Entscheidung des Dienstes ist eine einzelne SQL-Anweisung mit ihrer Bedingung im
`WHERE`, ausgeführt im selben Stapel wie die auslösende Änderung. Zwei gleichzeitig
sendende Telefone können den Zustand so nicht auseinandertreiben.

**Fassung 1 bleibt** für Apps, die noch nicht aktualisiert sind. Treffen alte und neue
App in einer Runde aufeinander, antwortet der Dienst mit 426 statt halb zu funktionieren.

**Preis dieser Entscheidung:** Der Dienst sieht mehr als vorher – die Titel gespeicherter
Rezepte und die vereinigten Angaben zu Ernährung, Unverträglichkeiten und Abneigungen.
Eine Unverträglichkeit ist eine Gesundheitsangabe. Sie steht deshalb ohne Namen, ohne
Zuordnung zu einer Person und nur bis zum Ablauf der Sitzung dort; die App sagt es vor dem
Start, und Datenschutzseite und README nennen es ausdrücklich.

### 2. Drei Arten von "das esse ich nicht" – getrennt

`DietaryFilter` kennt jetzt drei Dinge, und die Oberfläche zeigt sie überall als drei
Gruppen mit einem Satz dazu:

| Gruppe | Bedeutung | Beispiel |
|---|---|---|
| Ernährung | Bedingung: nur das kommt infrage | "Vegan" = nur vegane Rezepte |
| Unverträglichkeiten | Ausschluss, Gesundheit | "Nüsse" = nichts mit Nüssen |
| Abneigungen | Ausschluss, Geschmack | "Vegane Gerichte" = nichts ausdrücklich Veganes |

Abneigungen sind harte Ausschlüsse wie Unverträglichkeiten – "mag ich nicht" soll nicht
"kommt seltener" heissen. Dazu eigene Stichworte für alles, was in der Liste fehlt.

### 3. Was eine Karte erfüllen muss, steht an einer Stelle

`CardRules` (Art, Filter, Geräte, "nur Mahlzeiten") gilt für den Wischstapel, den
Wochenplan und die gemeinsame Runde. Geprüft wird zweimal: am Titel – mehr kennt die App
von einer Karte aus dem Verzeichnis nicht – und, sobald Ernährung, Unverträglichkeiten
oder Abneigungen im Spiel sind, an der geladenen Rezeptseite. Der Titel kann nur
aussortieren; zusagen kann erst die Seite.

Dazu gehört die Einteilung in Mahlzeit, Getränk, Dessert und Beiwerk (`CourseRules`). Sie
liest das Grundwort des Titels und den Teil vor dem Verhältniswort ("Lachs mit
Tee-Marinade" ist ein Lachs). Im Zweifel bleibt ein Rezept eine Mahlzeit.

### 4. Sammlung und Plan sind zweierlei

Ein Rezept, das der Wochenplan holt, wird gespeichert (sonst liesse es sich nicht kochen),
gehört aber nicht zur Sammlung: `recipes.inCollection = 0`. Es erscheint weder in "Meine
Rezepte" noch beim Wischen, bis jemand es ausdrücklich behält, und wird gelöscht, sobald
es nicht mehr im Plan steht. Umgekehrt verschwindet ein gelöschtes Rezept, das gerade im
Plan steht, nur aus der Sammlung.

Bestehende Rezepte bleiben nach dem Update alle in der Sammlung. Die App kann nicht
unterscheiden, was davon bewusst gespeichert und was vom Plan hineingelegt wurde – und im
Zweifel löscht sie nichts.

### 5. Der Plan

- **Zwei Mahlzeiten pro Tag** (`plan_entries.slot`, eindeutig je Tag und Mahlzeit).
  Reichen die Rezepte nicht, bekommt zuerst jeder Tag ein Abendessen.
- **Ersetzen mit Gedächtnis.** Das verworfene Rezept merkt sich der Plan bis zum nächsten
  neu gebauten; gewählt wird zufällig unter den besten freien, und geht der Vorrat zur
  Neige, wird nachgezogen. Vorher war das eben Verworfene sofort wieder der beste freie
  Kandidat – "ersetzen" wechselte zwischen zwei Rezepten hin und her.
- **Der erste gewählte Laden ist der Hauptladen.** Mit ihm wird gerechnet; die anderen
  (bis zu zwei) stehen zum Vergleich daneben. Das Land bestimmt Läden, Währung und
  Preisniveau.
- **Küchen sind eine Vorliebe, kein Filter.** Gewähltes kommt häufiger, anderes weiterhin
  ab und zu.

## Folgen

- Die gemeinsame Runde braucht auf beiden Telefonen die neue Fassung der App.
- Die Preisniveaus ausserhalb der Schweiz (DE/AT/IT 0.7, FR 0.75, übrige 0.65) sind
  Schätzwerte, keine amtlichen Zahlen. Die Oberfläche zeigt alle Summen mit "ca.".
- Der Dienst wird vor der App ausgerollt: Ihre Gerätetests spielen eine Runde gegen den
  echten Dienst.
- Selbst erfasste Rezepte der anderen Person lassen sich aus einer Runde nicht
  übernehmen – geteilt wird davon nur der Titel. Die App sagt das, statt still nichts zu tun.
- Wer neue Einstellungen ergänzt, ergänzt den Test, der ihre Wirkung zeigt. Eine
  Einstellung ohne Folge ist ein Fehler, kein Platzhalter.

## Nachtrag: Frist und selbst erfasste Treffer

Zwei Lücken der Fassung 2 fielen im Gebrauch auf und sind geschlossen, ohne die
Schnittstelle zu brechen (ältere Apps spielen weiter mit):

- **Eine Runde wartet nicht mehr endlos auf Karten.** Schickte ein Telefon seinen Beitrag
  nie, blieb die Runde stehen. Jetzt läuft mit dem ersten Beitrag eine Frist von einer
  Minute; danach baut der Dienst den Stapel aus dem, was da ist. Die App zeigt die Frist an.
- **Ein selbst erfasster Treffer lässt sich übernehmen.** Er hat keine Adresse, von der die
  andere Person ihn holen könnte. Die App hinterlegt deshalb Zutaten und Zubereitung beim
  Dienst – nur für gemeinsame Treffer, nur vom Gerät, das die Karte beigesteuert hat, ohne
  Foto, und gelöscht mit der Runde. Das weicht den Satz "Zutaten und Zubereitung bleiben auf
  dem Gerät" auf; die App sagt es vor dem Start, und es steht in der Datenschutzerklärung.

Dazu in der App: "Übernehmen" unterscheidet jetzt, *warum* etwas nicht ging (Woche voll,
schon im Plan, Seite nicht erreichbar, Inhalt noch unterwegs), bietet "Nochmal versuchen" an
und fragt bei voller Woche, ob noch nicht Gekochtes weichen soll. Auf die Einkaufsliste
kommt dabei nichts mehr von selbst. Einzelheiten: `pairing/README.md`.

## Nachtrag: Woche überschreiben, Dubletten, Ladezeit

Aus dem Gebrauch der Fassung mit Frist kamen drei weitere Punkte:

- **Woche überschreiben.** "In den Wochenplan" legt Treffer auf freie Plätze und fragt bei
  voller Woche, ob noch nicht Gekochtes weichen soll. Wer die Woche ganz den Treffern
  überlassen will, hat dafür einen eigenen Knopf: Ein Blatt zeigt jeden Treffer mit den
  sieben Tagen darunter (und Mittag/Abend, wenn beides geplant wird). Vorgeschlagen wird
  eine Verteilung ab heute; ein Tipp auf einen vergebenen Tag tauscht die beiden Gerichte,
  derselbe Tag noch einmal lässt eines weg. Ersetzt wird mit dem, was sich holen liess –
  lässt sich gar nichts holen, bleibt die Woche stehen, und was fehlte, kommt mit
  "Erneut versuchen" später an seinen Tag.
- **Keine Dubletten beim Übernehmen.** Ein Rezept wurde an der buchstabengleichen Adresse
  wiedererkannt; kannte das andere Telefon dieselbe Seite mit "www." oder Schrägstrich am
  Ende, entstand eine zweite Kopie. Wiedererkannt wird jetzt an einer vereinheitlichten
  Adresse und – bei selbst erfassten Rezepten – am Titel, an einer einzigen Stelle für
  Sammlung, Plan und Runde. Selbst erfasste Rezepte tragen auch als Karte einen Schlüssel
  aus dem Titel: Haben beide dasselbe, liegt es nur einmal im Stapel.
- **Neue Karten laden schneller, bei voller Zahl.** Die Seiten neuer Karten wurden
  stapelweise geprüft, und jeder Stapel wartete auf seine langsamste Seite. Jetzt laufen die
  Prüfungen als gleitendes Fenster (zwölf zugleich, höchstens zwei je Quelle – die Abstände,
  die robots.txt verlangt, bleiben eingehalten) und hören auf, sobald genug Karten bestanden
  haben. Die Zahl der Karten ist dieselbe wie zuvor.
