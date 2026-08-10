package ch.rezeptli.app.domain.model

/**
 * Normalisiert Zutatennamen fuer Vergleiche.
 *
 * Bewusst als reine Kotlin-Funktion gehalten, damit sie sowohl vom Parser als auch
 * spaeter vom Vorrats-Abgleich und der Einkaufsliste genutzt werden kann.
 *
 * Die Normalisierung glaettet die typischen Schreibvarianten im deutschsprachigen
 * Raum: Gross-/Kleinschreibung, Umlaute ("Rüebli" / "Rueebli") und das in der Schweiz
 * unuebliche "ß" ("Grieß" / "Griess").
 */
object IngredientNameNormalizer {
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9 ]")
    private val WHITESPACE = Regex("\\s+")

    fun normalize(name: String): String =
        name
            .lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .replace("é", "e")
            .replace("è", "e")
            .replace("ê", "e")
            .replace("à", "a")
            .replace("â", "a")
            .replace("î", "i")
            .replace("ô", "o")
            .replace("û", "u")
            .replace("-", " ")
            .replace(NON_ALPHANUMERIC, " ")
            .replace(WHITESPACE, " ")
            .trim()
}

/**
 * Regionale Synonyme, vor allem Schweizer Begriffe und ihre hochdeutsche Normalform.
 *
 * Die App zeigt immer den vom Nutzer eingegebenen Begriff an. Der kanonische Name
 * dient ausschliesslich dem Zusammenfuehren gleicher Zutaten - heute beim Import,
 * spaeter beim Vorrats-Abgleich und beim Erzeugen der Einkaufsliste.
 */
object SwissIngredientSynonyms {
    private val SYNONYMS: Map<String, String> =
        mapOf(
            "rueebli" to "Karotte",
            "rueebli gelb" to "Karotte",
            "karotten" to "Karotte",
            "moehren" to "Karotte",
            "moehre" to "Karotte",
            "peperoni" to "Paprika",
            "peperoncini" to "Chili",
            "zucchetti" to "Zucchini",
            "randen" to "Rote Bete",
            "rande" to "Rote Bete",
            "kefen" to "Zuckerschoten",
            "nuesslisalat" to "Feldsalat",
            "nuessler" to "Feldsalat",
            "kabis" to "Kohl",
            "weisskabis" to "Weisskohl",
            "rotkabis" to "Rotkohl",
            "blaukabis" to "Rotkohl",
            "haerdoepfel" to "Kartoffel",
            "kartoffeln" to "Kartoffel",
            "peterli" to "Petersilie",
            "knobli" to "Knoblauch",
            "porree" to "Lauch",
            "rahm" to "Sahne",
            "vollrahm" to "Sahne",
            "schlagrahm" to "Sahne",
            "halbrahm" to "Sahne",
            "sauerrahm" to "Saure Sahne",
            "bouillon" to "Brühe",
            "teigwaren" to "Nudeln",
            "maizena" to "Speisestärke",
            "kristallzucker" to "Zucker",
            "sultaninen" to "Rosinen",
            "baumnuesse" to "Walnüsse",
            "poulet" to "Hähnchen",
            "pouletbrust" to "Hähnchenbrust",
            "gehacktes" to "Hackfleisch",
            "hoerndli" to "Hörnli",
            "glace" to "Speiseeis",
            "guetzli" to "Plätzchen",
            "gipfeli" to "Croissant",
            "griess" to "Grieß",
            "eiweiss" to "Eiweiß",
            "zwiebeln" to "Zwiebel",
            "eier" to "Ei",
            "tomaten" to "Tomate",
        )

    /**
     * Liefert die hochdeutsche Normalform eines Begriffs oder `null`, wenn der Begriff
     * bereits normalisiert ist bzw. kein Synonym hinterlegt ist.
     */
    fun canonicalFor(name: String): String? {
        val key = IngredientNameNormalizer.normalize(name)
        if (key.isEmpty()) return null
        val direct = SYNONYMS[key]
        if (direct != null) return direct
        // Auch zusammengesetzte Angaben wie "frische Rüebli" aufloesen, indem das
        // letzte Wort geprueft wird - das ist im Deutschen fast immer das Grundwort.
        val lastWord = key.substringAfterLast(' ', key)
        return if (lastWord != key) SYNONYMS[lastWord] else null
    }
}
