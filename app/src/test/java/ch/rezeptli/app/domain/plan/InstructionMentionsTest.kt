package ch.rezeptli.app.domain.plan

import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Was nur in der Zubereitung steht.
 *
 * Die Zutatenliste einer Rezeptseite ist nicht immer vollstaendig. Die Faelle hier stammen aus
 * dem Lauf an echten Seiten (FilterAuditTest): Bei rund jeder fuenfzigsten nannte nur die
 * Zubereitung, was jemand ausgeschlossen haben koennte - und bei etwa jeder zehnten stand dort
 * etwas, das nach einem Lebensmittel klingt und keines ist. Beides halten diese Tests fest.
 */
class InstructionMentionsTest {
    private val classifier = RecipeClassifier()

    private fun traits(titel: String, zubereitung: String, vararg zutaten: String, tags: List<String> = emptyList()) =
        classifier.classify(
            Recipe(
                title = titel,
                instructions = zubereitung,
                ingredients = zutaten.map { Ingredient(name = it) },
                tags = tags,
            ),
        )

    // ------------------------------------------------------------------ was gefunden werden muss

    @Test
    fun `die Seite mit der verstuemmelten Zutatenliste - Zwiebel und Sardellen stehen nur im Text`() {
        // ichkoche.at, "Gebackene Champignons": Die Seite selbst nennt als Zutaten nur diese vier.
        val seite = traits(
            "Gebackene Champignons",
            "Champignons halbieren, mit Salz und Pfeffer überstreuen, gut mit Mehl bestäuben, durch verquirltes " +
                "Ei ziehen und in Semmelbrösel panieren, dann im heissen Backfett hellbraun backen. 10 dkg Majo " +
                "mit jeweils 1 El gehackter Petersilie, Zwiebel, Essiggurkerl durchmischen und mit gehackten " +
                "Saft einer Zitrone, Kapern, Sardellen und Zucker pikant abschmecken.",
            "Pfeffer",
            "Ehl",
            "Eier",
            "Emmelbroesel",
        )

        assertTrue(Dislike.PILZE in seite.dislikes)
        assertTrue(Dislike.ZWIEBELN in seite.dislikes)
        assertTrue(Dislike.FISCH in seite.dislikes)
        // Mit Sardellen ist es nichts fuer jemanden, der vegetarisch isst.
        assertEquals(DietClass.FISCH, seite.diet)
    }

    @Test
    fun `in Schmalz gebacken ist nicht vegetarisch`() {
        val backerbsen = traits(
            "Backerbsen",
            "Den Teig durch ein Sieb in heisses Schmalz tropfen lassen und goldgelb backen.",
            "Mehl",
            "Eier",
            "Milch",
            "Fett zum Backen",
        )

        assertEquals(DietClass.FLEISCH, backerbsen.diet)
        assertTrue(Dislike.SCHWEIN in backerbsen.dislikes)
    }

    @Test
    fun `Fleisch, Gefluegel und Innereien aus dem Text zaehlen`() {
        val fuellung = traits(
            "Gefüllter Braten",
            "Für die Füllung die Truthahnleber fein schneiden und mit den Innereien anbraten.",
            "Semmelwürfel",
            "Petersilie",
        )
        val pfanne = traits(
            "Reispfanne",
            "Das Hackfleisch krümelig anbraten, dann den Reis dazugeben.",
            "Reis",
            "Peperoni",
        )
        val eintopf = traits("Eintopf", "Die Pouletbrust in Würfel schneiden und mitkochen.", "Karotten", "Lauch")

        assertTrue(Dislike.INNEREIEN in fuellung.dislikes)
        assertTrue(Dislike.POULET in fuellung.dislikes)
        // Hackfleisch ohne Tierangabe: wie in der Zutatenliste Rind und Schwein.
        assertEquals(setOf(Dislike.RIND, Dislike.SCHWEIN), pfanne.dislikes)
        assertEquals(DietClass.FLEISCH, pfanne.diet)
        assertTrue(Dislike.POULET in eintopf.dislikes)
    }

    @Test
    fun `Meeresfruechte aus dem Text zaehlen auch als Unvertraeglichkeit`() {
        val risotto = traits("Risotto", "Zum Schluss die Crevetten unterheben und kurz ziehen lassen.", "Risottoreis")

        assertTrue(Dislike.MEERESFRUECHTE in risotto.dislikes)
        assertTrue(Intolerance.MEERESFRUECHTE in risotto.allergens)
        assertEquals(DietClass.FISCH, risotto.diet)
    }

    @Test
    fun `Pilze, Knoblauch, Koriander, Oliven, Aubergine und Kohl aus dem Text zaehlen`() {
        val gericht = traits(
            "Ofengemüse",
            "Steinpilze putzen. Knoblauch pressen, Koriander hacken. Oliven halbieren, die Aubergine würfeln " +
                "und den Wirsing in Streifen schneiden.",
            "Gemüse nach Saison",
        )

        assertEquals(
            setOf(
                Dislike.PILZE,
                Dislike.KNOBLAUCH,
                Dislike.KORIANDER,
                Dislike.OLIVEN,
                Dislike.AUBERGINE,
                Dislike.KOHL,
            ),
            gericht.dislikes,
        )
    }

    // ------------------------------------------------------------------ was nicht zaehlen darf

    @Test
    fun `Geraete und Vergleiche sind keine Zutaten`() {
        val gericht = traits(
            "Gemüsepfanne",
            "Die Kartoffeln in walnussgrosse Stücke schneiden, den Lauch in speckwürfelgrosse. Kräuter mit dem " +
                "Zwiebelhacker zerkleinern, den Ingwer durch die Knoblauchpresse drücken. Die Rüebli lachsfarben " +
                "dünsten und mit dem Fischheber wenden. Mit der Geflügelschere die Kräuter schneiden.",
            "Kartoffeln",
            "Lauch",
            "Rüebli",
            "Ingwer",
        )

        assertTrue(gericht.dislikes.isEmpty(), "erkannt wurde ${gericht.dislikes}")
        assertEquals(DietClass.VEGAN, gericht.diet)
    }

    @Test
    fun `Tunwoerter und Redensarten sind keine Zutaten`() {
        val gericht = traits(
            "Schmorgemüse",
            "Scharf anbraten. Während das Gemüse brät, die Lorbeerblätter herausfischen. Es zergeht auf der " +
                "Zunge.",
            "Gemüse",
            "Lorbeer",
        )

        assertTrue(gericht.dislikes.isEmpty(), "erkannt wurde ${gericht.dislikes}")
        assertEquals(DietClass.VEGAN, gericht.diet)
    }

    @Test
    fun `die Haxen im Text sind die Kalbshaxen aus der Zutatenliste - kein Schwein`() {
        val ossobuco = traits(
            "Ossobuco",
            "Die Haxen rundum anbraten, mit Weisswein ablöschen und zugedeckt schmoren.",
            "Kalbshaxen",
            "Weisswein",
        )

        assertTrue(Dislike.RIND in ossobuco.dislikes)
        assertFalse(Dislike.SCHWEIN in ossobuco.dislikes)
    }

    @Test
    fun `nach einem Gericht benannt heisst nicht, dass Fleisch drin ist`() {
        val bratlinge = traits(
            "Karotten-Couscous-Bratlinge",
            "Die Bratlinge wie ein Schnitzel panieren und von beiden Seiten braten. Schmeckt wie Gulasch.",
            "Karotten",
            "Couscous",
        )

        assertEquals(DietClass.VEGAN, bratlinge.diet)
        assertTrue(bratlinge.dislikes.isEmpty(), "erkannt wurde ${bratlinge.dislikes}")
    }

    @Test
    fun `ein Rezept, das sich als fleischlos ausweist, meint mit Wuerstchen den Ersatz`() {
        val vegan = traits(
            "Vegane Bratwurst mit Kartoffelstock",
            "Die Würstchen in der Pfanne anbraten. Den Speck weglassen.",
            "vegane Bratwürste",
            "Kartoffeln",
        )
        val gruenkohl = traits(
            "Vegetarischer Grünkohl",
            "Den Räuchertofu wie Speck knusprig braten.",
            "Grünkohl",
            "Räuchertofu",
        )

        assertEquals(DietClass.VEGAN, vegan.diet)
        assertFalse(Dislike.SCHWEIN in vegan.dislikes)
        assertEquals(DietClass.VEGAN, gruenkohl.diet)
        // Die Pflanzen aus dem Text zaehlen auch hier.
        assertTrue(Dislike.KOHL in gruenkohl.dislikes)
    }

    @Test
    fun `Serviervorschlaege machen aus einem Rezept kein Gluten und keine Milch`() {
        val salat = traits(
            "Randensalat",
            "Dazu passt Baguette. Wer mag, streut Feta darüber oder serviert Sauerrahm dazu. In walnussgrosse " +
                "Stücke schneiden.",
            "Randen",
            "Äpfel",
        )

        assertTrue(salat.allergens.isEmpty(), "erkannt wurde ${salat.allergens}")
        assertEquals(DietClass.VEGAN, salat.diet)
    }

    @Test
    fun `Trueffel aus Schokolade bleiben Konfekt - auch wenn der Text sie nennt`() {
        val pralinen = traits(
            "Kaffee-Trüffel-Pralinen",
            "Aus der Masse kleine Trüffel formen und in Schokoraspeln wälzen.",
            "Kuvertüre",
            "Espresso",
        )

        assertFalse(Dislike.PILZE in pralinen.dislikes)
    }

    // ------------------------------------------------------------------ Ausnahmen in den Listen

    @Test
    fun `das Ei eines Huhns ist kein Huhn - der Huehnereintopf schon`() {
        assertFalse(Dislike.POULET in traits("Omelette", "", "Hühnereier").dislikes)
        assertEquals(DietClass.VEGETARISCH, traits("Omelette", "", "Hühnereier").diet)
        assertTrue(Dislike.POULET in traits("Hühnereintopf", "", "Karotten").dislikes)
    }

    @Test
    fun `Ochsenaugen sind Spiegeleier und Sushi-Reis ist Reis`() {
        assertFalse(Dislike.RIND in traits("Spinat mit Ochsenaugen", "", "Spinat", "Eier").dislikes)
        assertFalse(Dislike.FISCH in traits("Gemüse-Bowl", "", "Sushi-Reis", "Avocado").dislikes)
        assertTrue(Dislike.FISCH in traits("Sushi", "", "Sushi-Reis", "Lachs").dislikes)
    }

    @Test
    fun `ohne Zubereitung aendert sich nichts`() {
        val titel = classifier.classifyTitle("Kartoffelgratin")

        assertTrue(titel.dislikes.isEmpty())
    }
}
