import com.jondude1.summonersrpg.sim.*

// Plays whole runs with nobody watching and reports how far each hero got.
fun main(args: Array<String>) {
    val runs = args.getOrNull(0)?.toIntOrNull() ?: 6
    val maxHours = args.getOrNull(1)?.toFloatOrNull() ?: 6f
    val verbose = args.getOrNull(2) == "v"
    val first = args.getOrNull(3)?.toIntOrNull() ?: 0
    var wins = 0
    for (r in first until first + runs) {
        var g = Game(1000L + r * 77)
        val start = System.nanoTime()
        var t = 0f
        var lastRegion = -1
        var saved = false
        val regionAt = HashMap<Int, Float>()
        while (g.phase != "ended" && t < maxHours * 3600f) {
            g.tick(0.1f)
            t += 0.1f
            if (g.regionIdx != lastRegion) {
                lastRegion = g.regionIdx
                regionAt[lastRegion] = t
            }
            // save and reload once mid-run to make sure saves round-trip
            if (!saved && g.regionIdx == 2) {
                saved = true
                g = SaveIo.load(SaveIo.save(g))
            }
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        if (g.won) wins++
        val h = g.hero
        println("run $r: ${h.name} (${h.job}, ${h.personaKey}) -> ${if (g.won) "WON" else if (g.phase == "ended") "DIED: ${g.deathCause}" else "TIMEOUT in ${g.def.name} step ${g.step} doing='${g.doing}'"}" +
            " | ${"%.0f".format(t / 60)} game-min, hero lv ${h.level}, party ${g.party.joinToString { "${it.nick}/${it.species.name} L${it.level} b${it.bond}" }}" +
            " | wins ${h.wins} binds ${h.binds} gold ${h.gold} pot ${h.potions} | ${ms}ms")
        println("   regions reached at minute: " + regionAt.entries.sortedBy { it.key }.joinToString { "${it.key}@${"%.0f".format(it.value / 60)}" })
        if (verbose) {
            val tf = g.foes.filter { it.targetHero && !it.dead }
            println("   STATE inCombat=${g.inCombat} line=${g.line?.text} talk=${g.talk.size} camping=${g.camping} binding=${g.binding} evolving=${g.evolving} summoning=${g.summoning} fleeing=${g.fleeing} order=${g.order} target=${g.targetUid}")
            println("   hero ${g.hero.x},${g.hero.y} hp ${g.hero.hp}/${g.hero.maxHp} mana ${g.hero.mana} crit ${g.cx},${g.cy} lead=${g.lead?.nick} hp ${g.lead?.hp}")
            
            for (f in tf) println("   FOE ${f.uid} ${f.name} L${f.level} hp ${f.hp}/${f.maxHp} at ${f.x},${f.y} hostile=${f.hostile} rift=${f.riftTouched} walk=${g.map.walkable(f.x.toInt(), f.y.toInt())}")
        }
        if (verbose) g.log.takeLast(40).forEach { println("      [${it.kind}] ${it.text}") }
    }
    println("won $wins / $runs")
}
