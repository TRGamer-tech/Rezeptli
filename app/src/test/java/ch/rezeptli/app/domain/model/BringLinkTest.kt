package ch.rezeptli.app.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BringLinkTest {
    @Test
    fun `reicht Adresse und Portionen weiter`() {
        val link = BringLink.forRecipe("https://www.swissmilk.ch/de/rezepte/rösti/?a=1&b=2", servings = 2, persons = 4)

        assertEquals(
            "https://api.getbring.com/rest/bringrecipes/deeplink" +
                "?url=https%3A%2F%2Fwww.swissmilk.ch%2Fde%2Frezepte%2Fr%C3%B6sti%2F%3Fa%3D1%26b%3D2" +
                "&source=web&baseQuantity=2&requestedQuantity=4",
            link,
        )
    }

    @Test
    fun `ohne Portionsangabe rechnet Bring mit vier`() {
        val link = BringLink.forRecipe("https://x.ch/r", servings = null, persons = 2)

        assertEquals(true, link!!.endsWith("&baseQuantity=4&requestedQuantity=2"))
    }

    @Test
    fun `eigene Rezepte ohne Quelle haben keinen Bring-Link`() {
        assertNull(BringLink.forRecipe(null, servings = 4, persons = 2))
        assertNull(BringLink.forRecipe("content://foto", servings = 4, persons = 2))
    }
}
