package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Die Einstufung an dem gemessen, was die Rezeptseiten wirklich schreiben.
 *
 * Gemeldet war: Rezepte mit Pilzen trotz "keine Pilze". Ein Lauf ueber das Verzeichnis und
 * 245 echte Rezeptseiten (FilterAuditTest) zeigte, woran die Stichwortlisten litten - in beide
 * Richtungen: "Schwammerl", "Thon" und "Truten" wurden uebersehen, dafuer galt Flammkuchen als
 * Lammgericht, gehackte Tomaten als Hackfleisch und die Austernpilze als Meeresfruechte. Jede
 * Zeile hier stammt aus diesem Lauf oder ist ihm nachgebildet.
 */
class RealWorldClassificationTest {
    private val classifier = RecipeClassifier()

    private fun traits(titel: String, vararg zutaten: String) = classifier.classify(
        Recipe(title = titel, ingredients = zutaten.map { Ingredient(name = it) }),
    )

    private fun abneigungen(titel: String, vararg zutaten: String) = traits(titel, *zutaten).dislikes

    private fun zutat(name: String) = abneigungen("Gericht", name)

    // ------------------------------------------------------------------ Pilze

    @ParameterizedTest(name = "{0} sind Pilze")
    @CsvSource(
        "Champignons",
        "braune Egerlinge",
        "Schwammerl",
        "Eierschwammerl",
        "Eierschwämmli",
        "Kräuterseitlinge",
        "Kräuter-Seitlinge",
        "Steinpilze, getrocknet",
        "Shiitake",
        "Shitake",
        "Austernpilze",
        "Parasole",
        "Pfifferlinge",
        "Morcheln",
        "Mu-Err-Pilze",
        "Trüffelöl",
        "Funghi porcini",
        "Enoki",
        "gemischte Pilze",
        "Pilzfond",
    )
    fun `Pilze unter allen ihren Namen`(name: String) {
        assertTrue(Dislike.PILZE in zutat(name), name)
    }

    @ParameterizedTest(name = "{0} nennt Pilze schon im Titel")
    @CsvSource(
        "Pizza Funghi – der italienische Klassiker",
        "Klare Schwammerlsuppe",
        "Schwammerlgulasch",
        "Tagliatelle ai Funghi Porcini",
        "Parasol gebraten",
        "Eierschwammerl-Gulasch mit Laugengebäck",
        "Pilzragout mit Rotweinsauce",
    )
    fun `Pilzgerichte werden am Titel erkannt`(titel: String) {
        assertTrue(Dislike.PILZE in classifier.classifyTitle(titel).dislikes, titel)
    }

    @Test
    fun `Trueffel aus Schokolade sind keine Pilze - und der Edelpilzkaese auch nicht`() {
        assertFalse(Dislike.PILZE in abneigungen("Rahm-Schokoladentrüffel", "Butter", "Kakaopulver", "Rahm"))
        assertFalse(Dislike.PILZE in abneigungen("Kaffee-Trüffel-Pralinen", "Kuvertüre", "Espresso"))
        assertFalse(Dislike.PILZE in zutat("Edelpilzkäse"))
        assertTrue(Dislike.PILZE in abneigungen("Kartoffel-Trüffelsuppe", "Kartoffeln", "Trüffel"))
    }

    @Test
    fun `Pilze in der Notiz zaehlen - auch als Beispiel`() {
        val rezept = Recipe(
            title = "Gemüsepfanne",
            ingredients = listOf(
                Ingredient(name = "Gemüse", note = "z. B. Champignons oder Zucchetti, in Scheiben"),
                Ingredient(name = "Reis"),
            ),
        )

        assertTrue(Dislike.PILZE in classifier.classify(rezept).dislikes)
    }

    // ------------------------------------------------------------------ Fisch und Meeresfruechte

    @ParameterizedTest(name = "{0} ist Fisch")
    @CsvSource(
        "Dorade",
        "Goldbrasse",
        "Wolfsbarschfilet",
        "Seebarsch",
        "Sardinen",
        "Thon",
        "Thonmousse",
        "Kaviar",
        "Seezunge",
        "Matjesfilet",
        "Rotbarsch",
        "Heilbutt",
        "Eglifilets",
        "Seehechtrückenfilets",
        "Sardellenfilets",
    )
    fun `Fisch unter seinen Namen`(name: String) {
        assertTrue(Dislike.FISCH in zutat(name), name)
    }

    @Test
    fun `Austernpilze sind weder Meeresfruechte noch Fisch`() {
        val pilze = traits("Pilzpfanne", "Austernpilze", "Olivenöl")

        assertFalse(Dislike.MEERESFRUECHTE in pilze.dislikes)
        assertFalse(Intolerance.MEERESFRUECHTE in pilze.allergens)
        assertEquals(DietClass.VEGAN, pilze.diet)
    }

    @Test
    fun `was nach Fisch klingt, ist nicht immer einer`() {
        // "Fischerl" ist in Oesterreich das Schweinsfilet, "Muscheli" sind Teigwaren.
        assertFalse(Dislike.FISCH in classifier.classifyTitle("Schweinsfischerl in Pilzrahmsauce").dislikes)
        assertFalse(Dislike.MEERESFRUECHTE in classifier.classifyTitle("Muscheli-Gratin Express").dislikes)
        assertFalse(Dislike.FISCH in zutat("Lachsschinken"))
        assertTrue(Dislike.SCHWEIN in zutat("Lachsschinken"))
        assertTrue(Dislike.MEERESFRUECHTE in zutat("Miesmuscheln"))
        assertTrue(Dislike.MEERESFRUECHTE in zutat("Vongole"))
    }

    // ------------------------------------------------------------------ Fleisch

    @ParameterizedTest(name = "{0} ist Gefluegel")
    @CsvSource(
        "Trutenschnitzel",
        "Hendl",
        "Fasanenbrust",
        "Gänsebrust",
        "Mistkratzerli",
        "Coquelets",
        "Entenbrust",
        "Knusperente",
        "Hühnerbouillon",
        "Chicken Wings",
    )
    fun `Gefluegel unter seinen Namen`(name: String) {
        assertTrue(Dislike.POULET in zutat(name), name)
    }

    @ParameterizedTest(name = "{0} ist kein Gefluegel")
    @CsvSource(
        "Grüntee",
        "Sauerteigansatz",
        "Flammkuchenteig",
        "Frühlingsrollenteig",
        "Spaghetti al dente",
        "Orangenblütentee",
        "Wachteleier",
    )
    fun `in manchem Wort steckt bloss die Ente oder die Gans`(name: String) {
        assertFalse(Dislike.POULET in zutat(name), name)
    }

    @Test
    fun `Flammkuchen ist kein Lamm und kein Fleisch`() {
        val flammkuchen = traits("Zucchini-Flammkuchen mit Feta", "Weizenmehl", "Zucchini", "Saure Sahne", "Feta")

        assertFalse(Dislike.LAMM in flammkuchen.dislikes)
        assertFalse(Dislike.POULET in flammkuchen.dislikes)
        assertEquals(DietClass.VEGETARISCH, flammkuchen.diet)
        assertTrue(Dislike.LAMM in zutat("Lammnierstück"))
        assertFalse(Dislike.INNEREIEN in zutat("Lammnierstück"))
    }

    @Test
    fun `die Rinde ist kein Rind`() {
        assertFalse(Dislike.RIND in zutat("abgeriebene Zitronenrinde"))
        assertFalse(Dislike.RIND in zutat("Tamarindenpaste"))
        assertFalse(Intolerance.SOJA in traits("Curry", "Tamarindenpaste").allergens)
        assertFalse(Dislike.RIND in zutat("Ochsenherztomaten"))
        assertTrue(Dislike.RIND in zutat("Rindsfilet"))
        assertTrue(Dislike.RIND in zutat("Kalbsfond"))
        assertTrue(Dislike.RIND in zutat("Beiried"))
        assertEquals(DietClass.VEGAN, traits("Zitronensalat", "abgeriebene Zitronenrinde", "Salat").diet)
    }

    @Test
    fun `Hackfleisch ohne Tier ist Rind und Schwein - mit Tier nur dieses`() {
        assertEquals(setOf(Dislike.RIND, Dislike.SCHWEIN), zutat("Hackfleisch"))
        assertEquals(setOf(Dislike.RIND, Dislike.SCHWEIN), zutat("Faschiertes"))
        assertEquals(setOf(Dislike.RIND, Dislike.SCHWEIN), zutat("Gehacktes"))
        assertEquals(setOf(Dislike.RIND), zutat("Rindshackfleisch"))
        assertEquals(setOf(Dislike.POULET), zutat("Poulet-Hackfleisch"))
        assertEquals(setOf(Dislike.LAMM), zutat("Lammhack"))
    }

    @ParameterizedTest(name = "{0} ist bloss gehackt")
    @CsvSource(
        "gehackte Tomaten",
        "Tomaten gehackt",
        "gehackter Dill",
        "gehacktes Basilikum",
        "gehackte Haselnüsse",
    )
    fun `gehackt heisst nicht Hackfleisch`(name: String) {
        val gehackt = traits("Sauce", name)

        assertFalse(Dislike.RIND in gehackt.dislikes, name)
        assertFalse(gehackt.diet == DietClass.FLEISCH, name)
    }

    @Test
    fun `was nur nach Fleisch heisst, bleibt vegetarisch`() {
        assertEquals(DietClass.VEGAN, traits("Salat", "Fleischtomaten", "Olivenöl").diet)
        assertEquals(DietClass.VEGAN, traits("Suppe", "Kürbisfleisch", "Gemüsebouillon").diet)
        assertEquals(DietClass.VEGAN, classifier.classifyTitle("Pilzragout").diet)
        assertEquals(DietClass.VEGAN, classifier.classifyTitle("Kohlrabischnitzel").diet)
        assertEquals(DietClass.FISCH, classifier.classifyTitle("Thunfisch-Steak auf Gemüse").diet)
        assertEquals(DietClass.FLEISCH, traits("Suppe", "Fleischbouillon", "Lauch").diet)
        assertEquals(DietClass.FLEISCH, traits("Pfanne", "Kaninchenkeulen").diet)
        assertEquals(DietClass.FLEISCH, traits("Creme", "Gelatine", "Rahm").diet)
    }

    @Test
    fun `Schwein und Wurst - aber nicht die Palatschinke`() {
        assertFalse(Dislike.SCHWEIN in classifier.classifyTitle("Palatschinken mit Marillenmarmelade").dislikes)
        assertFalse(Dislike.SCHWEIN in zutat("Kalbshaxe"))
        assertTrue(Dislike.RIND in zutat("Kalbshaxe"))
        assertTrue(Dislike.SCHWEIN in zutat("Würstel"))
        assertTrue(Dislike.SCHWEIN in zutat("Cipollata"))
        assertFalse(Dislike.ZWIEBELN in zutat("Cipollata"))
        assertTrue(Dislike.SCHWEIN in zutat("Eisbein"))
        assertTrue(Dislike.SCHWEIN in zutat("Brätkügeli"))
        assertTrue(Dislike.SCHWEIN in zutat("Salsiz"))
    }

    @Test
    fun `Innereien - aber weder Garnitur noch Artischockenherzen`() {
        assertFalse(Dislike.INNEREIEN in zutat("Petersilie zum Garnieren"))
        assertFalse(Dislike.INNEREIEN in zutat("Artischockenherzen"))
        assertFalse(Dislike.INNEREIEN in zutat("Seezunge"))
        assertFalse(Dislike.INNEREIEN in zutat("Weizenkleber"))
        assertFalse(Dislike.INNEREIEN in classifier.classifyTitle("Lungenbraten-Gugelhupf").dislikes)
        assertTrue(Dislike.INNEREIEN in zutat("Kalbsnierli"))
        assertTrue(Dislike.INNEREIEN in zutat("Geflügelleber"))
        assertTrue(Dislike.INNEREIEN in zutat("Kalbsbeuschel"))
        assertTrue(Dislike.INNEREIEN in zutat("Rindszunge"))
    }

    @Test
    fun `ein Fleischersatz ist kein Fleisch - er zaehlt als Tofu und Fleischersatz`() {
        val wurst = traits("Znacht", "vegane Bratwurst", "Senf")
        val hack = traits("Veganes Gehacktes mit Hörnli", "Soja-Geschnetzeltes", "Hörnli")

        assertFalse(Dislike.SCHWEIN in wurst.dislikes)
        assertTrue(Dislike.TOFU in wurst.dislikes)
        assertFalse(wurst.diet == DietClass.FLEISCH)
        assertFalse(Dislike.RIND in hack.dislikes)
        assertTrue(Dislike.TOFU in hack.dislikes)
        // Sojasauce und eine vegane Sauce sind dagegen kein Fleischersatz.
        assertFalse(Dislike.TOFU in zutat("Sojasauce"))
        assertFalse(Dislike.TOFU in classifier.classifyTitle("Vegane Bratensosse").dislikes)
    }

    // ------------------------------------------------------------------ Gemuese, Kaese, Schaerfe

    @ParameterizedTest(name = "{0} ist Kohl")
    @CsvSource(
        "Rosenkohlauflauf",
        "Rotkohlsalat mit Granatapfel",
        "Vegane Wirsingrouladen",
        "Kohlrouladen mit Specksosse",
        "Wirzgratin",
        "Zuger Chabisbünteli",
        "Klassisch amerikanischer Coleslaw",
        "Apfel-Most-Kraut",
        "Kabissalat",
    )
    fun `Kohl auch in Zusammensetzungen`(titel: String) {
        assertTrue(Dislike.KOHL in classifier.classifyTitle(titel).dislikes, titel)
    }

    @Test
    fun `Blumenkohl, Brokkoli, Kohlrabi und Krautstiel sind kein Kohl`() {
        listOf("Blumenkohl", "Brokkoli", "Kohlrabi", "Krautstiel", "Bohnenkraut", "Kohlenhydratarme Pasta").forEach {
            assertFalse(Dislike.KOHL in zutat(it), it)
        }
    }

    @Test
    fun `Knoblauch, Zwiebeln, Oliven, Aubergine`() {
        assertTrue(Dislike.KNOBLAUCH in zutat("Bärlauch"))
        assertTrue(Dislike.KNOBLAUCH in classifier.classifyTitle("Spaghetti Aglio Olio e Peperoncino").dislikes)
        assertTrue(Dislike.ZWIEBELN in zutat("Bundzwiebeln"))
        assertTrue(Dislike.OLIVEN in zutat("Tapenade"))
        assertFalse(Dislike.OLIVEN in zutat("Oliven-Öl"))
        assertFalse(Dislike.OLIVEN in zutat("Olivenöl extra vergine"))
        assertTrue(Dislike.AUBERGINE in classifier.classifyTitle("Moussaka").dislikes)
    }

    @Test
    fun `Ziegen- und Blauschimmelkaese - auch mit Bindestrich`() {
        assertTrue(Dislike.KRAEFTIGER_KAESE in zutat("Ziegen-Frischkäse"))
        assertTrue(Dislike.KRAEFTIGER_KAESE in zutat("Ziegencamembert"))
        assertTrue(Dislike.KRAEFTIGER_KAESE in zutat("Schafkäse"))
        assertTrue(Dislike.KRAEFTIGER_KAESE in zutat("Blauschimmelkäse"))
        // "Tartiflette mit Schimmelkäse": Ohne Farbe ist fast immer der blaue gemeint.
        assertTrue(Dislike.KRAEFTIGER_KAESE in zutat("Schimmelkäse"))
        assertTrue(Dislike.KRAEFTIGER_KAESE in zutat("Edelschimmelkäse"))
        assertFalse(Dislike.KRAEFTIGER_KAESE in zutat("Weissschimmelkäse"))
        assertFalse(Dislike.KRAEFTIGER_KAESE in zutat("Gruyère"))
    }

    @Test
    fun `scharf ist Chili und was scharf heisst - mittelscharfer Senf nicht`() {
        assertTrue(Dislike.SCHARF in zutat("Paprikapulver scharf"))
        assertTrue(Dislike.SCHARF in classifier.classifyTitle("Spaghetti mit scharfen Garnelen").dislikes)
        assertTrue(Dislike.SCHARF in classifier.classifyTitle("Penne all'Arrabbiata").dislikes)
        assertTrue(Dislike.SCHARF in zutat("Wasabi"))
        assertFalse(Dislike.SCHARF in zutat("mittelscharfer Senf"))
        // "Scharf angebraten" in der Notiz ist eine Zubereitung, keine Schaerfe.
        val steak = Recipe(
            title = "Steak",
            ingredients = listOf(Ingredient(name = "Rindssteak", note = "scharf angebraten")),
        )
        assertFalse(Dislike.SCHARF in classifier.classify(steak).dislikes)
    }

    // ------------------------------------------------------------------ Milch, Gluten, Nuesse

    @ParameterizedTest(name = "{0} ist ein Milchprodukt")
    @CsvSource(
        "Crème fraîche",
        "Schlagobers",
        "Magertopfen",
        "Jogurt nature",
        "Crème double",
        "Sauerrahm",
        "Mascarpone",
    )
    fun `Milchprodukte in Schweizer und oesterreichischer Schreibweise`(name: String) {
        val traits = traits("Dessert", name)

        assertTrue(Intolerance.LAKTOSE in traits.allergens, name)
        assertEquals(DietClass.VEGETARISCH, traits.diet, name)
    }

    @Test
    fun `was nur nach Milch oder Butter heisst, bleibt vegan`() {
        listOf(
            "Kokosmilch",
            "Hafermilch",
            "Erdnussbutter",
            "Butternusskürbis",
            "vegane Butter",
            "Kakaobutter",
        ).forEach {
            assertEquals(DietClass.VEGAN, traits("Gericht", it).diet, it)
        }
        assertEquals(DietClass.VEGAN, traits("Fruchtsalat", "Honigmelone", "Minze").diet)
    }

    @Test
    fun `mehligkochende Kartoffeln haben kein Gluten, die Kalbsnuss ist keine Nuss`() {
        assertFalse(Intolerance.GLUTEN in traits("Stock", "mehligkochende Kartoffeln", "Salz").allergens)
        assertFalse(Intolerance.GLUTEN in traits("Brei", "Polentamehl").allergens)
        assertTrue(Intolerance.GLUTEN in traits("Znüni", "Baguette").allergens)
        // Ein Gewuerz, das nach einem Gebaeck heisst, ist kein Gebaeck.
        assertFalse(Intolerance.GLUTEN in traits("Gugelhupf", "Kürbis", "Spekulatiusgewürz").allergens)
        assertFalse(Intolerance.GLUTEN in traits("Braten", "Brotgewürz", "Lebkuchengewürz").allergens)
        assertTrue(Intolerance.GLUTEN in traits("Dessert", "Spekulatius").allergens)
        assertFalse(Intolerance.NUESSE in traits("Braten", "Kalbsnuss").allergens)
        assertFalse(Intolerance.NUESSE in traits("Suppe", "Butternusskürbis").allergens)
        assertTrue(Intolerance.NUESSE in traits("Kuchen", "Baumnüsse").allergens)
    }
}
