# 0006 – Vom reinen Offline-Betrieb zu "online, wo es nötig ist"

Status: angenommen

## Ausgangslage

Rezeptli war als App gedacht, die das Gerät nie verlässt. Mit der Web-Suche und dem
gemeinsamen Wischen ist das nicht mehr haltbar: Rezepte anderer Leute liegen nun einmal
im Internet, und zwei Telefone können sich ohne einen Vermittler nicht verständigen.

Die Frage war nicht ob, sondern wie viel Server.

## Entscheidung

Zwei getrennte, möglichst kleine Server-Teile statt eines Backends:

**Ein statischer Index.** Ein täglicher GitHub-Actions-Auftrag liest die Sitemaps der
unterstützten Quellen und legt das Ergebnis als gepackte Datei je Quelle auf GitHub
Pages ab. Die App lädt daraus einen einzigen Abruf statt sich selbst durch Verzeichnis
und Unterverzeichnisse zu arbeiten.

**Ein Pairing-Dienst** (Cloudflare Worker mit D1) für gemeinsame Runden. Er kennt einen
Code, die Rezepttitel einer Runde, zufällige Kennungen und Ja/Nein-Stimmen. Sonst nichts.

Alles andere bleibt lokal: Rezepte, Einkaufsliste, Swipe-Entscheidungen, Profil.

## Warum nicht ein richtiges Backend

Ein Aggregationsserver hätte die Suche über Zutaten ermöglicht und Geräte synchronisiert.
Er hätte aber Konten gebraucht, laufende Kosten verursacht und jede Suchanfrage vom Gerät
weggetragen. Für die Frage "was koche ich heute" ist das zu viel Apparat.

Der statische Index bringt den entscheidenden Teil des Nutzens zum Preis von null Betrieb:
Die Anbieter werden einmal täglich für alle zusammen abgefragt statt einmal pro
Installation, und die erste Karte ist sofort da.

## Warum der Pairing-Dienst trotzdem echt sein muss

Zuerst war ein Austausch über QR-Codes ohne Server geplant. Das scheitert daran, dass
niemand mitbekommt, wann die andere Person beigetreten oder fertig ist - ein
Wartezustand ohne Rückmeldung ist kein Wartezustand, sondern ein Rätsel.

Cloudflare KV wäre naheliegend gewesen, weil seine Einträge von selbst verfallen. KV ist
aber nur nachgiebig konsistent: Ein Beitritt könnte bis zu einer Minute unsichtbar
bleiben. Genau der Fall, der hier zählt. Deshalb D1 - es ist streng konsistent, die
Datenmenge ist winzig, und das Aufräumen ist ein DELETE.

## Was das kostet

Die App braucht `INTERNET`. Für die Web-Suche und den gemeinsamen Modus braucht sie
tatsächlich eine Verbindung, und das sagt die Oberfläche deutlich, statt es als Fehler
zu tarnen. Rezepte anlegen, suchen, wischen, einkaufen und kochen geht weiterhin ohne
Netz.

Beim gemeinsamen Wischen verlassen Rezepttitel das Gerät. Das ist die einzige Stelle,
an der eigene Inhalte weggehen, und sie steht im README, im Pairing-README und vor dem
Start im Bildschirm selbst.

## Nachtrag: Wischen zuerst, und warum der Index eine zweite Datei bekam

Die App begann zunächst in der eigenen Sammlung - bei einer neuen Installation also im
Leeren, mit der Suche als einzigem Ausweg. Das stand quer zum eigentlichen Zweck der
App: Sie beantwortet "was koche ich heute", und darauf ist Wischen die Antwort, nicht
Suchen. Die App startet deshalb auf dem Wischstapel; eine untere Leiste hält Suche,
eigene Rezepte und Einkaufsliste einen Griff entfernt.

Der Stapel zieht aus dem Verzeichnis der Web-Quellen. Das brachte die App an genau die
Stelle zurück, die dieses ADR eigentlich löste: Vor der ersten Karte lud sie die
Verzeichnisse aller Quellen - über 300'000 Adressen, zweistellige Megabytes -, weil der
Index als Ganzes für die Suche gedacht war, nicht für einen Stapel von ein paar Dutzend
Karten. Der tägliche Auftrag schreibt seither zusätzlich eine kleine Stapeldatei
(`stapel.tsv.gz`): einige hundert Rezepte je Quelle, mit Vorrang für Einträge, die schon
ein Bild nennen. Die App lädt diese eine kleine Datei statt der vollständigen
Verzeichnisse, und der Rückfall auf den langen Weg bleibt bestehen, falls sie fehlt.

Eine Wischkarte ohne Foto ist nur eine leere Fläche mit Titel. Karten, deren Quelle im
Verzeichnis kein Bild nennt, holen es deshalb im Hintergrund von der Rezeptseite nach
und bleiben bis dahin unsichtbar; lässt sich gar keins finden, fällt die Karte aus dem
Stapel, statt für immer als Lücke liegen zu bleiben.
