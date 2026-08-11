package ch.rezeptli.app.domain.model

/**
 * Grobe Warengruppen, nach denen die Einkaufsliste sortiert wird.
 *
 * Die Reihenfolge entspricht ungefaehr dem Weg durch einen Schweizer Laden - so muss
 * man beim Einkaufen nicht zweimal durch denselben Gang.
 */
enum class IngredientCategory {
    GEMUESE,
    FRUECHTE,
    MILCHPRODUKTE,
    FLEISCH_FISCH,
    TEIGWAREN_GETREIDE,
    KONSERVEN_SAUCEN,
    BACKEN_SUESSES,
    GEWUERZE_KRAEUTER,
    TIEFKUEHL,
    GETRAENKE,
    SONSTIGES,
    ;

    companion object {
        /**
         * Ordnet einen Zutatennamen einer Warengruppe zu.
         *
         * Es gewinnt immer das laengste passende Stichwort. Damit landet
         * "Paprikapulver" bei den Gewuerzen und nicht beim Gemuese, und
         * "Kokosmilch" bei den Konserven statt bei den Milchprodukten.
         */
        fun forIngredient(name: String): IngredientCategory {
            val normalized = IngredientNameNormalizer.normalize(name)
            if (normalized.isEmpty()) return SONSTIGES
            return KEYWORDS
                .firstOrNull { (keyword, _) -> normalized.contains(keyword) }
                ?.second
                ?: SONSTIGES
        }

        /** Stichwoerter in normalisierter Form, absteigend nach Laenge sortiert. */
        private val KEYWORDS: List<Pair<String, IngredientCategory>> = buildList {
            fun add(category: IngredientCategory, vararg words: String) {
                words.forEach { add(IngredientNameNormalizer.normalize(it) to category) }
            }

            add(
                GEMUESE,
                "rueebli", "karotte", "moehre", "zwiebel", "knoblauch", "knobli", "lauch",
                "porree", "sellerie", "tomate", "pelati", "gurke", "salat", "spinat",
                "broccoli", "brokkoli", "blumenkohl", "kabis", "kohl", "zucchetti",
                "zucchini", "aubergine", "peperoni", "paprika", "kartoffel", "haerdoepfel",
                "champignon", "pilz", "bohnen", "erbsen", "kefen", "mais", "randen",
                "fenchel", "kuerbis", "rucola", "nuesslisalat", "lattich", "spargel",
                "sellerieknolle", "ingwer", "chili",
            )
            add(
                FRUECHTE,
                "apfel", "aepfel", "birne", "banane", "orange", "zitrone", "limette",
                "erdbeer", "himbeer", "heidelbeer", "brombeer", "beeren", "aprikose",
                "pfirsich", "kirsche", "traube", "melone", "mango", "ananas", "rhabarber",
                "zwetschge", "pflaume", "feige", "dattel",
            )
            add(
                MILCHPRODUKTE,
                "milch", "rahm", "sahne", "butter", "kaese", "quark", "joghurt",
                "mozzarella", "parmesan", "gruyere", "sbrinz", "mascarpone", "ricotta",
                "creme fraiche", "ei", "eier", "eigelb", "eiweiss", "huettenkaese",
            )
            add(
                FLEISCH_FISCH,
                "fleisch", "poulet", "haehnchen", "huhn", "rind", "schwein", "kalb",
                "lamm", "speck", "schinken", "wurst", "cervelat", "hackfleisch",
                "gehacktes", "fisch", "lachs", "thunfisch", "crevette", "garnele",
                "salami", "bacon",
            )
            add(
                TEIGWAREN_GETREIDE,
                "mehl", "reis", "teigwaren", "nudeln", "spaghetti", "hoernli", "penne",
                "pasta", "polenta", "griess", "haferflocken", "brot", "broetchen",
                "paniermehl", "semmelbroesel", "couscous", "quinoa", "linsen",
                "kichererbsen", "blaetterteig", "kuchenteig",
            )
            add(
                KONSERVEN_SAUCEN,
                "tomatenpueree", "bouillon", "bruehe", "oel", "essig", "senf",
                "mayonnaise", "ketchup", "sojasauce", "kokosmilch", "kokosnussmilch",
                "konfituere", "erdnussbutter",
            )
            add(
                BACKEN_SUESSES,
                "zucker", "backpulver", "hefe", "vanille", "schokolade", "kakao",
                "honig", "marmelade", "mandeln", "nuesse", "baumnuesse", "haselnuesse",
                "rosinen", "sultaninen", "natron", "puderzucker", "staubzucker",
            )
            add(
                GEWUERZE_KRAEUTER,
                "salz", "pfeffer", "paprikapulver", "muskat", "zimt", "curry", "oregano",
                "basilikum", "thymian", "rosmarin", "petersilie", "peterli",
                "schnittlauch", "kraeuter", "kuemmel", "lorbeer", "safran", "kardamom",
                "nelken", "anis", "koriander", "dill", "salbei", "majoran",
            )
            add(TIEFKUEHL, "tiefkuehl", "gefroren", "glace", "speiseeis")
            add(GETRAENKE, "wein", "bier", "wasser", "saft", "sirup", "kaffee", "tee")

            sortByDescending { (keyword, _) -> keyword.length }
        }
    }
}
