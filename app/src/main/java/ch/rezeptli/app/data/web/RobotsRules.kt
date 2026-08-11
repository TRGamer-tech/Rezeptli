package ch.rezeptli.app.data.web

import javax.inject.Inject

/**
 * Die Regeln einer robots.txt fuer einen bestimmten Abrufer.
 *
 * Umgesetzt ist das ueblich unterstuetzte Verhalten: Es zaehlt die Gruppe mit dem
 * genauesten passenden User-Agent, sonst die Gruppe fuer `*`. Bei mehreren passenden
 * Regeln gewinnt die laengste; bei gleicher Laenge gewinnt Allow. `*` steht fuer eine
 * beliebige Zeichenfolge, `$` fuer das Ende der Adresse.
 */
class RobotsRules(
    private val rules: List<Rule>,
    val crawlDelayMs: Long,
) {
    data class Rule(val pattern: String, val allow: Boolean)

    fun isAllowed(path: String): Boolean {
        val match = rules
            .filter { matches(it.pattern, path) }
            .maxWithOrNull(compareBy({ it.pattern.length }, { if (it.allow) 1 else 0 }))
            ?: return true
        return match.allow
    }

    private fun matches(pattern: String, path: String): Boolean {
        val anchoredAtEnd = pattern.endsWith("$")
        val cleaned = pattern.removeSuffix("$")

        val parts = cleaned.split("*")
        var index = 0

        parts.forEachIndexed { position, part ->
            if (part.isEmpty()) return@forEachIndexed
            index = if (position == 0) {
                if (!path.startsWith(part)) return false
                part.length
            } else {
                val found = path.indexOf(part, index)
                if (found < 0) return false
                found + part.length
            }
        }

        // Ohne Platzhalter am Ende muss der Pfad hier zu Ende sein, wenn "$" gefordert ist.
        if (anchoredAtEnd && !cleaned.endsWith("*")) return index == path.length
        return true
    }

    companion object {
        /** Erlaubt alles - wenn keine robots.txt erreichbar ist. */
        val PERMISSIVE = RobotsRules(emptyList(), 0L)
    }
}

/**
 * Liest eine robots.txt.
 *
 * Rezeptli tritt unter eigenem Namen auf und ist kein Suchmaschinen- oder
 * Trainings-Crawler: Es laedt genau die eine Seite, die eine Person gerade importieren
 * will. Trotzdem werden die Regeln fuer `*` befolgt - wer sie aufstellt, hat einen
 * Grund dafuer.
 */
class RobotsParser @Inject constructor() {
    fun parse(robotsTxt: String, userAgentToken: String): RobotsRules {
        val groups = mutableMapOf<String, MutableList<RobotsRules.Rule>>()
        val delays = mutableMapOf<String, Long>()
        var currentAgents = mutableListOf<String>()
        var expectingAgents = false

        robotsTxt.lineSequence().forEach { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach

            val key = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()

            when (key) {
                "user-agent" -> {
                    if (!expectingAgents) {
                        currentAgents = mutableListOf()
                        expectingAgents = true
                    }
                    currentAgents.add(value.lowercase())
                    currentAgents.forEach { groups.getOrPut(it) { mutableListOf() } }
                }

                "disallow", "allow" -> {
                    expectingAgents = false
                    if (value.isNotEmpty()) {
                        currentAgents.forEach { agent ->
                            groups
                                .getOrPut(agent) { mutableListOf() }
                                .add(RobotsRules.Rule(value, allow = key == "allow"))
                        }
                    }
                }

                "crawl-delay" -> {
                    expectingAgents = false
                    val seconds = value.replace(',', '.').toDoubleOrNull() ?: return@forEach
                    currentAgents.forEach { agent -> delays[agent] = (seconds * 1000).toLong() }
                }

                else -> expectingAgents = false
            }
        }

        val token = userAgentToken.lowercase()
        val agent = groups.keys.firstOrNull { it == token } ?: WILDCARD
        return RobotsRules(
            rules = groups[agent].orEmpty(),
            crawlDelayMs = delays[agent] ?: delays[WILDCARD] ?: 0L,
        )
    }

    private companion object {
        const val WILDCARD = "*"
    }
}
