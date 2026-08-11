#!/usr/bin/env python3
"""Prueft Rezeptquellen, bevor sie in die App aufgenommen werden.

Fuer jede Quelle wird geklaert:

1. Was erlaubt die robots.txt? Sind Rezeptseiten fuer einen normalen Abruf frei?
2. Gibt es ein Verzeichnis (Sitemap), aus dem sich ein Suchindex bauen laesst?
3. Liefert eine Rezeptseite strukturierte Daten (schema.org), oder muesste man
   das HTML auseinandernehmen?
4. Sagen die Nutzungsbedingungen etwas zu automatisierten Abrufen?

Punkt 4 ersetzt keine Rechtsberatung - das Skript sammelt nur die Stellen, die
gelesen werden muessen. Die Entscheidung faellt danach ein Mensch.

Das Skript laeuft in der CI, weil die Entwicklungsumgebung keinen Zugang zu
diesen Seiten hat. Es fragt jede Seite nur wenige Male ab.
"""

import gzip
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "Rezeptli-Recherche/1.0 (Abklaerung vor Integration; +https://github.com/TRGamer-tech/Rezeptli)"

# Kandidaten nach Land. "vorhanden" markiert die bereits eingebauten Quellen.
SITES = [
    # --- Schweiz (vorhanden) ---
    ("CH", "https://www.bettybossi.ch", True),
    ("CH", "https://www.swissmilk.ch", True),
    ("CH", "https://www.gutekueche.ch", True),
    ("CH", "https://migusto.migros.ch", True),
    ("CH", "https://fooby.ch", True),
    ("CH", "https://bettyskuechenschaetze.ch", True),
    ("CH", "https://lemenu.ch", True),
    # --- Schweiz (neu) ---
    ("CH", "https://www.annemariewildeisen.ch", False),
    ("CH", "https://www.saisonkueche.ch", False),
    # --- Deutschland ---
    ("DE", "https://www.chefkoch.de", True),
    ("DE", "https://www.lecker.de", False),
    ("DE", "https://eatsmarter.de", False),
    ("DE", "https://www.essen-und-trinken.de", False),
    ("DE", "https://www.kochbar.de", False),
    ("DE", "https://www.daskochrezept.de", False),
    ("DE", "https://www.einfachkochen.de", False),
    ("DE", "https://www.koch-mit.de", False),
    # --- Oesterreich ---
    ("AT", "https://www.ichkoche.at", False),
    ("AT", "https://www.gutekueche.at", False),
    ("AT", "https://www.kochrezepte.at", False),
    # --- Frankreich ---
    ("FR", "https://www.marmiton.org", False),
    ("FR", "https://www.cuisineaz.com", False),
    ("FR", "https://www.750g.com", False),
    ("FR", "https://www.ptitchef.com", False),
    # --- Italien ---
    ("IT", "https://www.giallozafferano.it", False),
    ("IT", "https://www.cookaround.com", False),
    ("IT", "https://www.misya.info", False),
    ("IT", "https://www.cucchiaio.it", False),
]

RECIPE_HINT = re.compile(r"rezept|recipe|recette|ricetta|kochen|cucina", re.I)

# Begriffe, die in Nutzungsbedingungen auf ein Verbot automatisierter Abrufe deuten.
TOS_TERMS = [
    "scrap", "crawl", "spider", "robot", "roboter", "automatisiert",
    "automated", "automatique", "automatico", "data mining", "text und data",
    "text and data", "estrazione", "extraction", "auslesen", "auswerten",
    "vervielfält", "reproduction", "riproduzione",
]

TOS_PATHS = [
    "/agb", "/nutzungsbedingungen", "/impressum", "/rechtliches",
    "/terms", "/terms-of-use", "/legal", "/conditions-generales",
    "/conditions-generales-utilisation", "/cgu", "/mentions-legales",
    "/termini-e-condizioni", "/note-legali", "/condizioni-generali",
]


def get(url, limit=4_000_000, timeout=40):
    request = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        raw = response.read(limit)
        final = response.geturl()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.GzipFile(fileobj=io.BytesIO(raw)).read()
    return raw.decode("utf-8", "replace"), final


def parse_robots(text):
    """Regeln der Gruppe 'User-agent: *' sowie alle Sitemap-Zeilen."""
    sitemaps, disallows, allows, delay = [], [], [], None
    active = False
    for line in text.splitlines():
        stripped = line.split("#", 1)[0].strip()
        if not stripped:
            continue
        low = stripped.lower()
        if low.startswith("user-agent:"):
            active = low.split(":", 1)[1].strip() == "*"
        elif low.startswith("sitemap:"):
            sitemaps.append(stripped.split(":", 1)[1].strip())
        elif active and low.startswith("disallow:"):
            disallows.append(low.split(":", 1)[1].strip())
        elif active and low.startswith("allow:"):
            allows.append(low.split(":", 1)[1].strip())
        elif active and low.startswith("crawl-delay:"):
            delay = low.split(":", 1)[1].strip()
    return sitemaps, disallows, allows, delay


def blocks_ai_agents(text):
    """Namen bekannter KI-Crawler, die vollstaendig ausgesperrt werden."""
    blocked = []
    current = []
    for line in text.splitlines():
        low = line.split("#", 1)[0].strip().lower()
        if low.startswith("user-agent:"):
            current.append(low.split(":", 1)[1].strip())
        elif low.startswith("disallow:"):
            if low.split(":", 1)[1].strip() == "/":
                blocked.extend(current)
            current = []
        elif not low:
            current = []
    interesting = ("gptbot", "ccbot", "claude", "anthropic", "perplexity",
                   "bytespider", "scrapy", "google-extended", "applebot-extended")
    return sorted({b for b in blocked if any(i in b for i in interesting)})


def recipe_urls_from_sitemaps(sitemaps):
    for sitemap in sitemaps[:8]:
        try:
            xml, _ = get(sitemap)
        except Exception:
            continue
        locations = re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", xml)
        if "<sitemapindex" in xml:
            child = next((l for l in locations if RECIPE_HINT.search(l)), None)
            child = child or (locations[0] if locations else None)
            if not child:
                continue
            try:
                xml, _ = get(child)
                locations = re.findall(r"<loc>\s*([^<\s]+)\s*</loc>", xml)
            except Exception:
                continue
        hits = [l for l in locations if RECIPE_HINT.search(l)]
        if len(hits) >= 5:
            return sitemap, len(locations), hits
    return None, 0, []


def types_in(node, out):
    if isinstance(node, list):
        for item in node:
            types_in(item, out)
    elif isinstance(node, dict):
        kind = node.get("@type")
        if kind:
            out.add(kind if isinstance(kind, str) else "/".join(map(str, kind)))
        for key in ("@graph", "mainEntity", "itemListElement"):
            if key in node:
                types_in(node[key], out)


def find_recipe(node):
    if isinstance(node, list):
        for item in node:
            found = find_recipe(item)
            if found:
                return found
    elif isinstance(node, dict):
        if "Recipe" in str(node.get("@type", "")):
            return node
        for key in ("@graph", "mainEntity"):
            if key in node:
                found = find_recipe(node[key])
                if found:
                    return found
    return None


def check_terms(base):
    """Sucht die Nutzungsbedingungen und meldet Fundstellen zu Automatisierung."""
    for path in TOS_PATHS:
        try:
            html, final = get(base + path, 900_000, timeout=25)
        except Exception:
            continue
        text = re.sub(r"<script.*?</script>|<style.*?</style>", " ", html, flags=re.S)
        text = re.sub(r"<[^>]+>", " ", text)
        text = re.sub(r"\s+", " ", text)
        if len(text) < 800:
            continue
        hits = []
        low = text.lower()
        for term in TOS_TERMS:
            index = low.find(term)
            if index >= 0:
                hits.append((term, text[max(0, index - 130):index + 170].strip()))
        return final, hits
    return None, []


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else None
    for country, site, existing in SITES:
        if only and only not in site:
            continue
        mark = " (vorhanden)" if existing else ""
        print("=" * 72)
        print(f"{country}  {site}{mark}")
        print("=" * 72)

        try:
            robots, _ = get(site + "/robots.txt", 300_000, timeout=25)
        except Exception as exc:
            print(f"  robots.txt NICHT ERREICHBAR: {str(exc)[:80]}")
            print()
            continue

        sitemaps, disallows, allows, delay = parse_robots(robots)
        blocking = [d for d in disallows if d and RECIPE_HINT.search(d)]
        print(f"  robots.txt: {len(disallows)} Disallow, {len(allows)} Allow, "
              f"crawl-delay={delay or '-'}, {len(sitemaps)} Sitemaps")
        print(f"    Disallow mit Rezeptbezug: {blocking or 'keine'}")
        ai = blocks_ai_agents(robots)
        print(f"    KI-Crawler gesperrt: {ai or 'keine'}")
        if "/" in disallows:
            print("    ACHTUNG: Disallow: / fuer alle - Quelle scheidet aus")

        sitemap, total, hits = recipe_urls_from_sitemaps(sitemaps)
        if hits:
            print(f"  Verzeichnis: {sitemap}")
            print(f"    {total} URLs, davon {len(hits)} mit Rezeptbezug")
            print(f"    Beispiel: {hits[len(hits) // 2]}")
        else:
            print("  Verzeichnis: keine Rezept-URLs ueber Sitemap gefunden")

        if hits:
            try:
                html, _ = get(hits[len(hits) // 2])
                blocks = re.findall(
                    r"<script[^>]*application/ld\+json[^>]*>(.*?)</script>", html, re.S)
                kinds, recipe = set(), None
                for block in blocks:
                    try:
                        data = json.loads(block)
                    except Exception:
                        continue
                    types_in(data, kinds)
                    recipe = recipe or find_recipe(data)
                micro = html.count('itemprop="recipeIngredient"')
                print(f"  JSON-LD: {sorted(kinds) or 'keine'} | Microdata-Zutaten: {micro}")
                if recipe:
                    ingredients = recipe.get("recipeIngredient") or []
                    instructions = recipe.get("recipeInstructions")
                    steps = len(instructions) if isinstance(instructions, list) else "kein Array"
                    print(f"    Zutaten: {len(ingredients)} | Schritte: {steps}")
                    if ingredients:
                        print(f"    Beispiel: {json.dumps(ingredients[:2], ensure_ascii=False)[:150]}")
            except Exception as exc:
                print(f"  Rezeptseite FEHLER: {str(exc)[:80]}")

        url, term_hits = check_terms(site)
        if url:
            print(f"  Nutzungsbedingungen: {url}")
            if term_hits:
                for term, context in term_hits[:4]:
                    print(f"    [{term}] ...{context[:200]}...")
            else:
                print("    keine Fundstelle zu automatisierten Abrufen")
        else:
            print("  Nutzungsbedingungen: nicht gefunden (Pfade durchprobiert)")
        print()
        time.sleep(2)


if __name__ == "__main__":
    main()
