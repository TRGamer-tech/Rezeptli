package ch.rezeptli.app.domain.nutrition

import ch.rezeptli.app.domain.parser.IngredientTextParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Die Schaetzung von Kalorien und Eiweiss aus der Zutatenliste.
 *
 * Gewuenscht war eine Schaetzung fuer jedes Rezept, wenn die Quelle nichts nennt. Geprueft
 * wird mit Zutatenzeilen, wie Rezepte sie schreiben - sie laufen durch denselben Parser wie
 * in der App - und gegen Werte, die man mit einer Naehrwerttabelle von Hand nachrechnen kann.
 */
class NutritionEstimatorTest {
    private val parser = IngredientTextParser()

    private fun zutaten(vararg zeilen: String) = zeilen.mapIndexed { index, zeile ->
        requireNotNull(parser.parseLine(zeile)) { "keine Zutat: $zeile" }.toIngredient(index)
    }

    private fun schaetze(vararg zeilen: String): Nutrients? = NutritionEstimator.estimate(zutaten(*zeilen))

    private fun kcal(vararg zeilen: String): Double = requireNotNull(schaetze(*zeilen)) { "keine Schätzung" }.kcal

    private fun eiweiss(vararg zeilen: String): Double = requireNotNull(schaetze(*zeilen)).proteinGrams

    private fun assertUngefaehr(erwartet: Double, wert: Double, spiel: Double = 0.08) {
        val abweichung = kotlin.math.abs(wert - erwartet)
        assertTrue(abweichung <= erwartet * spiel + 1.0) { "erwartet etwa $erwartet, war $wert" }
    }

    // ------------------------------------------------------------------ ganze Rezepte

    @Test
    fun `Spaghetti Bolognese fuer vier kommt auf gut 650 Kalorien und 30 Gramm Eiweiss pro Portion`() {
        val ganz = schaetze(
            "400 g Spaghetti",
            "300 g Rindshackfleisch",
            "1 Zwiebel",
            "2 Knoblauchzehen",
            "2 EL Olivenöl",
            "400 g Pelati",
            "50 g Parmesan",
            "Salz, Pfeffer",
        )!!

        // Von Hand: 1420 (Teigwaren) + 690 (Fleisch) + 40 + 11 + 211 (Öl) + 100 + 200 (Käse) = rund 2670 kcal.
        assertUngefaehr(2670.0, ganz.kcal, spiel = 0.03)
        assertUngefaehr(668.0, ganz.kcal / 4, spiel = 0.03)
        // 50 (Teigwaren) + 57 (Fleisch) + 17 (Käse) + 7 (Gemüse) = rund 131 g.
        assertUngefaehr(131.0, ganz.proteinGrams, spiel = 0.04)
    }

    @Test
    fun `Rösti mit Spiegelei rechnet Stueck und Loeffel in Gramm um`() {
        val ganz = schaetze("800 g Kartoffeln", "4 Eier", "2 EL Bratbutter", "1 Prise Salz")!!

        // 616 (Kartoffeln) + 330 (4 × 55 g Ei) + 180 (24 g Butter).
        assertUngefaehr(1126.0, ganz.kcal, spiel = 0.02)
        assertUngefaehr(44.0, ganz.proteinGrams)
    }

    @Test
    fun `ein Kuchen zaehlt Mehl, Zucker, Butter und Eier`() {
        val ganz = schaetze(
            "250 g Mehl",
            "200 g Zucker",
            "200 g Butter",
            "4 Eier",
            "1 Päckchen Backpulver",
            "1 dl Milch",
            "1 Prise Salz",
        )!!

        // 850 + 800 + 1500 + 330 + 67 (103 g Milch) = rund 3550 kcal.
        assertUngefaehr(3550.0, ganz.kcal, spiel = 0.02)
    }

    // ------------------------------------------------------------------ Einheiten

    @Test
    fun `Deziliter und Liter rechnen mit dem Gewicht der Fluessigkeit`() {
        assertUngefaehr(680.0, kcal("2 dl Rahm"), spiel = 0.01)
        // Öl ist leichter als Wasser: 1 dl wiegt 92 g.
        assertUngefaehr(810.0, kcal("1 dl Olivenöl"), spiel = 0.01)
        assertUngefaehr(670.0, kcal("1 l Milch"), spiel = 0.01)
    }

    @Test
    fun `ein Essloeffel wiegt je nach Zutat anders`() {
        assertUngefaehr(105.0, kcal("1 EL Öl"), spiel = 0.02)
        assertUngefaehr(27.0, kcal("1 EL Mehl"), spiel = 0.05)
        assertUngefaehr(62.0, kcal("1 EL Honig"), spiel = 0.02)
        // Ein Teeloeffel ist ein Drittel davon.
        assertUngefaehr(17.0, kcal("1 TL Zucker"), spiel = 0.05)
    }

    @Test
    fun `Dose, Bund, Zehe, Scheibe und Packung haben ihre ueblichen Gewichte`() {
        // Eine Dose Kichererbsen: 240 g abgetropft und gekocht.
        assertUngefaehr(288.0, kcal("1 Dose Kichererbsen"), spiel = 0.01)
        assertUngefaehr(100.0, kcal("1 Dose Pelati"), spiel = 0.01)
        assertUngefaehr(760.0, kcal("1 Dose Kokosmilch"), spiel = 0.01)
        assertUngefaehr(12.0, kcal("1 Bund Petersilie"), spiel = 0.01)
        assertUngefaehr(11.0, kcal("2 Zehen Knoblauch"), spiel = 0.05)
        assertUngefaehr(200.0, kcal("2 Scheiben Brot"), spiel = 0.01)
        assertUngefaehr(1140.0, kcal("1 Packung Blätterteig"), spiel = 0.01)
    }

    @Test
    fun `was im Namen steht statt in der Einheit, zaehlt trotzdem - cl, Becher, Handvoll`() {
        assertUngefaehr(92.0, kcal("4 cl Rum"), spiel = 0.01)
        assertUngefaehr(680.0, kcal("1 Becher Rahm"), spiel = 0.01)
        assertUngefaehr(6.0, kcal("1 Handvoll Rucola"), spiel = 0.01)
    }

    @Test
    fun `Stueckzahlen kennen ihr Gewicht`() {
        assertUngefaehr(83.0, kcal("1 Ei"), spiel = 0.02)
        assertUngefaehr(40.0, kcal("1 Zwiebel"), spiel = 0.01)
        assertUngefaehr(375.0, kcal("1 Mozzarella"), spiel = 0.01)
        assertUngefaehr(240.0, kcal("1 Avocado"), spiel = 0.01)
        assertUngefaehr(416.0, kcal("2 Pouletbrüste"), spiel = 0.01)
        // Ein ganzes Poulet ist kein Brustfilet.
        assertUngefaehr(1330.0, kcal("1 Poulet"), spiel = 0.01)
        assertUngefaehr(540.0, kcal("2 Bratwürste"), spiel = 0.01)
    }

    @Test
    fun `Tranchen, Pakete und Beutel sind Schweizer Rezeptdeutsch fuer Scheiben und Packungen`() {
        assertUngefaehr(396.0, kcal("8 Tranchen Bratspeck"), spiel = 0.01)
        assertUngefaehr(700.0, kcal("1 Paket Tortellini"), spiel = 0.01)
        assertUngefaehr(20.0, kcal("1 Beutel Rucola"), spiel = 0.01)
        assertUngefaehr(1140.0, kcal("1 Rolle Blätterteig"), spiel = 0.01)
    }

    // ------------------------------------------------------------------ gekocht oder trocken

    @Test
    fun `Linsen sind trocken gemeint, Kichererbsen und Bohnen gekocht - ausser es steht anders da`() {
        assertUngefaehr(990.0, kcal("300 g rote Linsen"), spiel = 0.01)
        assertUngefaehr(264.0, kcal("1 Dose Linsen"), spiel = 0.01)
        assertUngefaehr(480.0, kcal("400 g Kichererbsen"), spiel = 0.01)
        assertUngefaehr(700.0, kcal("200 g getrocknete Kichererbsen"), spiel = 0.01)
        assertUngefaehr(400.0, kcal("400 g Kidneybohnen"), spiel = 0.01)
        assertUngefaehr(150.0, kcal("500 g grüne Bohnen"), spiel = 0.01)
    }

    @Test
    fun `gekochter Reis vom Vortag ist nicht trockener Reis`() {
        assertUngefaehr(1050.0, kcal("300 g Reis"), spiel = 0.01)
        assertUngefaehr(520.0, kcal("400 g gekochter Reis"), spiel = 0.01)
    }

    // ------------------------------------------------------------------ Namen, die taeuschen

    @Test
    fun `im Schweinefleisch steckt kein Wein und in mehligen Kartoffeln kein Mehl`() {
        assertUngefaehr(800.0, kcal("500 g Schweinefleisch"), spiel = 0.01)
        assertUngefaehr(105.0, eiweiss("500 g Schweinsfilet"), spiel = 0.06)
        assertUngefaehr(616.0, kcal("800 g mehligkochende Kartoffeln"), spiel = 0.01)
        // Kein Lamm - und auch kein Kuchenteig, sondern ein Teig ohne viel Fett.
        assertUngefaehr(1500.0, kcal("500 g Flammkuchenteig"), spiel = 0.01)
    }

    @Test
    fun `Zusammensetzungen meinen das Ganze, nicht ihre Teile`() {
        assertUngefaehr(380.0, kcal("2 dl Kokosmilch"), spiel = 0.01)
        assertUngefaehr(98.0, kcal("1 EL Erdnussbutter"), spiel = 0.02)
        assertUngefaehr(200.0, kcal("500 g Butternusskürbis"), spiel = 0.01)
        assertUngefaehr(700.0, kcal("200 g Milchreis"), spiel = 0.01)
        assertUngefaehr(40.0, kcal("100 g Zuckerschoten"), spiel = 0.01)
        assertUngefaehr(20.0, kcal("100 g Nüsslisalat"), spiel = 0.01)
        assertUngefaehr(6.0, kcal("1 EL Reisessig"), spiel = 0.2)
        assertUngefaehr(100.0, kcal("100 g Seelachs"), spiel = 0.01)
        assertUngefaehr(200.0, kcal("100 g Räucherlachs"), spiel = 0.01)
        assertUngefaehr(20.0, kcal("100 g Mangold"), spiel = 0.01)
        assertUngefaehr(15.0, kcal("100 g Essiggurken"), spiel = 0.01)
        assertUngefaehr(310.0, kcal("50 g gemahlene Mandeln"), spiel = 0.01)
        // Wurst bleibt Wurst, auch vom Kalb und in der Mehrzahl.
        assertUngefaehr(1080.0, kcal("4 Kalbsbratwürste"), spiel = 0.01)
        assertUngefaehr(400.0, kcal("500 g Rahmspinat"), spiel = 0.01)
        assertUngefaehr(114.0, kcal("30 g Proteinpulver"), spiel = 0.01)
        assertUngefaehr(880.0, kcal("1 dl Kürbiskernöl"), spiel = 0.1)
    }

    @Test
    fun `Wasser, Gewuerze, Eiswuerfel und Backpulver zaehlen nichts - auch nicht als unbekannt`() {
        val mit = kcal(
            "200 g Reis",
            "5 dl Wasser",
            "1 dl Nudelwasser",
            "1 TL Paprikapulver",
            "1 Prise Muskatnuss",
            "2 Lorbeerblätter",
            "1 Päckchen Backpulver",
            "6 Eiswürfel",
            "Salz und Pfeffer",
        )

        assertEquals(kcal("200 g Reis"), mit)
    }

    @Test
    fun `Bouillon ist Fluessigkeit, kein Fleisch`() {
        assertUngefaehr(25.0, kcal("5 dl Rindsbouillon"), spiel = 0.01)
        assertUngefaehr(40.0, kcal("8 dl Gemüsebrühe"), spiel = 0.01)
    }

    // ------------------------------------------------------------------ Fett

    @Test
    fun `Fett ohne Mengenangabe zaehlt als knapper Essloeffel`() {
        assertUngefaehr(88.0, kcal("Öl zum Braten"), spiel = 0.01)
        assertUngefaehr(75.0, kcal("Butter für die Form"), spiel = 0.01)
    }

    @Test
    fun `vom Frittieroel landet nur ein Bruchteil im Essen`() {
        // Ein Liter Öl waeren 8000 kcal - im Essen bleibt etwa ein Zehntel.
        assertUngefaehr(810.0, kcal("1 l Öl zum Frittieren"), spiel = 0.01)
    }

    // ------------------------------------------------------------------ wenn es nicht reicht

    @Test
    fun `ohne Mengen gibt es keine Schaetzung`() {
        assertNull(schaetze("Mehl", "Eier", "Milch", "Salz"))
        assertNull(NutritionEstimator.estimate(emptyList()))
    }

    @Test
    fun `nur Wasser und Gewuerze ergeben keine Schaetzung`() {
        assertNull(schaetze("1 l Wasser", "1 TL Salz", "2 Lorbeerblätter"))
    }

    @Test
    fun `bleibt zu viel unerkannt, gibt es lieber keine Zahl als eine falsche`() {
        // Zwei von drei Zutaten sagen der App nichts.
        assertNull(schaetze("300 g Quorgelschnatz", "2 Flimmerknollen", "1 Zwiebel"))
        // Eine unter vielen stoert nicht.
        assertNotNull(schaetze("400 g Spaghetti", "200 g Speck", "3 Eier", "50 g Parmesan", "100 g Quorgelschnatz"))
    }

    @Test
    fun `was die Tabelle nicht kennt, bekommt den Wert seiner Warengruppe`() {
        // "Kalbsbäggli" steht nirgends - als Fleisch zaehlt es trotzdem.
        val mit = kcal("600 g Kalbsbäggli", "1 Zwiebel")

        assertTrue(mit > 700.0) { "Fleisch fehlt: $mit" }
    }
}
