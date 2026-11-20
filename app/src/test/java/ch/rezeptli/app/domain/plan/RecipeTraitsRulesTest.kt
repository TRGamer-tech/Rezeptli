package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Appliance
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.Dislike
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Was die App einem Rezept ansieht: ob es ein Essen oder ein Getraenk ist, zu welcher Kueche
 * es gehoert und was darin steckt, das jemand nicht mag.
 *
 * Die Beispiele sind echte Titel aus den Verzeichnissen - samt der Fallen, die sie stellen
 * ("Lachs mit Tee-Marinade" ist kein Tee).
 */
class RecipeTraitsRulesTest {
    private val classifier = RecipeClassifier()

    private fun recipe(
        title: String,
        vararg zutaten: String,
        text: String = "In der Pfanne anbraten.",
        tags: List<String> = emptyList(),
    ) = Recipe(
        title = title,
        instructions = text,
        ingredients = zutaten.map { Ingredient(name = it) },
        tags = tags,
    )

    // ------------------------------------------------------------------ Essen oder Getraenk

    @ParameterizedTest(name = "{0} ist ein Getraenk")
    @CsvSource(
        "Erdbeer-Smoothie",
        "Hugo",
        "Mojito",
        "Heisse Schokolade",
        "Eistee mit Pfirsich",
        "Mandelmilch",
        "Ingwer-Zitronen-Tee",
        "Aperol Spritz",
        "Glühwein",
        "Hausgemachte Limonade",
        "Bananen-Shake",
        "Holunderblütensirup",
    )
    fun `Getraenke erkennt die App am Titel`(titel: String) {
        assertEquals(Course.GETRAENK, classifier.classifyTitle(titel).course)
    }

    @ParameterizedTest(name = "{0} ist kein Getraenk")
    @CsvSource(
        "Lachs mit Tee-Marinade",
        "Rindsfilet an Rotweinsauce",
        "Poulet in Weissweinsauce",
        "Kokosmilch-Curry",
        "Espresso-Brownies",
        "Kürbissuppe",
        "Risotto mit Prosecco",
        "Bierbrot",
        "Smoothie-Bowl",
        "Kaffeecreme",
        "Milchreis",
    )
    fun `was ein Getraenk nur im Namen traegt, bleibt ein Essen`(titel: String) {
        assertFalse(classifier.classifyTitle(titel).course == Course.GETRAENK, titel)
    }

    @ParameterizedTest(name = "{0} ist ein Dessert")
    @CsvSource(
        "Schokoladenkuchen",
        "Apfelwähe",
        "Vanilleglace",
        "Tiramisu",
        "Zimtsterne",
        "Panna Cotta",
        "Erdbeereis",
        "Wassereis am Stiel",
        "Milchreis",
        "Espresso-Brownies",
        "Zitronen-Cheesecake",
        "Mailänderli",
        "Rhabarber-Crumble",
    )
    fun `Desserts und suesses Gebaeck erkennt die App am Titel`(titel: String) {
        assertEquals(Course.DESSERT, classifier.classifyTitle(titel).course)
    }

    @ParameterizedTest(name = "{0} ist eine Mahlzeit")
    @CsvSource(
        "Zwiebelkuchen",
        "Lachsmousse",
        "Reis mit Gemüse",
        "Gebratener Gemüsereis",
        "Kartoffelgratin",
        "Spaghetti Bolognese",
        "Pasta mit Tomatensauce",
        "Zürcher Geschnetzeltes",
        "Gemüsestrudel",
        "Spinatwähe",
        "Poulet an Senfsauce",
        "Linseneintopf",
    )
    fun `Herzhaftes bleibt eine Mahlzeit, auch wenn es wie Kuchen oder Sauce klingt`(titel: String) {
        assertEquals(Course.HAUPTGERICHT, classifier.classifyTitle(titel).course)
    }

    @ParameterizedTest(name = "{0} ist Beiwerk")
    @CsvSource("Tomatensauce", "Kräuterbutter", "Bauernbrot", "Hummus", "Pesto Genovese", "Granola", "Salatdressing")
    fun `Saucen, Brot und Aufstriche sind Essen, aber keine Mahlzeit`(titel: String) {
        assertEquals(Course.SONSTIGES, classifier.classifyTitle(titel).course)
    }

    @Test
    fun `die Auswahl beim Wischen folgt dieser Einteilung`() {
        val smoothie = classifier.classifyTitle("Erdbeer-Smoothie").course
        val kuchen = classifier.classifyTitle("Schokoladenkuchen").course
        val gratin = classifier.classifyTitle("Kartoffelgratin").course

        // "Essen" ist alles ausser Getraenken - auch der Kuchen.
        assertFalse(CourseChoice.ESSEN.allows(smoothie))
        assertTrue(CourseChoice.ESSEN.allows(kuchen))
        assertTrue(CourseChoice.ESSEN.allows(gratin))

        assertTrue(CourseChoice.GETRAENKE.allows(smoothie))
        assertFalse(CourseChoice.GETRAENKE.allows(kuchen))
        assertFalse(CourseChoice.GETRAENKE.allows(gratin))

        assertTrue(listOf(smoothie, kuchen, gratin).all(CourseChoice.BEIDES::allows))
    }

    @Test
    fun `ohne eindeutigen Titel entscheiden die Zutaten ueber suess oder herzhaft`() {
        val suess = recipe("Omas Sonntagsfreude", "Zucker", "Schokolade", "Rahm", "Vanille")
        val herzhaft = recipe("Omas Sonntagsfreude", "Zucker", "Zwiebeln", "Rindfleisch", "Bouillon")

        assertEquals(Course.DESSERT, classifier.classify(suess).course)
        assertEquals(Course.HAUPTGERICHT, classifier.classify(herzhaft).course)
    }

    @Test
    fun `die Schlagworte der Quelle zaehlen mit`() {
        assertEquals(
            Course.GETRAENK,
            classifier.classify(recipe("Sommertraum", "Minze", tags = listOf("Getränke"))).course,
        )
        assertEquals(
            Course.DESSERT,
            classifier.classify(recipe("Sommertraum", "Minze", tags = listOf("Dessert"))).course,
        )
        assertEquals(
            Course.HAUPTGERICHT,
            classifier.classify(recipe("Sommertraum", "Minze", tags = listOf("Hauptgericht", "Dessert"))).course,
        )
    }

    // ------------------------------------------------------------------ Kuechen

    @Test
    fun `die Kueche steht meistens im Titel`() {
        fun kuechen(titel: String) = classifier.classifyTitle(titel).cuisines

        assertEquals(setOf(Cuisine.ITALIENISCH), kuechen("Spaghetti Carbonara"))
        assertEquals(setOf(Cuisine.SCHWEIZER_KLASSIKER), kuechen("Älplermagronen"))
        assertEquals(setOf(Cuisine.SCHWEIZER_KLASSIKER), kuechen("Zürcher Geschnetzeltes"))
        assertEquals(setOf(Cuisine.ASIATISCH), kuechen("Pad Thai"))
        assertEquals(setOf(Cuisine.ASIATISCH), kuechen("Gemüsecurry"))
        assertEquals(setOf(Cuisine.LATEINAMERIKANISCH), kuechen("Chili con Carne"))
        assertEquals(setOf(Cuisine.ORIENTALISCH), kuechen("Shakshuka"))
        assertEquals(setOf(Cuisine.AMERIKANISCH), kuechen("Pulled Pork Burger"))
        assertEquals(setOf(Cuisine.FRANZOESISCH), kuechen("Quiche Lorraine"))
        assertEquals(setOf(Cuisine.DEUTSCH_OESTERREICHISCH), kuechen("Wiener Schnitzel"))
        assertEquals(setOf(Cuisine.MEDITERRAN), kuechen("Paella"))
    }

    @Test
    fun `die meisten Rezepte gehoeren zu keiner erkennbaren Kueche`() {
        assertTrue(classifier.classifyTitle("Kartoffelgratin").cuisines.isEmpty())
        assertTrue(classifier.classifyTitle("Gemüsesuppe").cuisines.isEmpty())
        assertTrue(classifier.classifyTitle("Ofengemüse mit Kräutern").cuisines.isEmpty())
    }

    @Test
    fun `nennt die Quelle die Kueche, gilt das auch ohne Hinweis im Titel`() {
        val traits = classifier.classify(recipe("Grossmutters Auflauf", "Kartoffeln", tags = listOf("Italienisch")))

        assertEquals(setOf(Cuisine.ITALIENISCH), traits.cuisines)
    }

    // ------------------------------------------------------------------ Abneigungen

    private fun abneigungen(titel: String, vararg zutaten: String) = classifier
        .classify(
            recipe(titel, *zutaten),
        ).dislikes

    @Test
    fun `Fleischsorten werden einzeln erkannt`() {
        assertTrue(Dislike.POULET in abneigungen("Geschnetzeltes", "Pouletbrust", "Rahm"))
        assertTrue(Dislike.RIND in abneigungen("Hacktätschli", "Rindshackfleisch", "Zwiebeln"))
        assertTrue(Dislike.SCHWEIN in abneigungen("Älplermagronen", "Speckwürfel", "Hörnli"))
        assertTrue(Dislike.LAMM in abneigungen("Sonntagsbraten", "Lammgigot", "Rosmarin"))
        assertTrue(Dislike.INNEREIEN in abneigungen("Leberli", "Kalbsleber", "Butter"))

        val poulet = abneigungen("Geschnetzeltes", "Pouletbrust", "Rahm")
        assertFalse(Dislike.RIND in poulet)
        assertFalse(Dislike.SCHWEIN in poulet)
    }

    @Test
    fun `Fisch und Meeresfruechte sind zwei verschiedene Dinge`() {
        val lachs = abneigungen("Lachs mit Reis", "Lachsfilet", "Reis")
        val crevetten = abneigungen("Crevetten-Pfanne", "Crevetten", "Knoblauch")

        assertTrue(Dislike.FISCH in lachs)
        assertFalse(Dislike.MEERESFRUECHTE in lachs)
        assertTrue(Dislike.MEERESFRUECHTE in crevetten)
        assertFalse(Dislike.FISCH in crevetten)
    }

    @Test
    fun `Gemuese und Gewuerze, die oft nicht gemocht werden`() {
        assertTrue(Dislike.PILZE in abneigungen("Rahmsauce", "Champignons", "Rahm"))
        assertTrue(Dislike.ZWIEBELN in abneigungen("Sauce", "Schalotten", "Butter"))
        assertTrue(Dislike.KNOBLAUCH in abneigungen("Pasta", "Knoblauchzehe", "Spaghetti"))
        assertTrue(Dislike.KORIANDER in abneigungen("Curry", "Koriander", "Kokosmilch"))
        assertTrue(Dislike.AUBERGINE in abneigungen("Auflauf", "Auberginen", "Tomaten"))
        assertTrue(Dislike.KOHL in abneigungen("Eintopf", "Rosenkohl", "Kartoffeln"))
        assertTrue(Dislike.KRAEFTIGER_KAESE in abneigungen("Pasta", "Gorgonzola", "Penne"))
    }

    @Test
    fun `aehnlich klingende Zutaten loesen nichts aus`() {
        // Olivenoel ist keine Olive, Kohlrabi kein Kohl, Leberkaese keine Leber.
        assertFalse(Dislike.OLIVEN in abneigungen("Salat", "Olivenöl", "Tomaten"))
        assertTrue(Dislike.OLIVEN in abneigungen("Salat", "Schwarze Oliven", "Tomaten"))
        assertFalse(Dislike.KOHL in abneigungen("Gemüsepfanne", "Kohlrabi", "Karotten"))
        assertFalse(Dislike.INNEREIEN in abneigungen("Znüni", "Leberkäse", "Senf"))
        // Gefluegelwurst ist kein Schwein.
        assertFalse(Dislike.SCHWEIN in abneigungen("Schneller Znacht", "Pouletwurst", "Essiggurken"))
    }

    @Test
    fun `scharf ist, was Chili enthaelt oder sich so nennt`() {
        assertTrue(Dislike.SCHARF in abneigungen("Nudelpfanne", "Chilischote", "Nudeln"))
        assertTrue(Dislike.SCHARF in abneigungen("Scharfe Nudelpfanne", "Nudeln", "Karotten"))
        assertFalse(Dislike.SCHARF in abneigungen("Milde Nudelpfanne", "Nudeln", "Karotten"))
    }

    @Test
    fun `vegane Gerichte sind, was sich so nennt oder auf Fleischersatz baut - nicht jeder Salat`() {
        assertTrue(Dislike.VEGAN in abneigungen("Veganes Curry", "Kichererbsen", "Kokosmilch"))
        assertTrue(Dislike.VEGAN in abneigungen("Geschnetzeltes", "Tofu", "Sojasauce"))
        assertTrue(Dislike.TOFU in abneigungen("Geschnetzeltes", "Tofu", "Sojasauce"))

        // Ein Tomatensalat kommt ohne Tierisches aus, ist aber kein "veganes Gericht" in dem
        // Sinn, in dem jemand "vegan mag ich nicht" sagt.
        val salat = classifier.classify(recipe("Tomatensalat", "Tomaten", "Olivenöl", "Basilikum"))
        assertEquals(DietClass.VEGAN, salat.diet)
        assertFalse(Dislike.VEGAN in salat.dislikes)
    }

    @Test
    fun `schon der Titel verraet das Offensichtliche`() {
        assertTrue(Dislike.FISCH in classifier.classifyTitle("Lachsfilet mit Dill").dislikes)
        assertTrue(Dislike.PILZE in classifier.classifyTitle("Pilzrisotto").dislikes)
        assertTrue(classifier.classifyTitle("Kartoffelgratin").dislikes.isEmpty())
    }

    // ------------------------------------------------------------------ Dampfgarer

    @Test
    fun `der Dampfgarer wird unter seinen Namen erkannt`() {
        fun geraete(
            text: String,
        ) = classifier.classify(recipe("Gemüse", "Broccoli", "Karotten", text = text)).requiredAppliances

        assertEquals(setOf(Appliance.DAMPFGARER), geraete("Im Steamer bei 100 Grad 12 Minuten garen."))
        assertEquals(setOf(Appliance.DAMPFGARER), geraete("Das Gemüse im Dampfgarer garen."))
        assertEquals(setOf(Appliance.DAMPFGARER), geraete("Im Combi-Steam mit Heissluft und Dampf garen."))
        // Ein Rezept fuer den Herd bleibt eines fuer den Herd.
        assertEquals(setOf(Appliance.HERD), geraete("In der Pfanne anbraten."))
    }

    @Test
    fun `ein Rezept fuer ein besonderes Geraet fehlt nur, wem das Geraet fehlt`() {
        val gedaempft = classifier.classify(recipe("Lachs", "Lachs", "Broccoli", text = "Im Steamer garen."))

        assertTrue(gedaempft.missesSpecialAppliance(setOf(Appliance.HERD, Appliance.BACKOFEN)))
        assertFalse(gedaempft.missesSpecialAppliance(setOf(Appliance.HERD, Appliance.DAMPFGARER)))
        // Ohne Angabe zur Kueche wird nichts ausgeschlossen.
        assertFalse(gedaempft.missesSpecialAppliance(emptySet()))
    }
}
