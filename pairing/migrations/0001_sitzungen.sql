-- Schema des Pairing-Dienstes.
--
-- Der Dienst weiss so wenig wie moeglich: einen kurzen Code, die Rezepte einer
-- Sitzung mit Titel und - falls vorhanden - der Quelladresse, und wer was
-- gewischt hat. Keine Namen, keine Konten, keine Zutaten, keine Zubereitung.
-- Teilnehmende sind zufaellige Zeichenketten, die das Geraet erzeugt und die
-- nirgends sonst vorkommen.
--
-- Alles verfaellt: `verfaellt_am` wird beim Anlegen gesetzt, ein taeglicher
-- Auftrag raeumt auf. Eine Sitzung ist ein Abend, keine Datensammlung.

CREATE TABLE IF NOT EXISTS sitzungen (
    code            TEXT PRIMARY KEY,
    erstellt_am     INTEGER NOT NULL,
    verfaellt_am    INTEGER NOT NULL,
    -- Wer die Sitzung eroeffnet hat. Nur diese Kennung darf sie schliessen.
    gastgeber       TEXT NOT NULL,
    -- Wie viele Personen hoechstens mitmachen duerfen.
    max_teilnehmer  INTEGER NOT NULL DEFAULT 2
);

CREATE INDEX IF NOT EXISTS idx_sitzungen_verfall ON sitzungen (verfaellt_am);

-- Die Rezepte, ueber die abgestimmt wird. Sie stammen aus der Sammlung der
-- Person, die die Sitzung eroeffnet hat.
CREATE TABLE IF NOT EXISTS rezepte (
    code            TEXT NOT NULL,
    -- Die Kennung des Rezepts auf dem Geraet des Gastgebers. Sie ist nur
    -- innerhalb dieser Sitzung von Bedeutung.
    rezept_id       INTEGER NOT NULL,
    position        INTEGER NOT NULL,
    titel           TEXT NOT NULL,
    quelle_url      TEXT,
    bild_url        TEXT,
    zubereitungszeit INTEGER,
    PRIMARY KEY (code, rezept_id),
    FOREIGN KEY (code) REFERENCES sitzungen (code) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS teilnehmer (
    code            TEXT NOT NULL,
    teilnehmer_id   TEXT NOT NULL,
    beigetreten_am  INTEGER NOT NULL,
    -- Gesetzt, sobald jemand alle Rezepte bewertet hat.
    fertig_am       INTEGER,
    PRIMARY KEY (code, teilnehmer_id),
    FOREIGN KEY (code) REFERENCES sitzungen (code) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS stimmen (
    code            TEXT NOT NULL,
    teilnehmer_id   TEXT NOT NULL,
    rezept_id       INTEGER NOT NULL,
    mag             INTEGER NOT NULL,
    entschieden_am  INTEGER NOT NULL,
    -- Der Primaerschluessel macht das Nachreichen derselben Stimme unschaedlich:
    -- Ein Geraet mit wackeligem Netz darf denselben Wisch mehrfach senden.
    PRIMARY KEY (code, teilnehmer_id, rezept_id),
    FOREIGN KEY (code) REFERENCES sitzungen (code) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_stimmen_sitzung ON stimmen (code);
