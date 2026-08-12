# ADR 0005: Rezepte aus dem Web importieren

**Status:** Angenommen · **Datum:** 2026-08-11

## Kontext

Rezeptli soll Rezepte bei Schweizer Anbietern finden und übernehmen können: Betty Bossi,
Swissmilk, Gutekueche, Migusto, Bettys Küchenschätze, Fooby – dazu Chefkoch aus
Deutschland. Le Menu war anfangs dabei, ist aber wieder draussen: Die Seite veröffentlicht
keine maschinenlesbaren Rezeptdaten und kein Verzeichnis, taucht also weder in der Suche
noch im Import je auf. Sollte sich das ändern, kommt sie zurück. Das ändert die bisherige Haltung der App: Sie braucht damit erstmals eine
Internet-Verbindung.

Vor dem Entwurf wurden alle acht Seiten analysiert (robots.txt, Sitemaps, strukturierte
Daten). Das Ergebnis hat den Entwurf bestimmt – nicht umgekehrt.

## Entscheidung

### Auslesen über strukturierte Daten, nicht über HTML

Ausgewertet wird, was die Seiten für Suchmaschinen ohnehin mitliefern: schema.org als
JSON-LD oder Microdata. Drei Strategien greifen nacheinander:

1. **JSON-LD `Recipe`** – der Normalfall (Betty Bossi, Gutekueche, Migusto, Bettys
   Küchenschätze).
2. **JSON-LD `HowTo` plus Microdata-Zutaten** – so liefert Swissmilk aus: Titel,
   Gesamtzeit und Schritte stehen im `HowTo`, die Zutaten als `itemprop="recipeIngredient"`
   im HTML.
3. **Reine Microdata** als Rückfall.

Damit funktioniert derselbe Code für alle Quellen, und ein Redesign einer Seite bricht den
Import nicht. Die Alternative – pro Quelle CSS-Selektoren pflegen – wäre für acht Seiten
dauerhaft unbezahlbar und würde bei jedem Relaunch stillschweigend kaputtgehen.

### Suche über Sitemaps, nicht über die Suchseiten der Anbieter

Die Suche arbeitet mit den Verzeichnissen, die die Seiten selbst für Maschinen
veröffentlichen. Gesucht wird im sprechenden Teil der Adresse, der bei allen Quellen den
Rezepttitel enthält.

Der Grund ist nicht nur technisch: **Fooby untersagt in seiner robots.txt ausdrücklich das
Abfragen der eigenen Suchendpunkte.** Eine Suche, die deren Suchseite abgreift, wäre bei
mindestens einer Quelle ein Regelverstoss – und bei den übrigen ein fragiler Umweg.

Der Preis: Es ist eine Titelsuche, keine Volltextsuche über Zutaten. Dafür braucht eine
Suche keine hundert Seitenabrufe beim Anbieter, sondern genau einen pro Woche und Quelle.

### robots.txt wird gelesen und befolgt

Vor jedem Abruf prüft die App die robots.txt des Hosts (einmal pro Sitzung, dann aus dem
Speicher). Umgesetzt ist das übliche Verhalten: genaueste passende User-Agent-Gruppe, sonst
`*`; längste Regel gewinnt, bei Gleichstand `Allow`; `*` und `$` als Platzhalter. Ein
`Crawl-delay` wird eingehalten.

Die App tritt dabei unter eigenem Namen auf (`Rezeptli/1.0` mit Link auf das Repository)
und fällt damit unter die Regeln für `*`. Sie ist kein Suchmaschinen- oder
Trainings-Crawler: Sie lädt genau die eine Seite, die eine Person gerade importieren will.
Chefkochs robots.txt etwa sperrt eine lange Liste von KI-Crawlern vollständig aus,
erlaubt Rezeptseiten unter `*` aber ausdrücklich.

Abfrageparameter und Anker werden vor dem Laden entfernt. Das ist zugleich Höflichkeit
(mehrere Quellen schliessen parametrisierte Adressen als Dubletten aus) und Hygiene
(geteilte Links tragen oft Herkunfts-Parameter).

### Import läuft durch dieselbe Kontrolle wie eingefügter Text

Ein geladenes Rezept wird nicht direkt gespeichert. Die Zutatenzeilen gehen durch denselben
Parser wie ein von Hand eingefügter Text, und das Ergebnis landet in derselben
Vorschau zur Korrektur. Danach ist ein importiertes Rezept ein ganz normales Rezept – es
lässt sich bearbeiten und sofort mitswipen.

Herkunft und Quelle werden am Rezept festgehalten. Wer fremde Inhalte übernimmt, soll
sehen können, woher sie stammen.

## Konsequenzen

- Die App braucht die Berechtigung `INTERNET`. Netzzugriffe passieren ausschliesslich auf
  ausdrückliche Handlung – Suchen oder Importieren. Es gibt weiterhin kein Konto, keine
  Analyse-Bibliothek, und eigene Rezepte verlassen das Gerät nach wie vor nicht.
- Der Extraktor ist ohne Netz testbar: Die Testvorlagen bilden die tatsächlichen Strukturen
  der Quellen nach.
- Ändert eine Quelle ihre strukturierten Daten, meldet der Import sauber "kein Rezept
  gefunden" statt still Unsinn zu übernehmen.
- **Fooby** verlangt 10 Sekunden Abstand zwischen Anfragen und stellt kein brauchbares
  Rezeptverzeichnis bereit – dort ist nur der Import über einen konkreten Link vorgesehen.
- **Chefkoch** antwortet aus Rechenzentren teilweise mit 403. Vom Handy aus klappt der
  Import in der Regel; wenn nicht, sagt die App das und schlägt Copy-Paste vor. Eine
  Bot-Erkennung zu umgehen kommt nicht in Frage.
