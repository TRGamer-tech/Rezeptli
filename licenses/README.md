# Lizenzen mitgelieferter Dateien

## Roboto Flex

`app/src/main/res/font/roboto_flex.ttf`

Copyright 2017 The Roboto Flex Project Authors, lizenziert unter der
SIL Open Font License 1.1 (siehe `RobotoFlex-OFL.txt`).

Die Datei ist eine bearbeitete Fassung des Originals:

- Die Achsen `GRAD`, `XOPQ`, `YOPQ`, `XTRA`, `YTUC`, `YTLC`, `YTAS`, `YTDE`
  und `YTFI` sind auf ihrem Standardwert festgesetzt und damit entfernt.
  Erhalten bleiben `opsz`, `wght`, `wdth` und `slnt`.
- Der Zeichenvorrat ist auf Latein reduziert (inklusive Umlaute, franzoesischer
  und italienischer Akzente, Bruchzahlen und Euro-Zeichen).

Dadurch schrumpft die Datei von 1.79 MB auf 396 KB. Die OFL erlaubt diese
Bearbeitung ausdruecklich; der Schriftname bleibt unveraendert, weil die
Lizenz nur fuer die reservierten Namen eine Umbenennung verlangt - "Roboto
Flex" ist kein reservierter Name (die OFL-Datei nennt keinen).

## GSAP und ScrollTrigger (Webseite)

`site/vendor/gsap.min.js`, `site/vendor/ScrollTrigger.min.js`

GSAP 3.13.0, Copyright GreenSock, unveraendert aus dem npm-Paket `gsap`. Lizenz:
GreenSock Standard "no charge" License (https://gsap.com/standard-license).
Die Dateien gehoeren nicht zur App, nur zur Startseite.

## Bricolage Grotesque und Roboto Flex (Webseite)

`site/fonts/bricolage-grotesque-latin-wdth-normal.woff2`,
`site/fonts/roboto-flex-latin-wght-normal.woff2`

Copyright der jeweiligen Projektautoren, SIL Open Font License 1.1
(`BricolageGrotesque-OFL.txt`, `RobotoFlex-OFL.txt`). Es sind die auf Latein
reduzierten Variablen-Schriften aus den Fontsource-Paketen
(`@fontsource-variable/bricolage-grotesque` 5.3.0, `@fontsource-variable/roboto-flex`
5.3.0), unveraendert uebernommen.
