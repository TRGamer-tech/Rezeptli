# Rezeptli-Pairing

Der einzige Server, den Rezeptli kennt. Er tut genau eine Sache: zwei Telefonen
erlauben, dieselbe Swipe-Runde zu teilen.

## Was er speichert

- einen kurzen Code (6 Zeichen, ohne I, O, 0 und 1 - die verwechselt man beim Abtippen)
- die Karten der Runde: Titel, Quelladresse und Bildlink, sofern vorhanden
- zufaellige Kennungen der Beteiligten, die das jeweilige Geraet erzeugt
- wer welche Karte mit Ja oder Nein bewertet hat
- was die Runde sucht: wie viele Gerichte, Essen oder Getraenke
- was in dieser Runde nicht auf den Tisch darf: Ernaehrungsform, Unvertraeglichkeiten
  und Abneigungen beider Personen, **vereinigt** - ohne Vermerk, von wem welche Angabe stammt
- Zutaten und Zubereitung eines **selbst erfassten** Rezepts, sobald beide es mochten -
  und nur dann (siehe unten)

## Was er nicht speichert

Keine Konten, keine Namen, keine Fotos, keine Einkaufslisten, keine Adressen,
keine Geraetekennungen. Zutaten und Zubereitung speichert er nicht - mit der einen
Ausnahme oben. Er kennt keine E-Mail und kann niemanden wiedererkennen: Die Kennung
einer Person gilt nur innerhalb einer Sitzung.

Eine Sitzung verfaellt nach zwoelf Stunden. Ein taeglicher Auftrag loescht, was
abgelaufen ist, und wer die Runde eroeffnet hat, kann sie jederzeit sofort
loeschen. Dann verschwinden Karten, Beteiligte, Stimmen und hinterlegte
Rezeptinhalte mit - dafuer sorgen die Fremdschluessel, und genau das prueft ein Test.

## Warum Titel und Angaben ueberhaupt den Server sehen

Rezept-Kennungen sind auf jedem Geraet andere. Damit zwei Personen ueber
dieselben Rezepte abstimmen koennen, muss die Runde selbst geteilt werden - der
Titel ist das Mindeste, damit die zweite Person weiss, worueber sie entscheidet.
Seit Fassung 2 kommen die Karten zuerst aus den **gespeicherten Rezepten beider**;
deren Titel gehen also fuer die Dauer der Runde an den Dienst.

Dasselbe gilt fuer das, was nicht auf den Tisch darf: Soll "kein Fisch" der einen
Person auch fuer die andere gelten, muss das zweite Telefon davon erfahren. Eine
Unvertraeglichkeit ist eine Angabe zur Gesundheit - deshalb steht sie ohne Namen,
ohne Zuordnung zu einer Person und nur bis zum Ablauf der Sitzung dort.

Das ist eine bewusste Abweichung vom sonstigen Grundsatz, dass nichts das Geraet
verlaesst. Sie gilt nur fuer eine laufende gemeinsame Runde, und die App sagt es
vor dem Start deutlich.

## Warum der Inhalt eines selbst erfassten Treffers den Server sieht

Ein Rezept aus dem Web holt sich die andere Person selbst von seiner Adresse. Ein
selbst erfasstes hat keine: Es steht nur auf dem Telefon, auf dem es eingetippt
wurde. Beide konnten sich darauf einigen - aber nur eine Person konnte es kochen.

Deshalb hinterlegt die App Zutaten, Zubereitung, Portionen, Zeit und Schlagworte
eines solchen Rezepts beim Dienst, **sobald es ein gemeinsamer Treffer ist**. Die
Grenzen zieht die Datenbank in derselben Anweisung, die speichert:

- nur fuer Karten ohne Quelladresse,
- nur wenn beide Ja gesagt haben,
- nur vom Geraet, das die Karte beigesteuert hat,
- nur wenn nicht mehr gewischt wird (`frage` oder `beendet`),
- hoechstens 32 KB, ohne Foto.

Was nicht gemocht wurde, verlaesst das Telefon also nie. Der Inhalt haengt an der
Sitzung und wird mit ihr geloescht.

## Ablauf (Fassung 2)

| Zustand | Was passiert |
|---|---|
| `lobby` | Eine Person eroeffnet und bekommt sofort den Code. Die zweite tritt bei. Wer eroeffnet hat, legt Anzahl, Art (Essen, Getraenke, beides) und Quelle fest; die andere Person sieht es mit. |
| `sammeln` | Beide Geraete steuern Karten bei, die zu den vereinigten Angaben passen. Der Dienst mischt sie abwechselnd zu einem Stapel; ein Rezept, das beide kennen, liegt nur einmal darin. Fehlt ein Beitrag, geht es eine Minute nach dem ersten ohne ihn weiter (`fristRestMs` zaehlt herunter). |
| `laeuft` | Beide wischen denselben Stapel in derselben Reihenfolge. Jede Stimme kommt sofort an. Sind genug Treffer da, endet die Runde fuer beide. |
| `frage` | Der Stapel ist durch, das Ziel nicht erreicht. Wer eroeffnet hat, entscheidet: mit neuen Rezepten weiter oder aufhoeren. |
| `beendet` | Die Treffer stehen fest (`endeGrund`: `ziel`, `leer`, `gastgeber` oder `verlassen`). |

Hat niemand ein passendes gespeichertes Rezept, geht es ohne Rueckfrage mit neuen
weiter - es gibt dann nichts, wozwischen zu waehlen waere.

Welche Rezepte Treffer sind, nennt der Dienst erst, wenn nicht mehr gewischt wird.
Waehrend der Runde gibt es nur die Zahl: Am einzelnen Treffer liesse sich sonst
ablesen, wie die andere Person gerade gewischt hat.

## Schnittstelle

### Fassung 2

| Methode | Pfad | Zweck |
|---|---|---|
| `POST` | `/v2/sitzung` | Runde eroeffnen, liefert den Code |
| `POST` | `/v2/sitzung/{code}/beitreten` | Beitreten oder zurueckkehren |
| `POST` | `/v2/sitzung/{code}/einstellungen` | Anzahl, Art, Quelle - nur wer eroeffnet hat, nur in der Lobby |
| `POST` | `/v2/sitzung/{code}/start` | Lobby schliessen - nur wer eroeffnet hat, nur zu zweit |
| `POST` | `/v2/sitzung/{code}/beitrag` | Karten zur laufenden Runde beisteuern |
| `POST` | `/v2/sitzung/{code}/stimmen` | Stimmen senden; `mag: null` nimmt eine zurueck |
| `POST` | `/v2/sitzung/{code}/weiter` | Nach der Frage mit neuen Rezepten weitermachen |
| `POST` | `/v2/sitzung/{code}/beenden` | Aufhoeren und behalten, was gefunden wurde |
| `POST` | `/v2/sitzung/{code}/schliessen` | Sofort loeschen - nur wer eroeffnet hat |
| `POST` | `/v2/sitzung/{code}/inhalt` | Zutaten und Zubereitung eines eigenen, selbst erfassten Treffers hinterlegen |
| `GET`  | `/v2/sitzung/{code}/inhalt?teilnehmer=…&rezept=…` | Den hinterlegten Inhalt eines Treffers holen; `inhalt: null`, solange noch nichts da ist |
| `GET`  | `/v2/sitzung/{code}?teilnehmer=…&stand=…&runde=…` | Stand; nur fuer Beteiligte |

Jede Antwort ist der aktuelle Stand aus Sicht der anfragenden Person. Der Stapel
haengt nur dran, wenn das Geraet ihn noch nicht hat (`runde` bzw. `kenntRunde`
weicht ab); hat sich seit `stand` nichts getan, kommt nur `unveraendert`.

### Fassung 1 (fuer Apps, die noch nicht aktualisiert sind)

| Methode | Pfad | Zweck |
|---|---|---|
| `POST` | `/sitzung` | Runde eroeffnen, liefert den Code |
| `POST` | `/sitzung/{code}/beitreten` | Beitreten, liefert die Rezepte |
| `POST` | `/sitzung/{code}/stimmen` | Stimmen senden, wiederholbar |
| `GET`  | `/sitzung/{code}` | Stand und - wenn alle fertig sind - die Treffer |
| `POST` | `/sitzung/{code}/schliessen` | Sofort beenden, nur der Gastgeber |
| `GET`  | `/gesundheit` | Lebt der Dienst? Welche Fassungen spricht er? |

Trifft eine App auf eine Runde der anderen Fassung, antwortet der Dienst mit
`426` - "bitte aktualisieren" statt einer Runde, die nur halb funktioniert.

## Warum eine Runde nicht ewig auf Karten wartet

Schickte ein Telefon seine Karten nie - Funkloch, App geschlossen, Akku leer -,
blieb die Runde im Zustand `sammeln` stehen, bis jemand sie von Hand beendete.
Jetzt laeuft mit dem ersten Beitrag eine Frist von einer Minute. Ist sie um, baut
der Dienst den Stapel aus dem, was da ist; kam gar nichts Passendes, geht es wie
sonst mit neuen Rezepten weiter.

Der Dienst hat dafuer keinen Wecker. Er prueft die Frist, wenn ein Telefon nach
dem Stand fragt - und das tun beide ohnehin alle paar Sekunden, solange sie warten.
Wie bei allen Uebergaengen steht die Bedingung im `WHERE` einer einzigen Anweisung:
Fragen beide im selben Augenblick, baut nur eine Anfrage den Stapel.

## Warum der Dienst entscheidet, wann Schluss ist

Die Runde soll aufhoeren, sobald genug Treffer da sind. Das kann kein einzelnes
Telefon wissen - es kennt nur die eigenen Stimmen. Also zaehlt der Dienst.

Jede dieser Entscheidungen (Stapel steht, Ziel erreicht, Stapel durch) ist eine
einzige SQL-Anweisung mit ihrer Bedingung im `WHERE` und laeuft im selben Stapel
wie die Aenderung, die sie ausloest. D1 fuehrt einen Stapel am Stueck aus; zwei
Telefone, die im selben Augenblick senden, koennen den Zustand so nicht in
verschiedene Richtungen schieben. Die Anweisungen stehen in `src/queries.ts`.

## Entwickeln

```bash
cd pairing
npm install
npm run migrate:lokal   # Schema in die lokale Datenbank
npm run dev             # Dienst lokal starten
npm test                # beide Pruefungen
```

`tests/treffer_test.py` laeuft gegen SQLite mit dem echten Schema und liest die
Treffer-Abfrage woertlich aus `src/queries.ts`. Damit kann die geprueefte Abfrage
nicht von der ausgefuehrten abweichen.

`tests/ablauf_test.mjs` startet den Dienst lokal in derselben Laufzeit wie bei
Cloudflare (workerd ueber Miniflare, echte D1-Datenbank, echte Migrationen) und
spielt mit zwei gedachten Telefonen ganze Runden durch: Lobby, vereinigte
Angaben, gleicher Stapel fuer beide, Zuruecknehmen, Ende beim Ziel, die Frage
nach dem Weitermachen, die Frist fuer fehlende Beitraege, die Uebergabe eines
selbst erfassten Treffers samt allem, was dabei abgewiesen werden muss - und dass
die alte Fassung weiterlaeuft.
