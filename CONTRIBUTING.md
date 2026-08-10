# Mitmachen bei Rezeptli

Schön, dass du mithelfen willst. Rezeptli ist ein Freizeitprojekt ohne kommerzielle
Absicht – entsprechend entspannt ist der Umgangston, entsprechend wichtig sind
nachvollziehbare Beiträge.

## Kurzfassung

1. Issue anlegen oder ein bestehendes übernehmen, damit niemand doppelt arbeitet.
2. Feature-Branch von `main` abzweigen.
3. Code schreiben – mit Tests für alles, was Logik enthält.
4. `./gradlew ktlintFormat test` lokal grün bekommen.
5. Pull Request gegen `main` öffnen.

## Branch-Strategie

- `main` ist immer baubar und wird nie direkt bepusht.
- Alles andere läuft über Feature-Branches: `feat/swipe-undo`, `fix/parser-dl`,
  `docs/readme-screenshots`.
- Jede Änderung kommt über einen Pull Request, auch kleine.

## Commit-Konvention

Wir nutzen [Conventional Commits](https://www.conventionalcommits.org/) auf Deutsch:

```
feat: Swipe-Screen mit Undo-Funktion
fix: Parser erkennt dl nicht korrekt
docs: Build-Anleitung für JDK 17 ergänzt
test: Beispieleingaben für Schweizer Einheiten
refactor: Zutaten-Mapper in eigene Datei
chore: AGP auf 8.7.3 angehoben
```

Erlaubte Typen: `feat`, `fix`, `docs`, `test`, `refactor`, `perf`, `chore`, `ci`.
Die Betreffzeile bleibt unter 72 Zeichen und beschreibt das Ergebnis, nicht den Weg.

## Code-Style

- Kotlin, offizieller Style, geprüft mit [ktlint](https://pinterest.github.io/ktlint/).
- `./gradlew ktlintFormat` korrigiert das meiste automatisch; `./gradlew ktlintCheck`
  läuft in der CI und muss grün sein.
- Maximale Zeilenlänge: 120 Zeichen.
- Kommentare erklären das *Warum*, nicht das *Was* – und sie sind auf Deutsch, wie der
  Rest des Projekts.

## Tests

Ein Feature gilt erst als fertig, wenn seine Logik getestet ist:

- **Use Cases und ViewModels**: Unit-Tests mit JUnit 5, MockK und
  `kotlinx-coroutines-test`.
- **Parser und Swipe-Matching**: Diese beiden tragen die Kernlogik der App. Änderungen
  daran brauchen zwingend Tests mit realistischen Beispieleingaben – inklusive Schweizer
  Schreibweisen (`dl`, `KL`, `Rüebli`, `½`).
- **Kritische UI-Flows**: Compose-UI-Tests, insbesondere für die Swipe-Geste.

```bash
./gradlew test                  # Unit-Tests
./gradlew connectedAndroidTest  # UI-Tests auf Emulator oder Gerät
```

## Datenbank-Schema

Room exportiert bei jedem Build das aktuelle Schema nach `app/schemas/`. Diese Dateien
gehören ins Repository: Nur mit ihnen lassen sich später Migrationen gegen die
tatsächliche Vorgängerversion testen. Wenn dein Beitrag Entities oder DAOs ändert:

1. `./gradlew assembleDebug` laufen lassen – das aktualisiert `app/schemas/`.
2. Die geänderte Schema-Datei mitcommitten.
3. Für eine neue Datenbankversion eine `Migration` in `RezeptliDatabase.MIGRATIONS`
   ergänzen. `fallbackToDestructiveMigration` ist tabu – niemand soll seine Rezepte
   durch ein Update verlieren.

Die CI meldet einen Fehler, wenn das erzeugte Schema von einer eingecheckten Datei
abweicht. Ist noch keine eingecheckt, hängt sie das erzeugte Schema als Artefakt
`room-schema` an den Workflow-Lauf – von dort lässt es sich herunterladen und einchecken.

## Architektur

Bitte die Schichtung beibehalten:

- `domain/` kennt weder Android noch Room noch Compose. Reines Kotlin, damit es schnell
  und ohne Emulator testbar bleibt.
- `data/` kennt die Domain, aber nicht die Präsentation.
- `presentation/` kennt die Domain, aber nie direkt Room-Entities.

Grössere Entscheidungen werden als ADR in `docs/adr/` festgehalten. Wenn dein PR eine
solche Entscheidung trifft, leg bitte eine neue ADR dazu an.

## Leitplanken

Diese Punkte sind nicht verhandelbar, weil sie den Charakter des Projekts ausmachen:

- Keine Werbung, kein Tracking, keine Analytics-SDKs, keine In-App-Käufe.
- Keine kostenpflichtigen oder proprietären Dienste als Pflichtabhängigkeit.
- Keine Funktion, die Nutzerdaten ohne ausdrückliche Handlung vom Gerät schickt.
- Neue Abhängigkeiten brauchen eine Begründung im PR – die App soll schlank bleiben.

## Übersetzungen

Die App ist auf Hochdeutsch ausgeliefert. Alle Texte liegen in
`app/src/main/res/values/strings.xml`. Übersetzungen (Französisch und Italienisch für die
Schweiz, Englisch für den Rest) sind sehr willkommen: neuen Ordner `values-fr`, `values-it`
oder `values-en` anlegen und die Datei übersetzen. Fehlende Einträge fallen automatisch auf
Deutsch zurück.
