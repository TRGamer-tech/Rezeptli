# Rezeptli

**Was koche ich heute?** Rezeptli beantwortet die Frage so, wie man heute Entscheidungen
trifft: durch Wischen. Du legst deine eigenen Rezepte an oder fügst sie per Copy-Paste ein,
und wenn dir nichts einfällt, swipest du durch deine eigene Sammlung – nach rechts für Ja,
nach links für Nein. Am Ende steht eine Liste mit allem, was dir zugesagt hat, inklusive
Zutaten, damit du sofort weisst, was noch einzukaufen ist.

Rezeptli ist eine native Android-App, komplett offline, ohne Konto, ohne Werbung, ohne
Tracking – und quelloffen unter der MIT-Lizenz.

## Funktionen (Version 1.0)

- **Rezepte verwalten** – anlegen, bearbeiten, löschen; mit Zutaten, Zubereitung, Foto,
  Tags und Zubereitungszeit
- **Text-Import** – ein aus Google Docs, einer Notiz-App oder einer Website kopiertes
  Rezept einfügen; ein regelbasierter Parser erkennt Titel, Zutaten, Zubereitung und
  Zubereitungszeit. Jede erkannte Zeile lässt sich vor dem Speichern korrigieren.
- **Swipe-Modus** – Kartenstapel mit deinen Rezepten, wischen für Ja/Nein, mit Undo und
  optionalem Vorab-Filter nach Tags und Zeit
- **Ergebnisliste** – alle Treffer einer Session auf einen Blick, samt vollständiger
  Zutatenliste
- **Suche und Filter** – über Titel, Zubereitung und Zutatennamen

### Screenshots

Noch keine – die App ist gerade erst gebaut. Wer sie auf einem Gerät laufen lässt, darf
gerne welche beisteuern: Rezeptliste, Swipe-Karte und Ergebnisliste als PNG in `docs/img/`
legen und hier einbinden. Für den Swipe-Flow eignet sich ein kurzes GIF am besten.

## Ausprobieren ohne Build

Unter [Releases](https://github.com/TRGamer-tech/Rezeptli/releases) liegt eine Debug-APK
zum direkten Herunterladen aufs Handy. Sie ist mit dem Android-Debug-Schlüssel signiert
und nicht optimiert – zum Testen gedacht, nicht zur Weitergabe. Zusätzlich hängt die CI
bei jedem Push eine Debug-APK als Artefakt an den Workflow-Lauf.

Schweizer Eigenheiten sind eingebaut: **dl** und **KL** sind vollwertige Einheiten, und
regionale Begriffe wie *Rüebli*, *Peperoni* oder *Zucchetti* kennt die App als Synonyme
zu ihrer hochdeutschen Normalform.

## Datenschutz

Alle Daten bleiben auf dem Gerät. Die App hat keine Internet-Berechtigung, kein Analytics-
SDK, keinen Absturzbericht-Dienst und kein Nutzerkonto. Was du in Rezeptli eingibst,
verlässt dein Telefon nicht.

## Build

Voraussetzungen:

| Werkzeug        | Version                          |
|-----------------|----------------------------------|
| Android Studio  | Ladybug (2024.2.1) oder neuer    |
| JDK             | 17                               |
| Android SDK     | compileSdk 35, minSdk 26         |

```bash
git clone https://github.com/TRGamer-tech/Rezeptli.git
cd Rezeptli

./gradlew assembleDebug     # Debug-APK bauen
./gradlew test              # Unit-Tests (JUnit 5)
./gradlew ktlintCheck       # Code-Style prüfen
./gradlew ktlintFormat      # Code-Style automatisch korrigieren
./gradlew connectedAndroidTest   # UI-Tests, benötigt Emulator oder Gerät
```

Die fertige Debug-APK liegt danach unter `app/build/outputs/apk/debug/`.

## Architektur

Drei Schichten, MVVM in der Präsentationsschicht:

```
presentation/  Compose-Screens, ViewModels (UiState + Events, unidirektionaler Datenfluss)
domain/        Modelle, Use Cases, Parser, Repository-Interfaces – reines Kotlin
data/          Room-Datenbank, DAOs, Mapper, Repository-Implementierungen
```

Details und die Begründung einzelner Entscheidungen stehen in [`docs/adr/`](docs/adr/).

## Mitmachen

Pull Requests sind willkommen. Wie das Projekt arbeitet – Branch-Strategie, Commit-
Konvention, Code-Style, Testpflicht – steht in [CONTRIBUTING.md](CONTRIBUTING.md).

## Geplant

Die folgenden Funktionen sind bewusst noch nicht Teil von Version 1.0, die Datenstruktur
ist aber darauf vorbereitet:

- **Vorrats-Abgleich** – "das habe ich zuhause" mit den Zutaten abgleichen
- **Einkaufsliste** – deduplizierte, kategorisierte Liste aus den Treffern, teilbar
- **Mehrspieler-Swipe** – zwei Geräte über einen Freundschaftscode verbinden und nur die
  gemeinsamen Treffer anzeigen (lokal über QR-Code/Nearby, ohne kostenpflichtige Cloud)
- **Rezept-Historie** – Wiederholungen vermeiden, indem die App weiss, was zuletzt auf
  dem Tisch stand
- **Backup als JSON** – Export und Import der ganzen Sammlung, kein Vendor-Lock-in

## Lizenz

[MIT](LICENSE) – benutze, verändere und verteile Rezeptli, wie du möchtest.
