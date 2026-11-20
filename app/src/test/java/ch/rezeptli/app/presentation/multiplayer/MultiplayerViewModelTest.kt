package ch.rezeptli.app.presentation.multiplayer

import ch.rezeptli.app.domain.deck.CuratedDeckBuilder
import ch.rezeptli.app.domain.deck.DeckEntry
import ch.rezeptli.app.domain.deck.DeckTitles
import ch.rezeptli.app.domain.model.Ingredient
import ch.rezeptli.app.domain.model.Recipe
import ch.rezeptli.app.domain.model.RecipeFilter
import ch.rezeptli.app.domain.model.WebRecipe
import ch.rezeptli.app.domain.multiplayer.ActiveRound
import ch.rezeptli.app.domain.multiplayer.PairingError
import ch.rezeptli.app.domain.multiplayer.RoundEnd
import ch.rezeptli.app.domain.multiplayer.RoundKeys
import ch.rezeptli.app.domain.multiplayer.RoundOffer
import ch.rezeptli.app.domain.multiplayer.RoundPhase
import ch.rezeptli.app.domain.multiplayer.RoundSource
import ch.rezeptli.app.domain.multiplayer.SharedRecipe
import ch.rezeptli.app.domain.multiplayer.SharedSelectionHolder
import ch.rezeptli.app.domain.multiplayer.SharedVote
import ch.rezeptli.app.domain.parser.IngredientTextParser
import ch.rezeptli.app.domain.plan.AddToPlanUseCase
import ch.rezeptli.app.domain.plan.CheckRecipeCardUseCase
import ch.rezeptli.app.domain.plan.CourseChoice
import ch.rezeptli.app.domain.plan.DietaryFilter
import ch.rezeptli.app.domain.plan.PlanGenerator
import ch.rezeptli.app.domain.plan.PlanPersister
import ch.rezeptli.app.domain.plan.ReadRecipeCardUseCase
import ch.rezeptli.app.domain.plan.RecipeClassifier
import ch.rezeptli.app.domain.profile.Country
import ch.rezeptli.app.domain.profile.Diet
import ch.rezeptli.app.domain.profile.Dislike
import ch.rezeptli.app.domain.profile.Intolerance
import ch.rezeptli.app.domain.profile.UserProfile
import ch.rezeptli.app.domain.ranking.SourceOrigin
import ch.rezeptli.app.domain.repository.DeckPool
import ch.rezeptli.app.domain.shopping.ShoppingListAggregator
import ch.rezeptli.app.domain.steps.InstructionSplitter
import ch.rezeptli.app.domain.translate.NoTranslation
import ch.rezeptli.app.domain.usecase.AddRecipesToShoppingListUseCase
import ch.rezeptli.app.domain.usecase.BuildRoundContributionUseCase
import ch.rezeptli.app.domain.usecase.BuildSwipeDeckUseCase
import ch.rezeptli.app.domain.usecase.LoadWebRecipeUseCase
import ch.rezeptli.app.domain.usecase.ObserveRoundUseCase
import ch.rezeptli.app.domain.usecase.SaveRecipeUseCase
import ch.rezeptli.app.fake.FakeActiveRoundRepository
import ch.rezeptli.app.fake.FakePairingRepository
import ch.rezeptli.app.fake.FakePairingRepository.Companion.CODE
import ch.rezeptli.app.fake.FakePlanRepository
import ch.rezeptli.app.fake.FakeRecipeRepository
import ch.rezeptli.app.fake.FakeShoppingListRepository
import ch.rezeptli.app.fake.FakeUserProfileRepository
import ch.rezeptli.app.fake.FakeWebRecipeRepository
import ch.rezeptli.app.util.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Die gemeinsame Runde aus der Sicht eines Telefons. Die zweite Person spielt der Test
 * selbst, ueber [FakePairingRepository].
 *
 * Jeder Abschnitt gehoert zu einer Beobachtung vom Geraet: der Code soll zuerst stehen, nur
 * wer eroeffnet hat, legt die Anzahl fest, "das will ich nicht" gilt fuer beide, beide
 * sehen dieselbe erste Karte, beim Ziel ist Schluss, und ist der Stapel durch, wird gefragt.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MultiplayerViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val pairing = FakePairingRepository()
    private val activeRounds = FakeActiveRoundRepository()
    private val selectionHolder = SharedSelectionHolder()
    private val shoppingRepository = FakeShoppingListRepository()
    private val planRepository = FakePlanRepository()

    // ------------------------------------------------------------------ die eigene Sammlung

    private fun rezept(id: Long, titel: String, vararg zutaten: String, url: String? = "https://a.example/rezept-$id") =
        Recipe(
            id = id,
            title = titel,
            instructions = "In der Pfanne anbraten.",
            ingredients = zutaten.map { Ingredient(name = it) },
            servings = 4,
            photoUri = url?.let { "$it.jpg" } ?: "content://media/foto-$id",
            sourceUrl = url,
            sourceName = url?.let { "Quelle A" },
        )

    private val roesti = rezept(1L, "Rösti", "Kartoffeln", "Bratbutter")
    private val lachs = rezept(2L, "Lachs mit Reis", "Lachsfilet", "Reis")
    private val tofu = rezept(3L, "Tofu-Geschnetzeltes", "Tofu", "Sojasauce", "Reis")
    private val gratin = rezept(4L, "Omas Gratin", "Kartoffeln", "Rahm", url = null)
    private val smoothie = rezept(5L, "Erdbeer-Smoothie", "Erdbeeren", "Joghurt")

    private val sammlung = FakeRecipeRepository(listOf(roesti, lachs, tofu, gratin, smoothie))

    // ------------------------------------------------------------------ das Rezeptverzeichnis

    private fun eintrag(titel: String): DeckEntry {
        val name = titel.lowercase().replace(' ', '-')
        return DeckEntry(
            url = "https://neu.example/$name",
            title = titel,
            imageUrl = "https://neu.example/$name.jpg",
            sourceId = "neu",
            sourceName = "Neue Quelle",
        )
    }

    private val neuePasta = eintrag("Pasta Pesto")
    private val neuerFisch = eintrag("Forelle blau")
    private val neueSuppe = eintrag("Linsensuppe")
    private val neuerDrink = eintrag("Mojito")

    private fun seite(eintrag: DeckEntry, vararg zutaten: String) = eintrag.url to WebRecipe(
        title = eintrag.title,
        ingredientLines = zutaten.toList(),
        instructions = "Kochen.",
        imageUrl = eintrag.imageUrl,
        totalMinutes = 25,
        servings = "4",
        sourceUrl = eintrag.url,
        sourceName = eintrag.sourceName,
    )

    private val risottoUrl = "https://b.example/risotto"

    private val web = FakeWebRecipeRepository(
        pool = DeckPool(
            entriesBySource = mapOf("neu" to listOf(neuePasta, neuerFisch, neueSuppe, neuerDrink)),
            origins = mapOf("neu" to SourceOrigin("neu", Country.SCHWEIZ)),
        ),
        recipeByUrl = mapOf(
            seite(neuePasta, "400 g Spaghetti", "1 Bund Basilikum"),
            seite(neuerFisch, "2 Forellen", "1 Zitrone"),
            seite(neueSuppe, "300 g Linsen", "2 Karotten"),
            risottoUrl to WebRecipe(
                title = "Risotto",
                ingredientLines = listOf("300 g Risottoreis", "1 l Bouillon"),
                instructions = "Rühren.",
                servings = "4",
                sourceUrl = risottoUrl,
                sourceName = "Quelle B",
            ),
        ),
    )

    // ------------------------------------------------------------------ was die andere Person mitbringt

    private val risotto = RoundOffer(
        key = RoundKeys.forUrl(risottoUrl),
        title = "Risotto",
        sourceUrl = risottoUrl,
        imageUrl = "$risottoUrl.jpg",
    )
    private val eintopf = RoundOffer(key = "e:gegenueber:7", title = "Beats Eintopf")

    /** Dasselbe Rezept, das auch in der eigenen Sammlung liegt - nur von der anderen Person beigesteuert. */
    private val ihreRoesti =
        RoundOffer(key = RoundKeys.forUrl(roesti.sourceUrl!!), title = "Rösti", sourceUrl = roesti.sourceUrl)

    private fun createViewModel(
        profile: UserProfile = UserProfile(country = Country.SCHWEIZ),
        recipes: FakeRecipeRepository = sammlung,
        profileRepository: FakeUserProfileRepository = FakeUserProfileRepository(profile),
    ): MultiplayerViewModel {
        val readCard = ReadRecipeCardUseCase(web, IngredientTextParser(), RecipeClassifier())
        val checkCard = CheckRecipeCardUseCase(readCard)
        val loadWebRecipe = LoadWebRecipeUseCase(web, IngredientTextParser(), InstructionSplitter(), NoTranslation)
        val generator = PlanGenerator(ShoppingListAggregator())
        return MultiplayerViewModel(
            pairing = pairing,
            observeRound = ObserveRoundUseCase(pairing),
            buildContribution = BuildRoundContributionUseCase(
                recipeRepository = recipes,
                pairingRepository = pairing,
                classifier = RecipeClassifier(),
                buildDeck = BuildSwipeDeckUseCase(
                    webRepository = web,
                    profileRepository = profileRepository,
                    builder = CuratedDeckBuilder(),
                    titles = DeckTitles(RecipeClassifier()),
                    dispatcher = mainDispatcher.testDispatcher,
                ),
                checkCard = checkCard,
                dispatcher = mainDispatcher.testDispatcher,
            ),
            selectionHolder = selectionHolder,
            activeRounds = activeRounds,
            profileRepository = profileRepository,
            recipeRepository = recipes,
            readCard = readCard,
            loadWebRecipe = loadWebRecipe,
            saveRecipe = SaveRecipeUseCase(recipes, InstructionSplitter()),
            addToShoppingList = AddRecipesToShoppingListUseCase(recipes, shoppingRepository, ShoppingListAggregator()),
            addToPlan = AddToPlanUseCase(
                profileRepository = profileRepository,
                planRepository = planRepository,
                recipeRepository = recipes,
                loadWebRecipe = loadWebRecipe,
                classifier = RecipeClassifier(),
                generator = generator,
                persister = PlanPersister(recipes, shoppingRepository, planRepository),
            ),
        )
    }

    /**
     * Stellt ein ViewModel bereit und beendet danach sein Nachfragen beim Dienst.
     *
     * Das ViewModel fragt den Stand der Runde wiederholt ab. `runTest` wartet am Ende darauf,
     * dass nichts mehr geplant ist - ohne das Aufraeumen hier (und zwar *im* Testrumpf, ein
     * `@AfterEach` kaeme zu spaet) liefe der Test ewig.
     */
    private suspend fun TestScope.mitViewModel(
        viewModel: MultiplayerViewModel = createViewModel(),
        block: suspend TestScope.(MultiplayerViewModel) -> Unit,
    ) {
        try {
            block(viewModel)
        } finally {
            viewModel.onLeave()
            advanceUntilIdle()
        }
    }

    /** Laesst die Zeit bis zum naechsten Nachfragen beim Dienst vergehen. */
    private fun TestScope.nachfragen() {
        advanceTimeBy(POLL_MS)
        runCurrent()
    }

    private val MultiplayerViewModel.state get() = uiState.value

    private fun MultiplayerViewModel.offeneTitel() = state.remainingCards.map { it.title }

    private fun MultiplayerViewModel.kennung(titel: String) = state.stack.first { it.title == titel }.recipeId

    /** Ich eroeffne, die andere Person tritt bei. */
    private fun TestScope.lobbyAlsGastgeber(
        viewModel: MultiplayerViewModel,
        ihreAngaben: DietaryFilter = DietaryFilter(),
    ) {
        viewModel.onHost()
        pairing.partnerJoins(ihreAngaben)
        nachfragen()
    }

    /** Die andere Person hat eroeffnet, ich trete bei. */
    private fun beitreten(viewModel: MultiplayerViewModel, ihreAngaben: DietaryFilter = DietaryFilter()) {
        pairing.partnerHosts(ihreAngaben)
        viewModel.onCodeInputChange(CODE)
        viewModel.onJoin()
    }

    /** Bringt die Runde bis zum gemeinsamen Stapel aus den gespeicherten Rezepten beider. */
    private fun TestScope.bisZumStapel(
        viewModel: MultiplayerViewModel,
        ziel: Int = 3,
        vararg ihreKarten: RoundOffer = arrayOf(risotto, eintopf),
    ) {
        lobbyAlsGastgeber(viewModel)
        viewModel.onTargetChange(ziel)
        viewModel.onStart()
        pairing.partnerContributes(*ihreKarten)
        nachfragen()
    }

    // ------------------------------------------------------------------ Lobby: der Code zuerst

    @Test
    fun `eroeffnen liefert sofort den Code - noch bevor irgendetwas festgelegt ist`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost()

            assertEquals(MultiplayerStep.LOBBY, viewModel.state.step)
            assertEquals(CODE, viewModel.state.code)
            assertTrue(viewModel.state.isHost)
            assertTrue(viewModel.state.isAlone)
            // Es ist noch kein einziges Rezept beim Dienst - der Code allein genuegt zum Einladen.
            assertTrue(pairing.myContributions.isEmpty())
            assertEquals(
                ActiveRound(code = CODE, isHost = true, expiresAt = FakePairingRepository.EXPIRY),
                activeRounds.round,
            )
        }
    }

    @Test
    fun `wer eroeffnet hat, legt Anzahl, Art und Quelle fest`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost()

            viewModel.onTargetChange(5)
            viewModel.onCourseChange(CourseChoice.BEIDES)
            viewModel.onSourceChange(RoundSource.NEU)

            assertEquals(5, pairing.target)
            assertEquals(CourseChoice.BEIDES, pairing.course)
            assertEquals(RoundSource.NEU, pairing.source)
            assertEquals(5, viewModel.state.target)
            assertEquals(CourseChoice.BEIDES, viewModel.state.course)
            assertEquals(RoundSource.NEU, viewModel.state.source)
        }
    }

    @Test
    fun `die eingeladene Person kann die Anzahl nicht aendern - sie sieht aber, was festgelegt wird`() = runTest {
        mitViewModel { viewModel ->
            beitreten(viewModel)
            assertEquals(MultiplayerStep.LOBBY, viewModel.state.step)
            assertFalse(viewModel.state.isHost)

            viewModel.onTargetChange(7)
            viewModel.onCourseChange(CourseChoice.GETRAENKE)
            viewModel.onStart()

            assertEquals(3, pairing.target)
            assertEquals(3, viewModel.state.target)
            assertEquals(RoundPhase.LOBBY, pairing.phase)

            pairing.partnerConfigures(target = 2, course = CourseChoice.BEIDES, source = RoundSource.NEU)
            nachfragen()

            assertEquals(2, viewModel.state.target)
            assertEquals(CourseChoice.BEIDES, viewModel.state.course)
            assertEquals(RoundSource.NEU, viewModel.state.source)
        }
    }

    @Test
    fun `starten laesst sich erst, wenn die zweite Person da ist`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost()

            viewModel.onStart()
            assertEquals(RoundPhase.LOBBY, pairing.phase)

            pairing.partnerJoins()
            nachfragen()
            assertFalse(viewModel.state.isAlone)

            viewModel.onStart()
            assertEquals(RoundPhase.SAMMELN, pairing.phase)
            assertEquals(MultiplayerStep.SAMMELN, viewModel.state.step)
        }
    }

    // ------------------------------------------------------------------ was nicht auf den Tisch darf

    @Test
    fun `was eine Person nicht will, gilt fuer beide`() = runTest {
        // Der Fall vom Geraet: Mir ist vegan recht, mein Gegenueber will es nicht. Frueher
        // sah ich die veganen Karten trotzdem.
        val ich = UserProfile(country = Country.SCHWEIZ, dislikes = setOf(Dislike.FISCH))
        mitViewModel(createViewModel(profile = ich)) { viewModel ->
            lobbyAlsGastgeber(viewModel, ihreAngaben = DietaryFilter(dislikes = setOf(Dislike.VEGAN)))

            assertEquals(setOf(Dislike.FISCH, Dislike.VEGAN), viewModel.state.restrictions.dislikes)

            viewModel.onStart()

            // Mein Beitrag enthaelt weder den Lachs (das will ich nicht) noch das Tofu-Gericht
            // (das will mein Gegenueber nicht) - und kein Getraenk, gesucht ist Essen.
            assertEquals(
                setOf("Rösti", "Omas Gratin"),
                pairing.myContributions
                    .getValue(0)
                    .map { it.title }
                    .toSet(),
            )
        }
    }

    @Test
    fun `die eigenen Angaben kommen aus den Vorlieben und gehen beim Eroeffnen mit`() = runTest {
        val ich = UserProfile(
            diets = setOf(Diet.PESCETARISCH),
            intolerances = setOf(Intolerance.NUESSE),
            dislikes = setOf(Dislike.PILZE),
            customDislikes = listOf("Rosinen"),
        )
        mitViewModel(createViewModel(profile = ich)) { viewModel ->
            assertEquals(DietaryFilter.of(ich), viewModel.state.ownRestrictions)

            viewModel.onHost()

            assertEquals(DietaryFilter.of(ich), pairing.restrictions)
        }
    }

    @Test
    fun `fuer die Runde lassen sich die eigenen Angaben aendern, ohne die Vorlieben anzufassen`() = runTest {
        val profil = FakeUserProfileRepository(UserProfile(dislikes = setOf(Dislike.FISCH)))
        mitViewModel(createViewModel(profileRepository = profil)) { viewModel ->
            viewModel.onOwnDislikeToggle(Dislike.FISCH)
            viewModel.onOwnDislikeToggle(Dislike.PILZE)
            viewModel.onOwnMainDiet(Diet.VEGETARISCH)
            viewModel.onOwnIntoleranceToggle(Intolerance.GLUTEN)
            viewModel.onOwnCustomDislikeAdd("Rosinen")

            viewModel.onHost()

            assertEquals(
                DietaryFilter(
                    diets = setOf(Diet.VEGETARISCH),
                    intolerances = setOf(Intolerance.GLUTEN),
                    dislikes = setOf(Dislike.PILZE),
                    customDislikes = listOf("Rosinen"),
                ),
                pairing.restrictions,
            )
            // Die Vorlieben selbst sind unveraendert - die Aenderung galt nur dieser Runde.
            assertEquals(UserProfile(dislikes = setOf(Dislike.FISCH)), profil.current)
        }
    }

    @Test
    fun `nur Getraenke heisst - der eigene Beitrag besteht nur aus Getraenken`() = runTest {
        mitViewModel { viewModel ->
            lobbyAlsGastgeber(viewModel)
            viewModel.onCourseChange(CourseChoice.GETRAENKE)

            viewModel.onStart()

            assertEquals(listOf("Erdbeer-Smoothie"), pairing.myContributions.getValue(0).map { it.title })
        }
    }

    // ------------------------------------------------------------------ der gemeinsame Stapel

    @Test
    fun `der Stapel entsteht aus den gespeicherten Rezepten beider`() = runTest {
        mitViewModel { viewModel ->
            lobbyAlsGastgeber(viewModel)
            viewModel.onStart()

            // Mein Teil ist abgegeben; es fehlt noch der der anderen Person.
            assertEquals(MultiplayerStep.SAMMELN, viewModel.state.step)
            assertTrue(viewModel.state.hasContributed)
            assertEquals(4, pairing.myContributions.getValue(0).size)

            pairing.partnerContributes(risotto, eintopf)
            nachfragen()

            assertEquals(MultiplayerStep.WISCHEN, viewModel.state.step)
            assertEquals(6, viewModel.state.stack.size)
            assertTrue("Risotto" in viewModel.offeneTitel())
            assertTrue("Rösti" in viewModel.offeneTitel())
        }
    }

    @Test
    fun `ein eigenes Foto und eine eigene Rezeptnummer verlassen das Telefon nicht als Bild oder Adresse`() = runTest {
        mitViewModel { viewModel ->
            lobbyAlsGastgeber(viewModel)
            viewModel.onStart()

            val meinGratin = pairing.myContributions.getValue(0).first { it.title == "Omas Gratin" }
            // Ein Dateipfad vom eigenen Telefon nuetzt dem anderen nichts.
            assertNull(meinGratin.imageUrl)
            assertNull(meinGratin.sourceUrl)
            assertEquals(RoundKeys.forOwn(FakePairingRepository.PARTICIPANT, gratin.id), meinGratin.key)

            val meineRoesti = pairing.myContributions.getValue(0).first { it.title == "Rösti" }
            assertEquals(roesti.sourceUrl, meineRoesti.sourceUrl)
            assertEquals("${roesti.sourceUrl}.jpg", meineRoesti.imageUrl)
        }
    }

    @Test
    fun `beide sehen denselben Stapel ab der ersten Karte - nichts wird still uebersprungen`() = runTest {
        // Auf dem Geraet stand ich bei Karte 0 und mein Gegenueber bei Karte 1: Jedes Telefon
        // hatte fuer sich aussortiert und dabei still "nein" gestimmt.
        val ich = UserProfile(country = Country.SCHWEIZ, dislikes = setOf(Dislike.FISCH))
        mitViewModel(createViewModel(profile = ich)) { viewModel ->
            bisZumStapel(viewModel)

            assertEquals(pairing.stack.map { it.title }, viewModel.offeneTitel())
            assertEquals(
                pairing.stack.first().title,
                viewModel.state.remainingCards
                    .first()
                    .title,
            )
            assertTrue(pairing.receivedVotes.isEmpty(), "Vor dem ersten Wisch darf keine Stimme abgegeben sein")
            assertTrue(viewModel.state.decided.isEmpty())
        }
    }

    @Test
    fun `ein Rezept, das beide gespeichert haben, liegt nur einmal im Stapel`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ihreKarten = arrayOf(ihreRoesti, risotto))

            assertEquals(1, viewModel.state.stack.count { it.title == "Rösti" })
        }
    }

    @Test
    fun `der Stapel kommt einmal - nicht bei jedem Nachfragen wieder`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel)
            val gesendet = pairing.stacksSent

            pairing.partnerVotes(viewModel.kennung("Risotto"), liked = true)
            nachfragen()
            pairing.partnerVotes(viewModel.kennung("Rösti"), liked = false)
            nachfragen()
            nachfragen()

            assertEquals(gesendet, pairing.stacksSent)
            assertEquals(6, viewModel.state.stack.size)
        }
    }

    // ------------------------------------------------------------------ wischen, Treffer, Schluss

    @Test
    fun `jede Entscheidung geht sofort an den Dienst`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel)
            val erste = viewModel.state.remainingCards.first()

            viewModel.onSwiped(erste.id, liked = true)

            assertEquals(listOf(SharedVote(erste.id, true)), pairing.receivedVotes)
            assertFalse(erste.title in viewModel.offeneTitel())
            assertTrue(viewModel.state.canUndo)
        }
    }

    @Test
    fun `sobald die gewuenschte Anzahl gefunden ist, endet die Runde - auch mit offenen Karten`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ziel = 1)
            val risottoKarte = viewModel.kennung("Risotto")

            viewModel.onSwiped(risottoKarte, liked = true)
            assertEquals(MultiplayerStep.WISCHEN, viewModel.state.step)

            pairing.partnerVotes(risottoKarte, liked = true)
            nachfragen()

            // Fuenf Karten sind noch offen - niemand muss sie mehr durchwischen.
            assertEquals(MultiplayerStep.TREFFER, viewModel.state.step)
            assertEquals(RoundEnd.ZIEL, viewModel.state.end)
            assertEquals(listOf("Risotto"), viewModel.state.matches.map { it.title })
            assertNull(activeRounds.round)
        }
    }

    @Test
    fun `bringt der eigene Wisch den letzten Treffer, ist sofort Schluss`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ziel = 1)
            val risottoKarte = viewModel.kennung("Risotto")
            pairing.partnerVotes(risottoKarte, liked = true)

            viewModel.onSwiped(risottoKarte, liked = true)

            assertEquals(MultiplayerStep.TREFFER, viewModel.state.step)
            assertEquals(1, viewModel.state.matchCount)
        }
    }

    @Test
    fun `der Zaehler zeigt gemeinsame Treffer - eine Zustimmung allein zaehlt nicht`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ziel = 3)
            val risottoKarte = viewModel.kennung("Risotto")

            viewModel.onSwiped(risottoKarte, liked = true)
            assertEquals(0, viewModel.state.matchCount)

            pairing.partnerVotes(risottoKarte, liked = true)
            nachfragen()

            assertEquals(1, viewModel.state.matchCount)
            assertEquals(3, viewModel.state.target)
            assertEquals(MultiplayerStep.WISCHEN, viewModel.state.step)
        }
    }

    @Test
    fun `wer den Stapel durch hat, wartet - die Runde laeuft fuer die andere Person weiter`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel)

            viewModel.state.remainingCards.forEach { viewModel.onSwiped(it.id, liked = false) }

            assertEquals(MultiplayerStep.WARTET, viewModel.state.step)
            assertEquals(RoundPhase.LAEUFT, pairing.phase)
            assertEquals(6, pairing.receivedVotes.size)
        }
    }

    // ------------------------------------------------------------------ der Stapel ist durch

    @Test
    fun `sind die gespeicherten Rezepte durch und das Ziel nicht erreicht, wird gefragt`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ziel = 3)
            val risottoKarte = viewModel.kennung("Risotto")

            viewModel.state.remainingCards.forEach { viewModel.onSwiped(it.id, liked = it.id == risottoKarte) }
            pairing.partnerVotesAll { it.recipeId == risottoKarte }
            nachfragen()

            assertEquals(MultiplayerStep.FRAGE, viewModel.state.step)
            assertEquals(1, viewModel.state.matchCount)
            assertEquals(listOf("Risotto"), viewModel.state.matches.map { it.title })
            assertEquals(RoundSource.GESPEICHERT, viewModel.state.source)
        }
    }

    @Test
    fun `weitermachen bringt einen neuen Stapel aus neuen Rezepten`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ziel = 3)
            viewModel.state.remainingCards.forEach { viewModel.onSwiped(it.id, liked = false) }
            pairing.partnerVotesAll { false }
            nachfragen()

            viewModel.onContinue()

            // Mein Beitrag zur neuen Runde kommt aus dem Verzeichnis - ohne Getraenke, gesucht ist Essen.
            assertEquals(RoundSource.NEU, pairing.source)
            assertEquals(
                setOf("Pasta Pesto", "Forelle blau", "Linsensuppe"),
                pairing.myContributions
                    .getValue(1)
                    .map { it.title }
                    .toSet(),
            )

            pairing.partnerContributes(
                RoundOffer(key = "u-neu", title = "Shakshuka", sourceUrl = "https://b.example/shakshuka"),
            )
            nachfragen()

            assertEquals(MultiplayerStep.WISCHEN, viewModel.state.step)
            assertEquals(
                setOf("Pasta Pesto", "Forelle blau", "Linsensuppe", "Shakshuka"),
                viewModel.offeneTitel().toSet(),
            )
            // Die neue Runde beginnt ohne Vorgeschichte.
            assertTrue(viewModel.state.decided.isEmpty())
            assertFalse(viewModel.state.canUndo)
        }
    }

    @Test
    fun `neue Rezepte werden an ihrer Seite geprueft, wenn jemand etwas ausgeschlossen hat`() = runTest {
        mitViewModel { viewModel ->
            lobbyAlsGastgeber(viewModel, ihreAngaben = DietaryFilter(dislikes = setOf(Dislike.FISCH)))
            viewModel.onSourceChange(RoundSource.NEU)

            viewModel.onStart()

            // "Forelle blau" verraet sich schon im Titel; alles andere wurde gelesen und passt.
            assertEquals(
                setOf("Pasta Pesto", "Linsensuppe"),
                pairing.myContributions
                    .getValue(0)
                    .map { it.title }
                    .toSet(),
            )
        }
    }

    @Test
    fun `nur wer eroeffnet hat, entscheidet, ob es weitergeht`() = runTest {
        mitViewModel { viewModel ->
            beitreten(viewModel)
            pairing.partnerStarts()
            nachfragen()
            pairing.partnerContributes(risotto)
            nachfragen()
            viewModel.state.remainingCards.forEach { viewModel.onSwiped(it.id, liked = false) }
            pairing.partnerVotesAll { false }
            nachfragen()
            assertEquals(MultiplayerStep.FRAGE, viewModel.state.step)

            viewModel.onContinue()
            assertEquals(RoundPhase.FRAGE, pairing.phase)

            pairing.partnerContinues()
            nachfragen()
            assertEquals(1, pairing.round)
            assertTrue(
                1 in pairing.myContributions,
                "Sobald es weitergeht, steuert auch dieses Telefon neue Karten bei",
            )
        }
    }

    @Test
    fun `aufhoeren behaelt, was bis dahin gefunden wurde`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ziel = 3)
            val risottoKarte = viewModel.kennung("Risotto")
            viewModel.state.remainingCards.forEach { viewModel.onSwiped(it.id, liked = it.id == risottoKarte) }
            pairing.partnerVotesAll { it.recipeId == risottoKarte }
            nachfragen()

            viewModel.onEndRound()

            assertEquals(MultiplayerStep.TREFFER, viewModel.state.step)
            assertEquals(RoundEnd.GASTGEBER, viewModel.state.end)
            assertEquals(listOf("Risotto"), viewModel.state.matches.map { it.title })
        }
    }

    @Test
    fun `hat niemand ein passendes gespeichertes Rezept, geht es ohne Rueckfrage mit neuen weiter`() = runTest {
        mitViewModel(createViewModel(recipes = FakeRecipeRepository())) { viewModel ->
            lobbyAlsGastgeber(viewModel)
            viewModel.onStart()
            assertTrue(pairing.myContributions.getValue(0).isEmpty())

            pairing.partnerContributes()
            nachfragen()

            assertEquals(1, pairing.round)
            assertEquals(RoundSource.NEU, pairing.source)
            assertEquals(3, pairing.myContributions.getValue(1).size)
        }
    }

    @Test
    fun `beendet die andere Person die Runde, erfaehrt man es und behaelt die Treffer`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ziel = 3)
            val risottoKarte = viewModel.kennung("Risotto")
            viewModel.onSwiped(risottoKarte, liked = true)
            pairing.partnerVotes(risottoKarte, liked = true)

            pairing.partnerEnds()
            nachfragen()

            assertEquals(MultiplayerStep.TREFFER, viewModel.state.step)
            assertEquals(RoundEnd.VERLASSEN, viewModel.state.end)
            assertEquals(listOf("Risotto"), viewModel.state.matches.map { it.title })
        }
    }

    // ------------------------------------------------------------------ zuruecknehmen

    @Test
    fun `die letzte Entscheidung laesst sich zuruecknehmen - auf dem Telefon und beim Dienst`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel)
            val (erste, zweite) = viewModel.state.remainingCards

            viewModel.onSwiped(erste.id, liked = true)
            viewModel.onSwiped(zweite.id, liked = false)
            viewModel.onUndo()

            assertEquals(
                zweite.title,
                viewModel.state.remainingCards
                    .first()
                    .title,
            )
            assertEquals(SharedVote(zweite.id, null), pairing.receivedVotes.last())
            assertTrue(viewModel.state.canUndo)

            viewModel.onUndo()
            assertEquals(
                erste.title,
                viewModel.state.remainingCards
                    .first()
                    .title,
            )
            assertFalse(viewModel.state.canUndo)
            assertTrue(viewModel.state.decided.isEmpty())
        }
    }

    @Test
    fun `mit der zurueckgenommenen Zustimmung verschwindet auch der Treffer`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel, ziel = 3)
            val risottoKarte = viewModel.kennung("Risotto")
            pairing.partnerVotes(risottoKarte, liked = true)

            viewModel.onSwiped(risottoKarte, liked = true)
            assertEquals(1, viewModel.state.matchCount)

            viewModel.onUndo()

            assertEquals(0, viewModel.state.matchCount)
            assertTrue(pairing.matches.isEmpty())
        }
    }

    @Test
    fun `auch nach der letzten Karte laesst sich noch ein Versehen zuruecknehmen`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel)
            val karten = viewModel.state.remainingCards
            karten.forEach { viewModel.onSwiped(it.id, liked = false) }
            assertEquals(MultiplayerStep.WARTET, viewModel.state.step)

            viewModel.onUndo()

            assertEquals(MultiplayerStep.WISCHEN, viewModel.state.step)
            assertEquals(listOf(karten.last().title), viewModel.offeneTitel())
        }
    }

    // ------------------------------------------------------------------ Netz, Unterbruch, Verlust

    @Test
    fun `ohne Netz bleibt eine Entscheidung in der Warteschlange und wird nachgereicht`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel)
            val (erste, zweite) = viewModel.state.remainingCards

            pairing.failWith = PairingError.NO_CONNECTION
            viewModel.onSwiped(erste.id, liked = true)
            viewModel.onSwiped(zweite.id, liked = false)

            // Die Karten sind weg, die Runde laeuft weiter - nur weiss der Dienst noch nichts davon.
            assertTrue(viewModel.state.isOffline)
            assertTrue(pairing.receivedVotes.isEmpty())
            assertEquals(MultiplayerStep.WISCHEN, viewModel.state.step)
            assertEquals(4, viewModel.state.remainingCards.size)

            pairing.failWith = null
            nachfragen()

            assertEquals(listOf(SharedVote(erste.id, true), SharedVote(zweite.id, false)), pairing.receivedVotes)
            assertFalse(viewModel.state.isOffline)
        }
    }

    @Test
    fun `scheitert das Abgeben der eigenen Karten am Netz, wird es beim naechsten Nachfragen wiederholt`() = runTest {
        mitViewModel { viewModel ->
            lobbyAlsGastgeber(viewModel)
            pairing.failNextContributions = 1

            viewModel.onStart()

            // Die Runde sammelt, aber die eigenen Karten sind nicht angekommen.
            assertEquals(MultiplayerStep.SAMMELN, viewModel.state.step)
            assertTrue(pairing.myContributions.isEmpty())
            assertTrue(viewModel.state.isOffline)

            nachfragen()

            // Sonst wartete die andere Person fuer immer auf einen Stapel, der nie entsteht.
            assertEquals(4, pairing.myContributions.getValue(0).size)
            assertTrue(viewModel.state.hasContributed)
            assertFalse(viewModel.state.isOffline)
        }
    }

    @Test
    fun `eine unterbrochene Runde laesst sich fortsetzen - mit Stapel und eigenen Entscheidungen`() = runTest {
        val erstes = createViewModel()
        mitViewModel(erstes) { viewModel ->
            bisZumStapel(viewModel)
            viewModel.onSwiped(
                viewModel.state.remainingCards
                    .first()
                    .id,
                liked = true,
            )
        }
        // mitViewModel hat den Bildschirm verlassen - die Runde laeuft beim Dienst weiter.
        assertEquals(CODE, erstes.state.resumableCode)
        assertEquals(MultiplayerStep.START, erstes.state.step)

        mitViewModel(createViewModel()) { viewModel ->
            assertEquals(CODE, viewModel.state.resumableCode)

            viewModel.onResume()

            assertEquals(MultiplayerStep.WISCHEN, viewModel.state.step)
            assertEquals(5, viewModel.state.remainingCards.size)
            assertEquals(1, viewModel.state.decided.size)
            // Auch die Entscheidung von vor dem Unterbruch laesst sich noch zuruecknehmen.
            assertTrue(viewModel.state.canUndo)
            assertNull(viewModel.state.resumableCode)
        }
    }

    @Test
    fun `gibt es die Runde nicht mehr, fuehrt das zurueck an den Anfang - mit einer Erklaerung`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel)

            pairing.vanish()
            nachfragen()

            assertEquals(MultiplayerStep.START, viewModel.state.step)
            assertEquals(PairingError.UNKNOWN_CODE, viewModel.state.error)
            assertNull(viewModel.state.resumableCode)
            assertNull(activeRounds.round)
        }
    }

    @Test
    fun `ein unbekannter Code wird erklaert, ohne eine Runde zu beginnen`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onCodeInputChange("falsch")
            viewModel.onJoin()

            assertEquals(MultiplayerStep.START, viewModel.state.step)
            assertEquals(PairingError.UNKNOWN_CODE, viewModel.state.error)
            assertNull(activeRounds.round)

            viewModel.onDismissError()
            assertNull(viewModel.state.error)
        }
    }

    @Test
    fun `wer in der Lobby allein aufhoert, loescht die Runde beim Dienst`() = runTest {
        mitViewModel { viewModel ->
            viewModel.onHost()

            viewModel.onEndRound()

            assertTrue(pairing.closed)
            assertEquals(MultiplayerStep.START, viewModel.state.step)
            assertNull(viewModel.state.resumableCode)
            assertNull(activeRounds.round)
        }
    }

    // ------------------------------------------------------------------ mehr zur Karte

    @Test
    fun `antippen zeigt die Zutaten - aus der eigenen Sammlung, von der Seite, oder ehrlich nichts`() = runTest {
        mitViewModel { viewModel ->
            bisZumStapel(viewModel)

            // Mein eigenes, selbst erfasstes Rezept: Die Zutaten liegen auf dem Telefon.
            viewModel.onInfoRequest(viewModel.kennung("Omas Gratin"))
            assertEquals(
                listOf("Kartoffeln", "Rahm"),
                viewModel.state.info
                    ?.ingredients
                    ?.map { it.name },
            )
            assertFalse(viewModel.state.info!!.isPartnersOwn)

            // Ein Rezept der anderen Person aus dem Netz: Die Zutaten kommen von der Seite.
            viewModel.onInfoRequest(viewModel.kennung("Risotto"))
            assertEquals(listOf("300 g Risottoreis", "1 l Bouillon"), viewModel.state.info?.lines)
            assertEquals("Quelle B", viewModel.state.info?.sourceName)
            assertEquals(risottoUrl, viewModel.state.info?.sourceUrl)

            // Ein selbst erfasstes Rezept der anderen Person: Davon wurde nur der Titel geteilt.
            viewModel.onInfoRequest(viewModel.kennung("Beats Eintopf"))
            assertTrue(viewModel.state.info!!.isPartnersOwn)
            assertFalse(viewModel.state.info!!.hasIngredients)

            viewModel.onInfoDismiss()
            assertNull(viewModel.state.info)
            assertEquals(6, viewModel.state.remainingCards.size, "Ansehen entscheidet nichts")
        }
    }

    // ------------------------------------------------------------------ Treffer uebernehmen

    /** Spielt eine Runde, an deren Ende drei Treffer stehen: ein eigenes, ein fremdes aus dem Netz, ein fremdes eigenes. */
    private fun TestScope.dreiTreffer(viewModel: MultiplayerViewModel) {
        bisZumStapel(viewModel, ziel = 3)
        val treffer =
            setOf(viewModel.kennung("Rösti"), viewModel.kennung("Risotto"), viewModel.kennung("Beats Eintopf"))
        pairing.partnerVotesAll { it.recipeId in treffer }
        viewModel.state.remainingCards.forEach { viewModel.onSwiped(it.id, liked = it.id in treffer) }
        assertEquals(MultiplayerStep.TREFFER, viewModel.state.step)
    }

    @Test
    fun `Treffer uebernehmen - das eigene bleibt, das fremde wird geholt, das nicht Holbare wird genannt`() = runTest {
        mitViewModel { viewModel ->
            dreiTreffer(viewModel)

            viewModel.onKeepMatches()

            assertEquals(2, viewModel.state.keptRecipes)
            assertEquals(1, viewModel.state.notImportable)
            assertFalse(viewModel.state.isKeeping)
            // Das Risotto der anderen Person liegt jetzt in der eigenen Sammlung ...
            assertTrue(sammlung.visible(RecipeFilter()).any { it.title == "Risotto" })
            // ... die Rösti nicht doppelt ...
            assertEquals(1, sammlung.visible(RecipeFilter()).count { it.title == "Rösti" })
            // ... und die Zutaten beider stehen auf der Einkaufsliste.
            assertTrue((viewModel.state.addedItems ?: 0) >= 4)
        }
    }

    @Test
    fun `Treffer in den Wochenplan - ein eigenes Rezept muss dafuer nicht geladen werden`() = runTest {
        mitViewModel { viewModel ->
            dreiTreffer(viewModel)

            viewModel.onAddToPlan()

            // Rösti (eigene Sammlung) und Risotto (von der Seite geholt) - der Eintopf laesst sich nicht holen.
            assertEquals(2, viewModel.state.plannedMeals)
            val geplant = planRepository.currentPlan().map { it.recipeId }
            assertTrue(roesti.id in geplant)
            assertEquals(2, geplant.size)
        }
    }

    // ------------------------------------------------------------------ aus einer eigenen Wischrunde

    @Test
    fun `was in der eigenen Wischrunde gefiel, kommt mit in den Stapel - einmal`() = runTest {
        selectionHolder.set(
            listOf(
                SharedRecipe(
                    recipeId = 99L,
                    title = "Pasta Pesto",
                    sourceUrl = neuePasta.url,
                    imageUrl = neuePasta.imageUrl,
                ),
            ),
        )

        mitViewModel { viewModel ->
            assertEquals(1, viewModel.state.selectionCount)

            lobbyAlsGastgeber(viewModel)
            viewModel.onStart()

            val beitrag = pairing.myContributions.getValue(0).map { it.title }
            assertEquals("Pasta Pesto", beitrag.first())
            assertEquals(5, beitrag.size)
            assertEquals(0, viewModel.state.selectionCount)
        }
        assertTrue(selectionHolder.peek().isEmpty(), "Die Auswahl gilt fuer genau eine Runde")
    }

    private companion object {
        /** Etwas mehr als der Abstand, in dem die App beim Dienst nachfragt. */
        const val POLL_MS = 2_600L
    }
}
