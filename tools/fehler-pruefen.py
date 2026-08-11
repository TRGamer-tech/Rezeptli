#!/usr/bin/env python3
"""Prueft drei gemeldete Fehler an den echten Diensten.

Laeuft in der CI, weil die Entwicklungsumgebung weder die Rezeptseiten noch
den Pairing-Dienst erreicht.

1. kochrezepte.at: Wird ein Rezeptbild gefunden, und laesst es sich laden?
2. gutekueche.ch: Funktioniert die Quelle ueberhaupt noch?
3. Pairing-Dienst: Laeuft eine ganze Runde durch - anlegen, beitreten,
   abstimmen, Treffer abholen, schliessen?
"""

import gzip
import io
import json
import re
import sys
import urllib.error
import urllib.request

UA = "Rezeptli/1.0 (Open-Source-Rezept-App; +https://github.com/TRGamer-tech/Rezeptli)"
PAIRING = "https://rezeptli-pairing.rezeptli.workers.dev"


def hole(url, daten=None, methode=None, timeout=45):
    kopf = {"User-Agent": UA}
    if daten is not None:
        kopf["Content-Type"] = "application/json"
        daten = json.dumps(daten).encode()
    anfrage = urllib.request.Request(url, data=daten, headers=kopf, method=methode)
    with urllib.request.urlopen(anfrage, timeout=timeout) as antwort:
        roh = antwort.read()
        status = antwort.status
    if roh[:2] == b"\x1f\x8b":
        roh = gzip.GzipFile(fileobj=io.BytesIO(roh)).read()
    return status, roh


def json_ld(html):
    """Alle JSON-LD-Bloecke einer Seite."""
    gefunden = []
    for block in re.findall(
        r"<script[^>]*application/ld\+json[^>]*>(.*?)</script>", html, re.S,
    ):
        try:
            gefunden.append(json.loads(block))
        except Exception:
            pass
    return gefunden


def finde_rezept(knoten):
    if isinstance(knoten, list):
        for k in knoten:
            t = finde_rezept(k)
            if t:
                return t
    elif isinstance(knoten, dict):
        if "Recipe" in str(knoten.get("@type", "")):
            return knoten
        for schluessel in ("@graph", "mainEntity"):
            if schluessel in knoten:
                t = finde_rezept(knoten[schluessel])
                if t:
                    return t
    return None


def erste_rezeptadresse(sitemap, muster):
    _, xml = hole(sitemap)
    adressen = re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", xml.decode("utf-8", "replace"))
    passende = [a for a in adressen if re.search(muster, a, re.I)]
    return passende[len(passende) // 2] if passende else None


def pruefe_bild(quelle, sitemap, muster):
    print(f"\n{'=' * 62}\n{quelle}\n{'=' * 62}")
    try:
        adresse = erste_rezeptadresse(sitemap, muster)
    except Exception as fehler:
        print(f"  Sitemap nicht erreichbar: {str(fehler)[:90]}")
        return
    if not adresse:
        print("  Keine Rezeptadresse im Verzeichnis gefunden")
        return
    print(f"  Beispielrezept: {adresse}")

    try:
        status, roh = hole(adresse)
    except Exception as fehler:
        print(f"  Seite nicht erreichbar: {str(fehler)[:90]}")
        return
    html = roh.decode("utf-8", "replace")
    print(f"  Seite: HTTP {status}, {len(html) // 1024} KB")

    bloecke = json_ld(html)
    typen = set()
    for b in bloecke:
        def sammle(k):
            if isinstance(k, list):
                for x in k:
                    sammle(x)
            elif isinstance(k, dict):
                if k.get("@type"):
                    typen.add(str(k["@type"]))
                for s in ("@graph", "mainEntity", "itemListElement"):
                    if s in k:
                        sammle(k[s])
        sammle(b)
    print(f"  JSON-LD-Typen: {sorted(typen) or 'keine'}")
    microdata = html.count('itemprop="recipeIngredient"')
    print(f"  Microdata-Zutaten: {microdata}")

    rezept = None
    for b in bloecke:
        rezept = rezept or finde_rezept(b)

    if rezept is None:
        print("  KEIN Recipe-Objekt - Quelle liefert keine strukturierten Rezeptdaten")
        # Trotzdem schauen, was als Bild im HTML steht.
        og = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', html)
        print(f"  og:image: {og.group(1) if og else 'keins'}")
        return

    print(f"  Zutaten: {len(rezept.get('recipeIngredient') or [])}")
    bild = rezept.get("image")
    print(f"  image-Feld ({type(bild).__name__}): {json.dumps(bild, ensure_ascii=False)[:200]}")

    # Genau so, wie der Extraktor der App es tut: erst String, dann Liste, dann url.
    kandidat = None
    if isinstance(bild, str):
        kandidat = bild
    elif isinstance(bild, list) and bild:
        erstes = bild[0]
        kandidat = erstes if isinstance(erstes, str) else (erstes or {}).get("url")
    elif isinstance(bild, dict):
        kandidat = bild.get("url")

    if not kandidat:
        og = re.search(r'<meta[^>]+property="og:image"[^>]+content="([^"]+)"', html)
        print(f"  Kein Bild im Recipe-Objekt. og:image: {og.group(1) if og else 'keins'}")
        return

    print(f"  Bildadresse: {kandidat}")
    for beschreibung, kopf in [("ohne Referer", {}), ("mit Referer", {"Referer": adresse})]:
        try:
            anfrage = urllib.request.Request(
                kandidat, headers={"User-Agent": UA, **kopf},
            )
            with urllib.request.urlopen(anfrage, timeout=30) as antwort:
                daten = antwort.read(2048)
                print(f"    {beschreibung}: HTTP {antwort.status}, "
                      f"{antwort.headers.get('Content-Type')}, erste Bytes ok ({len(daten)})")
        except urllib.error.HTTPError as fehler:
            print(f"    {beschreibung}: HTTP {fehler.code} {fehler.reason}")
        except Exception as fehler:
            print(f"    {beschreibung}: {str(fehler)[:80]}")


def pruefe_pairing():
    print(f"\n{'=' * 62}\nPairing-Dienst\n{'=' * 62}")
    try:
        status, roh = hole(f"{PAIRING}/gesundheit", timeout=20)
        print(f"  /gesundheit: HTTP {status} {roh.decode()[:80]}")
    except Exception as fehler:
        print(f"  /gesundheit FEHLT: {str(fehler)[:120]}")
        return

    try:
        status, roh = hole(
            f"{PAIRING}/sitzung",
            {"gastgeber": "pruef-gastgeber", "rezepte": [
                {"rezeptId": 1, "titel": "Rösti"},
                {"rezeptId": 2, "titel": "Risotto"},
            ]},
        )
        angelegt = json.loads(roh)
        code = angelegt["code"]
        print(f"  Runde angelegt: {code} ({angelegt.get('rezepte')} Rezepte)")
    except urllib.error.HTTPError as fehler:
        print(f"  ANLEGEN FEHLGESCHLAGEN: HTTP {fehler.code} {fehler.read()[:200]}")
        return
    except Exception as fehler:
        print(f"  ANLEGEN FEHLGESCHLAGEN: {str(fehler)[:150]}")
        return

    try:
        _, roh = hole(f"{PAIRING}/sitzung/{code}/beitreten", {"teilnehmer": "pruef-gast"})
        beitritt = json.loads(roh)
        print(f"  Beigetreten, erhaltene Rezepte: {[r['titel'] for r in beitritt['rezepte']]}")

        for wer, stimmen in [
            ("pruef-gastgeber", [{"rezeptId": 1, "mag": True}, {"rezeptId": 2, "mag": True}]),
            ("pruef-gast", [{"rezeptId": 1, "mag": False}, {"rezeptId": 2, "mag": True}]),
        ]:
            _, roh = hole(
                f"{PAIRING}/sitzung/{code}/stimmen",
                {"teilnehmer": wer, "stimmen": stimmen, "fertig": True},
            )
            print(f"  Stimmen von {wer}: {roh.decode()[:60]}")

        _, roh = hole(f"{PAIRING}/sitzung/{code}")
        stand = json.loads(roh)
        print(f"  Stand: {stand['teilnehmer']} Teilnehmer, {stand['fertig']} fertig, "
              f"alleFertig={stand['alleFertig']}")
        treffer = [t["titel"] for t in stand.get("treffer", [])]
        print(f"  Treffer: {treffer}")
        if treffer == ["Risotto"]:
            print("  ERWARTUNG ERFUELLT: nur das gemeinsame Rezept")
        else:
            print(f"  ABWEICHUNG: erwartet ['Risotto'], bekommen {treffer}")

        _, roh = hole(f"{PAIRING}/sitzung/{code}/schliessen", {"teilnehmer": "pruef-gastgeber"})
        print(f"  Geschlossen: {roh.decode()[:60]}")
    except urllib.error.HTTPError as fehler:
        print(f"  FEHLER: HTTP {fehler.code} {fehler.read()[:250]}")
    except Exception as fehler:
        print(f"  FEHLER: {str(fehler)[:200]}")


def main():
    pruefe_bild(
        "kochrezepte.at",
        "https://www.kochrezepte.at/sitemap.xml",
        r"kochrezepte\.at/[^/]+-rezept-\d+",
    )
    pruefe_bild(
        "gutekueche.ch",
        "https://www.gutekueche.ch/sitemap.xml.gz",
        r"gutekueche\.ch/[^/]+-rezept-\d+",
    )
    pruefe_pairing()
    return 0


if __name__ == "__main__":
    sys.exit(main())
