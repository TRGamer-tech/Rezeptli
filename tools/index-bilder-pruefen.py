#!/usr/bin/env python3
"""Klaert, ob die Sitemaps der Quellen Bildadressen mitliefern.

Davon haengt ab, wie der Wisch-Stapel gebaut wird: Stehen die Bilder schon im
Verzeichnis, kostet eine Karte mit Foto nichts extra. Sonst muss die App die
Rezeptseite jeder Karte einzeln laden - und dann ist die Frage, wie schnell das
geht, bevor es sich wieder wie die alte langsame Suche anfuehlt.
"""

import gzip
import io
import re
import sys
import time
import urllib.request

UA = "Rezeptli/1.0 (Open-Source-Rezept-App; +https://github.com/TRGamer-tech/Rezeptli)"

QUELLEN = [
    ("swissmilk", "https://www.swissmilk.ch/de/sitemap.xml", r"rezepte-kochideen/rezepte/"),
    ("gutekueche", "https://cdn.gutekueche.ch/sitemaps/sitemap.xml.gz", r"-rezept-\d+"),
    ("bettybossi", "https://www.bettybossi.ch/sitemap.xml", r"/rezepte/rezept/"),
    ("einfachkochen", "https://www.einfachkochen.de/sitemap.xml", r"/rezepte/"),
    ("ichkoche", "https://www.ichkoche.at/sitemap.xml", r"-rezept-\d+"),
    ("kochrezepte", "https://www.kochrezepte.at/sitemap.xml", r"-rezept-\d+"),
    ("cuisineaz", "https://www.cuisineaz.com/xml/sitemap.xml", r"/recettes/.*\.aspx"),
    ("ptitchef", "https://www.ptitchef.com/sitemap.xml", r"/recettes/"),
    ("cookaround", "https://www.cookaround.com/sitemap/ricette.xml", r"/ricetta/"),
]


def hole(url, timeout=60):
    anfrage = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(anfrage, timeout=timeout) as antwort:
        roh = antwort.read()
    if roh[:2] == b"\x1f\x8b":
        roh = gzip.GzipFile(fileobj=io.BytesIO(roh)).read()
    return roh.decode("utf-8", "replace")


def main():
    print("Frage 1: Stehen Bilder schon im Verzeichnis?\n")
    beispielseiten = []

    for kennung, sitemap, muster in QUELLEN:
        try:
            xml = hole(sitemap)
        except Exception as fehler:
            print(f"  {kennung:<15} Sitemap nicht erreichbar: {str(fehler)[:60]}")
            continue

        # Ein Sitemap-Index verweist weiter; dann das erste Unterverzeichnis nehmen.
        if "<sitemapindex" in xml.lower():
            kinder = re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", xml)
            passend = [k for k in kinder if re.search(muster, k, re.I)] or kinder
            if not passend:
                print(f"  {kennung:<15} leerer Sitemap-Index")
                continue
            try:
                xml = hole(passend[0])
            except Exception as fehler:
                print(f"  {kennung:<15} Unterverzeichnis nicht erreichbar: {str(fehler)[:50]}")
                continue

        bilder = len(re.findall(r"<image:loc>", xml, re.I))
        adressen = re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", xml)
        rezepte = [a for a in adressen if re.search(muster, a, re.I)]
        print(f"  {kennung:<15} {len(rezepte):>6} Rezepte, {bilder:>6} <image:loc>"
              f"  {'JA' if bilder else 'nein'}")
        if rezepte:
            beispielseiten.append((kennung, rezepte[len(rezepte) // 2]))

    print("\nFrage 2: Wie lange dauert das Laden einer einzelnen Rezeptseite?\n")
    zeiten = []
    for kennung, adresse in beispielseiten:
        try:
            start = time.monotonic()
            html = hole(adresse, timeout=30)
            dauer = time.monotonic() - start
            zeiten.append(dauer)
            hat_bild = bool(re.search(r'property="og:image"', html))
            print(f"  {kennung:<15} {dauer * 1000:>6.0f} ms, {len(html) // 1024:>4} KB, "
                  f"og:image {'ja' if hat_bild else 'nein'}")
        except Exception as fehler:
            print(f"  {kennung:<15} Fehler: {str(fehler)[:60]}")

    if zeiten:
        zeiten.sort()
        print(f"\n  Median {zeiten[len(zeiten) // 2] * 1000:.0f} ms, "
              f"langsamste {zeiten[-1] * 1000:.0f} ms")
        print("\n  Bedeutung: So lange braucht die App pro Karte, wenn sie das Bild")
        print("  selbst holen muss. Drei Karten im Voraus zu laden waere damit")
        print(f"  rund {zeiten[len(zeiten) // 2] * 3:.1f} s Vorlauf.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
