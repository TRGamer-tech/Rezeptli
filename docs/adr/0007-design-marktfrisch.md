# 0007 – Design "Marktfrisch": Farben, Schriften und Bewegung

Status: angenommen

## Ausgangslage

Die App sah aus wie ein Werkzeug: Anthrazit, Violett, Glasflächen mit Leuchtrand. Das
passte zu einem UI-Kit, aber nicht zu einer App, mit der zwei Menschen zu Hause
aussuchen, was sie diese Woche kochen. Nach dem ersten Test zu zweit kam der Wunsch nach
einem neuen Auftritt – mit eigenen Schriften, mit Bewegung überall, aber so, dass die App
für Menschen, die Bewegung schlecht vertragen, ruhig bleibt.

## Entscheidung

**Farben: ein Wochenmarkt.** Creme statt Grau als Grund, Karten wie Papier darauf.
Kräutergrün für alles, was man tun kann (Material `primary`), Salbei für Ausgewähltes
(`secondary` – Material färbt damit Chips, die aktive Leiste und Nebenknöpfe), Tomate als
Akzent (`tertiary`), Butter nur dekorativ. Der Dunkelmodus ist eine Waldnacht
(`#0F1E18`), kein Schwarz. Jedes Schrift-Farbpaar ist nachgerechnet und erreicht
mindestens 4.5:1 (WCAG AA); die leuchtenden Fassungen von Tomate und Butter tragen nie
Schrift. Die Rohwerte stehen in `Tokens.kt`, die Rollen in `Color.kt`.

**Schriften:** Fraunces für Titel – eine weiche Antiqua, wie man sie aus Kochbüchern
kennt, variabel und hier ganz weich (`SOFT` 100) ohne Wackelbuchstaben (`WONK` 0), in
drei optischen Größen. Figtree für Text, Knöpfe und Zahlen, mit gleich breiten Ziffern
für Mengen und Preise. Beide unter der SIL Open Font License (siehe `licenses/`).

**Bewegung: verspielt, aber an einer Stelle.** Alle Federn und Dauern kommen aus
`MotionScheme` (`Motion.kt`); kein Bildschirm notiert eigene Werte. Darauf bauen:

- Knöpfe, Chips und Karten geben unter dem Finger nach (`pressScale`, `Bouncy*`).
- Symbole bewegen sich: Jeder Tab hat seine eigene Bewegung, das Herz springt, der
  Warenkorb wackelt, Haken poppen auf, "Neu laden" dreht sich (`MotionIcon`).
- Listen und Abschnitte steigen beim ersten Erscheinen nacheinander auf (`entrance`) –
  in langen Listen pro Eintrag nur einmal, nicht bei jedem Zurückscrollen.
- Bildschirmwechsel gleiten, Tabs blenden über, Schritte federn herein.
- Laden zeigt einen brodelnden Kochtopf statt eines Kreisels; Treffer bekommen Konfetti.

**Reduzierte Bewegung gilt immer.** Zwei Auslöser, ein Ergebnis:

1. Android "Animationen entfernen" (Animationsskala 0) – wird beobachtet und gilt sofort.
2. Der Schalter "Bewegung reduzieren" in den Einstellungen, für alle, die nur Rezeptli
   ruhiger wollen.

Dann gibt es keine Bewegung im Raum, kein Nachschwingen und nichts, was endlos läuft
(Dampf, Pulsieren, Atmen, Konfetti fallen weg). Was bleibt, sind kurze Blenden: Ein harter
Sprung ist schwerer zu verfolgen als eine Blende von 120 ms. Wischen folgt weiterhin dem
Finger – das ist direkte Bedienung, keine Animation.

Hat Android Animationen entfernt, lässt sich der App-Schalter nicht ausschalten. Die
Android-Einstellung ist eine Bedienungshilfe; eine App sollte sie nicht übergehen. Aus
demselben Grund gibt es keine Stufe "volle Bewegung trotz Android-Einstellung".

## Folgen

- Neue Bildschirme verwenden `Bouncy*`-Knöpfe, `MotionIcon`, `entrance` und
  `RezeptliTheme.motion` statt eigener Animationen – sonst vergessen sie die Einstellung.
- Unendliche Animationen dürfen nur hinter `if (!motion.reduced)` stehen.
- Das App-Symbol ist der Kochtopf in den Marktfarben, mit eigener einfarbiger Fassung für
  Themen-Symbole. Ab Android 12 bewegt sich das Startbild (Dampf, Deckel); davor und bei
  entfernten Animationen steht es still – das erste Bild ist das ganze Symbol.
