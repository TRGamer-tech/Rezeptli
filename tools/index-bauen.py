#!/usr/bin/env python3
"""Baut den Rezeptindex, den die App laedt.

Warum es das gibt: Ohne Index muss jedes Geraet die Sitemaps der Quellen selbst
holen - bei einem Verzeichnis mit Unterverzeichnissen sind das schnell hundert
Abrufe, und zwar bei jedem Menschen, der die App installiert. Einmal zentral
gebaut sind es hundert Abrufe pro Tag fuer alle zusammen.

Das Ergebnis ist eine Datei je Quelle: `<quelle>.tsv.gz`, eine Zeile pro Rezept,
Adresse und Titel durch einen Tabulator getrennt. Dazu eine `manifest.json` mit
Baudatum und Anzahl - daran erkennt die App, ob sich ein neuer Abruf lohnt.

Die Quellenliste wird aus dem Kotlin-Katalog gelesen, damit sie nicht zweimal
gepflegt werden muss.
"""

import gzip
import io
import json
import random
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

WURZEL = Path(__file__).resolve().parent.parent
KATALOG = WURZEL / "app/src/main/java/ch/rezeptli/app/data/web/RecipeSource.kt"
AUSGABE = WURZEL / "build/index"

UA = "Rezeptli-Index/1.0 (+https://github.com/TRGamer-tech/Rezeptli)"

MAX_UNTERVERZEICHNISSE = 12
MAX_TIEFE = 2

# So viele Rezepte je Quelle kommen in die Stapeldatei. Der Wischstapel braucht
# keine 320'000 Adressen - er zeigt ein paar Dutzend Karten. Frueher lud die App
# das ganze Verzeichnis aller Quellen, bevor die erste Karte erschien; das waren
# zweistellige Megabytes und Minuten am Handy.
STAPEL_JE_QUELLE = 400

LOC = re.compile(r"<loc>\s*([^<\s]+)\s*</loc>", re.I)
# Viele Sitemaps nennen zu jedem Eintrag ein Bild. Wo das so ist, kostet ein Foto
# auf der Wischkarte nichts extra - sonst muesste die App jede Seite einzeln laden.
URL_BLOCK = re.compile(r"<url>(.*?)</url>", re.I | re.S)
IMAGE_LOC = re.compile(r"<image:loc>\s*([^<\s]+)\s*</image:loc>", re.I)
PAGE_SUFFIX = re.compile(r"\.(html?|aspx)$", re.I)
TRAILING_ID = re.compile(r"-\d+$")
TRAILING_NOISE = re.compile(r"-(rezept|recipe|recette|ricetta|fid)$", re.I)


def quellen() -> list[dict]:
    """Liest id, Adressmuster, Sitemaps und Crawl-Delay aus dem Kotlin-Katalog."""
    text = KATALOG.read_text(encoding="utf-8")
    gefunden = []

    for block in re.findall(r"RecipeSource\((.*?)\n    \)", text, re.S):
        kennung = re.search(r'id\s*=\s*"([^"]+)"', block)
        muster = re.search(r'urlPattern\s*=\s*Regex\("((?:[^"\\]|\\.)*)"', block)
        sitemaps = re.findall(r'"(https://[^"]+)"', block)
        verzoegerung = re.search(r"minRequestIntervalMs\s*=\s*([\d_]+)", block)

        if kennung is None or muster is None:
            continue
        # Nur Sitemap-Adressen zaehlen, nicht die Startseite.
        sitemaps = [s for s in sitemaps if "sitemap" in s.lower() or s.endswith(".xml")]
        if not sitemaps:
            continue

        gefunden.append(
            {
                "id": kennung.group(1),
                "muster": re.compile(muster.group(1).replace("\\\\", "\\"), re.I),
                "sitemaps": sitemaps,
                "pause": int((verzoegerung.group(1) if verzoegerung else "0").replace("_", "")) / 1000,
            },
        )
    return gefunden


def laden(url: str, timeout: int = 60) -> str:
    anfrage = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(anfrage, timeout=timeout) as antwort:
        roh = antwort.read()
    if roh[:2] == b"\x1f\x8b":
        roh = gzip.GzipFile(fileobj=io.BytesIO(roh)).read()
    return roh.decode("utf-8", "replace")


def titel_aus_adresse(url: str) -> str:
    teil = url.rstrip("/").rsplit("/", 1)[-1].split("?")[0]
    teil = PAGE_SUFFIX.sub("", teil)
    teil = TRAILING_ID.sub("", teil)
    teil = TRAILING_NOISE.sub("", teil)

    woerter = [w for w in re.split(r"[-_]", teil) if w and not w.isdigit()]
    return " ".join(w[:1].upper() + w[1:] for w in woerter) or url


def eintraege(quelle: dict) -> list[tuple[str, str, str]]:
    """Alle Rezeptadressen einer Quelle, mit Titel und - falls vorhanden - Bild."""
    gesammelt: dict[str, tuple[str, str]] = {}

    def verarbeite(url: str, tiefe: int) -> None:
        if tiefe > MAX_TIEFE:
            return
        try:
            xml = laden(url)
        except Exception as fehler:
            print(f"    {url}: {str(fehler)[:70]}", file=sys.stderr)
            return

        adressen = LOC.findall(xml)
        if "<sitemapindex" in xml.lower():
            kinder = [a for a in adressen if quelle["muster"].search(a)] or adressen
            for kind in kinder[:MAX_UNTERVERZEICHNISSE]:
                if quelle["pause"]:
                    time.sleep(quelle["pause"])
                verarbeite(kind, tiefe + 1)
            return

        # Erst blockweise lesen, damit Bild und Adresse zusammenbleiben.
        for block in URL_BLOCK.findall(xml):
            treffer = LOC.search(block)
            if treffer is None:
                continue
            adresse = treffer.group(1)
            if not quelle["muster"].search(adresse):
                continue
            bild = IMAGE_LOC.search(block)
            gesammelt.setdefault(
                adresse, (titel_aus_adresse(adresse), bild.group(1) if bild else ""),
            )

        # Sitemaps ohne <url>-Bloecke (selten, aber es gibt sie).
        if not gesammelt:
            for adresse in adressen:
                if quelle["muster"].search(adresse):
                    gesammelt.setdefault(adresse, (titel_aus_adresse(adresse), ""))

    for sitemap in quelle["sitemaps"]:
        verarbeite(sitemap, 0)

    return sorted((adresse, titel, bild) for adresse, (titel, bild) in gesammelt.items())


def stapel_bauen(manifest: dict) -> None:
    """Schreibt eine kleine Auswahl fuer den Wischstapel.

    Bevorzugt werden Eintraege mit Bild: Eine Karte ohne Foto muss die App sonst
    selbst nachladen, und das dauert pro Karte rund eine Sekunde. Wo eine Quelle
    keine Bilder nennt, kommen Eintraege ohne Bild dazu - lieber eine Karte, die
    ihr Foto nachlaedt, als eine Quelle, die im Stapel gar nicht vorkommt.

    Gezogen wird mit festem Startwert, damit derselbe Index dieselbe Datei ergibt.
    """
    zufall = random.Random(1)
    zeilen: list[str] = []

    for kennung in sorted(manifest["quellen"]):
        quelldatei = AUSGABE / f"{kennung}.tsv.gz"
        if not quelldatei.exists():
            continue

        with gzip.GzipFile(quelldatei, "rb") as datei:
            alle = [z.split("\t") for z in datei.read().decode("utf-8").split("\n") if z]

        mit_bild = [t for t in alle if len(t) >= 3 and t[2]]
        ohne_bild = [t for t in alle if not (len(t) >= 3 and t[2])]

        gewaehlt = zufall.sample(mit_bild, min(STAPEL_JE_QUELLE, len(mit_bild)))
        fehlend = STAPEL_JE_QUELLE - len(gewaehlt)
        if fehlend > 0 and ohne_bild:
            gewaehlt += zufall.sample(ohne_bild, min(fehlend, len(ohne_bild)))

        for teile in gewaehlt:
            adresse = teile[0]
            titel = teile[1] if len(teile) > 1 else ""
            bild = teile[2] if len(teile) > 2 else ""
            zeilen.append(f"{adresse}\t{titel}\t{bild}\t{kennung}")

        manifest["quellen"][kennung]["imStapel"] = len(gewaehlt)

    zufall.shuffle(zeilen)
    ziel = AUSGABE / "stapel.tsv.gz"
    with gzip.GzipFile(ziel, "wb", mtime=0) as datei:
        datei.write("\n".join(zeilen).encode("utf-8"))

    manifest["stapel"] = {"eintraege": len(zeilen), "groesseBytes": ziel.stat().st_size}
    print(f"\nStapel: {len(zeilen)} Rezepte, {ziel.stat().st_size // 1024} KB")


def main() -> int:
    AUSGABE.mkdir(parents=True, exist_ok=True)
    manifest = {"gebautAm": int(time.time() * 1000), "quellen": {}}
    fehlgeschlagen = []

    for quelle in quellen():
        kennung = quelle["id"]
        print(f"{kennung} ...", flush=True)
        gefunden = eintraege(quelle)

        if not gefunden:
            print(f"  keine Eintraege - Quelle wird uebersprungen", file=sys.stderr)
            fehlgeschlagen.append(kennung)
            continue

        ziel = AUSGABE / f"{kennung}.tsv.gz"
        # mtime auf 0, damit dieselbe Eingabe dieselbe Datei ergibt.
        with gzip.GzipFile(ziel, "wb", mtime=0) as datei:
            # Adresse, Titel, Bild - durch Tabulatoren getrennt. Fehlt das Bild,
            # bleibt die dritte Spalte leer; aeltere Dateien ohne sie bleiben lesbar.
            inhalt = "\n".join(f"{adresse}\t{titel}\t{bild}" for adresse, titel, bild in gefunden)
            datei.write(inhalt.encode("utf-8"))

        mit_bild = sum(1 for _, _, bild in gefunden if bild)
        manifest["quellen"][kennung] = {
            "eintraege": len(gefunden),
            "mitBild": mit_bild,
            "groesseBytes": ziel.stat().st_size,
        }
        print(f"  {len(gefunden)} Eintraege ({mit_bild} mit Bild), "
              f"{ziel.stat().st_size // 1024} KB")

    stapel_bauen(manifest)

    (AUSGABE / "manifest.json").write_text(
        json.dumps(manifest, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )

    gesamt = sum(e["eintraege"] for e in manifest["quellen"].values())
    print(f"\n{len(manifest['quellen'])} Quellen, {gesamt} Rezepte insgesamt.")
    if fehlgeschlagen:
        print(f"Ohne Eintraege: {', '.join(fehlgeschlagen)}", file=sys.stderr)

    # Ein Ausfall einzelner Quellen darf den Index nicht verhindern - der alte
    # bleibt dann fuer diese Quelle einfach stehen.
    return 0 if manifest["quellen"] else 1


if __name__ == "__main__":
    sys.exit(main())
