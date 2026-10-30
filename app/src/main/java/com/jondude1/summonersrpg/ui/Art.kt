package com.jondude1.summonersrpg.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.jondude1.summonersrpg.sim.Biome
import com.jondude1.summonersrpg.sim.FoeKind
import com.jondude1.summonersrpg.sim.Ground
import com.jondude1.summonersrpg.sim.Hero
import com.jondude1.summonersrpg.sim.Obj
import com.jondude1.summonersrpg.sim.RegionMap

/**
 * The hand-drawn pixel art of the journey: people, foes, props and the painted ground of each land.
 * Every sprite is a small text grid, polished to twice its size with outlines and shading.
 */
object Art {
    const val TILE = 16

    fun sprite(rows: List<String>, pal: Map<Char, Int>): ImageBitmap {
        val h = rows.size
        val w = rows.maxOf { it.length }
        val src = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) src[y * w + x] = pal[rows[y].getOrElse(x) { '.' }] ?: 0
        val big = PixelFx.polish(src, w, h, true, true)
        val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
        bmp.setPixels(big, 0, w * 2, 0, 0, w * 2, h * 2)
        return bmp.asImageBitmap()
    }

    private fun c(v: Long): Int = v.toInt()

    // ---------------- people ----------------
    private val PERSON1 = listOf(
        "..rrrr..",
        ".rrrrrr.",
        ".rhhhhr.",
        ".hdhhdh.",
        "..hHHh..",
        ".ssssss.",
        "hsSssSsh",
        "hssssssh",
        ".sSbbSs.",
        ".pppppp.",
        ".pp..pp.",
        ".bb..bb.",
    )
    private val PERSON2 = listOf(
        "..rrrr..",
        ".rrrrrr.",
        ".rhhhhr.",
        ".hdhhdh.",
        "..hHHh..",
        ".ssssss.",
        "hsSssSsh",
        "hssssssh",
        ".sSbbSs.",
        ".pppppp.",
        "..pppp..",
        "..bbbb..",
    )
    private val HOOD = listOf("..ssss..", ".ssssss.", ".shhhhs.")
    private val HAT = listOf("...ss...", "..ssss..", "ssssssss")
    private val HELM = listOf("..mmmm..", ".mmmmmm.", ".mhhhhm.")

    private val HAIRS = intArrayOf(c(0xFF3B2A1A), c(0xFF8A5A2B), c(0xFF222222), c(0xFFD9B04A), c(0xFF7A2E1E), c(0xFFB0B0B0), c(0xFF5A3A6A), c(0xFF2E4A3A))
    private val SHIRTS = intArrayOf(c(0xFFD94F4F), c(0xFF4F7FD9), c(0xFFE0B040), c(0xFF8E4FD9), c(0xFF4FB58E), c(0xFFE07A3A), c(0xFFD94F9B), c(0xFFEFEFEF), c(0xFF8B5A2B), c(0xFF4A8FB0))
    private val SKINS = intArrayOf(c(0xFFF2CBA3), c(0xFFD9A57A), c(0xFFA8754F), c(0xFF6E4A30))

    private fun shade(col: Int, f: Float): Int = PixelFx.tone(col, f)

    fun person(hair: Int, shirt: Int, skin: Int, pants: Int = c(0xFF3A3F5C), top: List<String>? = null): Array<ImageBitmap> {
        val pal = mapOf(
            'r' to hair, 'h' to skin, 'H' to shade(skin, 0.85f), 'd' to c(0xFF222222),
            's' to shirt, 'S' to shade(shirt, 0.75f), 'p' to pants, 'b' to c(0xFF3B2A1A), 'm' to c(0xFF9AA3B5),
        )
        fun withTop(g: List<String>): List<String> {
            if (top == null) return g
            val out = g.toMutableList()
            for (i in top.indices) out[i] = top[i]
            return out
        }
        return arrayOf(sprite(withTop(PERSON1), pal), sprite(withTop(PERSON2), pal))
    }

    fun heroSprites(h: Hero): Array<ImageBitmap> =
        person(HAIRS[h.hair % HAIRS.size], SHIRTS[h.shirt % SHIRTS.size], SKINS[h.skin % SKINS.size])

    /** Townsfolk: a handful of looks, picked by a number. */
    val folk: List<Array<ImageBitmap>> = List(6) { i ->
        person(HAIRS[(i * 3 + 1) % HAIRS.size], SHIRTS[(i * 7 + 2) % SHIRTS.size], SKINS[(i + 1) % SKINS.size])
    }

    // ---------------- foes ----------------
    private val WOLF1 = listOf(
        "..........kk",
        "aa.......kkkk",
        ".aa.....kkekkw",
        "..kkkkkkkkkkk.",
        "..kkkkkkkkkk..",
        "..kKKkkkKKk...",
        "..kk.k..k.kk..",
        "..kk.k..k.kk..",
    )
    private val WOLF2 = listOf(
        "..........kk",
        "aa.......kkkk",
        ".aa.....kkekkw",
        "..kkkkkkkkkkk.",
        "..kkkkkkkkkk..",
        "..kKKkkkKKk...",
        "...kk.kk.kk...",
        "..k..k..k..k..",
    )
    private val OGRE = listOf(
        "....gggg....",
        "...gggggg...",
        "...gdggdg...",
        "...gwggwg...",
        "....gggg....",
        "..ssssssss..",
        ".gssSSSSssg.",
        "ggssssssssgg",
        "gg.ssssss.gg",
        "ww.bbbbbb.ww",
        "...pppppp...",
        "...pp..pp...",
        "...gg..gg...",
        "..ggg..ggg..",
    )
    private val WRAITH = listOf(
        "...aaaa...",
        "..aaaaaa..",
        ".aakaakaa.",
        ".aaaaaaaa.",
        "aAaaaaaaAa",
        "aAAaaaaAAa",
        ".aAaaaaAa.",
        ".aaAaaAaa.",
        "..aaaaaa..",
        "..a.aa.a..",
        ".a..a...a.",
        "....a.....",
    )
    private val DRAKELING = listOf(
        "........kk..",
        ".......kkkk.",
        "...ww..kekkk",
        "..wwww.kkkk.",
        ".wwkkkkkkk..",
        "...kkkkkka..",
        "..kkAkkkkaa.",
        ".kk.kkk..a..",
        ".k..k.k.....",
    )
    private val KNIGHT = listOf(
        "...mmmm...",
        "..mmmmmm..",
        "..maaaam..",
        "..mmmmmm..",
        ".MmmmmmmM.",
        "MmmmaammmM",
        "MmmmaammmM",
        ".mmmmmmmm.",
        "..mmmmmm..",
        "..mm..mm..",
        "..mm..mm..",
        ".MMM..MMM.",
    )

    class FoeArt(val frames: Array<ImageBitmap>, val tall: Float)

    private val foeArt = HashMap<FoeKind, FoeArt>()

    fun foe(k: FoeKind): FoeArt = foeArt.getOrPut(k) {
        when (k) {
            FoeKind.WOLF -> {
                val pal = mapOf('k' to c(0xFF4A4458), 'K' to c(0xFF2E2A3A), 'e' to c(0xFFFF4AD8), 'w' to c(0xFFEDE6F2), 'a' to c(0xFFB46CFF))
                FoeArt(arrayOf(sprite(WOLF1, pal), sprite(WOLF2, pal)), 0.9f)
            }
            FoeKind.BANDIT -> FoeArt(person(c(0xFFB0302A), c(0xFF6B4A2A), SKINS[1], c(0xFF3A2A20), HOOD.map { it.replace('s', 'r') }), 1f)
            FoeKind.CHIEF -> FoeArt(person(c(0xFF222222), c(0xFF7A2A2A), SKINS[0], c(0xFF2A2A2A), HELM), 1f)
            FoeKind.OGRE, FoeKind.WARLORD -> {
                val pal = if (k == FoeKind.OGRE) mapOf(
                    'g' to c(0xFF7D9A4A), 'd' to c(0xFF1E1A10), 'w' to c(0xFFEDE6D2), 's' to c(0xFF8A6A3A), 'S' to c(0xFF6A4A2A),
                    'b' to c(0xFF4A3A20), 'p' to c(0xFF5A4A3A),
                ) else mapOf(
                    'g' to c(0xFF6B7F8A), 'd' to c(0xFFFF4A2A), 'w' to c(0xFFEDE6D2), 's' to c(0xFF9AA3B5), 'S' to c(0xFF6A7385),
                    'b' to c(0xFF8A2A2A), 'p' to c(0xFF3A3A4A),
                )
                val a = sprite(OGRE, pal)
                FoeArt(arrayOf(a, a), 1f)
            }
            FoeKind.WRAITH -> {
                val pal = mapOf('a' to c(0xFF8C7BD9), 'A' to c(0xFFC9B8FF), 'k' to c(0xFF14102A))
                val a = sprite(WRAITH, pal)
                FoeArt(arrayOf(a, a), 1f)
            }
            FoeKind.DRAKELING -> {
                val pal = mapOf('k' to c(0xFF3A2A3A), 'e' to c(0xFFFF8A2A), 'w' to c(0xFF6A4A6A), 'a' to c(0xFFFF6A2A), 'A' to c(0xFFFFB04A))
                val a = sprite(DRAKELING, pal)
                FoeArt(arrayOf(a, a), 1f)
            }
            FoeKind.KNIGHT -> {
                val pal = mapOf('m' to c(0xFF3A3448), 'M' to c(0xFF241F30), 'a' to c(0xFFB46CFF))
                val a = sprite(KNIGHT, pal)
                FoeArt(arrayOf(a, a), 1f)
            }
            FoeKind.WITCH -> FoeArt(person(c(0xFF3A6A4A), c(0xFF4A2A6A), c(0xFFB8D0B0), c(0xFF2A2A3A), HAT), 1f)
            FoeKind.RIVAL -> FoeArt(person(c(0xFF1A1A22), c(0xFF2A1A3A), SKINS[0], c(0xFF1A1A22), HOOD), 1f)
            else -> FoeArt(person(HAIRS[2], SHIRTS[3], SKINS[1]), 1f)
        }
    }

    // ---------------- props ----------------
    private val TREE = listOf(
        "....llll....",
        "..lllLLlll..",
        ".llLLllllll.",
        "lllllllLLlll",
        "llLLllllllll",
        "lllllLLlllLl",
        ".llllllllll.",
        "..lLlllLll..",
        "....tTtt....",
        ".....tt.....",
        ".....tt.....",
        "....tttt....",
    )
    private val PINE = listOf(
        ".....ll.....",
        "....llll....",
        "...lLllll...",
        "....llll....",
        "..llLllLll..",
        "...llllll...",
        ".llllLllLll.",
        "..llllllll..",
        "lllLllllllLl",
        ".....tt.....",
        ".....tt.....",
    )
    private val BOULDER = listOf(
        "...ssss...",
        "..sLLsss..",
        ".sLssssSs.",
        "ssssssSSss",
        "sssssSSSSs",
        ".SSSSSSSS.",
    )
    private val HOUSE = listOf(
        "......rr......",
        "....rrrrrr....",
        "..rrrrRRrrrr..",
        "rrrrRRrrrrRRrr",
        ".wwwwwwwwwwww.",
        ".wwgwwwwwwgww.",
        ".wwgwwddwwgww.",
        ".wwwwwddwwwww.",
        ".WWWWWddWWWWW.",
    )
    private val REED = listOf(
        ".a..a.",
        ".l..l.",
        ".l.ll.",
        "ll.l..",
        ".l.l.l",
        ".llllL",
    )
    private val DEAD = listOf(
        "t...t...t",
        ".t..t..t.",
        "..t.t.t..",
        "...ttt...",
        "....t....",
        "....t....",
        "....T....",
        "...TTT...",
    )
    private val CRYSTAL = listOf(
        "..a...",
        ".aA.a.",
        ".aAaA.",
        "aAaAaa",
        "aaAaa.",
        ".kkkk.",
    )
    private val PILLAR = listOf(
        "ssssss",
        ".sLss.",
        ".sLss.",
        ".sLss.",
        ".sLss.",
        ".sLsS.",
        ".sLsS.",
        "SSSSSS",
    )
    private val BUSH = listOf(
        "..llll..",
        ".lLllal.",
        "llllLlll",
        ".lalllL.",
    )
    private val FLOWER = listOf(
        "a.A.a",
        ".l.l.",
    )
    private val TENT = listOf(
        ".....rr.....",
        "....rRrr....",
        "...rRrrrr...",
        "..rRrrdrrr..",
        ".rRrrddrrrr.",
        "rRrrrddrrrrr",
    )
    private val WELL = listOf(
        "rrrrrrrr",
        ".t....t.",
        ".t....t.",
        "ssssssss",
        "sbbbbbbs",
        "ssssssss",
    )
    private val GATE = listOf(
        "...ssssssss...",
        "..ssLLLLLLss..",
        ".ssL......Lss.",
        ".sL........Ls.",
        "ssL........Lss",
        "sL..........Ls",
        "sL..........Ls",
        "sL..........Ls",
        "sL..........Ls",
        "sL..........Ls",
        "SSSS......SSSS",
    )
    private val BONES = listOf(
        "w.w..ww.",
        ".ww.wwww",
    )
    private val STUMP = listOf(
        ".tTtt.",
        "tttttt",
        ".tTtt.",
    )
    private val CHEST = listOf(
        ".tttttt.",
        "tyttttyt",
        "TTTyyTTT",
        "tttyyttt",
        "TTTTTTTT",
    )
    private val SHRINE = listOf(
        "...aa...",
        "..aAAa..",
        "...aa...",
        ".ssssss.",
        "..sLss..",
        "..sLss..",
        "..sLss..",
        ".SSSSSS.",
    )

    class Props(
        val tree: ImageBitmap, val pine: ImageBitmap, val boulder: ImageBitmap, val house: ImageBitmap, val reed: ImageBitmap,
        val dead: ImageBitmap, val crystal: ImageBitmap, val pillar: ImageBitmap, val bush: ImageBitmap, val flower: ImageBitmap,
        val tent: ImageBitmap, val well: ImageBitmap, val gate: ImageBitmap, val bones: ImageBitmap, val stump: ImageBitmap,
        val chest: ImageBitmap, val chestOpen: ImageBitmap, val shrine: ImageBitmap,
    ) {
        fun of(o: Int): ImageBitmap? = when (o) {
            Obj.TREE -> tree
            Obj.PINE -> pine
            Obj.BOULDER -> boulder
            Obj.HOUSE -> house
            Obj.REED -> reed
            Obj.DEADTREE -> dead
            Obj.CRYSTAL -> crystal
            Obj.PILLAR -> pillar
            Obj.BUSH -> bush
            Obj.FLOWER -> flower
            Obj.TENT -> tent
            Obj.WELL -> well
            Obj.GATE -> gate
            Obj.BONES -> bones
            Obj.STUMP -> stump
            else -> null
        }
    }

    private val propCache = HashMap<Biome, Props>()

    fun props(b: Biome): Props = propCache.getOrPut(b) {
        val leaf = when (b) {
            Biome.FOREST -> c(0xFF2F7A3A) to c(0xFF4FA04A)
            Biome.SNOW -> c(0xFF2E5A4A) to c(0xFFE8F2F8)
            Biome.SWAMP -> c(0xFF4A6A3A) to c(0xFF6A8A4A)
            Biome.MOUNTAIN -> c(0xFF2E5A3A) to c(0xFF4A7A4A)
            Biome.ASH -> c(0xFF4A3A3A) to c(0xFF6A4A3A)
            Biome.CITADEL -> c(0xFF3A2A4A) to c(0xFF5A4A6A)
            else -> c(0xFF3F8A3F) to c(0xFF6ABF4A)
        }
        val stone = when (b) {
            Biome.ASH -> c(0xFF5A4A48)
            Biome.CITADEL -> c(0xFF4A4458)
            Biome.SNOW -> c(0xFF8A94A4)
            else -> c(0xFF8A8A90)
        }
        val crystal = when (b) {
            Biome.ASH -> c(0xFFFF6A3A)
            Biome.SNOW -> c(0xFF8FE8FF)
            Biome.CITADEL -> c(0xFFB46CFF)
            else -> c(0xFF6AD8FF)
        }
        val trunk = mapOf('t' to c(0xFF6B4A2A), 'T' to c(0xFF4A3220))
        val treePal = trunk + mapOf('l' to leaf.first, 'L' to leaf.second)
        val stonePal = mapOf('s' to stone, 'S' to shade(stone, 0.7f), 'L' to shade(stone, 1.25f))
        val roof = if (b == Biome.SNOW) c(0xFFE8F0F8) else c(0xFFB0563A)
        Props(
            tree = sprite(TREE, treePal),
            pine = sprite(PINE, treePal),
            boulder = sprite(BOULDER, stonePal),
            house = sprite(HOUSE, mapOf('r' to roof, 'R' to shade(roof, 0.75f), 'w' to c(0xFFE8D8B0), 'W' to c(0xFFB8A880), 'g' to c(0xFFFFD86A), 'd' to c(0xFF6B4A2A))),
            reed = sprite(REED, mapOf('l' to c(0xFF6A8A3A), 'L' to c(0xFF4A6A2A), 'a' to c(0xFF8A5A2A))),
            dead = sprite(DEAD, mapOf('t' to c(0xFF4A3A30), 'T' to c(0xFF2E241E))),
            crystal = sprite(CRYSTAL, mapOf('a' to crystal, 'A' to shade(crystal, 1.3f), 'k' to c(0xFF3A3448))),
            pillar = sprite(PILLAR, stonePal),
            bush = sprite(BUSH, mapOf('l' to leaf.first, 'L' to leaf.second, 'a' to c(0xFFE04A6A))),
            flower = sprite(FLOWER, mapOf('a' to c(0xFFFFD86A), 'A' to c(0xFFFF8AC8), 'l' to c(0xFF4FA04A))),
            tent = sprite(TENT, mapOf('r' to c(0xFF8A6A4A), 'R' to c(0xFF6A4A2A), 'd' to c(0xFF2A1A10))),
            well = sprite(WELL, mapOf('r' to c(0xFFB0563A), 't' to c(0xFF6B4A2A), 's' to stone, 'b' to c(0xFF2A4A8A))),
            gate = sprite(GATE, mapOf('s' to c(0xFF6A6478), 'S' to c(0xFF4A4458), 'L' to c(0xFFB46CFF))),
            bones = sprite(BONES, mapOf('w' to c(0xFFE8E0D0))),
            stump = sprite(STUMP, trunk),
            chest = sprite(CHEST, mapOf('t' to c(0xFF9A6A2A), 'T' to c(0xFF6A4A1A), 'y' to c(0xFFFFD86A))),
            chestOpen = sprite(listOf("t......t", "t......t", "TTTTTTTT", "tttyyttt", "TTTTTTTT"), mapOf('t' to c(0xFF9A6A2A), 'T' to c(0xFF6A4A1A), 'y' to c(0xFFFFD86A))),
            shrine = sprite(SHRINE, mapOf('s' to stone, 'S' to shade(stone, 0.7f), 'L' to shade(stone, 1.25f), 'a' to c(0xFFB46CFF), 'A' to c(0xFFE8C8FF))),
        )
    }

    // ---------------- the ground ----------------

    private fun groundColor(g: Int, b: Biome): Int = when (g) {
        Ground.GRASS -> when (b) { Biome.FOREST -> c(0xFF3E7A3A); Biome.SWAMP -> c(0xFF4A5E36); else -> c(0xFF5A9A44) }
        Ground.GRASS2 -> when (b) { Biome.FOREST -> c(0xFF346A32); Biome.SWAMP -> c(0xFF3E5230); Biome.MOUNTAIN -> c(0xFF5A7A48); else -> c(0xFF4E8A3E) }
        Ground.PATH -> when (b) { Biome.SNOW -> c(0xFFB8B4AC); Biome.ASH -> c(0xFF6A5A50); Biome.CITADEL -> c(0xFF5A5468); else -> c(0xFFB0905E) }
        Ground.WATER -> if (b == Biome.SWAMP) c(0xFF2E4A42) else c(0xFF2E6AB0)
        Ground.DEEP -> if (b == Biome.CITADEL) c(0xFF0E0A18) else if (b == Biome.SWAMP) c(0xFF1E3430) else c(0xFF1E4E90)
        Ground.SAND -> c(0xFFD8C890)
        Ground.ROCK -> when (b) { Biome.ASH -> c(0xFF4A4040); Biome.CITADEL -> c(0xFF3A3448); else -> c(0xFF7A7A80) }
        Ground.SNOW -> c(0xFFE4ECF4)
        Ground.MUD -> c(0xFF5A4A34)
        Ground.ASH -> c(0xFF3A3434)
        Ground.FLOOR -> c(0xFF4A4458)
        Ground.BRIDGE -> c(0xFF8A6A3E)
        Ground.LAVA -> c(0xFFE0581E)
        else -> c(0xFF5A9A44)
    }

    private fun isLiquid(g: Int) = g == Ground.WATER || g == Ground.DEEP || g == Ground.LAVA

    /** Paints the whole land into one bitmap, one tile = [TILE] pixels, with grain, edges and little details. */
    fun ground(m: RegionMap): ImageBitmap {
        val T = TILE
        val W = m.w * T
        val H = m.h * T
        val px = IntArray(W * H)
        val b = m.def.biome
        for (ty in 0 until m.h) for (tx in 0 until m.w) {
            val g = m.g(tx, ty)
            val base = groundColor(g, b)
            for (yy in 0 until T) for (xx in 0 until T) {
                val x = tx * T + xx
                val y = ty * T + yy
                val n = PixelFx.fbm(x / 9f, y / 9f, 7)
                var col = PixelFx.tone(base, 0.9f + n * 0.2f + (PixelFx.bayer(x, y) - 0.5f) * 0.05f)
                when (g) {
                    Ground.GRASS, Ground.GRASS2 -> if (PixelFx.rnd(x, y, 3) < 0.06f) col = PixelFx.tone(base, 1.25f)
                    Ground.PATH -> if (PixelFx.rnd(x, y, 5) < 0.05f) col = PixelFx.tone(base, 0.75f)
                    Ground.BRIDGE -> {
                        col = if (yy % 5 == 0) PixelFx.tone(base, 0.65f) else PixelFx.tone(base, 0.95f + (xx % 8) * 0.01f)
                        if (xx == 0 || xx == T - 1) col = PixelFx.tone(base, 0.55f)
                    }
                    Ground.FLOOR -> if (xx == 0 || yy == 0) col = PixelFx.tone(base, 0.75f)
                    Ground.SNOW -> if (PixelFx.rnd(x, y, 9) < 0.03f) col = c(0xFFFFFFFF)
                    Ground.LAVA -> col = PixelFx.mix(c(0xFFB02A10), c(0xFFFFB04A), PixelFx.fbm(x / 6f, y / 6f, 11))
                }
                // soft shore: land next to water gets lighter, water next to land gets foam
                if (isLiquid(g)) {
                    val near = (xx < 3 && !isLiquid(m.g(tx - 1, ty))) || (xx > T - 4 && !isLiquid(m.g(tx + 1, ty))) ||
                        (yy < 3 && !isLiquid(m.g(tx, ty - 1))) || (yy > T - 4 && !isLiquid(m.g(tx, ty + 1)))
                    if (near && g != Ground.LAVA && b != Biome.CITADEL) col = PixelFx.mix(col, c(0xFFCFE8FF), 0.35f)
                    if (near && b == Biome.CITADEL) col = PixelFx.mix(col, c(0xFF6A3A9A), 0.4f)
                } else if (g != Ground.BRIDGE) {
                    val wet = (xx < 2 && isLiquid(m.g(tx - 1, ty))) || (xx > T - 3 && isLiquid(m.g(tx + 1, ty))) ||
                        (yy < 2 && isLiquid(m.g(tx, ty - 1))) || (yy > T - 3 && isLiquid(m.g(tx, ty + 1)))
                    if (wet) col = PixelFx.tone(col, 0.78f)
                }
                px[y * W + x] = col
            }
        }
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, W, 0, 0, W, H)
        return bmp.asImageBitmap()
    }
}
