package com.jondude1.summonersrpg.sim

import org.json.JSONArray
import org.json.JSONObject

/** What lasts between runs: every species ever seen or bound, and the Hall of Legends. */
class Meta {
    val seen = HashSet<Int>()
    val caught = HashSet<Int>()
    val legends = ArrayList<Legend>()
    var runs = 0

    fun toJson(): String {
        val o = JSONObject()
        o.put("seen", JSONArray().also { a -> seen.sorted().forEach { a.put(it) } })
        o.put("caught", JSONArray().also { a -> caught.sorted().forEach { a.put(it) } })
        o.put("runs", runs)
        val l = JSONArray()
        for (g in legends) {
            l.put(
                JSONObject().put("name", g.name).put("job", g.job).put("persona", g.persona).put("reached", g.reached)
                    .put("won", g.won).put("cause", g.cause).put("bound", g.bound).put("best", g.best).put("days", g.days).put("level", g.level),
            )
        }
        o.put("legends", l)
        return o.toString()
    }

    companion object {
        fun fromJson(s: String): Meta {
            val m = Meta()
            val o = JSONObject(s)
            o.optJSONArray("seen")?.let { a -> for (i in 0 until a.length()) m.seen.add(a.getInt(i)) }
            o.optJSONArray("caught")?.let { a -> for (i in 0 until a.length()) m.caught.add(a.getInt(i)) }
            m.runs = o.optInt("runs", 0)
            o.optJSONArray("legends")?.let { a ->
                for (i in 0 until a.length()) {
                    val g = a.getJSONObject(i)
                    m.legends.add(
                        Legend(
                            g.optString("name"), g.optString("job"), g.optString("persona"), g.optString("reached"),
                            g.optBoolean("won", false), g.optString("cause"), g.optInt("bound", 0), g.optString("best"),
                            g.optInt("days", 1), g.optInt("level", 1),
                        ),
                    )
                }
            }
            return m
        }
    }
}

object SaveIo {
    private fun critterJson(c: Critter): JSONObject = JSONObject()
        .put("uid", c.uid).put("sp", c.sp).put("lv", c.level).put("xp", c.xp).put("hp", c.hp)
        .put("nick", c.nick).put("bond", c.bond).put("wins", c.wins)

    private fun critterFrom(o: JSONObject): Critter {
        val c = Critter(o.getInt("uid"), o.getInt("sp"), o.getInt("lv"))
        c.xp = o.optInt("xp", 0)
        c.hp = o.optInt("hp", 1)
        c.nick = o.optString("nick")
        c.bond = o.optInt("bond", 0)
        c.wins = o.optInt("wins", 0)
        return c
    }

    fun save(g: Game): String {
        val h = g.hero
        val o = JSONObject()
        o.put("v", 1)
        o.put("seed", g.runSeed)
        val ho = JSONObject()
            .put("name", h.name).put("job", h.job).put("persona", h.personaKey).put("motive", h.motive)
            .put("pronoun", h.pronoun).put("kin", h.kin).put("kinPronoun", h.kinPronoun).put("elder", h.elder)
            .put("mentor", h.mentor).put("rival", h.rival).put("rivalLine", h.rivalLine)
            .put("hair", h.hair).put("shirt", h.shirt).put("skin", h.skin)
            .put("level", h.level).put("xp", h.xp).put("hp", h.hp.toDouble()).put("maxHp", h.maxHp.toDouble())
            .put("mana", h.mana.toDouble()).put("maxMana", h.maxMana.toDouble()).put("gold", h.gold)
            .put("potions", h.potions).put("ethers", h.ethers).put("scrolls", h.scrolls)
            .put("x", h.x.toDouble()).put("y", h.y.toDouble()).put("wins", h.wins).put("binds", h.binds)
        o.put("hero", ho)
        o.put("party", JSONArray().also { a -> g.party.forEach { a.put(critterJson(it)) } })
        o.put("stored", JSONArray().also { a -> g.stored.forEach { a.put(critterJson(it)) } })
        o.put("active", g.active)
        o.put("nextUid", g.nextUid)
        o.put("region", g.regionIdx)
        o.put("step", g.step)
        o.put("questKind", g.questKind)
        o.put("questGiver", g.questGiver)
        o.put("questSpot", g.questSpot)
        o.put("quest", g.quest)
        o.put("trainT", g.trainT.toDouble())
        o.put("time", g.time.toDouble())
        o.put("phase", g.phase)
        o.put("won", g.won)
        o.put("cause", g.deathCause)
        o.put("used", JSONArray().also { a -> g.map.spots.forEachIndexed { i, s -> if (s.used) a.put(i) } })
        o.put("seenNow", JSONArray().also { a -> g.seenNow.forEach { a.put(it) } })
        o.put("caughtNow", JSONArray().also { a -> g.caughtNow.forEach { a.put(it) } })
        o.put("log", JSONArray().also { a ->
            g.log.takeLast(80).forEach { a.put(JSONObject().put("t", it.text).put("k", it.kind).put("d", it.day)) }
        })
        return o.toString()
    }

    fun load(s: String): Game {
        val o = JSONObject(s)
        val g = Game(o.getLong("seed"))
        g.talk.clear()
        g.line = null
        g.log.clear()
        val ho = o.getJSONObject("hero")
        val h = g.hero
        h.name = ho.optString("name", h.name)
        h.job = ho.optString("job", h.job)
        h.personaKey = ho.optString("persona", h.personaKey)
        h.motive = ho.optString("motive", h.motive)
        h.pronoun = ho.optInt("pronoun", 2)
        h.kin = ho.optString("kin", h.kin)
        h.kinPronoun = ho.optInt("kinPronoun", 2)
        h.elder = ho.optString("elder", h.elder)
        h.mentor = ho.optString("mentor", h.mentor)
        h.rival = ho.optString("rival", h.rival)
        h.rivalLine = ho.optInt("rivalLine", h.rivalLine)
        h.hair = ho.optInt("hair", 0)
        h.shirt = ho.optInt("shirt", 0)
        h.skin = ho.optInt("skin", 0)
        h.level = ho.optInt("level", 1)
        h.xp = ho.optInt("xp", 0)
        h.maxHp = ho.optDouble("maxHp", 60.0).toFloat()
        h.hp = ho.optDouble("hp", 60.0).toFloat()
        h.maxMana = ho.optDouble("maxMana", 30.0).toFloat()
        h.mana = ho.optDouble("mana", 30.0).toFloat()
        h.gold = ho.optInt("gold", 0)
        h.potions = ho.optInt("potions", 0)
        h.ethers = ho.optInt("ethers", 0)
        h.scrolls = ho.optInt("scrolls", 0)
        h.wins = ho.optInt("wins", 0)
        h.binds = ho.optInt("binds", 0)
        g.party.clear()
        o.optJSONArray("party")?.let { a -> for (i in 0 until a.length()) g.party.add(critterFrom(a.getJSONObject(i))) }
        o.optJSONArray("stored")?.let { a -> for (i in 0 until a.length()) g.stored.add(critterFrom(a.getJSONObject(i))) }
        g.active = o.optInt("active", 0).coerceIn(0, (g.party.size - 1).coerceAtLeast(0))
        g.nextUid = o.optInt("nextUid", 100)
        g.restoreRegion(o.optInt("region", 0))
        h.x = ho.optDouble("x", g.map.startX + 0.5).toFloat()
        h.y = ho.optDouble("y", g.map.startY + 0.5).toFloat()
        if (!g.map.reachable(h.x.toInt(), h.y.toInt())) {
            h.x = g.map.startX + 0.5f
            h.y = g.map.startY + 0.5f
        }
        g.cx = h.x - 0.8f
        g.cy = h.y
        g.step = o.optInt("step", 0)
        g.questKind = o.optString("questKind")
        g.questGiver = o.optString("questGiver")
        g.questSpot = o.optInt("questSpot", -1)
        g.quest = o.optString("quest")
        g.trainT = o.optDouble("trainT", 0.0).toFloat()
        g.time = o.optDouble("time", 0.0).toFloat()
        g.phase = o.optString("phase", "travel")
        g.won = o.optBoolean("won", false)
        g.deathCause = o.optString("cause")
        o.optJSONArray("used")?.let { a -> for (i in 0 until a.length()) g.map.spots.getOrNull(a.getInt(i))?.used = true }
        o.optJSONArray("seenNow")?.let { a -> for (i in 0 until a.length()) g.seenNow.add(a.getInt(i)) }
        o.optJSONArray("caughtNow")?.let { a -> for (i in 0 until a.length()) g.caughtNow.add(a.getInt(i)) }
        o.optJSONArray("log")?.let { a ->
            for (i in 0 until a.length()) {
                val l = a.getJSONObject(i)
                g.log.add(LogLine(l.optString("t"), l.optString("k"), l.optInt("d", 1)))
            }
        }
        // a run saved in the middle of the prologue picks up on the road instead
        if (g.phase == "prologue" && g.party.isNotEmpty()) {
            g.phase = "travel"
            g.step = 5
        }
        g.restoreAfterLoad()
        return g
    }
}
