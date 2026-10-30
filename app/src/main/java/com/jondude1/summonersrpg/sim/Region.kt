package com.jondude1.summonersrpg.sim

import java.util.Random
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin

// The seven lands of the journey and the tile maps they are walked on. Pure Kotlin, no Android.

object Ground {
    const val GRASS = 0
    const val GRASS2 = 1
    const val PATH = 2
    const val WATER = 3
    const val SAND = 4
    const val ROCK = 5
    const val SNOW = 6
    const val MUD = 7
    const val ASH = 8
    const val FLOOR = 9
    const val BRIDGE = 10
    const val DEEP = 11
    const val LAVA = 12
}

object Obj {
    const val NONE = 0
    const val TREE = 1
    const val PINE = 2
    const val BOULDER = 3
    const val HOUSE = 4
    const val REED = 5
    const val DEADTREE = 6
    const val CRYSTAL = 7
    const val PILLAR = 8
    const val BUSH = 9
    const val FLOWER = 10
    const val TENT = 11
    const val WELL = 12
    const val GATE = 13
    const val BONES = 14
    const val STUMP = 15

    fun blocks(o: Int): Boolean = o == TREE || o == PINE || o == BOULDER || o == HOUSE || o == PILLAR ||
        o == CRYSTAL || o == DEADTREE || o == WELL || o == TENT || o == GATE
}

enum class Biome { MEADOW, FOREST, MOUNTAIN, SWAMP, SNOW, ASH, CITADEL }

/** One land on the road east. */
class RegionDef(
    val idx: Int,
    val name: String,
    val biome: Biome,
    val habitats: List<String>,
    val lvMin: Int,
    val lvMax: Int,
    val foes: List<FoeKind>,
    val boss: FoeKind?,
    val bossName: String,
    val bossLv: Int,
    val town: String,
    val blurb: String,
)

object Regions {
    val all: List<RegionDef> = listOf(
        RegionDef(0, "Summoner's Village", Biome.MEADOW, listOf("meadow", "forest"), 2, 4,
            listOf(FoeKind.WOLF), null, "", 0, "Summoner's Village",
            "Home. Thatch roofs, the rift gate on the hill, and everyone you have ever known."),
        RegionDef(1, "Whisperwood", Biome.FOREST, listOf("forest", "meadow", "any"), 4, 9,
            listOf(FoeKind.BANDIT, FoeKind.WOLF), FoeKind.CHIEF, "Grisk the Brigand King", 12, "Mossford",
            "An old forest where the trees lean in to listen. Bandits have made it theirs."),
        RegionDef(2, "Ironpeak Pass", Biome.MOUNTAIN, listOf("rock", "ore", "meadow"), 10, 16,
            listOf(FoeKind.OGRE, FoeKind.BANDIT, FoeKind.WOLF), FoeKind.WARLORD, "Gorrum the Ogre Warlord", 19, "Cragmouth",
            "A cold road of switchbacks and ore veins, watched by ogres from the crags."),
        RegionDef(3, "Mirefen", Biome.SWAMP, listOf("water", "forest", "any"), 16, 22,
            listOf(FoeKind.WRAITH, FoeKind.BANDIT, FoeKind.OGRE), FoeKind.WITCH, "Sedge, the Drowned Witch", 25, "Reedholm",
            "Black water, white mist, and lights that are not lanterns."),
        RegionDef(4, "Frostreach", Biome.SNOW, listOf("snow", "rock", "any"), 22, 28,
            listOf(FoeKind.WOLF, FoeKind.WRAITH, FoeKind.OGRE), FoeKind.DRAKE, "Hoarfang the Rift Drake", 31, "Whitecairn",
            "A frozen highland where a rift has torn open in the sky."),
        RegionDef(5, "The Ashen Rift", Biome.ASH, listOf("rock", "ore", "any"), 28, 34,
            listOf(FoeKind.WRAITH, FoeKind.OGRE, FoeKind.DRAKELING), FoeKind.RIVAL, "", 37, "Last Lantern",
            "Burned land where the world grows thin. Things push through from the other side."),
        RegionDef(6, "The Rift Citadel", Biome.CITADEL, listOf("any", "ore", "rock"), 34, 40,
            listOf(FoeKind.WRAITH, FoeKind.DRAKELING, FoeKind.KNIGHT), FoeKind.HOLLOW, "The Hollow King", 45, "",
            "A fortress built on the seam between worlds, older than any village."),
    )

    val LAST = all.size - 1
}

/** A place on the map the story can send the hero to. */
class Spot(val kind: String, val x: Int, val y: Int, var used: Boolean = false, val label: String = "")

/** The walkable tile map of one land. Regenerated from the run seed, so it never needs saving. */
class RegionMap(val def: RegionDef, seed: Long) {
    val w = 72
    val h = 44
    val ground = IntArray(w * h)
    val obj = IntArray(w * h)
    val spots = ArrayList<Spot>()
    var startX = 2
    var startY = h / 2
    var exitX = w - 3
    var exitY = h / 2
    var townX = -1
    var townY = -1
    var bossX = -1
    var bossY = -1
    var gateX = -1
    var gateY = -1
    /** Tiles connected to the road. Anything outside is a sealed pocket nobody should end up in. */
    var open = BooleanArray(0)
    private val rnd = Random(seed)
    private val nseed = (seed and 0x7fffffff).toInt()

    fun inside(x: Int, y: Int) = x in 0 until w && y in 0 until h
    fun g(x: Int, y: Int): Int = if (inside(x, y)) ground[y * w + x] else Ground.WATER
    fun o(x: Int, y: Int): Int = if (inside(x, y)) obj[y * w + x] else Obj.NONE

    /** Walkable and connected to the road. */
    fun reachable(x: Int, y: Int): Boolean = inside(x, y) && (open.isEmpty() || open[y * w + x]) && walkable(x, y)

    fun walkable(x: Int, y: Int): Boolean {
        if (!inside(x, y)) return false
        val gr = ground[y * w + x]
        if (gr == Ground.WATER || gr == Ground.DEEP || gr == Ground.LAVA) return false
        return !Obj.blocks(obj[y * w + x])
    }

    init {
        build()
    }

    // ---- noise ----
    private fun hash(x: Int, y: Int): Int {
        var hh = x * 374761393 + y * 668265263 + nseed * 1442695041
        hh = (hh xor (hh ushr 13)) * 1274126177
        return hh xor (hh ushr 16)
    }

    private fun r01(x: Int, y: Int): Float = (hash(x, y) and 0xffff) / 65535f

    private fun vnoise(x: Float, y: Float): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = x - x0
        val fy = y - y0
        val sx = fx * fx * (3 - 2 * fx)
        val sy = fy * fy * (3 - 2 * fy)
        val a = r01(x0, y0)
        val b = r01(x0 + 1, y0)
        val c = r01(x0, y0 + 1)
        val d = r01(x0 + 1, y0 + 1)
        return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy
    }

    fun noise(x: Float, y: Float): Float = vnoise(x, y) * 0.6f + vnoise(x * 2.1f + 17f, y * 2.1f + 5f) * 0.3f + vnoise(x * 4.3f, y * 4.3f + 31f) * 0.1f

    // ---- building the land ----
    private fun base(): Int = when (def.biome) {
        Biome.MEADOW, Biome.FOREST -> Ground.GRASS
        Biome.MOUNTAIN -> Ground.ROCK
        Biome.SWAMP -> Ground.MUD
        Biome.SNOW -> Ground.SNOW
        Biome.ASH -> Ground.ASH
        Biome.CITADEL -> Ground.FLOOR
    }

    private fun build() {
        val b = base()
        for (y in 0 until h) for (x in 0 until w) {
            val n = noise(x / 7f, y / 7f)
            ground[y * w + x] = when (def.biome) {
                Biome.MEADOW, Biome.FOREST -> if (n > 0.58f) Ground.GRASS2 else Ground.GRASS
                Biome.MOUNTAIN -> if (n < 0.36f) Ground.GRASS2 else if (n > 0.66f) Ground.SNOW else Ground.ROCK
                Biome.SWAMP -> if (n > 0.6f) Ground.GRASS2 else Ground.MUD
                Biome.SNOW -> if (n < 0.3f) Ground.ROCK else Ground.SNOW
                Biome.ASH -> if (n > 0.64f) Ground.ROCK else Ground.ASH
                Biome.CITADEL -> if (n > 0.62f) Ground.ROCK else Ground.FLOOR
            }
        }
        // water bodies (lava in the burned land, chasms in the citadel)
        val wet = when (def.biome) {
            Biome.SWAMP -> 0.52f
            Biome.MEADOW, Biome.FOREST -> 0.70f
            Biome.SNOW -> 0.72f
            Biome.ASH -> 0.71f
            Biome.CITADEL -> 0.74f
            Biome.MOUNTAIN -> 0.76f
        }
        for (y in 0 until h) for (x in 0 until w) {
            val n = noise(x / 9f + 40f, y / 9f + 11f)
            if (n > wet) {
                ground[y * w + x] = when (def.biome) {
                    Biome.ASH -> Ground.LAVA
                    Biome.CITADEL -> Ground.DEEP
                    else -> if (n > wet + 0.07f) Ground.DEEP else Ground.WATER
                }
            } else if (n > wet - 0.035f && def.biome != Biome.CITADEL && def.biome != Biome.ASH && def.biome != Biome.SWAMP) {
                ground[y * w + x] = if (def.biome == Biome.SNOW) Ground.ROCK else Ground.SAND
            }
        }
        // the road west to east, meandering
        val phase = rnd.nextFloat() * 6f
        val amp = 5f + rnd.nextFloat() * 5f
        startY = (h / 2 + (sin(phase) * amp).toInt()).coerceIn(4, h - 5)
        val road = ArrayList<Pair<Int, Int>>()
        var prevY = startY
        for (x in 1 until w - 1) {
            val ty = (h / 2 + sin(phase + x / 9f) * amp + sin(phase * 2 + x / 4f) * 1.5f).toInt().coerceIn(4, h - 5)
            // vertical steps so the road stays connected
            var yy = prevY
            while (yy != ty) {
                road.add(x to yy)
                yy += if (ty > yy) 1 else -1
            }
            road.add(x to ty)
            prevY = ty
        }
        for ((x, y) in road) for (dy in 0..1) {
            val i = (y + dy) * w + x
            if (!inside(x, y + dy)) continue
            ground[i] = if (ground[i] == Ground.WATER || ground[i] == Ground.DEEP || ground[i] == Ground.LAVA) Ground.BRIDGE else Ground.PATH
        }
        val roadAt = HashMap<Int, Int>()
        for ((x, y) in road) roadAt[x] = y
        fun roadY(x: Int) = roadAt[x.coerceIn(1, w - 2)] ?: (h / 2)
        startX = 2
        startY = roadY(2)
        exitX = w - 3
        exitY = roadY(exitX)

        // scatter the land's plants and stones
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            val gr = ground[i]
            if (gr == Ground.PATH || gr == Ground.BRIDGE || gr == Ground.WATER || gr == Ground.DEEP || gr == Ground.LAVA) continue
            if (abs(y - roadY(x)) <= 2) continue
            val n = noise(x / 5f + 90f, y / 5f + 3f)
            val r = r01(x * 3 + 7, y * 5 + 1)
            obj[i] = when (def.biome) {
                Biome.MEADOW -> when {
                    n > 0.66f && r < 0.6f -> Obj.TREE
                    r < 0.04f -> Obj.BUSH
                    r < 0.10f -> Obj.FLOWER
                    else -> Obj.NONE
                }
                Biome.FOREST -> when {
                    n > 0.48f && r < 0.72f -> if (r < 0.2f) Obj.PINE else Obj.TREE
                    r < 0.06f -> Obj.BUSH
                    r < 0.09f -> Obj.FLOWER
                    r < 0.10f -> Obj.STUMP
                    else -> Obj.NONE
                }
                Biome.MOUNTAIN -> when {
                    n > 0.58f && r < 0.55f -> Obj.BOULDER
                    n < 0.32f && r < 0.4f -> Obj.PINE
                    r < 0.03f -> Obj.CRYSTAL
                    else -> Obj.NONE
                }
                Biome.SWAMP -> when {
                    n > 0.6f && r < 0.5f -> Obj.DEADTREE
                    r < 0.12f -> Obj.REED
                    r < 0.14f -> Obj.BONES
                    else -> Obj.NONE
                }
                Biome.SNOW -> when {
                    n > 0.55f && r < 0.6f -> Obj.PINE
                    r < 0.04f -> Obj.BOULDER
                    r < 0.05f -> Obj.CRYSTAL
                    else -> Obj.NONE
                }
                Biome.ASH -> when {
                    n > 0.62f && r < 0.45f -> Obj.DEADTREE
                    r < 0.05f -> Obj.BOULDER
                    r < 0.08f -> Obj.BONES
                    r < 0.09f -> Obj.CRYSTAL
                    else -> Obj.NONE
                }
                Biome.CITADEL -> when {
                    (x % 6 == 0 && y % 5 == 0) -> Obj.PILLAR
                    r < 0.04f -> Obj.CRYSTAL
                    r < 0.06f -> Obj.BONES
                    else -> Obj.NONE
                }
            }
        }

        // the town (or the home village) sits on the road
        if (def.town.isNotEmpty()) {
            townX = if (def.idx == 0) w / 2 - 6 else (w * 0.32f).toInt() + rnd.nextInt(6)
            townY = roadY(townX)
            clear(townX, townY, 8, 6, settle = true)
            val homes = if (def.idx == 0) 7 else 5
            val slots = listOf(-6 to -4, -2 to -5, 3 to -4, 6 to -3, -5 to 3, 0 to 4, 5 to 3, -7 to 0, 8 to 1)
            var placed = 0
            for ((dx, dy) in slots) {
                if (placed >= homes) break
                val hx = townX + dx
                val hy = townY + dy
                if (!inside(hx, hy) || abs(hy - roadY(hx)) <= 1) continue
                obj[hy * w + hx] = Obj.HOUSE
                placed++
            }
            setObj(townX + 1, townY - 2, Obj.WELL)
            spots.add(Spot("inn", townX - 2, townY, label = "the inn"))
            spots.add(Spot("shop", townX + 2, townY, label = "the market"))
        }
        // the home village keeps the rift gate on its hill
        if (def.idx == 0) {
            gateX = townX + 13
            gateY = (roadY(gateX) - 5).coerceAtLeast(3)
            clear(gateX, gateY, 3, 3, settle = true)
            setObj(gateX, gateY - 1, Obj.GATE)
            spots.add(Spot("gate", gateX, gateY + 1, label = "the rift gate"))
        }

        // boss arena near the far edge
        if (def.boss != null) {
            bossX = w - 10
            bossY = roadY(bossX)
            clear(bossX, bossY, 5, 5, settle = false)
            spots.add(Spot("boss", bossX, bossY, label = def.bossName.ifBlank { "the rival" }))
        }

        // enemy camps, ruins, chests and a shrine off the road
        val camps = if (def.idx == 0) 1 else 2
        repeat(camps) { k ->
            val cx = (w * (0.5f + 0.18f * k)).toInt() + rnd.nextInt(5) - 2
            val side = if (rnd.nextBoolean()) -1 else 1
            val cy = (roadY(cx) + side * (7 + rnd.nextInt(5))).coerceIn(4, h - 5)
            clear(cx, cy, 3, 3, settle = true)
            setObj(cx - 2, cy - 1, Obj.TENT)
            spots.add(Spot("camp", cx, cy, label = "a camp"))
            carve(cx, cy, cx, roadY(cx))
        }
        if (def.idx > 0) {
            val sx = (w * 0.22f).toInt() + rnd.nextInt(8)
            val side = if (rnd.nextBoolean()) -1 else 1
            val sy = (roadY(sx) + side * (6 + rnd.nextInt(6))).coerceIn(3, h - 4)
            clear(sx, sy, 2, 2, settle = true)
            spots.add(Spot("shrine", sx, sy, label = "a rift shrine"))
            carve(sx, sy, sx, roadY(sx))
            val rx = (w * 0.62f).toInt() + rnd.nextInt(6)
            val ry = (roadY(rx) - side * (6 + rnd.nextInt(6))).coerceIn(3, h - 4)
            clear(rx, ry, 3, 2, settle = false)
            setObj(rx - 2, ry - 1, Obj.PILLAR)
            setObj(rx + 2, ry - 1, Obj.PILLAR)
            spots.add(Spot("ruin", rx, ry, label = "old ruins"))
            carve(rx, ry, rx, roadY(rx))
        }
        repeat(3 + rnd.nextInt(2)) {
            for (tries in 0 until 40) {
                val cx = 6 + rnd.nextInt(w - 12)
                val cy = 3 + rnd.nextInt(h - 6)
                if (!walkable(cx, cy) || abs(cy - roadY(cx)) < 4) continue
                if (spots.any { abs(it.x - cx) + abs(it.y - cy) < 6 }) continue
                spots.add(Spot("chest", cx, cy, label = "a chest"))
                carve(cx, cy, cx, roadY(cx))
                break
            }
        }
        // make sure everything is reachable from the start
        val seen = reach()
        spots.removeAll { !seen[it.y * w + it.x] }
        open = seen
    }

    private fun setObj(x: Int, y: Int, o: Int) {
        if (inside(x, y)) obj[y * w + x] = o
    }

    /** Clears a patch, turning water to land; [settle] makes it trodden village ground. */
    private fun clear(cx: Int, cy: Int, rx: Int, ry: Int, settle: Boolean) {
        for (y in cy - ry..cy + ry) for (x in cx - rx..cx + rx) {
            if (!inside(x, y)) continue
            val d = hypot((x - cx).toFloat() / (rx + 0.5f), (y - cy).toFloat() / (ry + 0.5f))
            if (d > 1.05f) continue
            val i = y * w + x
            obj[i] = Obj.NONE
            val gr = ground[i]
            if (gr == Ground.WATER || gr == Ground.DEEP || gr == Ground.LAVA) ground[i] = base()
            if (settle && gr != Ground.PATH && d < 0.8f && def.biome != Biome.CITADEL) ground[i] = if (def.biome == Biome.SNOW) Ground.SNOW else Ground.GRASS2
            if (!settle && def.biome != Biome.CITADEL && d < 0.9f) ground[i] = if (def.biome == Biome.ASH) Ground.ASH else Ground.ROCK
        }
    }

    /** A little trail from (x0,y0) toward (x1,y1) so side places can be walked to. */
    private fun carve(x0: Int, y0: Int, x1: Int, y1: Int) {
        var x = x0
        var y = y0
        var guard = 0
        while ((x != x1 || y != y1) && guard++ < 200) {
            if (y != y1) y += if (y1 > y) 1 else -1 else x += if (x1 > x) 1 else -1
            if (!inside(x, y)) break
            val i = y * w + x
            if (Obj.blocks(obj[i])) obj[i] = Obj.NONE
            val gr = ground[i]
            if (gr == Ground.WATER || gr == Ground.DEEP || gr == Ground.LAVA) ground[i] = Ground.BRIDGE
            else if (gr != Ground.PATH && gr != Ground.BRIDGE && r01(x, y) < 0.7f) ground[i] = Ground.PATH
        }
    }

    private fun reach(): BooleanArray {
        val seen = BooleanArray(w * h)
        val q = ArrayDeque<Int>()
        q.add(startY * w + startX)
        seen[startY * w + startX] = true
        while (q.isNotEmpty()) {
            val c = q.removeFirst()
            val x = c % w
            val y = c / w
            for (d in 0 until 4) {
                val nx = x + DX[d]
                val ny = y + DY[d]
                if (!walkable(nx, ny)) continue
                val ni = ny * w + nx
                if (seen[ni]) continue
                seen[ni] = true
                q.add(ni)
            }
        }
        return seen
    }

    /** Shortest walk between tiles, as a list of tiles (excluding the start). Empty when unreachable. */
    fun findPath(sx: Int, sy: Int, tx: Int, ty: Int): List<Int> {
        if (!inside(sx, sy) || !inside(tx, ty)) return emptyList()
        var gx = tx
        var gy = ty
        if (!walkable(gx, gy)) {
            // nearest walkable tile to the goal
            var best = -1
            var bd = Int.MAX_VALUE
            for (yy in ty - 3..ty + 3) for (xx in tx - 3..tx + 3) if (walkable(xx, yy)) {
                val d = abs(xx - tx) + abs(yy - ty)
                if (d < bd) { bd = d; best = yy * w + xx }
            }
            if (best < 0) return emptyList()
            gx = best % w
            gy = best / w
        }
        val prev = IntArray(w * h) { -2 }
        val start = sy * w + sx
        val goal = gy * w + gx
        prev[start] = -1
        val q = ArrayDeque<Int>()
        q.add(start)
        while (q.isNotEmpty()) {
            val c = q.removeFirst()
            if (c == goal) break
            val x = c % w
            val y = c / w
            for (d in 0 until 4) {
                val nx = x + DX[d]
                val ny = y + DY[d]
                if (!walkable(nx, ny)) continue
                val ni = ny * w + nx
                if (prev[ni] != -2) continue
                prev[ni] = c
                q.add(ni)
            }
        }
        if (prev[goal] == -2) return emptyList()
        val out = ArrayList<Int>()
        var c = goal
        while (c != start && c >= 0) {
            out.add(c)
            c = prev[c]
        }
        out.reverse()
        return out
    }

    /** A random walkable tile within [r] of (x,y), or null. */
    fun near(x: Int, y: Int, r: Int, rr: Random): Pair<Int, Int>? {
        for (t in 0 until 30) {
            val nx = x + rr.nextInt(r * 2 + 1) - r
            val ny = y + rr.nextInt(r * 2 + 1) - r
            if (reachable(nx, ny)) return nx to ny
        }
        return null
    }

    companion object {
        private val DX = intArrayOf(1, -1, 0, 0)
        private val DY = intArrayOf(0, 0, 1, -1)
    }
}
