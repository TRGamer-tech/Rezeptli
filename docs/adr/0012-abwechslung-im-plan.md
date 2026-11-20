# ADR 0012: Der Wochenplan wiederholt sich nicht

**Status:** Angenommen · **Datum:** 2026-11-20

## Kontext

Der Plan wusste nicht, was zuletzt gekocht wurde. Ein Gericht vom Montag konnte am
Mittwoch der nächsten Woche wieder dastehen, und wer den Plan neu baute, bekam oft dieselben
Gerichte in anderer Reihenfolge. Der Vermerk "gekocht" war da, wurde aber nicht benutzt.

## Entscheidung

### Drei Stufen statt eines Punktabzugs

Der Plan wählt in dieser Reihenfolge:

1. Gerichte, die es **nicht eben erst** gab,
2. Gerichte des Plans, der gerade ersetzt wird,
3. Gerichte, die in den **letzten 14 Tagen gekocht** wurden – davon zuerst, was am längsten
   zurückliegt.

Eine spätere Stufe kommt erst zum Zug, wenn die frühere nicht reicht. Ein blosser
Punktabzug hätte das nicht geleistet: Die Regel "nicht zweimal dieselbe Art Gericht
hintereinander" greift quer durch die Rangliste und hätte ein eben gekochtes Gericht
wieder nach vorn geholt. Auch das Budget tauscht nie gegen kürzlich Gekochtes – Sparen ist
kein Grund, das Essen von gestern zu wiederholen.

Ausgeschlossen wird nichts. Wer wenige passende Rezepte hat, bekommt lieber das Gericht von
vor zehn Tagen als einen leeren Tag.

Zwischen zwei und vier Wochen verliert ein gekochtes Gericht noch Punkte, immer weniger.
Danach zählt es wie jedes andere.

### Der Plan merkt sich Gekochtes selbst

Der Vermerk steht am Rezept – aber ein Rezept, das nur für den Plan geholt wurde, wird mit
dem nächsten Plan wieder weggeräumt, und der Vermerk ginge mit ihm verloren. Gerade diese
Rezepte sind es, die der Plan sonst sofort wieder vorschlüge.

Deshalb merkt sich der Plan Gekochtes zusätzlich selbst: Quelladresse und Titel mit dem
Zeitpunkt, in derselben kleinen Einstellungsdatei wie die verworfenen Vorschläge. Über den
Titel zählt auch dasselbe Gericht aus einer anderen Quelle. Einträge älter als sechzig Tage
fallen weg; wer einen Vermerk zurücknimmt, löscht ihn auch hier.

## Folgen

- Die Datenbank bleibt, wie sie ist – keine Migration.
- Dieses Gedächtnis verlässt das Telefon nicht und ist nicht Teil des Exports.
- "Gekocht" heisst: als gekocht vermerkt – im Kochmodus, im Plan oder in der Liste. Was
  gekocht, aber nicht vermerkt wurde, kennt der Plan nur als Gericht des alten Plans
  (Stufe 2).
