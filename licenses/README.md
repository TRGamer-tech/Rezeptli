# Lizenzen mitgelieferter Dateien

Die App verwendete bis zum Redesign "Marktfrisch" Roboto Flex; die Webseite nutzt
Roboto Flex weiterhin (siehe unten).

## Fraunces (App)

`app/src/main/res/font/fraunces.ttf`

Copyright 2020 The Fraunces Project Authors (github.com/undercasetype/Fraunces),
lizenziert unter der SIL Open Font License 1.1 (siehe `Fraunces-OFL.txt`).

Die Datei ist die auf Latein reduzierte variable Schrift aus dem Fontsource-Paket
`@fontsource-variable/fraunces` 5.3.0 (`fraunces-latin-full-normal.woff2`), ohne
inhaltliche Aenderung von WOFF2 nach TrueType umgepackt (fontTools), weil Android
kein WOFF2 liest. Erhalten sind alle vier Achsen: `opsz`, `wght`, `SOFT`, `WONK`.
194 KB. Zeichen ausserhalb des lateinischen Bereichs (etwa Bruchzeichen wie "⅓")
zeichnet Android in der Systemschrift.

## Figtree (App)

`app/src/main/res/font/figtree_*.ttf` (Regular, Medium, SemiBold, Bold, ExtraBold)

Copyright 2022 The Figtree Project Authors (https://github.com/erikdkennedy/figtree),
lizenziert unter der SIL Open Font License 1.1 (siehe `Figtree-OFL.txt`).

Die statischen Schnitte stammen unveraendert aus dem npm-Paket
`@expo-google-fonts/figtree` 0.4.1 (dort aus Google Fonts), je rund 40 KB.

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
