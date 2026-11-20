-- Zwei Luecken der gemeinsamen Runde.
--
-- 1. Steuerte ein Telefon seine Karten nie bei (App geschlossen, kein Netz), wartete das
--    andere ohne Ende. Neu laeuft eine Frist, sobald der erste Beitrag da ist: Danach
--    beginnt die Runde mit dem, was vorliegt.
--
-- 2. Ein selbst erfasstes Rezept hat keine Adresse, von der die andere Person es holen
--    koennte. War es ein Treffer, liess es sich deshalb nicht uebernehmen. Neu legt das
--    Geraet, dem es gehoert, den Inhalt eines solchen Treffers hier ab - und nur den.

-- Wann in der laufenden Sammelphase der erste Beitrag eintraf. NULL: noch keiner.
ALTER TABLE sitzungen ADD COLUMN beitrag_seit INTEGER;

-- Zutaten und Zubereitung selbst erfasster Rezepte, die BEIDE mochten.
--
-- Bisher wusste der Dienst von einem Rezept nur Titel, Adresse und Bild. Das hier ist die
-- eine Ausnahme, und sie ist eng gefasst: nur fuer gemeinsame Treffer, nur fuer Rezepte ohne
-- Adresse, nur vom Geraet, das die Karte beigesteuert hat - und es verschwindet mit der
-- Sitzung. Fotos kommen nicht mit.
CREATE TABLE IF NOT EXISTS inhalte (
    code        TEXT NOT NULL,
    rezept_id   INTEGER NOT NULL,
    -- Der Inhalt als JSON, vom Dienst geprueft und gekuerzt.
    inhalt      TEXT NOT NULL,
    PRIMARY KEY (code, rezept_id),
    FOREIGN KEY (code) REFERENCES sitzungen (code) ON DELETE CASCADE
);
