#!/usr/bin/env python3
"""Prueft den frisch gebauten Index Quelle fuer Quelle.

Ein gruener Build-Schritt heisst nur, dass das Skript durchgelaufen ist. Er sagt
nicht, dass jede Quelle auch wirklich Rezepte geliefert hat - genau daran ist
Gutekueche lange unbemerkt vorbeigelaufen: falsche Adresse, 404, null Eintraege,
Build trotzdem gruen.

Deshalb hier die Gegenprobe: Jede Quelle, die eine Sitemap nennt, muss im
Manifest mit Eintraegen stehen. Fehlt eine, endet dieser Schritt rot und nennt
sie beim Namen.
"""
from __future__ import annotations

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).parent))

from importlib import import_module

bauen = import_module("index-bauen")

MANIFEST = pathlib.Path("build/index/manifest.json")


def main() -> int:
    if not MANIFEST.exists():
        print(f"::error::{MANIFEST} fehlt - der Index wurde nicht gebaut")
        return 1

    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    gebaut = manifest.get("quellen", {})
    erwartet = [q for q in bauen.quellen() if q["sitemaps"]]

    breite = max(len(q["id"]) for q in erwartet)
    leer: list[str] = []

    print(f"{'Quelle'.ljust(breite)}  Eintraege  mit Bild")
    for quelle in sorted(erwartet, key=lambda q: q["id"]):
        eintrag = gebaut.get(quelle["id"])
        anzahl = eintrag["eintraege"] if eintrag else 0
        bilder = eintrag.get("mitBild", 0) if eintrag else 0
        marke = "" if anzahl else "   <- nichts geliefert"
        print(f"{quelle['id'].ljust(breite)}  {anzahl:9d}  {bilder:8d}{marke}")
        if not anzahl:
            leer.append(quelle["id"])

    gesamt = sum(e["eintraege"] for e in gebaut.values())
    print(f"\n{len(gebaut)} von {len(erwartet)} Quellen mit Inhalt, {gesamt} Rezepte insgesamt.")

    if leer:
        for kennung in leer:
            print(f"::error::Quelle '{kennung}' hat keine Rezepte geliefert")
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
