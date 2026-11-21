#!/usr/bin/env python3
"""Stellt Gemeldetes am echten Pairing-Dienst nach.

Laeuft in der CI, weil die Entwicklungsumgebung den Dienst nicht erreicht. Gespielt wird
eine ganze gemeinsame Runde mit zwei gedachten Telefonen - so, wie die App sie spielt:

1. Gilt, was die eingeladene Person nicht mag, danach auch fuer die Person, die eroeffnet hat?
2. Kommen grosse Beitraege an - alle gespeicherten Rezepte beider, nicht nur ein Dutzend?

Die Einstufung der Rezepte selbst (erkennt die App Pilze in einer Zutatenliste?) prueft der
Lauf `FilterAuditTest` in derselben CI-Aufgabe.
"""

import json
import secrets
import sys
import time
import urllib.error
import urllib.request

UA = "Rezeptli/1.0 (Open-Source-Rezept-App; +https://github.com/TRGamer-tech/Rezeptli)"
PAIRING = "https://rezeptli-pairing.rezeptli.workers.dev"

abweichungen = []


def ruf(methode, pfad, daten=None, timeout=45):
    """Eine Anfrage an den Dienst: Status, Antwort als Objekt, Dauer in Millisekunden."""
    kopf = {"User-Agent": UA}
    rumpf = None
    if daten is not None:
        kopf["Content-Type"] = "application/json"
        rumpf = json.dumps(daten).encode()
    anfrage = urllib.request.Request(f"{PAIRING}{pfad}", data=rumpf, headers=kopf, method=methode)
    beginn = time.monotonic()
    try:
        with urllib.request.urlopen(anfrage, timeout=timeout) as antwort:
            status, roh = antwort.status, antwort.read()
    except urllib.error.HTTPError as fehler:
        status, roh = fehler.code, fehler.read()
    dauer = int((time.monotonic() - beginn) * 1000)
    try:
        inhalt = json.loads(roh)
    except Exception:
        inhalt = {"roh": roh[:200].decode("utf-8", "replace")}
    return status, inhalt, dauer


def pruefe(bedingung, text):
    print(f"  {'ok  ' if bedingung else 'FEHLT'} {text}")
    if not bedingung:
        abweichungen.append(text)


def karten(praefix, anzahl):
    return [
        {
            "schluessel": f"u{praefix}{nummer:04d}",
            "titel": f"Rezept {praefix} {nummer}",
            "quelleUrl": f"https://example.org/{praefix}/{nummer}",
        }
        for nummer in range(anzahl)
    ]


def runde(gastgeber_karten, gast_karten):
    """Spielt eine Runde bis zum fertigen Stapel und gibt zurueck, wie viele Karten er hat."""
    anna = f"pruef-a-{secrets.token_hex(6)}"
    beat = f"pruef-b-{secrets.token_hex(6)}"
    print(f"\n-- Runde mit {gastgeber_karten} + {gast_karten} gespeicherten Rezepten")

    status, stand, _ = ruf("POST", "/v2/sitzung", {
        "teilnehmer": anna,
        "einschraenkungen": {"ernaehrung": [], "unvertraeglich": [], "ohne": [], "eigene": []},
    })
    pruefe(status == 201, f"eroeffnet (HTTP {status})")
    if status != 201:
        print(f"    {stand}")
        return None
    code = stand["code"]

    status, stand, _ = ruf("POST", f"/v2/sitzung/{code}/beitreten", {
        "teilnehmer": beat,
        "einschraenkungen": {"ernaehrung": [], "unvertraeglich": ["NUESSE"], "ohne": ["PILZE"], "eigene": ["Rosinen"]},
    })
    pruefe(status == 200, f"beigetreten (HTTP {status})")
    pruefe("PILZE" in stand.get("einschraenkungen", {}).get("ohne", []), "wer beitritt, sieht die eigene Abneigung")

    status, stand, _ = ruf("GET", f"/v2/sitzung/{code}?teilnehmer={anna}")
    angaben = stand.get("einschraenkungen", {})
    pruefe(
        "PILZE" in angaben.get("ohne", []) and "NUESSE" in angaben.get("unvertraeglich", [])
        and "Rosinen" in angaben.get("eigene", []),
        f"wer eroeffnet hat, bekommt die Abneigungen der anderen Person: {angaben}",
    )

    ruf("POST", f"/v2/sitzung/{code}/einstellungen", {"teilnehmer": anna, "ziel": 3, "art": "essen", "quelle": "gespeichert"})
    status, stand, _ = ruf("POST", f"/v2/sitzung/{code}/start", {"teilnehmer": anna})
    pruefe(stand.get("zustand") == "sammeln", f"gestartet: {stand.get('zustand')} (HTTP {status})")
    pruefe(
        "PILZE" in stand.get("einschraenkungen", {}).get("ohne", []),
        "auch beim Sammeln gelten die Angaben beider",
    )

    status, stand, dauer = ruf("POST", f"/v2/sitzung/{code}/beitrag", {
        "teilnehmer": anna, "runde": 0, "rezepte": karten("a", gastgeber_karten),
    })
    pruefe(status == 200, f"Beitrag 1 mit {gastgeber_karten} Karten angenommen (HTTP {status}, {dauer} ms)")
    if status != 200:
        print(f"    {stand}")

    status, stand, dauer = ruf("POST", f"/v2/sitzung/{code}/beitrag", {
        "teilnehmer": beat, "runde": 0, "rezepte": karten("b", gast_karten),
    })
    pruefe(status == 200, f"Beitrag 2 mit {gast_karten} Karten angenommen (HTTP {status}, {dauer} ms)")
    if status != 200:
        print(f"    {stand}")

    status, stand, dauer = ruf("GET", f"/v2/sitzung/{code}?teilnehmer={anna}&runde=-1")
    stapel = stand.get("stapel") or []
    eigene = sum(1 for karte in stapel if karte.get("vonMir"))
    print(f"  Stapel: {len(stapel)} Karten ({eigene} von A, {len(stapel) - eigene} von B), "
          f"Zustand {stand.get('zustand')}, {dauer} ms")

    ruf("POST", f"/v2/sitzung/{code}/beenden", {"teilnehmer": anna})
    status, _, _ = ruf("POST", f"/v2/sitzung/{code}/schliessen", {"teilnehmer": anna})
    pruefe(status == 200, "Runde wieder geloescht")
    return len(stapel)


def main():
    print(f"{'=' * 62}\nPairing-Dienst: {PAIRING}\n{'=' * 62}")
    status, inhalt, dauer = ruf("GET", "/gesundheit", timeout=20)
    print(f"  /gesundheit: HTTP {status} {inhalt} ({dauer} ms)")
    if status != 200:
        print("  Der Dienst ist nicht erreichbar - mehr laesst sich nicht pruefen.")
        return 0

    for gastgeber, gast in ((12, 12), (45, 30), (80, 80), (300, 250)):
        anzahl = runde(gastgeber, gast)
        if anzahl is not None:
            print(f"  => im Stapel: {anzahl} von {gastgeber + gast} beigesteuerten Karten")

    print(f"\n{len(abweichungen)} Abweichung(en)")
    for text in abweichungen:
        print(f"  - {text}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
