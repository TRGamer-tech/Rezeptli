#!/usr/bin/env python3
"""Prueft, ob aus den gemeldeten Quellen wirklich ein Rezept herauskommt.

Der Index-Nachweis sagt nur, dass eine Quelle Adressen liefert. Ob auf diesen
Seiten auch maschinenlesbare Rezeptdaten stehen, ist eine andere Frage - genau
daran sind Gutekueche und Ichkoche gescheitert, obwohl beide im Verzeichnis
stehen.

Geprueft wird deshalb pro Quelle eine echte Rezeptseite: robots.txt, HTTP-Code,
JSON-LD, Microdata und das Bild.
"""
from __future__ import annotations

import gzip
import io
import json
import re
import sys
import urllib.error
import urllib.request
from urllib.parse import urljoin, urlsplit

UA = "Rezeptli/1.0 (Open-Source-Rezept-App; +https://github.com/TRGamer-tech/Rezeptli)"

LOC = re.compile(r"<loc>\s*([^<\s]+)\s*</loc>", re.I)
JSON_LD = re.compile(r'<script[^>]+type=["\']application/ld\+json["\'][^>]*>(.*?)</script>', re.S | re.I)
OG_IMAGE = re.compile(r'<meta[^>]+property=["\']og:image["\'][^>]+content=["\']([^"\']+)["\']', re.I)
MICRODATA = re.compile(r'itemprop=["\']recipeIngredient["\']', re.I)

QUELLEN = [
    ("gutekueche", "https://cdn.gutekueche.ch/sitemaps/sitemap.xml.gz", r"gutekueche\.ch/[^/]+-rezept-\d+"),
    ("ichkoche", "https://www.ichkoche.at/sitemap.xml", r"ichkoche\.at/.+-rezept-\d+"),
    ("cookaround", "https://www.cookaround.com/sitemap/ricette.xml", r"cookaround\.com/ricetta/"),
    ("swissmilk", "https://www.swissmilk.ch/de/sitemap.xml", r"swissmilk\.ch/de/rezepte-kochideen/rezepte/"),
    ("bettybossi", "https://www.bettybossi.ch/sitemap.xml", r"bettybossi\.ch/de/Rezept/"),
]


def hole(url: str, timeout: int = 45) -> bytes:
    anfrage = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(anfrage, timeout=timeout) as antwort:
        roh = antwort.read()
    if roh[:2] == b"\x1f\x8b":
        roh = gzip.GzipFile(fileobj=io.BytesIO(roh)).read()
    return roh


def robots_erlaubt(url: str) -> str:
    wurzel = f"{urlsplit(url).scheme}://{urlsplit(url).netloc}"
    try:
        text = hole(f"{wurzel}/robots.txt", timeout=20).decode("utf-8", "replace")
    except Exception as fehler:
        return f"robots.txt nicht lesbar ({str(fehler)[:40]}) - gilt als erlaubt"

    # Nur der allgemeine Abschnitt zaehlt hier.
    abschnitt, aktiv = [], False
    for zeile in text.splitlines():
        schlank = zeile.strip().lower()
        if schlank.startswith("user-agent:"):
            aktiv = schlank.split(":", 1)[1].strip() == "*"
        elif aktiv and schlank.startswith("disallow:"):
            abschnitt.append(zeile.split(":", 1)[1].strip())

    pfad = urlsplit(url).path
    treffer = [regel for regel in abschnitt if regel and pfad.startswith(regel)]
    return f"robots.txt verbietet {treffer[0]}" if treffer else "robots.txt erlaubt es"


def erste_adresse(sitemap: str, muster: str, tiefe: int = 0) -> str | None:
    if tiefe > 2:
        return None
    xml = hole(sitemap).decode("utf-8", "replace")
    adressen = LOC.findall(xml)

    if "<sitemapindex" in xml.lower():
        for kind in adressen[:6]:
            gefunden = erste_adresse(kind, muster, tiefe + 1)
            if gefunden:
                return gefunden
        return None

    for adresse in adressen:
        if re.search(muster, adresse, re.I):
            return adresse
    return None


def rezept_aus(html: str) -> dict | None:
    for block in JSON_LD.findall(html):
        try:
            daten = json.loads(block.strip())
        except json.JSONDecodeError:
            continue
        stapel = [daten]
        while stapel:
            knoten = stapel.pop()
            if isinstance(knoten, list):
                stapel.extend(knoten)
            elif isinstance(knoten, dict):
                typ = knoten.get("@type")
                typen = typ if isinstance(typ, list) else [typ]
                if "Recipe" in typen:
                    return knoten
                stapel.extend(v for v in knoten.values() if isinstance(v, (dict, list)))
    return None


def pruefe(kennung: str, sitemap: str, muster: str) -> None:
    print(f"\n{'=' * 62}\n{kennung}\n{'=' * 62}")
    try:
        adresse = erste_adresse(sitemap, muster)
    except Exception as fehler:
        print(f"  Sitemap nicht erreichbar: {str(fehler)[:70]}")
        return

    if adresse is None:
        print("  Keine passende Rezeptadresse im Verzeichnis gefunden")
        return

    print(f"  Beispielrezept: {adresse}")
    print(f"  {robots_erlaubt(adresse)}")

    try:
        html = hole(adresse).decode("utf-8", "replace")
    except urllib.error.HTTPError as fehler:
        print(f"  Seite: HTTP {fehler.code} - Rezept nicht abrufbar")
        return
    except Exception as fehler:
        print(f"  Seite nicht ladbar: {str(fehler)[:70]}")
        return

    print(f"  Seite: {len(html) // 1024} KB")

    rezept = rezept_aus(html)
    if rezept is None:
        zutaten = len(MICRODATA.findall(html))
        print(f"  KEIN JSON-LD-Recipe. Microdata-Zutaten: {zutaten}")
        print("  => Import wuerde scheitern" if zutaten == 0 else "  => nur ueber Microdata moeglich")
    else:
        zutaten = rezept.get("recipeIngredient") or []
        anleitung = rezept.get("recipeInstructions") or []
        print(f"  JSON-LD-Recipe: '{str(rezept.get('name'))[:50]}'")
        print(f"    Zutaten: {len(zutaten)}, Schritte: {len(anleitung) if isinstance(anleitung, list) else 1}")
        if not zutaten:
            print("    => Rezept ohne Zutaten - fuer die App unbrauchbar")

    treffer = OG_IMAGE.search(html)
    if treffer:
        bild = urljoin(adresse, treffer.group(1))
        try:
            anfrage = urllib.request.Request(bild, headers={"User-Agent": UA, "Referer": adresse})
            with urllib.request.urlopen(anfrage, timeout=30) as antwort:
                groesse = len(antwort.read())
            print(f"  og:image: {groesse // 1024} KB abrufbar - {bild[:70]}")
        except Exception as fehler:
            print(f"  og:image nicht abrufbar: {str(fehler)[:50]} - {bild[:60]}")
    else:
        print("  Kein og:image auf der Seite")


def main() -> int:
    for kennung, sitemap, muster in QUELLEN:
        try:
            pruefe(kennung, sitemap, muster)
        except Exception as fehler:
            print(f"  unerwartet: {str(fehler)[:80]}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
