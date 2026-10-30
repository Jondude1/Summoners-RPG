package com.jondude1.summonersrpg.sim

import kotlin.math.max

/** Everything that fights the hero. Wild critters use their species instead of these numbers. */
enum class FoeKind(
    val label: String,
    val hp: Int,
    val atk: Int,
    val def: Int,
    val spd: Int,
    val t1: CType,
    val t2: CType?,
    val ranged: Boolean,
    val fx: String,
    val boss: Boolean,
    val gold: Int,
    val size: Float,
    val moveName: String,
) {
    WILD("Wild critter", 0, 0, 0, 0, CType.PLAIN, null, false, "lunge", false, 2, 1f, ""),
    WOLF("Rift Wolf", 54, 60, 44, 70, CType.SHADE, null, false, "lunge", false, 3, 1f, "Rending Bite"),
    BANDIT("Bandit", 56, 56, 50, 54, CType.PLAIN, null, false, "lunge", false, 8, 1f, "Cudgel"),
    OGRE("Crag Ogre", 96, 80, 72, 34, CType.STONE, null, false, "rain", false, 12, 1.5f, "Club Slam"),
    WRAITH("Rift Wraith", 58, 72, 46, 72, CType.SPIRIT, CType.SHADE, true, "swirl", false, 10, 1.1f, "Soul Drain"),
    DRAKELING("Ash Drakeling", 72, 84, 64, 74, CType.EMBER, CType.SHADE, true, "shot", false, 14, 1.2f, "Cinder Spit"),
    KNIGHT("Hollow Knight", 90, 92, 98, 50, CType.IRON, CType.SHADE, false, "lunge", false, 18, 1.3f, "Void Blade"),
    CHIEF("Brigand King", 84, 76, 68, 60, CType.PLAIN, CType.IRON, false, "lunge", true, 60, 1.35f, "Cleaving Axe"),
    WARLORD("Ogre Warlord", 124, 94, 88, 40, CType.STONE, CType.IRON, false, "rain", true, 110, 1.9f, "Earthsplitter"),
    WITCH("Drowned Witch", 96, 94, 74, 76, CType.VENOM, CType.TIDE, true, "burst", true, 160, 1.3f, "Bog Curse"),
    DRAKE("Rift Drake", 120, 104, 90, 86, CType.FROST, CType.GALE, true, "beam", true, 220, 2.4f, "Rift Breath"),
    RIVAL("Rival summoner", 104, 102, 86, 92, CType.SHADE, null, true, "burst", true, 300, 1.6f, "Void Lash"),
    HOLLOW("The Hollow King", 168, 124, 104, 98, CType.SHADE, CType.SPIRIT, true, "swirl", true, 0, 2.8f, "Unmaking"),
    ;

    val types: List<CType> get() = if (t2 != null) listOf(t1, t2) else listOf(t1)
}

/** One live enemy (or wild critter) on the map. */
class Foe(val uid: Int, val kind: FoeKind, var level: Int, var x: Float, var y: Float) {
    /** The wild critter itself, for kind WILD and for the rival's partner. */
    var critter: Critter? = null
    var hp = 1
    var maxHp = 1
    var homeX = x
    var homeY = y
    var cd = 1f
    var hostile = true
    /** Rift-touched critters glow purple, attack on sight and cannot be bound until beaten. */
    var riftTouched = false
    var named = ""
    var targetHero = false
    var wanderT = 0f
    var tx = x
    var ty = y
    var facing = 1
    var hitFlash = 0f
    var dead = false
    var deathT = 0f
    var campIdx = -1
    var anim = 0f
    var lunge = 0f

    val boss: Boolean get() = kind.boss
    val name: String get() = named.ifBlank { critter?.species?.name ?: kind.label }
    val types: List<CType> get() = critter?.species?.types ?: kind.types
    val ranged: Boolean get() = if (critter != null) critter!!.moves.any { it.fx != "lunge" && it.tier >= 2 } || critter!!.species.t1 == CType.GLOW else kind.ranged
    val hpFrac: Float get() = (hp.toFloat() / max(1, maxHp)).coerceIn(0f, 1f)

    private fun stat(base: Int) = base * 2 * level / 100 + 5

    val atk: Int get() = critter?.atk ?: (stat(kind.atk) * (if (boss) 1.15f else 1f)).toInt()
    val def: Int get() = critter?.def ?: (stat(kind.def) * (if (boss) 1.1f else 1f)).toInt()
    val spd: Int get() = critter?.spd ?: stat(kind.spd)

    fun setup() {
        val c = critter
        maxHp = if (c != null) {
            (c.maxHp * (if (boss) 3.6f else if (riftTouched) 1.25f else 1f)).toInt()
        } else {
            val base = kind.hp * 2 * level / 100 + level + 10
            (base * (if (boss) 3.9f else 1.15f)).toInt()
        }
        hp = maxHp
    }

    /** The move this foe uses: its species' best against the target, or its kind's own attack. */
    fun attackMove(targetTypes: List<CType>): Move {
        val c = critter
        if (c != null) {
            var best = c.moves.first()
            var bs = -1f
            for (m in c.moves) {
                var s = m.power * (m.acc / 100f) * Combat.eff(m.type, targetTypes)
                if (c.species.types.contains(m.type)) s *= 1.3f
                if (s > bs) { bs = s; best = m }
            }
            return best
        }
        val tier = when {
            level >= 30 -> 3
            level >= 14 -> 2
            else -> 1
        }
        val power = when (tier) { 1 -> 42; 2 -> 64; else -> 88 } + if (boss) 10 else 0
        return Move("foe_${kind.name}", kind.moveName, kind.t1, power, 95, kind.fx, tier)
    }
}

object Combat {
    fun eff(att: CType, def: List<CType>): Float {
        var e = 1f
        for (t in def) e *= Dex.effOne(att, t)
        return e
    }

    /** Same shape as the village battles: level, power, attack over defense, type bonus, effectiveness, a little luck. */
    fun damage(level: Int, move: Move, atk: Int, def: Int, attTypes: List<CType>, defTypes: List<CType>, roll: Float, crit: Boolean): Pair<Int, Float> {
        val e = eff(move.type, defTypes)
        var d = ((2f * level / 5f + 2f) * move.power * atk / max(1, def) / 50f + 2f)
        if (attTypes.contains(move.type)) d *= 1.3f
        d *= e
        if (crit) d *= 1.5f
        d *= 0.88f + roll * 0.12f
        return max(1, d.toInt()) to e
    }
}
