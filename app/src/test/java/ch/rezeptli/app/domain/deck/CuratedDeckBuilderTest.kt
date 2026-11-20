package ch.rezeptli.app.domain.deck

import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Cuisine
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.random.Random

@DisplayName("CuratedDeckBuilder")
class CuratedDeckBuilderTest {
    private val builder = CuratedDeckBuilder()

    // Feste Saat: der Stapel soll zufaellig wirken, aber pruefbar bleiben.
    private fun random() = Random(42)

    private fun eintraege(quelle: String, anzahl: Int): List<DeckEntry> =
        (1..anzahl).map {
            DeckEntry(
                url = "https://$quelle.example/rezept-$it",
                title = "$quelle $it",
                sourceId = quelle,
                sourceName = quelle,
            )
        }

    private val herkunft = mapOf(
        "gutekueche" to SourceOrigin("gutekueche", Country.SCHWEIZ),
        "kochrezepte" to SourceOrigin("kochrezepte", Country.OESTERREICH),
        "cuisineaz" to SourceOrigin("cuisineaz", Country.FRANKREICH),
        "cookaround" to SourceOrigin("cookaround", Country.ITALIEN),
    )

    private val alle = mapOf(
        "gutekueche" to eintraege("gutekueche", 200),
        "kochrezepte" to eintraege("kochrezepte", 200),
        "cuisineaz" to eintraege("cuisineaz", 200),
        "cookaround" to eintraege("cookaround", 200),
    )

    private val schweizerin = UserProfile(country = Country.SCHWEIZ)

    @Test
    fun `liefert genau so viele Karten wie verlangt`() {
        val stapel = builder.build(alle, herkunft, schweizerin, size = 30, random = random())

        assertEquals(30, stapel.size)
    }

    @Test
    fun `wiederholt kein Rezept`() {
        val stapel = builder.build(alle, herkunft, schweizerin, size = 60, random = random())

        assertEquals(stapel.size, stapel.map { it.url }.toSet().size)
    }

    @Test
    fun `ohne besonderes Interesse bleibt Fremdsprachiges eine Prise`() {
        val stapel = builder.build(alle, herkunft, schweizerin, size = 100, random = random())

        val fremd = stapel.count { it.sourceId == "cuisineaz" || it.sourceId == "cookaround" }
        assertEquals(15, fremd)
    }

    @Test
    fun `angegebene Kueche erhoeht den Anteil und trifft den richtigen Sprachraum`() {
        val profil = schweizerin.copy(cuisines = setOf(Cuisine.ITALIENISCH))

        val stapel = builder.build(alle, herkunft, profil, size = 100, random = random())

        assertEquals(40, stapel.count { it.sourceId == "cookaround" })
        // Franzoesisch wurde nicht gewuenscht, also gehoert es nicht in die Beimischung.
        assertEquals(0, stapel.count { it.sourceId == "cuisineaz" })
    }

    // Eine Quelle aus dem eigenen Land mit drei Sorten Gerichten: Die Kueche steht im Titel.
    private fun gemischteQuelle(curry: Int, pizza: Int, gratin: Int): Map<String, List<DeckEntry>> = mapOf(
        "gutekueche" to
            eintraege("gutekueche", curry + pizza + gratin).mapIndexed { index, eintrag ->
                val gericht = when {
                    index < curry -> "Curry"
                    index < curry + pizza -> "Pizza"
                    else -> "Gratin"
                }
                eintrag.copy(title = "$gericht ${index + 1}")
            },
    )

    private val kuecheAmTitel: (DeckEntry) -> Set<Cuisine> = { eintrag ->
        when {
            eintrag.title.startsWith("Curry") -> setOf(Cuisine.ASIATISCH)
            eintrag.title.startsWith("Pizza") -> setOf(Cuisine.ITALIENISCH)
            else -> emptySet()
        }
    }

    private fun List<DeckEntry>.anzahl(gericht: String) = count { it.title.startsWith(gericht) }

    @Test
    fun `eine gewaehlte Kueche faerbt den Stapel - die Haelfte Gemochtes, anderes nur ab und zu`() {
        val profil = schweizerin.copy(cuisines = setOf(Cuisine.ASIATISCH))

        val stapel = builder.build(
            gemischteQuelle(curry = 100, pizza = 100, gratin = 100),
            herkunft,
            profil,
            size = 40,
            random = random(),
            cuisinesOf = kuecheAmTitel,
        )

        assertEquals(20, stapel.anzahl("Curry"))
        assertEquals(4, stapel.anzahl("Pizza"))
        // Was zu keiner Kueche gehoert, fuellt den Rest - es wird weder bevorzugt noch zurueckgestellt.
        assertEquals(16, stapel.anzahl("Gratin"))
    }

    @Test
    fun `ohne gewaehlte Kueche spielt die Kueche eines Gerichts keine Rolle`() {
        val stapel = builder.build(
            gemischteQuelle(curry = 100, pizza = 100, gratin = 100),
            herkunft,
            schweizerin,
            size = 40,
            random = random(),
            cuisinesOf = { error("Ohne Vorliebe darf niemand nach der Kueche fragen") },
        )

        assertEquals(40, stapel.size)
    }

    @Test
    fun `eine Vorliebe verkleinert den Stapel nicht, wenn es zu wenig Gemochtes gibt`() {
        val profil = schweizerin.copy(cuisines = setOf(Cuisine.ASIATISCH))

        val stapel = builder.build(
            gemischteQuelle(curry = 3, pizza = 100, gratin = 100),
            herkunft,
            profil,
            size = 40,
            random = random(),
            cuisinesOf = kuecheAmTitel,
        )

        assertEquals(40, stapel.size)
        assertEquals(3, stapel.anzahl("Curry"))
        // Eine Vorliebe ist kein Filter - anderes bleibt aber auch dann die Ausnahme.
        assertEquals(4, stapel.anzahl("Pizza"))
        assertEquals(33, stapel.anzahl("Gratin"))
    }

    @Test
    fun `gibt es nur Gemochtes und anderes, kommt zuerst mehr Gemochtes`() {
        val profil = schweizerin.copy(cuisines = setOf(Cuisine.ASIATISCH))

        val stapel = builder.build(
            gemischteQuelle(curry = 100, pizza = 100, gratin = 0),
            herkunft,
            profil,
            size = 40,
            random = random(),
            cuisinesOf = kuecheAmTitel,
        )

        assertEquals(36, stapel.anzahl("Curry"))
        assertEquals(4, stapel.anzahl("Pizza"))
    }

    @Test
    fun `wer italienisch mag, bekommt auch Pizza aus den eigenen Quellen haeufiger`() {
        val profil = schweizerin.copy(cuisines = setOf(Cuisine.ITALIENISCH))
        val quellen = gemischteQuelle(curry = 100, pizza = 100, gratin = 100) +
            ("cookaround" to eintraege("cookaround", 200))

        val stapel = builder.build(quellen, herkunft, profil, size = 100, random = random(), cuisinesOf = kuecheAmTitel)

        // 40 aus der italienischen Quelle; von den 60 eigenen ist die Haelfte Pizza.
        assertEquals(40, stapel.count { it.sourceId == "cookaround" })
        assertEquals(30, stapel.anzahl("Pizza"))
        assertEquals(6, stapel.anzahl("Curry"))
        assertEquals(24, stapel.anzahl("Gratin"))
    }

    @Test
    fun `eine grosse Quelle verdraengt die kleine nicht`() {
        val gemischt = mapOf(
            "gutekueche" to eintraege("gutekueche", 17_000),
            "bettybossi" to eintraege("bettybossi", 16),
        )
        val herkunftKlein = mapOf(
            "gutekueche" to SourceOrigin("gutekueche", Country.SCHWEIZ),
            "bettybossi" to SourceOrigin("bettybossi", Country.SCHWEIZ),
        )

        val stapel = builder.build(gemischt, herkunftKlein, schweizerin, size = 30, random = random())

        // Reihum gezogen kommt die kleine Quelle auf die Haelfte, nicht auf 16/17016.
        assertEquals(15, stapel.count { it.sourceId == "bettybossi" })
    }

    @Test
    fun `bereits gesehene Rezepte kommen nicht wieder`() {
        val gesehen = alle
            .getValue("gutekueche")
            .take(150)
            .map { it.url }
            .toSet()

        val stapel = builder.build(alle, herkunft, schweizerin, size = 40, exclude = gesehen, random = random())

        assertTrue(stapel.none { it.url in gesehen })
        assertEquals(40, stapel.size)
    }

    @Test
    fun `fuellt auf wenn eine Gruppe zu klein ist`() {
        val duenn = mapOf(
            "gutekueche" to eintraege("gutekueche", 5),
            "cookaround" to eintraege("cookaround", 200),
        )

        val stapel = builder.build(duenn, herkunft, schweizerin, size = 30, random = random())

        // Nur 5 deutschsprachige Rezepte vorhanden - der Rest kommt aus der Beimischung,
        // statt den Stapel kurz zu lassen.
        assertEquals(30, stapel.size)
        assertEquals(5, stapel.count { it.sourceId == "gutekueche" })
    }

    @Test
    fun `ohne Wohnland gilt keine Quelle als fremd`() {
        val stapel = builder.build(alle, herkunft, UserProfile(), size = 40, random = random())

        assertEquals(40, stapel.size)
        assertEquals(setOf("gutekueche", "kochrezepte", "cuisineaz", "cookaround"), stapel.map { it.sourceId }.toSet())
    }

    @Test
    fun `leeres Verzeichnis ergibt einen leeren Stapel`() {
        assertTrue(builder.build(emptyMap(), herkunft, schweizerin, random = random()).isEmpty())
    }

    @Test
    fun `unbekannte Herkunft gilt als nah und nicht als Beimischung`() {
        val stapel = builder.build(
            mapOf("unbekannt" to eintraege("unbekannt", 100)),
            emptyMap(),
            schweizerin,
            size = 20,
            random = random(),
        )

        assertEquals(20, stapel.size)
    }

    @Test
    fun `zwei Ziehungen mit derselben Saat sind gleich`() {
        val a = builder.build(alle, herkunft, schweizerin, size = 30, random = Random(7))
        val b = builder.build(alle, herkunft, schweizerin, size = 30, random = Random(7))

        assertEquals(a.map { it.url }, b.map { it.url })
    }

    @Test
    fun `zwei Ziehungen mit anderer Saat unterscheiden sich`() {
        val a = builder.build(alle, herkunft, schweizerin, size = 30, random = Random(7))
        val b = builder.build(alle, herkunft, schweizerin, size = 30, random = Random(8))

        assertTrue(a.map { it.url } != b.map { it.url })
    }
}
