package ch.rezeptli.app.presentation.deck

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.deck.DeckTitles
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.nutrition.NutritionBasis
import ch.rezeptli.app.domain.nutrition.NutritionValue
import ch.rezeptli.app.domain.nutrition.SourceNutrition
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.plan.CheckRecipeCardUseCase
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.ReadRecipeCardUseCase
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.domain.translate.NoTranslation
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Das Wischen allein: was gezeigt wird (nur Neues, nur Passendes, nur mit Bild), wann die
 * Runde endet, und dass sich eine Entscheidung zuruecknehmen und eine Karte ansehen laesst.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val herkunft = mapOf("a" to SourceOrigin("a", Country.SCHWEIZ))

    private fun karte(titel: String, mitBild: Boolean = true): DeckEntry {
        val name = titel.lowercase().replace(' ', '-')
        return DeckEntry(
            url = "https://a.example/$name",
            title = titel,
            imageUrl = if (mitBild) "https://a.example/$name.jpg" else null,
            sourceId = "a",
            sourceName = "Quelle A",
        )
    }

    private fun pool(vararg karten: DeckEntry) = DeckPool(
        entriesBySource = mapOf("a" to karten.toList()),
        origins = herkunft,
    )

    private fun seite(karte: DeckEntry, vararg zutaten: String) = karte.url to WebRecipe(
        title = karte.title,
        ingredientLines = zutaten.toList(),
        instructions = "Geheime Zubereitung, die auf einer Karte nichts verloren hat.",
        imageUrl = karte.imageUrl,
        totalMinutes = 35,
        servings = "4",
        sourceUrl = karte.url,
        sourceName = "Quelle A",
    )

    private val mitBild = karte("Hat ein Bild")
    private val ohneBildAberNachladbar = karte("Bild kommt nach", mitBild = false)
    private val ohneBildUndUnrettbar = karte("Kein Bild zu holen", mitBild = false)
    private val dreiKarten = pool(mitBild, ohneBildAberNachladbar, ohneBildUndUnrettbar)

    /** Ein Vorrat, der fuer eine lange Runde reicht. */
    private val vieleKarten = pool(*(1..60).map { karte("Gericht $it") }.toTypedArray())

    private fun createViewModel(
        webRepository: FakeWebRecipeRepository = FakeWebRecipeRepository(pool = dreiKarten).apply {
            cardImagesByUrl = mapOf(ohneBildAberNachladbar.url to "https://a.example/nachgeladen.jpg")
        },
        recipeRepository: FakeRecipeRepository = FakeRecipeRepository(),
        profile: UserProfile = UserProfile(country = Country.SCHWEIZ),
        profileRepository: FakeUserProfileRepository = FakeUserProfileRepository(profile),
    ): DeckViewModel {
        val readCard = ReadRecipeCardUseCase(webRepository, IngredientTextParser(), RecipeClassifier())
        return DeckViewModel(
            buildDeck = BuildSwipeDeckUseCase(
                webRepository = webRepository,
                profileRepository = profileRepository,
                builder = CuratedDeckBuilder(),
                titles = DeckTitles(RecipeClassifier()),
                dispatcher = mainDispatcher.testDispatcher,
            ),
            selectionHolder = SharedSelectionHolder(),
            webRepository = webRepository,
            loadWebRecipe = LoadWebRecipeUseCase(
                webRepository,
                IngredientTextParser(),
                InstructionSplitter(),
                NoTranslation,
            ),
            saveRecipe = SaveRecipeUseCase(recipeRepository, InstructionSplitter()),
            profileRepository = profileRepository,
            recipeRepository = recipeRepository,
            checkCard = CheckRecipeCardUseCase(readCard),
            readCard = readCard,
        )
    }

    private fun DeckViewModel.sichtbareTitel() = uiState.value.remainingCards.map { it.title }

    private fun DeckViewModel.wischen(titel: String, liked: Boolean) {
        onSwiped(uiState.value.cards.first { it.title == titel }, liked)
    }

    // ------------------------------------------------------------------ was gezeigt wird

    @Test
    fun `mit Allergie in den Vorlieben erscheinen nur gepruefte, passende Karten`() = runTest {
        val mitNuessen = karte("Linzer Schnitten")
        val ohneNuesse = karte("Rösti")
        val unlesbar = karte("Unlesbar")
        val web = FakeWebRecipeRepository(
            pool = pool(mitNuessen, ohneNuesse, unlesbar),
            recipeByUrl = mapOf(
                seite(mitNuessen, "200 g Haselnüsse", "100 g Zucker"),
                seite(ohneNuesse, "800 g Kartoffeln", "1 EL Bratbutter"),
            ),
        )
        val viewModel = createViewModel(
            webRepository = web,
            profile = UserProfile(country = Country.SCHWEIZ, intolerances = setOf(Intolerance.NUESSE)),
        )

        viewModel.onStart()

        // Die Nuesse stehen nur in den Zutaten, nicht im Titel - und was sich nicht pruefen
        // laesst, wird im Zweifel nicht gezeigt.
        assertEquals(listOf("Rösti"), viewModel.sichtbareTitel())
    }

    @Test
    fun `was jemand nicht mag, erscheint nicht - ob es im Titel steht oder nur in den Zutaten`() = runTest {
        val offensichtlich = karte("Lachsfilet mit Dill")
        val versteckt = karte("Sommerliche Gemüsepfanne")
        val harmlos = karte("Kartoffelgratin")
        val web = FakeWebRecipeRepository(
            pool = pool(offensichtlich, versteckt, harmlos),
            recipeByUrl = mapOf(
                seite(offensichtlich, "400 g Lachsfilet", "1 Bund Dill"),
                seite(versteckt, "2 Zucchetti", "6 Sardellenfilets"),
                seite(harmlos, "800 g Kartoffeln", "2 dl Rahm"),
            ),
        )
        val viewModel = createViewModel(
            webRepository = web,
            profile = UserProfile(country = Country.SCHWEIZ, dislikes = setOf(Dislike.FISCH)),
        )

        viewModel.onStart()

        assertEquals(listOf("Kartoffelgratin"), viewModel.sichtbareTitel())
    }

    @Test
    fun `wer mitten in der Runde etwas ausschliesst, sieht es danach nicht mehr`() = runTest {
        // Gemeldet: Pilzgerichte trotz "keine Pilze". Der Stapel bleibt liegen, waehrend man im
        // Tab "Vorlieben" etwas antippt - und zeigte danach weiter, was vorher gezogen war.
        val imTitel = karte("Pilzrisotto")
        val versteckt = karte("Sommerliche Gemüsepfanne")
        val harmlos = karte("Kartoffelgratin")
        val schonGemocht = karte("Rahmschnitzel")
        val web = FakeWebRecipeRepository(
            pool = pool(imTitel, versteckt, harmlos, schonGemocht),
            recipeByUrl = mapOf(
                seite(imTitel, "300 g Risottoreis", "250 g Steinpilze"),
                seite(versteckt, "2 Zucchetti", "200 g braune Champignons"),
                seite(harmlos, "800 g Kartoffeln", "2 dl Rahm"),
                seite(schonGemocht, "4 Schweinsschnitzel", "150 g Eierschwämmli"),
            ),
        )
        val vorlieben = FakeUserProfileRepository(UserProfile(country = Country.SCHWEIZ))
        val viewModel = createViewModel(webRepository = web, profileRepository = vorlieben)
        viewModel.onTargetChange(null)
        viewModel.onStart()
        assertEquals(4, viewModel.sichtbareTitel().size)
        viewModel.wischen("Rahmschnitzel", liked = true)

        vorlieben.update { it.copy(dislikes = setOf(Dislike.PILZE)) }

        assertEquals(listOf("Kartoffelgratin"), viewModel.sichtbareTitel())
        assertTrue(
            viewModel.uiState.value.rules.filter.dislikes
                .contains(Dislike.PILZE),
        )
        // Was schon gefallen hat, bleibt - das war eine eigene Entscheidung.
        assertEquals(
            listOf("Rahmschnitzel"),
            viewModel.uiState.value.liked
                .map { it.title },
        )
        assertEquals(DeckStep.WISCHEN, viewModel.uiState.value.step)
    }

    @Test
    fun `faellt eine Einschraenkung weg, kommen die Karten zurueck`() = runTest {
        val fisch = karte("Lachsfilet mit Dill")
        val harmlos = karte("Kartoffelgratin")
        val web = FakeWebRecipeRepository(
            pool = pool(fisch, harmlos),
            recipeByUrl = mapOf(
                seite(fisch, "400 g Lachsfilet", "1 Bund Dill"),
                seite(harmlos, "800 g Kartoffeln", "2 dl Rahm"),
            ),
        )
        val vorlieben =
            FakeUserProfileRepository(UserProfile(country = Country.SCHWEIZ, dislikes = setOf(Dislike.FISCH)))
        val viewModel = createViewModel(webRepository = web, profileRepository = vorlieben)
        viewModel.onTargetChange(null)
        viewModel.onStart()
        assertEquals(listOf("Kartoffelgratin"), viewModel.sichtbareTitel())

        vorlieben.update { it.copy(dislikes = emptySet()) }

        assertEquals(setOf("Kartoffelgratin", "Lachsfilet mit Dill"), viewModel.sichtbareTitel().toSet())
    }

    @Test
    fun `eine Aenderung, die mit dem Essen nichts zu tun hat, laesst den Stapel in Ruhe`() = runTest {
        val vorlieben = FakeUserProfileRepository(UserProfile(country = Country.SCHWEIZ))
        val viewModel = createViewModel(
            webRepository = FakeWebRecipeRepository(pool = vieleKarten),
            profileRepository = vorlieben,
        )
        viewModel.onTargetChange(null)
        viewModel.onStart()
        viewModel.wischen(viewModel.sichtbareTitel().first(), liked = false)
        val vorher = viewModel.uiState.value.cards

        vorlieben.update { it.copy(firstName = "Mia", householdSize = 4) }

        assertEquals(vorher, viewModel.uiState.value.cards)
        assertTrue(viewModel.uiState.value.canUndo, "die letzte Entscheidung laesst sich weiter zuruecknehmen")
    }

    @Test
    fun `gespeicherte Rezepte tauchen beim Entdecken nicht noch einmal auf`() = runTest {
        val schonDa = karte("Schon gespeichert")
        val neu = karte("Noch unbekannt")
        val nurImPlan = karte("Nur im Wochenplan")
        val sammlung = FakeRecipeRepository(
            listOf(
                // Gespeichert unter einer leicht anderen Adresse - es ist trotzdem dasselbe Rezept.
                Recipe(id = 1L, title = "Schon gespeichert", sourceUrl = "http://www.a.example/schon-gespeichert/"),
                Recipe(id = 2L, title = "Nur im Wochenplan", sourceUrl = nurImPlan.url, inCollection = false),
                Recipe(id = 3L, title = "Selbst erfasst"),
            ),
        )
        val viewModel = createViewModel(
            webRepository = FakeWebRecipeRepository(pool = pool(schonDa, neu, nurImPlan)),
            recipeRepository = sammlung,
        )

        viewModel.onStart()

        // Was nur fuer den Plan geholt wurde, hat man sich nicht ausgesucht - das darf man noch entdecken.
        assertEquals(setOf("Noch unbekannt", "Nur im Wochenplan"), viewModel.sichtbareTitel().toSet())
    }

    @Test
    fun `die Auswahl vor der Runde bestimmt, ob Essen, Getraenke oder beides kommt`() = runTest {
        val web = FakeWebRecipeRepository(
            pool = pool(karte("Kartoffelgratin"), karte("Erdbeer-Smoothie"), karte("Mojito"), karte("Linsensuppe")),
        )

        val essen = createViewModel(webRepository = web)
        essen.onStart()
        assertEquals(setOf("Kartoffelgratin", "Linsensuppe"), essen.sichtbareTitel().toSet())

        val getraenke = createViewModel(webRepository = web)
        getraenke.onCourseChange(CourseChoice.GETRAENKE)
        getraenke.onStart()
        assertEquals(setOf("Erdbeer-Smoothie", "Mojito"), getraenke.sichtbareTitel().toSet())

        val beides = createViewModel(webRepository = web)
        beides.onCourseChange(CourseChoice.BEIDES)
        beides.onStart()
        assertEquals(4, beides.sichtbareTitel().size)
    }

    @Test
    fun `ohne eigene Wahl wird nach Essen gesucht`() {
        assertEquals(CourseChoice.ESSEN, createViewModel().uiState.value.course)
    }

    @Test
    fun `zeigt nur Karten mit Bild - fehlende werden nachgeladen oder fallen heraus`() = runTest {
        val viewModel = createViewModel()

        viewModel.onStart()
        val state = viewModel.uiState.value
        val sichtbareTitel = state.remainingCards.map { it.title }

        assertTrue("Hat ein Bild" in sichtbareTitel)
        assertTrue("Bild kommt nach" in sichtbareTitel, "Ein nachladbares Bild sollte die Karte sichtbar machen")
        assertFalse("Kein Bild zu holen" in sichtbareTitel, "Ohne Bild darf eine Karte nie erscheinen")
        assertTrue(
            state.cards.none { it.url == ohneBildUndUnrettbar.url },
            "Eine Karte ohne erreichbares Bild soll aus dem Stapel fallen, nicht nur versteckt sein",
        )
    }

    // ------------------------------------------------------------------ wann die Runde endet

    @Test
    fun `endlose Runde endet nicht von selbst, solange es Karten gibt`() = runTest {
        val viewModel = createViewModel(webRepository = FakeWebRecipeRepository(pool = vieleKarten))
        viewModel.onTargetChange(null)
        viewModel.onStart()

        repeat(12) {
            val oberste = viewModel.uiState.value.cards
                .first()
            viewModel.onSwiped(oberste, liked = true)
        }

        assertEquals(DeckStep.WISCHEN, viewModel.uiState.value.step, "Ohne Ziel darf nichts automatisch beenden")
        assertEquals(12, viewModel.uiState.value.foundCount)
    }

    @Test
    fun `ist der Vorrat aufgebraucht, endet die Runde ehrlich statt ewig zu laden`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTargetChange(null)
        viewModel.onStart()

        viewModel.wischen("Hat ein Bild", liked = true)
        viewModel.wischen("Bild kommt nach", liked = false)

        val state = viewModel.uiState.value
        assertEquals(DeckStep.FERTIG, state.step)
        assertTrue(state.exhausted)
        assertEquals(listOf("Hat ein Bild"), state.liked.map { it.title })
    }

    @Test
    fun `Fertig-Knopf beendet die endlose Runde`() = runTest {
        val viewModel = createViewModel()
        viewModel.onTargetChange(null)
        viewModel.onStart()

        viewModel.onFinishEarly()

        assertEquals(DeckStep.FERTIG, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.exhausted)
    }

    @Test
    fun `Runde mit Ziel endet von selbst, sobald es erreicht ist`() = runTest {
        val viewModel = createViewModel(webRepository = FakeWebRecipeRepository(pool = vieleKarten))
        viewModel.onTargetChange(2)
        viewModel.onStart()

        viewModel.onSwiped(
            viewModel.uiState.value.cards
                .first(),
            liked = true,
        )
        assertEquals(DeckStep.WISCHEN, viewModel.uiState.value.step)
        viewModel.onSwiped(
            viewModel.uiState.value.cards
                .first(),
            liked = false,
        )
        viewModel.onSwiped(
            viewModel.uiState.value.cards
                .first(),
            liked = true,
        )

        assertEquals(DeckStep.FERTIG, viewModel.uiState.value.step)
        assertEquals(2, viewModel.uiState.value.foundCount)
    }

    @Test
    fun `ohne Verzeichnis heisst es kein Netz - mit Verzeichnis, aber ohne Passendes, heisst es genau das`() = runTest {
        val ohneNetz = createViewModel(webRepository = FakeWebRecipeRepository(pool = DeckPool()))
        ohneNetz.onStart()
        assertTrue(ohneNetz.uiState.value.loadFailed)
        assertFalse(ohneNetz.uiState.value.nothingFits)
        assertEquals(DeckStep.ANZAHL, ohneNetz.uiState.value.step)

        // Nur Essen im Verzeichnis, gesucht sind Getraenke: Das ist kein Verbindungsproblem.
        val nichtsPassendes =
            createViewModel(webRepository = FakeWebRecipeRepository(pool = pool(karte("Kartoffelgratin"))))
        nichtsPassendes.onCourseChange(CourseChoice.GETRAENKE)
        nichtsPassendes.onStart()
        assertFalse(nichtsPassendes.uiState.value.loadFailed)
        assertTrue(nichtsPassendes.uiState.value.nothingFits)
        assertEquals(DeckStep.ANZAHL, nichtsPassendes.uiState.value.step)
    }

    // ------------------------------------------------------------------ zuruecknehmen

    @Test
    fun `die letzte Entscheidung laesst sich zuruecknehmen - die Karte liegt wieder oben`() = runTest {
        val viewModel = createViewModel(webRepository = FakeWebRecipeRepository(pool = vieleKarten))
        viewModel.onStart()
        val erste = viewModel.uiState.value.cards
            .first()
        assertFalse(viewModel.uiState.value.canUndo)

        viewModel.onSwiped(erste, liked = true)
        assertEquals(
            listOf(erste.title),
            viewModel.uiState.value.liked
                .map { it.title },
        )
        assertTrue(viewModel.uiState.value.canUndo)

        viewModel.onUndo()

        val state = viewModel.uiState.value
        assertEquals(erste.title, state.remainingCards.first().title)
        assertTrue(state.liked.isEmpty(), "Ein zurueckgenommenes Ja ist kein Ja mehr")
        assertFalse(state.canUndo)
    }

    @Test
    fun `zuruecknehmen geht Schritt fuer Schritt weiter zurueck`() = runTest {
        val viewModel = createViewModel(webRepository = FakeWebRecipeRepository(pool = vieleKarten))
        viewModel.onTargetChange(null)
        viewModel.onStart()
        val (eins, zwei, drei) = viewModel.uiState.value.cards

        viewModel.onSwiped(eins, liked = true)
        viewModel.onSwiped(zwei, liked = false)
        viewModel.onSwiped(drei, liked = true)

        viewModel.onUndo()
        assertEquals(
            drei.title,
            viewModel.uiState.value.remainingCards
                .first()
                .title,
        )
        assertEquals(
            listOf(eins.title),
            viewModel.uiState.value.liked
                .map { it.title },
        )

        viewModel.onUndo()
        assertEquals(
            zwei.title,
            viewModel.uiState.value.remainingCards
                .first()
                .title,
        )

        viewModel.onUndo()
        assertEquals(
            eins.title,
            viewModel.uiState.value.remainingCards
                .first()
                .title,
        )
        assertTrue(
            viewModel.uiState.value.liked
                .isEmpty(),
        )
        assertFalse(viewModel.uiState.value.canUndo)

        // Mehr gibt es nicht zurueckzunehmen - ein weiterer Tipp aendert nichts.
        viewModel.onUndo()
        assertEquals(
            eins.title,
            viewModel.uiState.value.remainingCards
                .first()
                .title,
        )
    }

    @Test
    fun `eine neue Runde beginnt ohne Vorgeschichte`() = runTest {
        val viewModel = createViewModel(webRepository = FakeWebRecipeRepository(pool = vieleKarten))
        viewModel.onStart()
        viewModel.onSwiped(
            viewModel.uiState.value.cards
                .first(),
            liked = false,
        )

        viewModel.onRestart()
        viewModel.onStart()

        assertFalse(viewModel.uiState.value.canUndo)
        assertTrue(
            viewModel.uiState.value.liked
                .isEmpty(),
        )
    }

    // ------------------------------------------------------------------ mehr zur Karte

    @Test
    fun `antippen zeigt Quelle, Adresse und Zutaten - aber keine Zubereitung`() = runTest {
        val gratin = karte("Kartoffelgratin")
        val web = FakeWebRecipeRepository(
            pool = pool(gratin),
            recipeByUrl = mapOf(seite(gratin, "800 g Kartoffeln", "2 dl Rahm", "1 Knoblauchzehe")),
        )
        val viewModel = createViewModel(webRepository = web)
        viewModel.onStart()

        viewModel.onInfoRequest(
            viewModel.uiState.value.remainingCards
                .first()
                .id,
        )

        val info = viewModel.uiState.value.info!!
        assertEquals("Kartoffelgratin", info.title)
        assertEquals("Quelle A", info.sourceName)
        assertEquals(gratin.url, info.sourceUrl)
        assertEquals(listOf("800 g Kartoffeln", "2 dl Rahm", "1 Knoblauchzehe"), info.lines)
        assertEquals(35, info.minutes)
        assertEquals("4", info.servings)
        assertFalse(info.isLoading)
        assertFalse(info.failed)
        // Die Zubereitung gehoert zum gespeicherten Rezept, nicht auf eine Karte: Sie kommt
        // im Blatt an keiner Stelle vor.
        assertFalse(info.toString().contains("Geheime Zubereitung"))
        // Unter den Zutaten stehen Kalorien und Eiweiss. Die Seite nennt keine - also geschaetzt:
        // 616 kcal Kartoffeln und 680 kcal Rahm, durch vier.
        assertTrue(info.showsNutrition)
        val werte = info.nutrition!!
        assertEquals(NutritionBasis.SERVING, werte.basis)
        assertTrue(werte.isEstimate)
        assertTrue(werte.kcal!!.amount in 310..340, "geschaetzt: ${werte.kcal}")
        assertEquals(5, werte.protein!!.amount)

        viewModel.onInfoDismiss()
        assertNull(viewModel.uiState.value.info)
    }

    @Test
    fun `nennt die Rezeptseite Kalorien und Eiweiss, stehen ihre Werte im Blatt - nicht die Schaetzung`() = runTest {
        val gratin = karte("Kartoffelgratin")
        val (adresse, rezept) = seite(gratin, "800 g Kartoffeln", "2 dl Rahm")
        val web = FakeWebRecipeRepository(
            pool = pool(gratin),
            recipeByUrl = mapOf(
                adresse to rezept.copy(nutrition = SourceNutrition(kcal = 398, proteinGrams = 7.4)),
            ),
        )
        val viewModel = createViewModel(webRepository = web)
        viewModel.onStart()

        viewModel.onInfoRequest(
            viewModel.uiState.value.remainingCards
                .first()
                .id,
        )

        val werte = viewModel.uiState.value.info!!
            .nutrition!!
        assertEquals(NutritionValue(398, estimated = false), werte.kcal)
        assertEquals(NutritionValue(7, estimated = false), werte.protein)
        assertTrue(werte.isFromSource)
    }

    @Test
    fun `laesst sich die Rezeptseite nicht lesen, sagt das Blatt das - Titel und Adresse bleiben`() = runTest {
        val gratin = karte("Kartoffelgratin")
        val viewModel = createViewModel(webRepository = FakeWebRecipeRepository(pool = pool(gratin)))
        viewModel.onStart()

        viewModel.onInfoRequest(
            viewModel.uiState.value.remainingCards
                .first()
                .id,
        )

        val info = viewModel.uiState.value.info!!
        assertTrue(info.failed)
        assertFalse(info.isLoading)
        assertEquals("Kartoffelgratin", info.title)
        assertEquals(gratin.url, info.sourceUrl)
        assertTrue(info.lines.isEmpty())
        // Ohne Seite gibt es auch nichts ueber Naehrwerte zu sagen.
        assertFalse(info.showsNutrition)
    }

    @Test
    fun `das Ansehen einer Karte entscheidet nichts`() = runTest {
        val viewModel = createViewModel(webRepository = FakeWebRecipeRepository(pool = vieleKarten))
        viewModel.onStart()
        val vorher = viewModel.uiState.value.remainingCards
            .map { it.id }

        viewModel.onInfoRequest(vorher.first())
        viewModel.onInfoDismiss()

        assertEquals(
            vorher,
            viewModel.uiState.value.remainingCards
                .map { it.id },
        )
        assertTrue(
            viewModel.uiState.value.liked
                .isEmpty(),
        )
        assertFalse(viewModel.uiState.value.canUndo)
    }
}
