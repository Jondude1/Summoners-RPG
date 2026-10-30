package com.jondude1.summonersrpg.sim

import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// The whole run: one villager, their critters, the road east, and the AI that makes every choice.
// Pure Kotlin: the screen only reads this and calls tick(), so the whole journey can be tested headless.

class Fx(val kind: String, var x0: Float, var y0: Float, var x1: Float, var y1: Float, val dur: Float, val color: Long, val size: Float = 1f) {
    var t = 0f
}

class Floater(val text: String, val x: Float, val y: Float, val color: Long, val big: Boolean) {
    var t = 0f
}

class LogLine(val text: String, val kind: String, val day: Int)

private class Hit(var t: Float, val toFoe: Int, val toHero: Boolean, val dmg: Int, val eff: Float, val crit: Boolean, val srcName: String)

enum class Order(val label: String, val words: List<String>) {
    ATTACK("Attack", listOf("attack", "fight", "charge", "kill", "hit", "battle", "strike", "aggressive", "go get")),
    DEFEND("Defend", listOf("defend", "guard", "shield", "protect", "block", "careful", "cautious")),
    FLEE("Flee", listOf("flee", "run", "retreat", "escape", "back off", "get out", "leave")),
    REST("Rest", listOf("rest", "camp", "sleep", "recover", "break", "nap")),
    BIND("Bind", listOf("bind", "catch", "capture", "tame", "befriend", "recruit", "collect")),
    SUMMON("Summon", listOf("summon", "scroll", "ritual", "call")),
    EXPLORE("Explore", listOf("explore", "search", "look around", "wander", "loot", "treasure", "chest", "scout")),
    TRAIN("Train", listOf("train", "grind", "level", "practice", "hunt", "xp", "stronger")),
    ONWARD("Onward", listOf("onward", "hurry", "boss", "go on", "move on", "continue", "next", "east", "story", "quest")),
    TOWN("Town", listOf("town", "inn", "shop", "buy", "market", "village", "heal up")),
    SWAP("Swap", listOf("swap", "switch", "change", "rotate", "send out")),
    POTION("Potion", listOf("potion", "drink", "heal", "medicine", "ether")),
    SNEAK("Sneak", listOf("sneak", "hide", "quiet", "stealth", "avoid")),
    BOND("Bond", listOf("talk", "pet", "play", "bond", "praise", "love", "feed", "hug")),
    EVOLVE("Evolve", listOf("evolve", "evolution", "grow")),
}

class Game(seed: Long) {
    val rnd = Random(seed)
    var runSeed = seed
    var hero = Hero.random(rnd)

    // ---- the party ----
    val party = ArrayList<Critter>()
    val stored = ArrayList<Critter>()
    var active = 0
    var nextUid = 1
    var cx = 0f
    var cy = 0f
    var cFacing = 1
    var cCd = 0f
    var cLunge = 0f
    var cHurt = 0f

    // ---- the world ----
    var regionIdx = 0
    lateinit var map: RegionMap
    val foes = ArrayList<Foe>()
    val visited = HashSet<String>()

    // ---- story ----
    var step = 0
    var questKind = ""
    var questGiver = ""
    var questSpot = -1
    var questDone = false
    var trainT = 0f
    var bossSpawned = false
    var bossUid = -1
    var keeper = ""

    // ---- time ----
    var time = 0f
    var speed = 1f
    val dayLen = 240f
    val tod: Float get() = ((time + dayLen * 0.3f) % dayLen) / dayLen
    val night: Boolean get() = tod > 0.78f || tod < 0.04f

    // ---- what the screen shows ----
    val fx = ArrayList<Fx>()
    val floaters = ArrayList<Floater>()
    val log = ArrayList<LogLine>()
    val talk = ArrayDeque<Line>()
    var line: Line? = null
    var lineT = 0f
    var bubble = ""
    var bubbleT = 0f
    var critBubble = ""
    var critBubbleT = 0f
    var banner = ""
    var bannerSub = ""
    var bannerT = 0f
    var shake = 0f
    var quest = ""
    var doing = ""

    // ---- state ----
    var phase = "prologue" // prologue | travel | ended
    var won = false
    var deathCause = ""
    var targetUid = -1
    var bindTry = 0
    var binding = 0f
    var bindFoe = -1
    var camping = false
    var campT = 0f
    var campfireX = 0f
    var campfireY = 0f
    var fleeing = false
    var evolving = 0f
    var evolveUid = -1
    var summoning = 0f
    var order: Order? = null
    var orderT = 0f
    private val hits = ArrayList<Hit>()
    private var path: List<Int> = emptyList()
    private var pathIdx = 0
    private var pathGoal = -1
    private var thinkT = 0f
    private var spawnT = 0f
    private var nextFoe = 1
    private var outT = 0f
    private var waitT = 0f
    private var castCd = 0f
    private var potionCd = 0f
    private var lastBark = -99f
    private var restUntilFull = false
    private var lastClash = 0f
    private var bindRest = 0f
    private var fleeT = 0f
    private var campSaid = false
    var deaths = 0

    /** Species seen and bound across every run, merged into the Critterdex by the caller. */
    val seenNow = HashSet<Int>()
    val caughtNow = HashSet<Int>()

    val def: RegionDef get() = Regions.all[regionIdx]
    val lead: Critter? get() = party.getOrNull(active)?.takeIf { it.hp > 0 }
    val inCombat: Boolean get() = foes.any { it.hostile && !it.dead && it.targetHero }
    val bossFoe: Foe? get() = foes.firstOrNull { it.uid == bossUid && !it.dead }

    init {
        enterRegion(0, fresh = true)
        step = 0
        val h = hero
        say(Lore.prologue(h))
        log("${h.name}, ${aOrAn(h.job.lowercase())} ${h.job.lowercase()}'s child, stands before the rift gate for the gift test.", "story")
        doing = "Walking to the rift gate"
        quest = "Pass the gift test"
    }

    // ================= small helpers =================

    private fun aOrAn(w: String) = if ((w.firstOrNull()?.lowercaseChar() ?: 'x') in "aeiou") "an" else "a"

    fun log(text: String, kind: String) {
        log.add(LogLine(text, kind, hero.days))
        while (log.size > 160) log.removeAt(0)
    }

    private fun say(lines: List<Line>) {
        for (l in lines) talk.addLast(l)
    }

    fun bark(text: String, force: Boolean = false) {
        if (!force && time - lastBark < 9f) return
        lastBark = time
        bubble = text
        bubbleT = 3.2f
    }

    private fun barkFrom(list: List<String>, other: String = "", species: String = "", boss: String = "", force: Boolean = false) {
        bark(Lore.fill(Lore.pick(rnd, list), hero, hero.crit, other, species, boss), force)
    }

    private fun float(text: String, x: Float, y: Float, color: Long, big: Boolean = false) {
        floaters.add(Floater(text, x, y, color, big))
    }

    private fun d(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return sqrt(dx * dx + dy * dy)
    }

    private fun newCritter(sp: Int, level: Int): Critter {
        val c = Critter(nextUid++, sp, level)
        c.heal()
        return c
    }

    private fun nameFor(c: Critter) {
        val used = (party + stored).map { it.nick }.toSet()
        val free = Lore.CRITTER_NAMES.filter { it !in used && it != hero.name }
        c.nick = if (free.isEmpty()) Lore.CRITTER_NAMES[rnd.nextInt(Lore.CRITTER_NAMES.size)] else free[rnd.nextInt(free.size)]
    }

    private fun refreshCritName() {
        hero.crit = (party.getOrNull(active) ?: party.firstOrNull())?.nick ?: "friend"
    }

    fun partyLevel(): Int = party.maxOfOrNull { it.level } ?: 1

    // ================= regions =================

    private fun enterRegion(idx: Int, fresh: Boolean) {
        regionIdx = idx
        map = RegionMap(def, runSeed * 31 + idx * 7919L)
        foes.clear()
        fx.clear()
        hits.clear()
        bossSpawned = false
        bossUid = -1
        targetUid = -1
        path = emptyList()
        if (fresh) {
            hero.x = map.startX + 0.5f
            hero.y = map.startY + 0.5f
            cx = hero.x - 0.8f
            cy = hero.y
        }
        keeper = Lore.KEEPERS[(runSeed + idx).toInt().mod(Lore.KEEPERS.size)]
        repeat(6) { spawnWild(far = true) }
        repeat(if (idx == 0) 0 else 2 + idx / 2) { spawnRoamer() }
    }

    private fun spotsOf(kind: String) = map.spots.withIndex().filter { it.value.kind == kind }

    // ================= spawning =================

    private fun levelHere(x: Float): Int {
        val r = def
        val prog = (x / map.w).coerceIn(0f, 1f)
        val base = r.lvMin + ((r.lvMax - r.lvMin) * prog).toInt() + regionIdx / 2
        return (base + rnd.nextInt(3) - 1).coerceIn(2, Dex.MAX_LEVEL)
    }

    private fun pickSpecies(level: Int): Species {
        val hab = def.habitats
        var total = 0f
        val w = FloatArray(Dex.species.size)
        for ((i, sp) in Dex.species.withIndex()) {
            if (sp.rarity >= 5) continue
            var x = when (sp.rarity) { 1 -> 10f; 2 -> 4f; 3 -> 1.6f; else -> 0.6f }
            if (sp.habitat !in hab && sp.habitat != "any") x *= 0.08f
            if (sp.habitat == hab.first()) x *= 2f
            if (sp.stage == 2 && level < 12) x *= if (regionIdx == 0) 0f else 0.15f
            if (regionIdx == 0 && sp.rarity > 2) x = 0f
            if (sp.stage == 3 && level < 26) x = 0f
            if (sp.stage == 1 && sp.evolveTo > 0 && level > 24) x *= 0.3f
            if (sp.activity == 1 && !night) x *= 0.1f
            if (sp.activity == 2 && night) x = 0f
            w[i] = x
            total += x
        }
        var r = rnd.nextFloat() * total
        for (i in w.indices) {
            r -= w[i]
            if (r <= 0f && w[i] > 0f) return Dex.species[i]
        }
        return Dex.species[0]
    }

    private fun spawnAt(far: Boolean): Pair<Int, Int>? {
        for (t in 0 until 40) {
            val x = 3 + rnd.nextInt(map.w - 6)
            val y = 2 + rnd.nextInt(map.h - 4)
            if (!map.reachable(x, y)) continue
            val dist = d(x + 0.5f, y + 0.5f, hero.x, hero.y)
            if (dist < (if (far) 12f else 9f)) continue
            if (map.townX >= 0 && abs(x - map.townX) < 10 && abs(y - map.townY) < 8) continue
            return x to y
        }
        return null
    }

    private fun spawnWild(far: Boolean) {
        val at = spawnAt(far) ?: return
        val lv = levelHere(at.first.toFloat())
        val sp = pickSpecies(lv)
        val f = Foe(nextFoe++, FoeKind.WILD, lv, at.first + 0.5f, at.second + 0.5f)
        val c = Critter(-1, sp.id, lv)
        // the land itself evolves what lives there
        while (c.species.evolveTo > 0 && c.species.evolveLv > 0 && lv >= c.species.evolveLv + 2) c.sp = c.species.evolveTo
        c.heal()
        f.critter = c
        f.riftTouched = regionIdx > 0 && rnd.nextFloat() < (if (night) 0.4f else 0.22f)
        f.hostile = f.riftTouched
        f.setup()
        foes.add(f)
    }

    private fun spawnRoamer() {
        if (def.foes.isEmpty()) return
        val at = spawnAt(true) ?: return
        val kind = def.foes[rnd.nextInt(def.foes.size)]
        val k = if (night && regionIdx >= 2 && rnd.nextBoolean()) FoeKind.WRAITH else kind
        val f = Foe(nextFoe++, k, levelHere(at.first.toFloat()), at.first + 0.5f, at.second + 0.5f)
        f.setup()
        foes.add(f)
    }

    private fun spawnPack(kind: FoeKind, n: Int, x: Int, y: Int, lvBonus: Int, campIdx: Int) {
        repeat(n) { i ->
            val p = map.near(x, y, 3, rnd) ?: (x to y)
            val f = Foe(nextFoe++, kind, levelHere(x.toFloat()) + lvBonus + (if (i == 0) 2 else 0), p.first + 0.5f, p.second + 0.5f)
            if (i == 0 && n > 2) f.named = "${kind.label} Captain"
            f.campIdx = campIdx
            f.setup()
            if (i == 0 && n > 2) { f.maxHp = (f.maxHp * 1.5f).toInt(); f.hp = f.maxHp }
            foes.add(f)
        }
    }

    private fun spawnBoss() {
        val r = def
        val kind = r.boss ?: return
        bossSpawned = true
        val lv = max(r.bossLv, partyLevel() + 2 + regionIdx / 2)
        val f = Foe(nextFoe++, kind, lv, map.bossX + 0.5f, map.bossY + 0.5f)
        f.named = if (kind == FoeKind.RIVAL) hero.rival else r.bossName
        when (kind) {
            FoeKind.RIVAL -> {
                val line = Dex.lineOf(hero.rivalLine)
                val c = Critter(-1, line.last().id, lv)
                c.nick = "${hero.rival}'s ${line.last().name}"
                f.critter = c
            }
            FoeKind.HOLLOW -> {
                val c = Critter(-1, 62, lv)
                f.critter = c
            }
            else -> Unit
        }
        f.setup()
        f.hostile = true
        foes.add(f)
        bossUid = f.uid
        when (kind) {
            FoeKind.CHIEF -> spawnPack(FoeKind.BANDIT, 2, map.bossX, map.bossY, 0, -1)
            FoeKind.WARLORD -> spawnPack(FoeKind.OGRE, 1, map.bossX, map.bossY, -2, -1)
            FoeKind.WITCH -> spawnPack(FoeKind.WRAITH, 2, map.bossX, map.bossY, -2, -1)
            FoeKind.HOLLOW -> spawnPack(FoeKind.KNIGHT, 2, map.bossX, map.bossY, -4, -1)
            else -> Unit
        }
        for (f2 in foes) if (d(f2.x, f2.y, map.bossX + 0.5f, map.bossY + 0.5f) < 6f && f2.kind != FoeKind.WILD) f2.hostile = true
    }

    // ================= the main loop =================

    fun tick(realDt: Float) {
        if (phase == "ended") {
            if (line != null) {
                lineT -= realDt
                if (lineT <= 0f) line = null
            } else if (talk.isNotEmpty()) {
                val l = talk.removeFirst()
                line = l
                lineT = 2.6f + l.text.length * 0.045f
                log(if (l.who == "narrator") l.text else "${l.speaker}: ${l.text}", if (l.who == "narrator") "story" else "talk")
            }
            animate(realDt)
            return
        }
        var left = realDt * speed * (if (camping) 6f else 1f)
        while (left > 0f) {
            val dt = min(left, 0.05f)
            step(dt)
            left -= dt
        }
        animate(realDt * min(speed, 3f))
    }

    private fun animate(dt: Float) {
        for (f in fx) {
            f.t += dt
        }
        fx.removeAll { it.t >= it.dur }
        for (f in floaters) f.t += dt
        floaters.removeAll { it.t > 1.4f }
        if (shake > 0f) shake = max(0f, shake - dt * 3f)
        if (bannerT > 0f) bannerT -= dt
    }

    private fun step(dt: Float) {
        time += dt
        if (bubbleT > 0f) bubbleT -= dt
        if (critBubbleT > 0f) critBubbleT -= dt
        if (hero.cast > 0f) hero.cast -= dt
        if (hero.hurt > 0f) hero.hurt -= dt
        if (cHurt > 0f) cHurt -= dt
        if (cLunge > 0f) cLunge -= dt
        castCd -= dt
        potionCd -= dt
        if (orderT > 0f) {
            orderT -= dt
            if (orderT <= 0f) order = null
        }

        // story dialogue holds the hero still while it plays
        val cur = line
        if (cur != null) {
            lineT -= dt
            if (lineT <= 0f) line = null
            if (!inCombat) {
                followCritter(dt)
                updateFoes(dt, calm = true)
                resolveHits(dt)
                return
            }
        } else if (talk.isNotEmpty() && !inCombat) {
            val l = talk.removeFirst()
            line = l
            lineT = 2.6f + l.text.length * 0.045f
            if (l.who != "narrator") log("${l.speaker}: ${l.text}", if (l.who == "voice") "voice" else "talk")
            else log(l.text, "story")
            return
        }

        // a new day
        val day = (time / dayLen).toInt() + 1
        if (day != hero.days) hero.days = day

        hero.mana = min(hero.maxMana, hero.mana + dt * (0.35f + hero.level * 0.02f))
        if (!inCombat && !camping) hero.hp = min(hero.maxHp, hero.hp + dt * 0.25f)
        for (c in party) {
            c.regen += dt
            if (c.hp > 0 && c != lead && c.regen > 4f) {
                c.regen = 0f
                c.hp = min(c.maxHp, c.hp + max(1, c.maxHp / 40))
            }
        }

        // bond grows just from being out together
        val l = lead
        if (l != null && !inCombat) {
            outT += dt
            if (outT > 50f) {
                outT = 0f
                l.bond++
            }
        }

        if (evolving > 0f) {
            evolving -= dt
            if (evolving <= 0f) finishEvolve()
            followCritter(dt)
            return
        }
        if (summoning > 0f) {
            summoning -= dt
            if (summoning <= 0f) finishSummon()
            return
        }
        if (binding > 0f) {
            binding -= dt
            updateFoes(dt, calm = false, frozen = bindFoe)
            if (binding <= 0f) finishBind()
            resolveHits(dt)
            return
        }
        if (camping) {
            doCamp(dt)
            return
        }

        spawnT -= dt
        if (spawnT <= 0f) {
            spawnT = 14f
            val wild = foes.count { it.kind == FoeKind.WILD && !it.dead }
            if (wild < 7) spawnWild(far = true)
            val roam = foes.count { it.kind != FoeKind.WILD && !it.dead && it.campIdx < 0 && it.uid != bossUid }
            if (regionIdx > 0 && roam < 2 + regionIdx / 2 + (if (night) 1 else 0)) spawnRoamer()
        }

        updateFoes(dt, calm = false)
        resolveHits(dt)
        if (phase == "ended") return

        thinkT -= dt
        if (thinkT <= 0f) {
            thinkT = 0.35f
            think()
        }
        if (inCombat) combat(dt) else walkPath(dt)
        unstick()
        followCritter(dt)
        foes.removeAll { it.dead && it.deathT > 1.2f }
        for (f in foes) if (f.dead) f.deathT += dt
    }

    // ================= the AI =================

    private fun think() {
        val h = hero
        if (inCombat) return
        fleeing = false
        // look after everyone between fights
        if (h.hp < h.maxHp * 0.4f && h.potions > 0 && potionCd <= 0f) drink(onHero = true)
        val l = lead
        if (l == null && party.any { it.hp > 0 }) swapTo(bestSwap(null), quiet = true)
        val ready = party.firstOrNull { it.hp > 0 && it.readyToEvolve() }
        if (ready != null && (order == Order.EVOLVE || rnd.nextFloat() < 0.5f)) {
            startEvolve(ready)
            return
        }

        if (phase == "prologue") {
            prologueStep()
            return
        }

        // the night: make camp unless safe in town or the boss is close
        val inTown = nearTown(6f)
        if (night && !inTown && order != Order.ONWARD && order != Order.ATTACK) {
            startCamp()
            return
        }
        val healthy = party.count { it.hp > it.maxHp / 3 }
        val needRest = h.hp < h.maxHp * 0.45f || healthy == 0 || (party.size >= 2 && healthy < (party.size + 1) / 2)
        if ((needRest || order == Order.TOWN) && map.townX >= 0 && d(h.x, h.y, map.townX + 0.5f, map.townY + 0.5f) < 30f) {
            goInn()
            return
        }
        if (needRest || order == Order.REST) {
            restUntilFull = true
            startCamp()
            return
        }

        if (order == Order.SUMMON && h.scrolls > 0) {
            startSummon()
            order = null
            return
        }
        if (order == Order.POTION) {
            drink(onHero = h.hp < h.maxHp * 0.7f)
            order = null
        }
        if (order == Order.SWAP) {
            swapTo(nextHealthy(), quiet = false)
            order = null
        }
        if (order == Order.BOND) {
            bondMoment()
            order = null
        }

        // shop when passing through town with coin to spare
        if (inTown && (h.potions < 3 + h.gold / 150 || (h.scrolls < 1 && h.gold > 80))) shop()

        // a critter in sight that is worth binding
        val brave = h.persona.bravery
        if (order != Order.FLEE && order != Order.SNEAK && order != Order.ONWARD && order != Order.DEFEND) {
            val want = foes.filter { it.kind == FoeKind.WILD && !it.dead && !it.hostile && wantBind(it) }
                .minByOrNull { d(it.x, it.y, h.x, h.y) }
            val reach = if (order == Order.BIND) 30f else 7f + h.persona.curiosity * 4f
            if (want != null && d(want.x, want.y, h.x, h.y) < reach && h.mana >= 14f && l != null) {
                engage(want)
                return
            }
            // train: pick fights to grow stronger
            val train = order == Order.TRAIN || order == Order.ATTACK || (step == 5 && trainT < trainCap())
            if (train && l != null && l.hp > l.maxHp / 2) {
                val prey = foes.filter { !it.dead && it.uid != bossUid && it.level <= partyLevel() + (if (brave > 0.7f) 4 else 2) }
                    .minByOrNull { d(it.x, it.y, h.x, h.y) }
                if (prey != null && d(prey.x, prey.y, h.x, h.y) < 22f) {
                    engage(prey)
                    return
                }
            }
        }
        if (order == Order.EXPLORE && explore()) return
        storyGoal()
    }

    private fun trainCap(): Float = 420f + regionIdx * 70f

    private fun wantBind(f: Foe): Boolean {
        val c = f.critter ?: return false
        if (f.riftTouched) return false
        val known = c.sp in caughtNow
        if (order == Order.BIND) return true
        if (time < bindRest) return false
        val weakest = party.minByOrNull { it.level } ?: return true
        if (!known) return party.size < 6 || c.level >= weakest.level
        if (party.size < 3) return true
        return party.size >= 6 && c.level > weakest.level + 5 && c.species.stage > weakest.species.stage
    }

    private fun engage(f: Foe) {
        lastClash = time
        f.hostile = true
        f.targetHero = true
        targetUid = f.uid
        bindTry = 0
        val l = lead
        // pick the best critter for this foe
        val best = bestSwap(f)
        if (l != null && best >= 0 && best != active) {
            val b = party[best]
            val gain = typeScore(b, f) - typeScore(l, f)
            if (gain > 0.6f) swapTo(best, quiet = false)
        }
        val wild = f.kind == FoeKind.WILD && !f.riftTouched
        if (wild) {
            if (f.critter!!.sp !in caughtNow) barkFrom(hero.persona.battle) else Unit
            doing = "Weakening a wild ${f.name} to bind it"
        } else {
            barkFrom(hero.persona.battle)
            doing = "Fighting ${aOrAn(f.name)} ${f.name}"
        }
        f.critter?.let { seenNow.add(it.sp) }
        for (o in foes) if (!o.dead && o.hostile && d(o.x, o.y, f.x, f.y) < 4f) o.targetHero = true
    }

    private fun typeScore(c: Critter, f: Foe): Float {
        var best = 0f
        for (m in c.moves) {
            val e = Combat.eff(m.type, f.types) * (if (c.species.types.contains(m.type)) 1.3f else 1f) * m.power / 60f
            if (e > best) best = e
        }
        var def = 1f
        for (t in f.types) def *= Combat.eff(t, c.species.types)
        return best - def * 0.5f + c.level * 0.03f
    }

    private fun bestSwap(f: Foe?): Int {
        var bi = -1
        var bs = -999f
        for ((i, c) in party.withIndex()) {
            if (c.hp <= c.maxHp / 5) continue
            val s = (if (f != null) typeScore(c, f) else 0f) + c.level * 0.05f + c.hpFrac
            if (s > bs) { bs = s; bi = i }
        }
        return bi
    }

    private fun nextHealthy(): Int {
        for (k in 1..party.size) {
            val i = (active + k) % party.size
            if (party[i].hp > 0) return i
        }
        return -1
    }

    private fun swapTo(i: Int, quiet: Boolean) {
        if (i < 0 || i >= party.size || (i == active && party[i].hp > 0)) return
        val old = party.getOrNull(active)
        active = i
        val c = party[i]
        refreshCritName()
        fx.add(Fx("summon", hero.x, hero.y, cx, cy, 0.9f, c.species.t1.color))
        if (!quiet) {
            bark(if (old != null && old.hp <= 0) "${old.nick}, rest. ${c.nick}, you're up!" else "Switch! ${c.nick}, go!", force = true)
            log("${hero.name} calls ${c.label} out of the rune-mark on ${hero.their} palm.", "fight")
        }
    }

    // ---- the prologue in the home village ----
    private fun prologueStep() {
        val h = hero
        val gate = spotsOf("gate").firstOrNull()?.value
        when (step) {
            0 -> {
                if (gate == null) { step = 2; return }
                if (walkTo(gate.x, gate.y)) {
                    step = 1
                    summoning = 2.4f
                    fx.add(Fx("rift", gate.x + 0.5f, gate.y - 0.5f, h.x, h.y, 2.4f, 0xFFB46CFF, 1.4f))
                    doing = "Reading the scroll at the rift gate"
                }
            }
            1 -> Unit
            2 -> {
                step = 3
                say(Lore.raid(h))
                val tx = map.townX
                val ty = map.townY
                spawnPack(FoeKind.WOLF, 3, tx + 6, ty + 3, -1, -1)
                for (f in foes) if (f.kind == FoeKind.WOLF) {
                    f.level = 3
                    f.setup()
                    f.hostile = true
                }
                doing = "Defending the village from rift wolves"
                quest = "Drive the rift wolves out of the village"
            }
            3 -> {
                val wolves = foes.count { it.kind == FoeKind.WOLF && !it.dead }
                if (wolves == 0) {
                    step = 4
                    say(Lore.sendOff(h))
                    log("The village gives ${h.name} three potions, two scrolls, and a road east.", "loot")
                    quest = "Leave the village and head east"
                } else {
                    val w = foes.filter { it.kind == FoeKind.WOLF && !it.dead }.minByOrNull { d(it.x, it.y, h.x, h.y) }
                    if (w != null) engage(w)
                }
            }
            4 -> {
                h.potions = max(h.potions, 3)
                h.scrolls = max(h.scrolls, 2)
                step = 5
                phase = "travel"
                doing = "Heading east"
            }
        }
    }

    private fun finishSummon() {
        val h = hero
        if (phase == "prologue" && step == 1) {
            val types = Lore.starterTypes(h.job)
            val pool = Dex.species.filter { it.stage == 1 && it.rarity == 1 && it.evolveTo > 0 && it.types.any { t -> t in types } }
                .ifEmpty { Dex.species.filter { it.stage == 1 && it.rarity == 1 } }
            val sp = pool[rnd.nextInt(pool.size)]
            val c = newCritter(sp.id, 5)
            nameFor(c)
            c.bond = 4
            party.add(c)
            active = 0
            caughtNow.add(c.sp)
            seenNow.add(c.sp)
            refreshCritName()
            cx = h.x + 1f
            cy = h.y
            fx.add(Fx("burst", cx, cy, cx, cy, 0.9f, sp.t1.color, 1.6f))
            say(Lore.voiceIntro(h, c))
            log("${h.name} has the gift! A ${sp.name} answers and is named ${c.nick}.", "bind")
            banner("The Gift", "${h.name} summons ${c.nick} the ${sp.name}")
            step = 2
            return
        }
        // an ordinary scroll at a shrine or on the road
        if (h.scrolls <= 0) return
        h.scrolls--
        val lv = (def.lvMin + rnd.nextInt(max(1, def.lvMax - def.lvMin + 1))).coerceAtLeast(3)
        var sp = pickSpecies(lv)
        // a rare scroll pull now and then
        if (rnd.nextFloat() < 0.12f) {
            val rare = Dex.species.filter { it.rarity == 4 }
            sp = rare[rnd.nextInt(rare.size)]
        }
        val c = newCritter(sp.id, lv)
        nameFor(c)
        addToParty(c, "summons")
        fx.add(Fx("burst", cx, cy, cx, cy, 0.9f, sp.t1.color, 1.6f))
    }

    private fun startSummon() {
        if (hero.scrolls <= 0) return
        summoning = 2.2f
        doing = "Reading a summoning scroll"
        bark("Let's see who answers...", force = true)
        fx.add(Fx("rift", hero.x + 1.2f, hero.y - 0.6f, hero.x, hero.y, 2.2f, 0xFFB46CFF, 1f))
        hero.cast = 2.2f
    }

    private fun addToParty(c: Critter, how: String) {
        val h = hero
        caughtNow.add(c.sp)
        seenNow.add(c.sp)
        h.binds++
        if (party.size >= 6) {
            val weakest = party.minByOrNull { it.level * 10 + it.bond }!!
            party.remove(weakest)
            stored.add(weakest)
            weakest.heal()
            log("${weakest.label} is sent home to Summoner's Village to make room.", "bind")
            active = party.indexOfFirst { it.hp > 0 }.coerceAtLeast(0)
        }
        party.add(c)
        refreshCritName()
        log("${h.name} $how ${c.label}, Lv ${c.level} (${c.species.typeText}).", "bind")
        barkFrom(h.persona.bind, other = c.nick, species = c.species.name, force = true)
        float("Bound!", cx, cy - 1f, 0xFFB46CFF, big = true)
        if (c.species.rarity >= 4) banner("A Rare Bond", "${c.nick} the ${c.species.name} joins ${h.name}")
    }

    // ---- the story, land by land ----
    private fun storyGoal() {
        val h = hero
        val r = def
        when (step) {
            0 -> {
                say(Lore.arrive(h, r))
                banner(r.name, "Chapter ${r.idx}")
                log("Chapter ${r.idx}: ${h.name} reaches ${r.name}.", "story")
                step = if (map.townX >= 0) 1 else 5
                quest = if (map.townX >= 0) "Find the town of ${r.town}" else "Find a way through ${r.name}"
            }
            1 -> {
                doing = "Walking to ${r.town}"
                if (walkTo(map.townX, map.townY)) {
                    step = 2
                    visited.add("town$regionIdx")
                    say(Lore.townHello(h, r, keeper))
                    log("${h.name} reaches ${r.town}.", "story")
                }
            }
            2 -> {
                shop()
                innRest(free = true)
                // pick a job the town needs
                val options = ArrayList<String>()
                if (spotsOf("camp").any { !it.value.used }) { options.add("bell"); options.add("den") }
                if (spotsOf("ruin").isNotEmpty()) { options.add("relic"); options.add("herbs") }
                if (spotsOf("chest").isNotEmpty()) options.add("child")
                if (options.isEmpty() || regionIdx == 6) {
                    step = 5
                    return
                }
                questKind = options[rnd.nextInt(options.size)]
                questGiver = Lore.KEEPERS[(rnd.nextInt(Lore.KEEPERS.size))]
                val kid = Lore.KIDS[rnd.nextInt(Lore.KIDS.size)]
                val text = Lore.QUESTS.first { it.first == questKind }.second.replace("%k", kid)
                questSpot = when (questKind) {
                    "bell", "den" -> spotsOf("camp").firstOrNull { !it.value.used }?.index ?: -1
                    "relic", "herbs" -> spotsOf("ruin").firstOrNull()?.index ?: -1
                    else -> spotsOf("chest").maxByOrNull { d(it.value.x.toFloat(), it.value.y.toFloat(), map.townX.toFloat(), map.townY.toFloat()) }?.index ?: -1
                }
                if (questSpot < 0) { step = 5; return }
                say(Lore.questGive(h, questGiver, text))
                quest = when (questKind) {
                    "bell" -> "Take back the chapel bell from the bandit camp"
                    "den" -> "Clear the den near the road"
                    "relic" -> "Fetch the relic from the old ruins"
                    "herbs" -> "Gather moonleaf by the ruins"
                    else -> "Find little $kid in the wilds"
                }
                questDone = false
                step = 3
            }
            3 -> {
                if (order == Order.ONWARD) {
                    log("${h.name} leaves the job in ${r.town} undone and pushes on.", "order")
                    step = 5
                    return
                }
                val s = map.spots.getOrNull(questSpot) ?: run { step = 5; return }
                doing = quest
                if (d(h.x, h.y, s.x + 0.5f, s.y + 0.5f) < 9f && (questKind == "bell" || questKind == "den") && !s.used) {
                    s.used = true
                    val kind = if (questKind == "den") (if (regionIdx >= 3) FoeKind.WRAITH else FoeKind.WOLF) else (if (regionIdx >= 2) FoeKind.OGRE else FoeKind.BANDIT)
                    spawnPack(kind, 3 + regionIdx / 3, s.x, s.y, 1, questSpot)
                    for (f in foes) if (f.campIdx == questSpot) f.hostile = true
                    bark("There they are. Ready, ${h.crit}?", force = true)
                }
                if (walkTo(s.x, s.y)) {
                    val left = foes.count { it.campIdx == questSpot && !it.dead }
                    if (left == 0) {
                        questDone = true
                        step = 4
                        when (questKind) {
                            "bell" -> log("${h.name} finds the chapel bell under a tarp in the camp.", "loot")
                            "den" -> log("The den is quiet now.", "story")
                            "relic" -> { log("${h.name} lifts a rune-carved relic from the ruins. The air goes still.", "loot"); h.scrolls++ }
                            "herbs" -> log("${h.name} picks a bundle of silver moonleaf.", "loot")
                            else -> log("${h.name} finds the lost child hiding in a hollow and carries them on ${h.their} back.", "story")
                        }
                        s.used = true
                        quest = "Return to ${r.town}"
                    }
                }
            }
            4 -> {
                doing = "Returning to ${r.town}"
                if (walkTo(map.townX, map.townY)) {
                    val reward = 30 + regionIdx * 25
                    h.gold += reward
                    h.potions += 1
                    say(listOf(Line("npc", questGiver, "You did it! Here, ${reward} gold and a potion. ${r.town} won't forget you."), Line("hero", h.name, "Glad we could help.")))
                    log("${h.name} earns $reward gold and a potion in ${r.town}.", "loot")
                    gainHeroXp(20 + regionIdx * 10)
                    innRest(free = true)
                    step = 5
                    trainT = 0f
                }
            }
            5 -> {
                // prepare for what waits at the end of the land
                quest = if (r.boss != null) "Grow strong enough to face ${r.bossName.ifBlank { h.rival }}" else "Cross ${r.name}"
                val ready = (partyLevel() >= r.bossLv - 2 && trainT > 150f) || trainT >= trainCap() || order == Order.ONWARD
                trainT += 0.35f
                if (!ready) {
                    if (h.persona.curiosity > 0.5f && explore()) return
                    // wander and hunt
                    doing = "Training in ${r.name}"
                    val prey = foes.filter { !it.dead && it.uid != bossUid && (it.kind != FoeKind.WILD || it.level <= partyLevel() + 2) }
                        .minByOrNull { d(it.x, it.y, h.x, h.y) }
                    if (prey != null && lead != null) {
                        if (d(prey.x, prey.y, h.x, h.y) < 4f) engage(prey) else walkTo(prey.x.toInt(), prey.y.toInt())
                    }
                    return
                }
                if (explore()) return
                step = 6
            }
            6 -> {
                if (r.boss == null) {
                    step = 8
                    return
                }
                doing = "Marching to face ${r.bossName.ifBlank { h.rival }}"
                quest = "Defeat ${r.bossName.ifBlank { h.rival }}"
                if (!bossSpawned && d(h.x, h.y, map.bossX + 0.5f, map.bossY + 0.5f) < 11f) {
                    // top up before the big fight
                    if (h.hp < h.maxHp * 0.8f && h.potions > 1) drink(onHero = true)
                    spawnBoss()
                    say(Lore.bossIntro(h, r, bossFoe?.name ?: r.bossName))
                    banner(bossFoe?.name ?: "", "Boss of ${r.name}")
                    return
                }
                if (bossSpawned && bossFoe == null) {
                    step = 7
                    return
                }
                val b = bossFoe
                if (b != null && line == null && talk.isEmpty()) {
                    engage(b)
                    return
                }
                walkTo(map.bossX - 4, map.bossY)
            }
            7 -> {
                say(Lore.bossWin(h, r, r.bossName.ifBlank { h.rival }))
                log("${h.name} has beaten ${r.bossName.ifBlank { h.rival }}!", "story")
                h.gold += r.boss?.gold ?: 0
                gainHeroXp(60 + regionIdx * 30)
                h.scrolls++
                if (r.boss == FoeKind.DRAKE) {
                    // the drake leaves a legend behind in the closing rift
                    val legends = listOf(57, 58, 59, 60, 61)
                    val c = newCritter(legends[rnd.nextInt(legends.size)], r.bossLv - 3)
                    nameFor(c)
                    say(listOf(Line("narrator", "", "As the rift closes, something bright tumbles out of it and lands in the snow beside ${h.name}: ${c.species.name}, a legend.")))
                    addToParty(c, "binds the legend")
                }
                if (regionIdx == Regions.LAST) {
                    val c = newCritter(62, 45)
                    c.nick = "Hollow"
                    say(Lore.ending(h))
                    addToParty(c, "frees and binds")
                    step = 9
                    return
                }
                step = 8
            }
            8 -> {
                quest = "Head east out of ${r.name}"
                doing = "Heading east"
                if (walkTo(map.exitX, map.exitY)) travelOn()
            }
            9 -> {
                if (talk.isEmpty() && line == null) endRun(won = true, cause = "")
            }
        }
    }

    private fun travelOn() {
        if (regionIdx >= Regions.LAST) return
        val h = hero
        log("${h.name} leaves ${def.name} behind.", "story")
        for (c in party) c.hp = max(c.hp, c.maxHp / 2)
        enterRegion(regionIdx + 1, fresh = true)
        step = 0
        trainT = 0f
        questKind = ""
        questSpot = -1
        visited.clear()
    }

    private fun explore(): Boolean {
        val h = hero
        val opts = map.spots.withIndex().filter { !it.value.used && (it.value.kind == "chest" || it.value.kind == "shrine" || (it.value.kind == "ruin" && questKind != "relic" && questKind != "herbs")) }
        val g = opts.minByOrNull { d(h.x, h.y, it.value.x + 0.5f, it.value.y + 0.5f) } ?: return false
        if (d(h.x, h.y, g.value.x + 0.5f, g.value.y + 0.5f) > 40f) return false
        doing = "Exploring ${g.value.label}"
        if (walkTo(g.value.x, g.value.y)) {
            g.value.used = true
            when (g.value.kind) {
                "chest" -> openChest(g.value)
                "shrine" -> {
                    h.hp = h.maxHp
                    h.mana = h.maxMana
                    for (c in party) c.heal()
                    h.scrolls++
                    fx.add(Fx("heal", h.x, h.y, h.x, h.y, 1.2f, 0xFF8FE8FF, 1.6f))
                    log("At the rift shrine, ${h.name} rests, and finds a scroll in the offering bowl.", "loot")
                    if (party.size < 6) startSummon()
                }
                "ruin" -> {
                    h.gold += 20 + regionIdx * 10
                    h.ethers++
                    log("${h.name} searches the ruins and finds old coins and an ether.", "loot")
                    if (rnd.nextFloat() < 0.6f) {
                        spawnPack(if (regionIdx >= 3) FoeKind.WRAITH else FoeKind.BANDIT, 2, g.value.x, g.value.y, 1, -1)
                        for (f in foes) if (d(f.x, f.y, h.x, h.y) < 5f) { f.hostile = true }
                        bark("Ambush!", force = true)
                    }
                }
            }
        }
        return true
    }

    private fun openChest(s: Spot) {
        val h = hero
        fx.add(Fx("burst", s.x + 0.5f, s.y + 0.5f, s.x + 0.5f, s.y + 0.5f, 0.7f, 0xFFFFD86A))
        val roll = rnd.nextInt(10)
        val gold = 10 + regionIdx * 12 + rnd.nextInt(20)
        h.gold += gold
        val extra = when {
            roll < 4 -> { h.potions++; "a potion" }
            roll < 6 -> { h.scrolls++; "a summoning scroll" }
            roll < 8 -> { h.ethers++; "an ether" }
            else -> { h.potions += 2; "two potions" }
        }
        log("${h.name} opens a chest: $gold gold and $extra.", "loot")
        float("+$gold gold", s.x + 0.5f, s.y.toFloat(), 0xFFFFD86A)
    }

    private fun nearTown(r: Float): Boolean = map.townX >= 0 && d(hero.x, hero.y, map.townX + 0.5f, map.townY + 0.5f) < r

    private fun goInn() {
        doing = "Heading to the inn to recover"
        if (walkTo(map.townX - 2, map.townY)) {
            innRest(free = false)
            shop()
            if (order == Order.TOWN) order = null
        }
    }

    private fun innRest(free: Boolean) {
        val h = hero
        val cost = if (free) 0 else 5 + regionIdx * 4
        if (h.gold < cost) {
            restUntilFull = true
            startCamp()
            return
        }
        h.gold -= cost
        h.hp = h.maxHp
        h.mana = h.maxMana
        for (c in party) c.heal()
        fx.add(Fx("heal", h.x, h.y, h.x, h.y, 1.2f, 0xFF8FE8FF, 1.4f))
        if (cost > 0) log("${h.name} pays $cost gold for a bed at the inn. Everyone is rested.", "story")
        // a night at the inn passes the dark hours
        if (night) time += dayLen * (if (tod > 0.5f) (1.06f - tod) else (0.06f - tod)).coerceAtLeast(0f)
    }

    private fun shop() {
        val h = hero
        val potionCost = 12 + regionIdx * 6
        val scrollCost = 45 + regionIdx * 15
        var bought = ""
        val want = (3 + h.gold / 150).coerceAtMost(if (regionIdx >= 4) 10 else 7)
        while (h.potions < want && h.gold >= potionCost + 10) {
            h.gold -= potionCost
            h.potions++
            bought = "potions"
        }
        if (h.ethers < 2 && regionIdx >= 3 && h.gold >= scrollCost * 2) {
            h.gold -= scrollCost
            h.ethers++
            bought = if (bought.isEmpty()) "an ether" else "$bought and an ether"
        }
        if (h.scrolls < 2 && party.size < 6 && h.gold >= scrollCost + 20) {
            h.gold -= scrollCost
            h.scrolls++
            bought = if (bought.isEmpty()) "a scroll" else "$bought and a scroll"
        }
        if (bought.isNotEmpty()) log("${h.name} buys $bought at the market.", "loot")
    }

    // ---- camp ----
    private fun startCamp() {
        if (camping) return
        val h = hero
        camping = true
        campSaid = false
        campT = 0f
        campfireX = h.x + 0.9f
        campfireY = h.y + 0.3f
        doing = if (night) "Camping for the night" else "Resting at a small camp"
        log("${h.name} makes camp${if (night) " for the night" else ""} in ${def.name}.", "story")
        if (order == Order.REST) order = null
    }

    private fun doCamp(dt: Float) {
        val h = hero
        campT += dt
        updateFoes(dt, calm = true)
        h.hp = min(h.maxHp, h.hp + dt * h.maxHp / 40f)
        h.mana = min(h.maxMana, h.mana + dt * h.maxMana / 40f)
        for (c in party) {
            if (c.hp <= 0 && campT > 6f) c.hp = 1
            if (c.hp > 0) c.regen += dt
            if (c.regen > 1f) { c.regen = 0f; c.hp = min(c.maxHp, c.hp + max(1, c.maxHp / 25)) }
        }
        if (campT > 3f && !campSaid) {
            campSaid = true
            val l = party.getOrNull(active)
            say(listOf(Lore.campLine(h, l, rnd, def.name, h.wins)))
            l?.let { it.bond += 2 }
        }
        // something comes out of the dark now and then
        val threat = foes.firstOrNull { it.hostile && !it.dead && d(it.x, it.y, h.x, h.y) < 3.5f }
        if (threat != null) {
            camping = false
            engage(threat)
            bark("Up! Something's here!", force = true)
            return
        }
        val healed = h.hp >= h.maxHp * 0.98f && party.all { it.hp >= it.maxHp * 0.9f }
        val done = if (night) false else (!restUntilFull || healed || campT > 60f)
        if (done && campT > 8f) {
            camping = false
            restUntilFull = false
            doing = ""
        }
    }

    // ---- evolution ----
    private fun startEvolve(c: Critter) {
        evolving = 3.4f
        evolveUid = c.uid
        val i = party.indexOf(c)
        if (i >= 0 && i != active) {
            active = i
            refreshCritName()
        }
        doing = "${c.nick} is changing..."
        fx.add(Fx("evolve", cx, cy, cx, cy, 3.4f, c.species.t1.color, 1.5f))
        bark("${c.nick}? What's happening to you?", force = true)
        if (order == Order.EVOLVE) order = null
    }

    private fun finishEvolve() {
        val c = party.firstOrNull { it.uid == evolveUid } ?: return
        val from = c.species.name
        c.evolve()
        seenNow.add(c.sp)
        caughtNow.add(c.sp)
        refreshCritName()
        log("${c.nick} evolves from $from into ${c.species.name}!", "level")
        banner("Evolution!", "${c.nick} became ${c.species.name}")
        float("${c.species.name}!", cx, cy - 1.2f, c.species.t1.color, big = true)
        bark("${c.nick}... you're amazing.", force = true)
        shake = 0.4f
    }

    // ---- binding ----
    private fun startBind(f: Foe) {
        binding = 2.2f
        bindFoe = f.uid
        hero.mana -= 14f
        hero.cast = 2.2f
        doing = "Casting binding chains on the ${f.name}"
        fx.add(Fx("chains", hero.x, hero.y, f.x, f.y, 2.2f, 0xFFB46CFF, 1f))
    }

    private fun finishBind() {
        val f = foes.firstOrNull { it.uid == bindFoe && !it.dead } ?: return
        val c = f.critter ?: return
        val bonus = hero.level * 0.012f + (if (order == Order.BIND) 0.05f else 0f) + bindTry * 0.06f
        val chance = Dex.catchChance(c.species, c.level, f.hpFrac, bonus)
        if (rnd.nextFloat() < chance) {
            f.dead = true
            f.deathT = 0.8f
            val nc = newCritter(c.sp, c.level)
            nc.hp = max(1, (nc.maxHp * f.hpFrac).toInt())
            nameFor(nc)
            addToParty(nc, "binds")
            fx.add(Fx("bound", f.x, f.y, hero.x, hero.y, 1f, 0xFFB46CFF, 1.2f))
            targetUid = -1
            bindRest = time + 70f
            if (order == Order.BIND) order = null
            for (o in foes) if (o.targetHero && !o.dead && o.kind == FoeKind.WILD && !o.riftTouched && o.uid != f.uid) o.targetHero = false
        } else {
            bindTry++
            fx.add(Fx("burst", f.x, f.y, f.x, f.y, 0.6f, 0xFFB46CFF))
            float("Broke free!", f.x, f.y - 1f, 0xFFFF8080)
            bark(if (bindTry >= 2) "It won't take the bond. Let it go..." else "Almost! Again!", force = true)
            if (bindTry >= 3) {
                // let it go rather than hurt it more
                f.hostile = false
                f.targetHero = false
                targetUid = -1
                log("A wild ${c.species.name} refuses the bond and flees.", "fight")
                bindRest = time + 40f
                f.dead = true
            }
        }
    }

    // ================= combat =================

    private fun combat(dt: Float) {
        val h = hero
        val l = lead
        val foesOn = foes.filter { it.targetHero && it.hostile && !it.dead }
        var tgt = foesOn.firstOrNull { it.uid == targetUid }
        if (tgt == null) {
            tgt = foesOn.minByOrNull { d(it.x, it.y, cx, cy) + (if (it.boss) -2f else 0f) }
            targetUid = tgt?.uid ?: -1
        }
        if (tgt == null) return
        // a fight where nobody can reach anybody ends on its own
        if (time - lastClash > 22f && !tgt.boss) {
            for (f in foesOn) {
                f.targetHero = false
                if (f.kind == FoeKind.WILD && !f.riftTouched) f.hostile = false
                f.homeX = f.x
                f.homeY = f.y
            }
            targetUid = -1
            lastClash = time
            doing = ""
            return
        }

        // flee when it is hopeless
        val brave = h.persona.bravery + (if (order == Order.ATTACK) 0.5f else 0f)
        val hopeless = h.hp < h.maxHp * (0.22f + (1f - brave) * 0.1f) && h.potions == 0
        val noneLeft = party.none { it.hp > 0 }
        if (h.hp < h.maxHp * 0.3f && h.potions > 0 && potionCd < 3.5f) drink(onHero = true)
        if (order == Order.FLEE || (hopeless && (!tgt.boss || noneLeft)) || (noneLeft && (h.hp < h.maxHp * 0.85f || h.potions == 0))) {
            flee(dt, foesOn)
            return
        }
        fleeing = false

        // potions in the heat of it
        if (potionCd <= 0f) {
            if (h.hp < h.maxHp * 0.35f && h.potions > 0) drink(onHero = true)
            else if (l != null && l.hp < l.maxHp * 0.25f && h.potions > 1 && (tgt.boss || l.bond > 15)) drink(onHero = false)
            else if (h.mana < 14f && h.ethers > 0 && tgt.boss) {
                h.ethers--
                h.mana = h.maxMana
                potionCd = 4f
                log("${h.name} drinks an ether.", "fight")
            }
        }
        // a fainted lead is swapped out
        if (l == null && party.any { it.hp > 0 }) swapTo(bestSwap(tgt), quiet = false)

        val bindable = tgt.kind == FoeKind.WILD && !tgt.riftTouched && wantBind(tgt)
        val c = lead
        if (c != null) {
            // the critter closes to the range its best move needs, then attacks on its own rhythm
            val mv = pickMove(c, tgt)
            val range = if (mv.fx == "lunge") 1.2f + tgt.kind.size * 0.3f else 3.6f
            val dist = d(cx, cy, tgt.x, tgt.y)
            if (dist > range) moveCritterToward(tgt.x, tgt.y, dt, 4.0f)
            else if (dist < range * 0.5f && mv.fx != "lunge") moveCritterToward(cx + (cx - tgt.x), cy + (cy - tgt.y), dt, 2f)
            cFacing = if (tgt.x >= cx) 1 else -1
            cCd -= dt
            val holdForBind = bindable && tgt.hpFrac < 0.4f
            if (holdForBind && h.mana >= 14f) {
                startBind(tgt)
                return
            }
            if (cCd <= 0f && dist <= range + 0.3f && !(holdForBind && tgt.hpFrac < 0.2f)) {
                cCd = (2.3f - c.spd / 160f).coerceIn(0.8f, 2.4f) * (if (order == Order.ATTACK) 0.85f else 1f)
                critterAttack(c, tgt, mv, gentle = bindable)
            }
        }
        // the hero keeps behind the critter and casts glyphs
        val hx: Float
        val hy: Float
        if (c != null) {
            val dx = cx - tgt.x
            val dy = cy - tgt.y
            val n = max(0.1f, sqrt(dx * dx + dy * dy))
            hx = cx + dx / n * 2.2f
            hy = cy + dy / n * 2.2f
        } else {
            hx = h.x
            hy = h.y
        }
        if (d(h.x, h.y, hx, hy) > 0.6f) moveHeroToward(hx, hy, dt, 3.2f)
        h.facing = if (tgt.x >= h.x) 1 else -1
        if (castCd <= 0f) heroCast(tgt, c)
    }

    private fun pickMove(c: Critter, f: Foe): Move {
        var best = c.moves.first()
        var bs = -1f
        for (m in c.moves) {
            var s = m.power * (m.acc / 100f) * Combat.eff(m.type, f.types)
            if (c.species.types.contains(m.type)) s *= 1.3f
            s *= 0.8f + rnd.nextFloat() * 0.4f
            if (s > bs) { bs = s; best = m }
        }
        return best
    }

    private fun critterAttack(c: Critter, f: Foe, mv: Move, gentle: Boolean) {
        val hit = rnd.nextInt(100) < mv.acc
        val crit = rnd.nextInt(100) < 6 + c.bond / 8
        var (dmg, eff) = Combat.damage(c.level, mv, c.atk, f.def, c.species.types, f.types, rnd.nextFloat(), crit)
        // bond makes a critter fight harder for its summoner
        dmg = (dmg * (1f + min(c.bond, 60) * 0.004f)).toInt()
        if (gentle && dmg > f.hp - 1 && f.hpFrac > 0.12f) dmg = max(1, f.hp - max(1, f.maxHp / 8))
        if (mv.fx == "lunge") cLunge = 0.3f
        val dur = when (mv.fx) { "lunge" -> 0.3f; "shot" -> 0.45f; "beam" -> 0.4f; "burst" -> 0.55f; "rain" -> 0.7f; else -> 0.6f }
        fx.add(Fx(mv.fx, cx, cy - 0.4f, f.x, f.y - 0.4f, dur, mv.type.color, if (mv.tier >= 3) 1.5f else 1f))
        if (!hit) {
            hits.add(Hit(dur * 0.8f, f.uid, false, 0, 1f, false, mv.name))
            return
        }
        hits.add(Hit(dur * 0.8f, f.uid, false, dmg, eff, crit, mv.name))
    }

    private fun heroCast(tgt: Foe, c: Critter?) {
        val h = hero
        castCd = 2.6f
        if (c != null && c.hp < c.maxHp * 0.35f && h.mana >= 12f) {
            h.mana -= 12f
            val amt = max(6, c.maxHp * 3 / 10)
            c.hp = min(c.maxHp, c.hp + amt)
            h.cast = 0.6f
            fx.add(Fx("heal", cx, cy, cx, cy, 0.9f, 0xFF8FFFB0))
            float("+$amt", cx, cy - 1f, 0xFF8FFFB0)
            return
        }
        if (order == Order.DEFEND && h.mana >= 10f && c != null) {
            h.mana -= 10f
            fx.add(Fx("shield", cx, cy, cx, cy, 1.4f, 0xFF9DBEFF))
            c.regen = -3f
            return
        }
        if (h.mana >= 8f && (tgt.kind != FoeKind.WILD || tgt.riftTouched || tgt.hpFrac > 0.55f)) {
            h.mana -= 8f
            h.cast = 0.5f
            val dmg = (6 + h.level * 2.4f + rnd.nextInt(5)).toInt() * (if (tgt.boss) 1 else 1)
            fx.add(Fx("bolt", h.x, h.y - 0.6f, tgt.x, tgt.y - 0.4f, 0.4f, 0xFFFFE28A))
            hits.add(Hit(0.32f, tgt.uid, false, dmg, 1f, false, "glyph"))
        } else if (d(h.x, h.y, tgt.x, tgt.y) < 1.4f) {
            h.cast = 0.3f
            hits.add(Hit(0.15f, tgt.uid, false, 2 + h.level, 1f, false, "staff"))
        }
    }

    private fun drink(onHero: Boolean) {
        val h = hero
        if (h.potions <= 0) return
        h.potions--
        potionCd = 5f
        if (onHero) {
            val amt = h.maxHp * 0.55f
            h.hp = min(h.maxHp, h.hp + amt)
            fx.add(Fx("heal", h.x, h.y, h.x, h.y, 0.9f, 0xFFFF8FB0))
            float("+${amt.toInt()}", h.x, h.y - 1.2f, 0xFF8FFFB0)
            log("${h.name} drinks a potion.", "fight")
        } else {
            val c = lead ?: return
            val amt = c.maxHp / 2
            c.hp = min(c.maxHp, c.hp + amt)
            fx.add(Fx("heal", cx, cy, cx, cy, 0.9f, 0xFFFF8FB0))
            float("+$amt", cx, cy - 1f, 0xFF8FFFB0)
            log("${h.name} pours a potion for ${c.nick}.", "fight")
        }
        if (order == Order.POTION) order = null
    }

    private fun flee(dt: Float, on: List<Foe>) {
        val h = hero
        if (h.hp < h.maxHp * 0.4f && h.potions > 0 && potionCd <= 0f) drink(onHero = true)
        if (!fleeing) {
            fleeing = true
            bark(if (h.persona.key == "hothead") "This isn't over!" else "Run, ${h.crit}! RUN!", force = true)
            log("${h.name} flees!", "danger")
            doing = "Fleeing"
        }
        // run away from whatever is closest, toward town if that way is clear
        fleeT -= dt
        if (fleeT <= 0f || path.isEmpty() || pathIdx >= path.size) {
            fleeT = 1.5f
            val near = on.minByOrNull { d(it.x, it.y, h.x, h.y) }
            var bx = -1
            var by = -1
            var bs = -1e9f
            for (k in 0 until 16) {
                val a = k * (Math.PI.toFloat() * 2f / 16f)
                val px = (h.x + kotlin.math.cos(a) * 11f).toInt().coerceIn(1, map.w - 2)
                val py = (h.y + kotlin.math.sin(a) * 11f).toInt().coerceIn(1, map.h - 2)
                if (!map.reachable(px, py)) continue
                var sc = if (near != null) d(px + 0.5f, py + 0.5f, near.x, near.y) else 0f
                if (map.townX >= 0) sc -= d(px.toFloat(), py.toFloat(), map.townX.toFloat(), map.townY.toFloat()) * 0.25f
                if (sc > bs) { bs = sc; bx = px; by = py }
            }
            if (bx >= 0) {
                path = map.findPath(h.x.toInt(), h.y.toInt(), bx, by)
                pathIdx = 0
                pathGoal = by * map.w + bx
            }
        }
        walkPath(dt, 3.8f)
        // anything far enough behind gives up
        for (f in on) if (d(f.x, f.y, h.x, h.y) > 7.5f) {
            f.targetHero = false
            if (f.kind == FoeKind.WILD && !f.riftTouched) f.hostile = false
        }
        if (order == Order.FLEE && on.none { d(it.x, it.y, h.x, h.y) < 7f }) order = null
    }

    private fun resolveHits(dt: Float) {
        if (hits.isEmpty()) return
        val done = ArrayList<Hit>()
        for (hh in hits) {
            hh.t -= dt
            if (hh.t > 0f) continue
            done.add(hh)
            if (hh.toHero) {
                applyToParty(hh)
            } else {
                val f = foes.firstOrNull { it.uid == hh.toFoe && !it.dead } ?: continue
                if (hh.dmg <= 0) {
                    float("miss", f.x, f.y - 1f, 0xFFCCCCCC)
                    continue
                }
                f.hp -= hh.dmg
                lastClash = time
                f.hitFlash = 0.18f
                f.hostile = true
                f.targetHero = true
                val col = when {
                    hh.crit -> 0xFFFFD84A
                    hh.eff > 1.4f -> 0xFFFF9A4A
                    hh.eff < 0.7f -> 0xFF9AA4B8
                    else -> 0xFFFFFFFF
                }
                float("${hh.dmg}" + (if (hh.crit) "!" else ""), f.x, f.y - 1f, col, big = hh.crit || hh.eff > 1.4f)
                if (hh.eff > 1.4f && rnd.nextFloat() < 0.4f) critBubbleSay("It's super effective!")
                if (f.boss || hh.crit) shake = max(shake, 0.25f)
                if (f.hp <= 0) defeat(f)
            }
        }
        hits.removeAll(done)
    }

    private fun critBubbleSay(s: String) {
        critBubble = s
        critBubbleT = 1.6f
    }

    private fun applyToParty(hh: Hit) {
        val h = hero
        val c = lead
        if (hh.toFoe == -2 || c == null) {
            // the hero is hit
            if (hh.dmg <= 0) {
                float("miss", h.x, h.y - 1.2f, 0xFFCCCCCC)
                return
            }
            h.hp -= hh.dmg
            lastClash = time
            h.hurt = 0.25f
            shake = max(shake, 0.3f)
            float("${hh.dmg}", h.x, h.y - 1.2f, 0xFFFF6A6A, big = hh.crit)
            if (h.hp < h.maxHp * 0.3f) barkFrom(h.persona.hurt)
            if (h.hp <= 0f) heroFalls(hh.srcName)
            return
        }
        if (hh.dmg <= 0) {
            float("miss", cx, cy - 1f, 0xFFCCCCCC)
            return
        }
        c.hp = max(0, c.hp - hh.dmg)
        lastClash = time
        cHurt = 0.25f
        float("${hh.dmg}", cx, cy - 1f, 0xFFFF6A6A, big = hh.crit)
        if (c.hp <= 0) {
            log("${c.label} faints!", "danger")
            fx.add(Fx("faint", cx, cy, cx, cy, 0.8f, 0xFFFFFFFF))
            c.bond = max(0, c.bond - 1)
            val nxt = bestSwap(foes.firstOrNull { it.uid == targetUid })
            if (nxt >= 0) swapTo(nxt, quiet = false)
            else bark("${c.nick}! No! ...It's just me now.", force = true)
        }
    }

    private fun heroFalls(by: String) {
        val h = hero
        h.hp = 0f
        say(Lore.death(h, def.name, by))
        deathCause = "Fell in ${def.name} to $by"
        log("${h.name} falls in ${def.name}.", "danger")
        // let the last words play, then end
        line = null
        endRun(won = false, cause = deathCause)
    }

    fun endRun(won: Boolean, cause: String) {
        if (phase == "ended") return
        this.won = won
        phase = "ended"
        camping = false
        if (won) {
            banner("Legend", "${hero.name} closed the rifts")
            log("${hero.name}'s journey is complete. The rifts are closed.", "story")
        } else {
            banner("The End", cause)
        }
    }

    private fun defeat(f: Foe) {
        val h = hero
        f.dead = true
        f.hp = 0
        fx.add(Fx("faint", f.x, f.y, f.x, f.y, 0.8f, f.critter?.species?.t1?.color ?: 0xFFFFFFFF))
        if (f.uid == targetUid) targetUid = -1
        val c = lead ?: party.firstOrNull { it.hp > 0 }
        val base = f.level * 3 + 8
        val xp = (base * (if (f.boss) 4f else if (f.riftTouched) 1.4f else 1f)).toInt()
        val gold = f.kind.gold + rnd.nextInt(4 + f.level / 2)
        h.gold += gold
        h.wins++
        if (gold > 0) float("+$gold", f.x + 0.4f, f.y - 0.4f, 0xFFFFD86A)
        val what = if (f.kind == FoeKind.WILD) (if (f.riftTouched) "a rift-touched ${f.name}" else "a wild ${f.name}") else f.name
        log("${c?.nick ?: h.name} defeats $what (Lv ${f.level}).", "fight")
        if (c != null) {
            c.wins++
            c.bond++
            grantXp(c, xp)
        }
        for (o in party) if (o != c && o.hp > 0) grantXp(o, xp * 2 / 5)
        gainHeroXp(f.level * 2 + (if (f.boss) 40 else 0))
        if (!inCombat) {
            if (rnd.nextFloat() < 0.5f) barkFrom(h.persona.win)
            doing = ""
        }
        // rift-touched critters are freed when beaten, and sometimes stay
        if (f.riftTouched && f.critter != null && rnd.nextFloat() < 0.2f && party.size < 6) {
            val nc = newCritter(f.critter!!.sp, f.level)
            nc.hp = nc.maxHp / 3
            nameFor(nc)
            log("Freed from the rift's grip, the ${nc.species.name} chooses to stay.", "bind")
            addToParty(nc, "is joined by")
        }
        if (f.uid == bossUid) {
            shake = 0.8f
            fx.add(Fx("burst", f.x, f.y, f.x, f.y, 1.2f, 0xFFFFFFFF, 2.5f))
            for (o in foes) if (!o.dead && o.uid != f.uid && d(o.x, o.y, f.x, f.y) < 8f) {
                o.dead = true
                fx.add(Fx("faint", o.x, o.y, o.x, o.y, 0.8f, 0xFFFFFFFF))
            }
        }
    }

    private fun grantXp(c: Critter, xp: Int) {
        val before = c.moves.map { it.id }.toSet()
        val gained = c.gainXp(xp)
        if (gained > 0) {
            if (c == lead) float("Lv ${c.level}!", cx, cy - 1.4f, 0xFF8FE8FF, big = true)
            log("${c.nick} grows to Lv ${c.level}.", "level")
            val learned = c.moves.filter { it.id !in before }
            for (m in learned) log("${c.nick} learns ${m.name}!", "level")
            if (c == lead) fx.add(Fx("levelup", cx, cy, cx, cy, 1f, 0xFF8FE8FF))
        }
    }

    private fun gainHeroXp(xp: Int) {
        val h = hero
        h.xp += xp
        while (h.xp >= h.xpNeed) {
            h.xp -= h.xpNeed
            h.level++
            h.maxHp += 7f
            h.maxMana += 3f
            h.hp = min(h.maxHp, h.hp + 20f)
            h.mana = h.maxMana
            log("${h.name} reaches summoner level ${h.level}.", "level")
            float("Summoner Lv ${h.level}", h.x, h.y - 1.6f, 0xFFB46CFF, big = true)
        }
    }

    // ================= foes =================

    private fun updateFoes(dt: Float, calm: Boolean, frozen: Int = -1) {
        val h = hero
        val sneak = order == Order.SNEAK
        for (f in foes) {
            if (f.dead) continue
            f.anim += dt
            if (f.hitFlash > 0f) f.hitFlash -= dt
            if (f.lunge > 0f) f.lunge -= dt
            if (f.uid == frozen) continue
            val dh = d(f.x, f.y, h.x, h.y)
            if (!calm && f.hostile && !f.targetHero && !camping) {
                val aggro = (if (f.boss) 8f else if (f.riftTouched) 4.5f else 5.5f) * (if (sneak) 0.45f else 1f) * (if (night) 1.2f else 1f)
                if (dh < aggro && line == null) {
                    f.targetHero = true
                    if (targetUid < 0) {
                        targetUid = f.uid
                        doing = "Ambushed by ${aOrAn(f.name)} ${f.name}"
                    }
                }
            }
            if (f.targetHero && f.hostile && !calm) {
                // attack the critter mostly, the summoner sometimes
                val c = lead
                val tx = if (c == null) h.x else cx
                val ty = if (c == null) h.y else cy
                val ranged = f.ranged
                val range = if (ranged) 3.4f else 1.1f + f.kind.size * 0.25f
                val dist = d(f.x, f.y, tx, ty)
                if (dist > range) stepFoe(f, tx, ty, dt, if (f.boss) 2.4f else 2.7f)
                f.facing = if (tx >= f.x) 1 else -1
                f.cd -= dt
                if (f.cd <= 0f && dist <= range + 0.3f) {
                    f.cd = (2.6f - f.spd / 140f).coerceIn(0.9f, 2.8f) * (if (f.boss) 0.85f else 1f)
                    // now and then a foe goes for the summoner instead, if the summoner is in reach
                    val heroInReach = d(f.x, f.y, h.x, h.y) <= (if (ranged) 4.2f else range + 0.6f)
                    val atHero = c == null || (heroInReach && rnd.nextFloat() < (if (f.boss) 0.3f else 0.18f))
                    foeAttack(f, atHero)
                }
            } else {
                // wander near home
                f.wanderT -= dt
                if (f.wanderT <= 0f) {
                    f.wanderT = 2f + rnd.nextFloat() * 4f
                    val p = map.near(f.homeX.toInt(), f.homeY.toInt(), 4, rnd)
                    if (p != null) { f.tx = p.first + 0.5f; f.ty = p.second + 0.5f }
                }
                if (d(f.x, f.y, f.tx, f.ty) > 0.2f) stepFoe(f, f.tx, f.ty, dt, 1.1f)
            }
        }
    }

    private fun foeAttack(f: Foe, atHero: Boolean) {
        val h = hero
        val c = lead
        val targetTypes = if (atHero || c == null) listOf(CType.PLAIN) else c.species.types
        val mv = f.attackMove(targetTypes)
        val def = if (atHero || c == null) h.def else c.def
        val hit = rnd.nextInt(100) < mv.acc
        val crit = rnd.nextInt(100) < 5
        var (dmg, eff) = Combat.damage(f.level, mv, f.atk, def, f.types, targetTypes, rnd.nextFloat(), crit)
        // the deeper lands hit harder, and bosses hardest of all
        dmg = (dmg * (1f + regionIdx * 0.06f) * (if (f.boss) 1.2f else 1f)).toInt().coerceAtLeast(1)
        if (order == Order.DEFEND && !atHero) dmg = (dmg * 0.7f).toInt()
        if (atHero || c == null) dmg = (dmg * 0.42f).toInt().coerceAtLeast(1)
        if (camping) dmg = (dmg * 0.6f).toInt()
        // home is gentle: the first land is where a new summoner learns
        if (regionIdx == 0) dmg = (dmg * 0.5f).toInt().coerceAtLeast(1)
        val tx = if (atHero || c == null) h.x else cx
        val ty = if (atHero || c == null) h.y else cy
        if (!f.kind.ranged && f.critter == null || mv.fx == "lunge") f.lunge = 0.3f
        val dur = when (mv.fx) { "lunge" -> 0.3f; "shot" -> 0.45f; "beam" -> 0.4f; "burst" -> 0.55f; "rain" -> 0.7f; else -> 0.6f }
        fx.add(Fx(mv.fx, f.x, f.y - 0.4f, tx, ty - 0.4f, dur, mv.type.color, if (f.boss) 1.6f else 1f))
        hits.add(Hit(dur * 0.8f, if (atHero || c == null) -2 else -1, true, if (hit) dmg else 0, eff, crit, f.name))
    }

    private fun stepFoe(f: Foe, tx: Float, ty: Float, dt: Float, sp: Float) {
        if (d(f.x, f.y, tx, ty) < 0.02f) return
        val r = slide(f.x, f.y, tx, ty, sp * dt, hop = f.kind == FoeKind.WRAITH || f.kind == FoeKind.DRAKE || f.kind == FoeKind.HOLLOW)
        f.x = r[0]
        f.y = r[1]
    }

    // ================= walking =================

    private val mv2 = FloatArray(2)

    /** If anything ever leaves the hero standing in water or a tree, step to the nearest open ground. */
    private fun unstick() {
        val h = hero
        if (map.reachable(h.x.toInt(), h.y.toInt())) return
        var best = -1
        var bd = 1e9f
        for (yy in h.y.toInt() - 8..h.y.toInt() + 8) for (xx in h.x.toInt() - 8..h.x.toInt() + 8) {
            if (!map.reachable(xx, yy)) continue
            val dd = d(xx + 0.5f, yy + 0.5f, h.x, h.y)
            if (dd < bd) { bd = dd; best = yy * map.w + xx }
        }
        if (best >= 0) {
            h.x = best % map.w + 0.5f
            h.y = best / map.w + 0.5f
            path = emptyList()
        }
    }

    /**
     * Moves a point toward a target with collisions: straight if it can, otherwise slides along the blocked
     * axis, otherwise sidesteps around the obstacle. [hop] lets summoned critters cross small things.
     */
    private fun slide(x: Float, y: Float, tx: Float, ty: Float, s: Float, hop: Boolean): FloatArray {
        mv2[0] = x
        mv2[1] = y
        val dx = tx - x
        val dy = ty - y
        val n = sqrt(dx * dx + dy * dy)
        if (n < 0.02f) return mv2
        val st = min(n, s)
        val ux = dx / n
        val uy = dy / n
        fun ok(px: Float, py: Float) = map.reachable(px.toInt(), py.toInt())
        val nx = x + ux * st
        val ny = y + uy * st
        if (ok(nx, ny)) { mv2[0] = nx; mv2[1] = ny; return mv2 }
        if (abs(ux) > 0.15f && ok(x + (if (ux > 0) st else -st), y)) { mv2[0] = x + (if (ux > 0) st else -st); return mv2 }
        if (abs(uy) > 0.15f && ok(x, y + (if (uy > 0) st else -st))) { mv2[1] = y + (if (uy > 0) st else -st); return mv2 }
        // sidestep: try the two perpendicular directions, preferring the one nearer the goal
        val p1x = x - uy * st
        val p1y = y + ux * st
        val p2x = x + uy * st
        val p2y = y - ux * st
        val o1 = ok(p1x, p1y)
        val o2 = ok(p2x, p2y)
        if (o1 && (!o2 || d(p1x, p1y, tx, ty) <= d(p2x, p2y, tx, ty))) { mv2[0] = p1x; mv2[1] = p1y; return mv2 }
        if (o2) { mv2[0] = p2x; mv2[1] = p2y; return mv2 }
        if (hop) { mv2[0] = nx; mv2[1] = ny }
        return mv2
    }

    /** Walks the hero toward a tile; true once there. */
    private fun walkTo(tx: Int, ty: Int): Boolean {
        val h = hero
        if (d(h.x, h.y, tx + 0.5f, ty + 0.5f) < 1.3f) {
            path = emptyList()
            return true
        }
        val goal = ty * map.w + tx
        // a fight or a sidestep can leave the hero off the old path; plan again from here
        val off = pathIdx < path.size && d(h.x, h.y, path[pathIdx] % map.w + 0.5f, path[pathIdx] / map.w + 0.5f) > 1.6f
        if (pathGoal != goal || path.isEmpty() || pathIdx >= path.size || off) {
            path = map.findPath(h.x.toInt(), h.y.toInt(), tx, ty)
            pathIdx = 0
            pathGoal = goal
            if (path.isEmpty()) {
                // stuck: nudge toward it
                moveHeroToward(tx + 0.5f, ty + 0.5f, 0.1f, 3f)
                waitT += 0.35f
                if (waitT > 4f) { waitT = 0f; return true }
            }
        }
        return false
    }

    private fun walkPath(dt: Float, sp: Float = 2.6f) {
        val h = hero
        if (path.isEmpty() || pathIdx >= path.size) {
            h.moving = false
            return
        }
        val t = path[pathIdx]
        if (d(h.x, h.y, t % map.w + 0.5f, t / map.w + 0.5f) > 1.6f) {
            path = emptyList()
            return
        }
        val tx = t % map.w + 0.5f
        val ty = t / map.w + 0.5f
        val dist = d(h.x, h.y, tx, ty)
        val step = sp * dt * (if (map.g(h.x.toInt(), h.y.toInt()) == Ground.MUD) 0.75f else 1f)
        if (dist <= step) {
            h.x = tx
            h.y = ty
            pathIdx++
        } else {
            h.x += (tx - h.x) / dist * step
            h.y += (ty - h.y) / dist * step
        }
        if (abs(tx - h.x) > 0.05f) h.facing = if (tx > h.x) 1 else -1
        h.moving = true
        h.walk += dt
        h.tiles += step
    }

    private fun moveHeroToward(tx: Float, ty: Float, dt: Float, sp: Float) {
        val h = hero
        if (d(h.x, h.y, tx, ty) < 0.05f) return
        val r = slide(h.x, h.y, tx, ty, sp * dt, hop = false)
        if (abs(r[0] - h.x) > 0.001f) h.facing = if (r[0] > h.x) 1 else -1
        h.x = r[0]
        h.y = r[1]
        h.moving = true
        h.walk += dt
    }

    private fun moveCritterToward(tx: Float, ty: Float, dt: Float, sp: Float) {
        val dx = tx - cx
        if (d(cx, cy, tx, ty) < 0.05f) return
        // critters can hop small obstacles
        val r = slide(cx, cy, tx, ty, sp * dt, hop = true)
        cx = r[0]
        cy = r[1]
        if (abs(dx) > 0.05f) cFacing = if (dx > 0) 1 else -1
    }

    private fun followCritter(dt: Float) {
        if (inCombat) return
        val h = hero
        val tx = h.x - h.facing * 0.9f
        val ty = h.y + 0.25f
        val dist = d(cx, cy, tx, ty)
        if (dist > 12f) { cx = tx; cy = ty; return }
        if (dist > 0.4f) moveCritterToward(tx, ty, dt, if (dist > 2f) 4.2f else 2.8f)
    }

    // ================= little moments =================

    private fun bondMoment() {
        val c = lead ?: return
        c.bond += 2
        val h = hero
        val lines = listOf(
            "Good job today, ${c.nick}.",
            "Who's the best ${c.species.name}? You are.",
            "Here, ${c.nick}, I saved you a berry.",
            "We make a good team, don't we?",
        )
        bark(lines[rnd.nextInt(lines.size)], force = true)
        critBubbleSay("♥")
        log("${h.name} spends a moment with ${c.nick}. Their trust deepens.", "bind")
    }

    private fun banner(title: String, sub: String) {
        banner = title
        bannerSub = sub
        bannerT = 4f
    }

    // ================= saving =================

    fun restoreRegion(idx: Int) {
        enterRegion(idx.coerceIn(0, Regions.LAST), fresh = true)
    }

    fun restoreAfterLoad() {
        refreshCritName()
        if (party.isEmpty()) {
            phase = "prologue"
            step = 0
            enterRegion(0, fresh = true)
        }
        doing = ""
    }

    /** This run as a line in the Hall of Legends. */
    fun legend(): Legend {
        val best = (party + stored).maxByOrNull { it.level }
        return Legend(
            hero.name, hero.job, hero.persona.trait, def.name, won, deathCause, (party + stored).size,
            best?.let { "${it.nick} the ${it.species.name}, Lv ${it.level}" } ?: "",
            hero.days, hero.level,
        )
    }

    // ================= the Voice =================

    /** Jon speaks to the hero. Returns what the hero says back. */
    fun command(text: String): String {
        val low = " " + text.lowercase().trim() + " "
        val h = hero
        if (phase == "ended") return "The story is over. Start a new journey."
        var best: Order? = null
        var bestPos = Int.MAX_VALUE
        for (o in Order.values()) for (w in o.words) {
            val i = low.indexOf(w)
            if (i in 0 until bestPos) { bestPos = i; best = o }
        }
        log("The Voice: \"${text.trim()}\"", "order")
        if (best == null) {
            val reply = "I hear you, Voice... but I don't understand."
            bark(reply, force = true)
            return reply
        }
        order = best
        orderT = 90f
        // some orders act right away
        when (best) {
            Order.FLEE -> Unit
            Order.REST -> if (inCombat) { order = Order.FLEE }
            Order.ATTACK -> {
                val f = foes.filter { !it.dead }.minByOrNull { d(it.x, it.y, h.x, h.y) }
                if (f != null && d(f.x, f.y, h.x, h.y) < 14f) engage(f)
            }
            Order.SWAP -> if (inCombat) { swapTo(nextHealthy(), quiet = false); order = null }
            Order.POTION -> if (inCombat) drink(onHero = h.hp < h.maxHp * 0.7f || lead == null)
            Order.EVOLVE -> {
                val c = party.firstOrNull { it.readyToEvolve() }
                if (c == null) {
                    order = null
                    val l = lead
                    val reply = if (l != null && l.species.evolveTo > 0) "${l.nick} isn't ready yet. Trust ${l.bond}/${l.trustNeeded()}." else "None of them can change right now."
                    bark(reply, force = true)
                    return reply
                }
            }
            Order.SUMMON -> if (h.scrolls <= 0) {
                order = null
                val reply = "I'm out of scrolls. Maybe a market sells them."
                bark(reply, force = true)
                return reply
            }
            Order.ONWARD -> if (step == 5) trainT = trainCap()
            else -> Unit
        }
        val reply = Lore.fill(Lore.pick(rnd, h.persona.obey), h, h.crit) + " " + when (best) {
            Order.ATTACK -> "Attacking!"
            Order.DEFEND -> "Shields up."
            Order.FLEE -> "Running!"
            Order.REST -> "We'll rest."
            Order.BIND -> "Looking for a critter to bind."
            Order.SUMMON -> "Reading a scroll."
            Order.EXPLORE -> "Let's look around."
            Order.TRAIN -> "Time to train."
            Order.ONWARD -> "Onward."
            Order.TOWN -> "Back to town."
            Order.SWAP -> "Switching."
            Order.POTION -> "Potion."
            Order.SNEAK -> "Quietly now."
            Order.BOND -> "Come here, ${h.crit}."
            Order.EVOLVE -> "${h.crit}, are you ready?"
        }
        bark(reply, force = true)
        return reply
    }
}
