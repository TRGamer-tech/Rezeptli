-- Fassung 2 der gemeinsamen Runde.
--
-- Bisher schickte die eroeffnende Person ihre Rezepte, die zweite stimmte ab, und am Ende
-- kamen alle Stimmen auf einmal. Neu gibt es eine Lobby (der Code steht, bevor irgendetwas
-- festgelegt ist), ein gemeinsames Ziel und Stimmen waehrend des Wischens - damit die Runde
-- aufhoeren kann, sobald genug Treffer da sind.
--
-- Bestehende Sitzungen bleiben Fassung 1 und laufen unveraendert zu Ende; die Vorgaben der
-- neuen Spalten sind so gewaehlt, dass die alte Schnittstelle nichts davon merkt.

-- Welche Fassung der Schnittstelle die Sitzung spricht. Wer mit der anderen kommt, wird
-- gebeten, die App zu aktualisieren, statt halb zu funktionieren.
ALTER TABLE sitzungen ADD COLUMN version INTEGER NOT NULL DEFAULT 1;

-- Wo die Runde steht: lobby, sammeln, laeuft, frage, beendet.
ALTER TABLE sitzungen ADD COLUMN zustand TEXT NOT NULL DEFAULT 'laeuft';

-- Wie viele gemeinsame Gerichte gesucht werden. Legt fest, wer eroeffnet hat.
ALTER TABLE sitzungen ADD COLUMN ziel INTEGER NOT NULL DEFAULT 3;

-- Wonach gesucht wird: essen, getraenke, beides.
ALTER TABLE sitzungen ADD COLUMN art TEXT NOT NULL DEFAULT 'essen';

-- Woher die Karten der laufenden Runde kommen: gespeichert (die Sammlungen beider) oder
-- neu (aus dem Rezeptverzeichnis).
ALTER TABLE sitzungen ADD COLUMN quelle TEXT NOT NULL DEFAULT 'gespeichert';

-- Die wievielte Runde laeuft. Jede Runde hat ihren eigenen Stapel.
ALTER TABLE sitzungen ADD COLUMN runde INTEGER NOT NULL DEFAULT 0;

-- Was nicht auf den Tisch darf - die Angaben beider Personen, vereinigt, als JSON. Es steht
-- nicht dabei, von wem welche Angabe stammt: Dafuer gibt es keinen Grund, also wird es
-- auch nicht gespeichert.
ALTER TABLE sitzungen ADD COLUMN einschraenkungen TEXT NOT NULL DEFAULT '{}';

-- Warum die Runde zu Ende ist: ziel, leer, gastgeber, verlassen.
ALTER TABLE sitzungen ADD COLUMN ende_grund TEXT;

-- Zaehlt jede Aenderung. Ein Geraet, das seinen Stand schon kennt, bekommt beim Nachfragen
-- nur "unveraendert" zurueck.
ALTER TABLE sitzungen ADD COLUMN stand_nr INTEGER NOT NULL DEFAULT 0;

-- Fuer welche Runde jemand seine Karten schon beigesteuert hat.
ALTER TABLE teilnehmer ADD COLUMN beitrag_runde INTEGER NOT NULL DEFAULT -1;

-- Zu welcher Runde eine Karte gehoert und wer sie beigesteuert hat.
ALTER TABLE rezepte ADD COLUMN runde INTEGER NOT NULL DEFAULT 0;
ALTER TABLE rezepte ADD COLUMN von TEXT;

-- Woran ein Rezept wiedererkannt wird. Die Kennung in `rezept_id` vergibt neu der Dienst;
-- der Schluessel kommt vom Geraet (aus der Adresse des Rezepts) und sorgt dafuer, dass
-- ein Rezept, das beide kennen, nur einmal im Stapel liegt - auch ueber Runden hinweg.
ALTER TABLE rezepte ADD COLUMN schluessel TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS idx_rezepte_schluessel ON rezepte (code, schluessel);
CREATE INDEX IF NOT EXISTS idx_rezepte_runde ON rezepte (code, runde, position);
