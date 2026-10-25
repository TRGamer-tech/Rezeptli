# Rezeptli

**Was koche ich diese Woche?** Du sagst Rezeptli, wo du einkaufst, für wie viele du kochst,
an welchen Tagen, was es kosten darf, wonach dir ist und was du nicht isst oder nicht
hast. Daraus baut die App einen Wochenplan mit Rezepten aus dem Netz und deiner Sammlung –
samt Einkaufsliste mit Packungen und geschätztem Preis. Wenn dir ein Gericht nicht passt,
tauschst du es mit einem Tipp; wenn ihr zu zweit seid, wischt ihr gemeinsam und legt die
Treffer auf freie Tage der Woche.

Rezeptli ist eine native Android-App ohne Konto, ohne Werbung, ohne Tracking – und
quelloffen unter der MIT-Lizenz. Deine Sammlung liegt auf deinem Gerät; ins Internet
geht die App nur für zwei Dinge, die ohne es nicht gehen: Rezepte im Web suchen und
gemeinsam mit jemandem entscheiden. Beides ist freiwillig, beides steht unten unter
[Datenschutz](#datenschutz).

## Funktionen

- **Einrichtung in acht Fragen** – Postleitzahl, Laden, Personen, Kochtage, Wochenbudget,
  Stimmung (bis zu drei), Ernährung samt Unverträglichkeiten und Küchengeräte. Alles
  vorbelegt und überspringbar; später im Tab *Vorlieben* änderbar.
- **Wochenplan** – ein Abendessen pro Kochtag. Ernährung, Unverträglichkeiten und Geräte
  schliessen Rezepte hart aus, Stimmungen gewichten nur. Dazu Kosten gegen Budget, eine
  Mahlzeit lässt sich mit einem Tipp tauschen, der ganze Plan neu bauen.
- **Einkaufsliste mit Packungen** – Bedarf in der Menge des Rezepts, darunter die Packung,
  die man kauft, und der geschätzte Preis. Abgehaktes sinkt ans Ende der Gruppe; die
  Liste lässt sich kopieren und teilen und um eigene Posten ergänzen.
- **Zu zweit wischen** – beide wischen am eigenen Handy durch Rezeptbilder, am Ende zeigt
  die App, worauf ihr beide Lust habt, und legt es in den Wochenplan.
- **Wischen allein** – Kartenstapel aus dem Rezeptverzeichnis, mit Undo
- **Rezepte verwalten** – anlegen, bearbeiten, löschen; mit Zutaten, Zubereitung, Foto,
  Tags und Zubereitungszeit; Text-Import per Copy-Paste mit regelbasiertem Parser
- **Rezepte im Web finden** – Suche bei elf Quellen aus der Schweiz, Deutschland,
  Österreich, Frankreich und Italien; ein Rezept aus dem Browser lässt sich über "Teilen"
  direkt an Rezeptli schicken
- **Kochmodus** – ein Arbeitsschritt pro Karte, mit den Zutaten dieses Schritts und Timer
- **Hell und dunkel** – folgt der Einstellung des Geräts

**Zu den Preisen:** Rezeptli fragt kein Geschäft ab. Die Preise sind Richtwerte für die
Schweiz, je nach Laden mit einem Faktor verschoben, und überall als Schätzung beschriftet.
Auch die Einstufung eines Rezepts (vegetarisch, enthält Gluten, braucht den Ofen …) ist
eine Heuristik aus den Zutaten – bei Allergien gilt die Zutatenliste der Quelle.

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

Es gibt kein Nutzerkonto, kein Analytics-SDK und keinen Absturzbericht-Dienst. Deine
Rezepte, deine Einkaufsliste, deine Swipe-Entscheidungen und deine Angaben aus dem
Onboarding bleiben auf dem Gerät.

Drei Dinge verlassen es trotzdem – jedes nur dann, wenn du es auslöst:

**Wenn du im Web suchst oder ein Rezept von einer Adresse lädst**, ruft die App die
betreffende Seite ab. Sie meldet sich unter eigenem Namen (`Rezeptli/1.0` mit Link auf
dieses Repository), liest die robots.txt der Seite und hält sich an deren Regeln samt
`Crawl-delay`. Deine Suchbegriffe gehen an niemanden: Gesucht wird in einem Verzeichnis,
das die App vorher geladen hat, auf dem Gerät.

**Beim Laden dieses Verzeichnisses** holt die App eine Datei von GitHub Pages. Sie
enthält Rezepttitel und Adressen der unterstützten Quellen und ist für alle dieselbe –
daraus lässt sich nicht ablesen, wonach du suchst.

**Wenn du zu zweit entscheidest**, gehen die Titel der Rezepte deiner Runde an unseren
Pairing-Dienst, damit die zweite Person weiss, worüber sie abstimmt. Zutaten und
Zubereitung bleiben hier. Der Dienst kennt keine Namen und keine Konten, die Kennung
deines Geräts ist eine Zufallszahl, und nach zwölf Stunden wird die Runde gelöscht.
Einzelheiten und der Quelltext stehen in [`pairing/`](pairing/README.md).

Ohne diese drei Funktionen läuft die App vollständig ohne Netz.

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
- **Einkaufsliste teilen** – die Liste als Text an andere Apps weitergeben
- **Rezept-Historie** – Wiederholungen vermeiden, indem die App weiss, was zuletzt auf
  dem Tisch stand
- **Backup als JSON** – Export und Import der ganzen Sammlung, kein Vendor-Lock-in

## Lizenz

[MIT](LICENSE) – benutze, verändere und verteile Rezeptli, wie du möchtest.
