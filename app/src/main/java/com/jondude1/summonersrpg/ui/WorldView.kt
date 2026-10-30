package com.jondude1.summonersrpg.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.jondude1.summonersrpg.sim.Biome
import com.jondude1.summonersrpg.sim.Foe
import com.jondude1.summonersrpg.sim.FoeKind
import com.jondude1.summonersrpg.sim.Fx
import com.jondude1.summonersrpg.sim.Game
import com.jondude1.summonersrpg.sim.Ground
import com.jondude1.summonersrpg.sim.Obj
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Where the view is looking. Follows the hero until Jon pans, double-tap brings it back. */
class Camera {
    var x = 0f
    var y = 0f
    var zoom = 1f
    var follow = true
    var lastNanos = 0L
    var inited = false
}

/** Everything drawn for one land, rebuilt when the hero enters a new one. */
class LandArt(val regionIdx: Int, val seed: Long, val ground: ImageBitmap, val props: Art.Props)

private fun col(v: Long) = Color(v)

@Composable
fun WorldView(
    game: Game,
    frame: androidx.compose.runtime.State<Long>,
    cam: Camera,
    land: LandArt,
    heroArt: Array<ImageBitmap>,
    cart: CritterArt,
    modifier: Modifier = Modifier,
    onTapWorld: (Float, Float) -> Unit,
) {
    val tm = rememberTextMeasurer()
    val sizeRef = remember { FloatArray(2) }
    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val tp = tilePx(sizeRef[0], sizeRef[1], cam.zoom)
                    // keep the point under the fingers still while zooming
                    val wx = cam.x + (centroid.x - sizeRef[0] / 2f) / tp
                    val wy = cam.y + (centroid.y - sizeRef[1] / 2f) / tp
                    cam.zoom = (cam.zoom * zoom).coerceIn(0.4f, 3.4f)
                    val tp2 = tilePx(sizeRef[0], sizeRef[1], cam.zoom)
                    cam.x = wx - (centroid.x - sizeRef[0] / 2f) / tp2
                    cam.y = wy - (centroid.y - sizeRef[1] / 2f) / tp2
                    if (abs(pan.x) + abs(pan.y) > 2f) {
                        cam.follow = false
                        cam.x -= pan.x / tp2
                        cam.y -= pan.y / tp2
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { cam.follow = true },
                    onTap = { p ->
                        val tp = tilePx(sizeRef[0], sizeRef[1], cam.zoom)
                        onTapWorld(cam.x + (p.x - sizeRef[0] / 2f) / tp, cam.y + (p.y - sizeRef[1] / 2f) / tp)
                    },
                )
            },
    ) {
        sizeRef[0] = size.width
        sizeRef[1] = size.height
        @Suppress("UNUSED_VARIABLE") val f = frame.value
        drawWorld(game, cam, land, heroArt, cart, tm)
    }
}

fun tilePx(w: Float, h: Float, zoom: Float): Float = (min(w, h) / 13f).coerceAtLeast(24f) * zoom

private class Ctx(
    val g: Game,
    val tp: Float,
    val ox: Float,
    val oy: Float,
    val t: Float,
    val tm: TextMeasurer,
) {
    fun sx(x: Float) = (x - ox) * tp
    fun sy(y: Float) = (y - oy) * tp
}

private fun DrawScope.drawWorld(g: Game, cam: Camera, land: LandArt, heroArt: Array<ImageBitmap>, cart: CritterArt, tm: TextMeasurer) {
    val now = System.nanoTime()
    val dt = if (cam.lastNanos == 0L) 0.016f else ((now - cam.lastNanos) / 1e9f).coerceIn(0f, 0.1f)
    cam.lastNanos = now
    val h = g.hero
    // follow the hero, leaning toward the fight when there is one
    if (cam.follow) {
        var fx = h.x
        var fy = h.y - 0.4f
        if (g.inCombat) {
            fx = (h.x + g.cx) / 2f
            fy = (h.y + g.cy) / 2f - 0.4f
        }
        if (!cam.inited) {
            cam.x = fx
            cam.y = fy
            cam.inited = true
        }
        val k = min(1f, dt * 3.5f)
        cam.x += (fx - cam.x) * k
        cam.y += (fy - cam.y) * k
    }
    val m = g.map
    cam.x = cam.x.coerceIn(0f, m.w.toFloat())
    cam.y = cam.y.coerceIn(0f, m.h.toFloat())
    val tp = tilePx(size.width, size.height, cam.zoom)
    var shx = 0f
    var shy = 0f
    if (g.shake > 0f) {
        shx = sin(g.time * 90f) * g.shake * 0.25f
        shy = cos(g.time * 71f) * g.shake * 0.2f
    }
    val ox = cam.x - size.width / 2f / tp + shx
    val oy = cam.y - size.height / 2f / tp + shy
    val c = Ctx(g, tp, ox, oy, g.time, tm)

    drawRect(col(0xFF0A0712))

    // ---- ground ----
    val x0 = max(0, ox.toInt() - 1)
    val y0 = max(0, oy.toInt() - 1)
    val x1 = min(m.w, (ox + size.width / tp).toInt() + 2)
    val y1 = min(m.h, (oy + size.height / tp).toInt() + 3)
    if (x1 > x0 && y1 > y0) {
        val T = Art.TILE
        drawImage(
            land.ground,
            srcOffset = IntOffset(x0 * T, y0 * T),
            srcSize = IntSize((x1 - x0) * T, (y1 - y0) * T),
            dstOffset = IntOffset(c.sx(x0.toFloat()).toInt(), c.sy(y0.toFloat()).toInt()),
            dstSize = IntSize(((x1 - x0) * tp).toInt() + 1, ((y1 - y0) * tp).toInt() + 1),
            filterQuality = FilterQuality.None,
        )
    }
    // living water and lava
    for (ty in y0 until y1) for (tx in x0 until x1) {
        val gr = m.g(tx, ty)
        if (gr != Ground.WATER && gr != Ground.DEEP && gr != Ground.LAVA) continue
        val ph = c.t * 1.3f + (tx * 13 + ty * 7) % 10
        val a = 0.5f + 0.5f * sin(ph)
        val lc = if (gr == Ground.LAVA) col(0xFFFFE08A) else if (m.def.biome == Biome.CITADEL) col(0xFF8A4AD8) else col(0xFFCFE8FF)
        val yy = c.sy(ty + 0.3f + (tx % 3) * 0.2f)
        val xx = c.sx(tx + 0.15f + 0.4f * ((sin(ph * 0.5f) + 1f) / 2f))
        drawLine(lc.copy(alpha = 0.25f * a), Offset(xx, yy), Offset(xx + tp * 0.35f, yy), strokeWidth = tp / 14f)
    }

    // ---- everything with a foot on the ground, sorted by depth ----
    val items = ArrayList<Pair<Float, DrawScope.() -> Unit>>()
    val props = land.props
    for (ty in y0 until y1) for (tx in x0 until x1) {
        val o = m.o(tx, ty)
        if (o == Obj.NONE) continue
        val img = props.of(o) ?: continue
        val sway = if (o == Obj.TREE || o == Obj.PINE || o == Obj.REED) sin(c.t * 1.2f + tx * 0.7f + ty * 0.3f) * 0.6f else 0f
        val sc = when (o) {
            Obj.HOUSE -> 1.75f
            Obj.GATE -> 1.6f
            Obj.TENT -> 1.3f
            Obj.TREE, Obj.PINE -> 1.25f + ((tx * 7 + ty * 3) % 5) * 0.04f
            Obj.DEADTREE, Obj.PILLAR -> 1.2f
            else -> 1f
        }
        val flip = (tx * 13 + ty * 5) % 2 == 0 && (o == Obj.TREE || o == Obj.BUSH || o == Obj.BOULDER || o == Obj.DEADTREE || o == Obj.REED)
        items.add(ty + 0.9f to { sprite(c, img, tx + 0.5f, ty + 0.92f, sc, flip, 1f, null, sway, shadow = o != Obj.FLOWER) })
    }
    for (s in m.spots) {
        when (s.kind) {
            "chest" -> items.add(s.y + 0.8f to { sprite(c, if (s.used) props.chestOpen else props.chest, s.x + 0.5f, s.y + 0.85f, 1f, false, 1f, null, 0f, true) })
            "shrine" -> items.add(s.y + 0.8f to {
                sprite(c, props.shrine, s.x + 0.5f, s.y + 0.9f, 1f, false, 1f, null, 0f, true)
                if (!s.used) glow(c, s.x + 0.5f, s.y - 0.3f, 0.9f, col(0xFFB46CFF), 0.35f + 0.15f * sin(c.t * 3f))
            })
            "gate" -> items.add(s.y - 1f to {
                val open = if (g.phase == "prologue" && g.step <= 2) 1f else 0.35f
                riftOval(c, s.x + 0.5f, s.y - 2.2f, 0.6f * open, 0.75f * open)
            })
        }
    }
    // townsfolk going about their day
    if (m.townX >= 0) {
        for (i in 0 until 5) {
            val ph = c.t * 0.25f + i * 1.7f
            val fx = m.townX + 0.5f + sin(ph + i) * (2.5f + i % 3)
            val fy = m.townY + 0.5f + cos(ph * 0.7f + i * 2) * 1.6f
            val walk = cos(ph + i) > 0f
            val art = Art.folk[i % Art.folk.size]
            items.add(fy to { sprite(c, art[if ((c.t * 3).toInt() % 2 == 0) 0 else 1], fx, fy, 0.95f, walk, 1f, null, 0f, true) })
        }
    }
    // camp
    if (g.camping) {
        items.add(g.campfireY to { campfire(c, g.campfireX, g.campfireY) })
    }
    // foes and wild critters
    for (f in g.foes) {
        if (f.x < x0 - 3 || f.x > x1 + 3 || f.y < y0 - 3 || f.y > y1 + 4) continue
        items.add(f.y to { drawFoe(c, f, cart) })
    }
    // the critter
    val lead = g.party.getOrNull(g.active)
    if (lead != null && lead.hp > 0 && g.evolving <= 0f) {
        items.add(g.cy to {
            var lx = g.cx
            var ly = g.cy
            if (g.cLunge > 0f) {
                val tf = g.foes.firstOrNull { it.uid == g.targetUid }
                if (tf != null) {
                    val k = sin(g.cLunge / 0.3f * PI.toFloat()) * 0.5f
                    lx += (tf.x - g.cx) * k * 0.4f
                    ly += (tf.y - g.cy) * k * 0.4f
                }
            }
            val n = if (((c.t * 2.2f).toInt() + lead.uid) % 2 == 0) 0 else 1
            val hop = if (abs(g.cx - g.hero.x) + abs(g.cy - g.hero.y) > 0.6f && !g.inCombat) abs(sin(c.t * 9f)) * 0.12f else 0f
            sprite(c, cart.frame(lead.sp, n), lx, ly - hop, critScale(cart, lead.sp), g.cFacing < 0, 1f, if (g.cHurt > 0f) ColorFilter.tint(Color.Red.copy(alpha = 0.6f), BlendMode.SrcAtop) else null, 0f, true)
        })
    }
    if (g.evolving > 0f && lead != null) {
        items.add(g.cy to {
            val p = 1f - g.evolving / 3.4f
            val flick = sin(c.t * (8f + p * 30f)) > 0f
            val sp = if (flick && p > 0.5f) lead.species.evolveTo.coerceAtLeast(lead.sp) else lead.sp
            sprite(c, cart.white(sp), g.cx, g.cy, critScale(cart, sp) * (1f + p * 0.2f), false, 1f, ColorFilter.tint(Color.White), 0f, false)
        })
    }
    // the hero
    if (g.phase != "ended" || g.won) {
        items.add(h.y to {
            val frameN = if (h.moving && (h.walk * 6f).toInt() % 2 == 1) 1 else 0
            val bob = if (h.moving) abs(sin(h.walk * 9f)) * 0.06f else sin(c.t * 2f) * 0.015f
            if (h.cast > 0f) {
                val a = (h.cast * 2f).coerceAtMost(1f)
                runeCircle(c, h.x, h.y + 0.05f, 0.7f, col(0xFFB46CFF), a * 0.8f, c.t * 2f)
            }
            sprite(c, heroArt[frameN], h.x, h.y - bob, 1f, h.facing < 0, 1f, if (h.hurt > 0f) ColorFilter.tint(Color.Red.copy(alpha = 0.6f), BlendMode.SrcAtop) else null, 0f, true)
        })
    }
    items.sortBy { it.first }
    for ((_, fn) in items) fn()

    // ---- effects ----
    for (e in g.fx) drawFx(c, e)

    // ---- weather ----
    weather(c, m.def.biome, x0, y0, x1, y1)

    // ---- night ----
    night(c, g, x0, y0, x1, y1)

    // ---- floating numbers and speech ----
    for (fl in g.floaters) {
        val p = fl.t / 1.4f
        val a = (1f - p).coerceIn(0f, 1f)
        val st = TextStyle(
            color = Color(fl.color).copy(alpha = a),
            fontSize = (if (fl.big) 18 else 14).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        val lay = c.tm.measure(fl.text, st)
        val x = c.sx(fl.x) - lay.size.width / 2f
        val y = c.sy(fl.y - p * 1.1f) - lay.size.height
        drawText(c.tm.measure(fl.text, st.copy(color = Color.Black.copy(alpha = a * 0.7f))), topLeft = Offset(x + 1.5f, y + 1.5f))
        drawText(lay, topLeft = Offset(x, y))
    }
    if (g.bubbleT > 0f && g.bubble.isNotEmpty()) bubble(c, g.bubble, h.x, h.y - 1.75f, (g.bubbleT / 0.3f).coerceAtMost(1f))
    if (g.critBubbleT > 0f && g.critBubble.isNotEmpty()) bubble(c, g.critBubble, g.cx, g.cy - 1.4f, (g.critBubbleT / 0.3f).coerceAtMost(1f), small = true)
}

fun critScale(cart: CritterArt, sp: Int): Float {
    val img = cart.frame(sp, 0)
    val big = img.width / 2f / 22f
    return 0.5f + 0.15f * big.coerceIn(0.5f, 1.6f)
}

/** One sprite standing at (x, foot) in tiles. */
private fun DrawScope.sprite(
    c: Ctx, img: ImageBitmap, x: Float, foot: Float, scale: Float, flip: Boolean, alpha: Float,
    filter: ColorFilter?, sway: Float, shadow: Boolean,
) {
    val w = img.width * c.tp / 16f * scale
    val hh = img.height * c.tp / 16f * scale
    val left = c.sx(x) - w / 2f
    val top = c.sy(foot) - hh
    if (shadow) {
        drawOval(Color.Black.copy(alpha = 0.22f * alpha), topLeft = Offset(c.sx(x) - w * 0.38f, c.sy(foot) - c.tp * 0.1f), size = Size(w * 0.76f, c.tp * 0.2f))
    }
    withTransform({
        if (sway != 0f) rotate(sway * 3f, Offset(c.sx(x), c.sy(foot)))
        if (flip) scale(-1f, 1f, Offset(c.sx(x), c.sy(foot)))
    }) {
        drawImage(
            img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(img.width, img.height),
            dstOffset = IntOffset(left.toInt(), top.toInt()),
            dstSize = IntSize(w.toInt().coerceAtLeast(1), hh.toInt().coerceAtLeast(1)),
            alpha = alpha,
            colorFilter = filter,
            filterQuality = FilterQuality.None,
        )
    }
}

private fun DrawScope.drawFoe(c: Ctx, f: Foe, cart: CritterArt) {
    val alpha = if (f.dead) (1f - f.deathT / 1.2f).coerceIn(0f, 1f) else 1f
    if (alpha <= 0f) return
    var x = f.x
    var y = f.y
    if (f.lunge > 0f) {
        val k = sin(f.lunge / 0.3f * PI.toFloat()) * 0.35f
        x += f.facing * k
    }
    val hurt = if (f.hitFlash > 0f) ColorFilter.tint(Color.White.copy(alpha = 0.75f), BlendMode.SrcAtop) else null
    val crit = f.critter
    val float = if (f.kind == FoeKind.WRAITH) sin(c.t * 2.5f + f.uid) * 0.12f + 0.15f else 0f
    if (f.riftTouched && !f.dead) glow(c, x, y - 0.4f, 0.9f, col(0xFFB46CFF), 0.3f + 0.15f * sin(c.t * 4f + f.uid))
    if (f.boss && !f.dead) glow(c, x, y - 0.6f, 1.6f * f.kind.size, col(0xFFFF4A6A), 0.18f + 0.08f * sin(c.t * 3f))
    if (crit != null) {
        val n = if (((c.t * 2f).toInt() + f.uid) % 2 == 0) 0 else 1
        val sc = critScale(cart, crit.sp) * (if (f.boss) f.kind.size * 0.9f else 1f)
        val tint = when {
            hurt != null -> hurt
            f.kind == FoeKind.HOLLOW -> ColorFilter.tint(col(0x553A0A5A), BlendMode.SrcAtop)
            f.riftTouched -> ColorFilter.tint(col(0x40B46CFF), BlendMode.SrcAtop)
            else -> null
        }
        sprite(c, cart.frame(crit.sp, n), x, y - float, sc, f.facing < 0, alpha, tint, 0f, true)
        if (f.kind == FoeKind.RIVAL && !f.dead) {
            val art = Art.foe(FoeKind.RIVAL)
            sprite(c, art.frames[0], x + 1.6f, y - 0.6f, 1f, true, alpha, null, 0f, true)
        }
    } else {
        val art = Art.foe(f.kind)
        val moving = f.targetHero
        val n = if (moving && ((c.t * 5f).toInt() + f.uid) % 2 == 1) 1 else 0
        val sc = (if (f.boss) f.kind.size else f.kind.size.coerceAtMost(1.5f)) * (if (f.kind == FoeKind.WOLF) 0.85f else 1f)
        val faceLeft = if (f.kind == FoeKind.WOLF || f.kind == FoeKind.DRAKELING) f.facing < 0 else f.facing < 0
        sprite(c, art.frames[n], x, y - float, sc, faceLeft, alpha * (if (f.kind == FoeKind.WRAITH) 0.85f else 1f), hurt, 0f, f.kind != FoeKind.WRAITH)
    }
    // health over anything in the fight
    if (!f.dead && (f.targetHero || f.hp < f.maxHp)) {
        val w = c.tp * (if (f.boss) 1.6f else 0.9f)
        val bx = c.sx(f.x) - w / 2f
        val by = c.sy(f.y) - c.tp * (if (f.boss) 1.4f + f.kind.size * 0.9f else 1.45f)
        drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(bx - 1f, by - 1f), Size(w + 2f, c.tp * 0.12f + 2f), CornerRadius(3f))
        val hpCol = if (f.kind == FoeKind.WILD && !f.riftTouched) col(0xFF6AD86A) else col(0xFFFF5A5A)
        drawRoundRect(hpCol, Offset(bx, by), Size(w * f.hpFrac, c.tp * 0.12f), CornerRadius(3f))
        if (c.tp > 34f) {
            val st = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            val label = "${f.name} Lv${f.level}"
            val lay = c.tm.measure(label, st)
            drawText(lay, topLeft = Offset(c.sx(f.x) - lay.size.width / 2f, by - lay.size.height - 1f))
        }
    }
}

private fun DrawScope.glow(c: Ctx, x: Float, y: Float, r: Float, color: Color, a: Float) {
    val cx = c.sx(x)
    val cy = c.sy(y)
    val rr = r * c.tp
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = a.coerceIn(0f, 1f)), Color.Transparent), Offset(cx, cy), rr), rr, Offset(cx, cy))
}

private fun DrawScope.runeCircle(c: Ctx, x: Float, y: Float, r: Float, color: Color, a: Float, spin: Float) {
    val cx = c.sx(x)
    val cy = c.sy(y)
    val rx = r * c.tp
    val ry = rx * 0.45f
    drawOval(color.copy(alpha = a * 0.8f), Offset(cx - rx, cy - ry), Size(rx * 2, ry * 2), style = Stroke(c.tp / 20f))
    for (i in 0 until 8) {
        val ang = spin + i * PI.toFloat() / 4f
        val px = cx + cos(ang) * rx * 0.8f
        val py = cy + sin(ang) * ry * 0.8f
        drawCircle(color.copy(alpha = a), c.tp / 26f, Offset(px, py))
    }
}

private fun DrawScope.riftOval(c: Ctx, x: Float, y: Float, rw: Float, rh: Float) {
    if (rw <= 0.01f) return
    val cx = c.sx(x)
    val cy = c.sy(y)
    val w = rw * c.tp
    val hh = rh * c.tp
    drawOval(Brush.radialGradient(listOf(col(0xFFFFE8FF), col(0xFFB46CFF), col(0xFF2A0A4A)), Offset(cx, cy), max(w, hh)), Offset(cx - w, cy - hh), Size(w * 2, hh * 2))
    for (i in 0 until 6) {
        val a = c.t * 1.5f + i
        drawCircle(col(0xFFE8C8FF).copy(alpha = 0.6f), c.tp / 24f, Offset(cx + cos(a) * w * 1.1f, cy + sin(a * 1.3f) * hh * 1.05f))
    }
}

private fun DrawScope.campfire(c: Ctx, x: Float, y: Float) {
    val cx = c.sx(x)
    val cy = c.sy(y)
    val u = c.tp / 16f
    drawLine(col(0xFF6B4A2A), Offset(cx - 6 * u, cy), Offset(cx + 6 * u, cy - 2 * u), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
    drawLine(col(0xFF4A3220), Offset(cx - 6 * u, cy - 2 * u), Offset(cx + 6 * u, cy), strokeWidth = 2.5f * u, cap = StrokeCap.Round)
    for (i in 0 until 5) {
        val ph = c.t * 7f + i * 1.3f
        val fh = (6f + 4f * sin(ph)) * u
        val fx = cx + (i - 2) * 2.2f * u + sin(ph * 0.7f) * u
        val p = Path().apply {
            moveTo(fx - 2.2f * u, cy - u)
            quadraticTo(fx, cy - fh * 1.4f, fx + 2.2f * u, cy - u)
            close()
        }
        drawPath(p, if (i % 2 == 0) col(0xFFFF8A2A) else col(0xFFFFD86A))
    }
    for (i in 0 until 4) {
        val p = ((c.t * 0.8f + i * 0.25f) % 1f)
        drawCircle(col(0xFFFFB04A).copy(alpha = 1f - p), u * 0.8f, Offset(cx + sin(p * 9f + i) * 3f * u, cy - 6 * u - p * 22f * u))
    }
}

private fun DrawScope.bubble(c: Ctx, text: String, x: Float, y: Float, a: Float, small: Boolean = false) {
    val st = TextStyle(color = col(0xFF1A1426).copy(alpha = a), fontSize = (if (small) 11 else 13).sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    val maxW = (size.width * 0.55f).toInt().coerceAtMost(520)
    val lay = c.tm.measure(text, st, constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxW))
    val pad = 10f
    val w = lay.size.width + pad * 2
    val hh = lay.size.height + pad * 1.4f
    val left = (c.sx(x) - w / 2f).coerceIn(6f, size.width - w - 6f)
    val top = c.sy(y) - hh
    drawRoundRect(Color.Black.copy(alpha = 0.25f * a), Offset(left + 2f, top + 3f), Size(w, hh), CornerRadius(14f))
    drawRoundRect(col(0xFFF8F4FF).copy(alpha = 0.95f * a), Offset(left, top), Size(w, hh), CornerRadius(14f))
    val tail = Path().apply {
        moveTo(c.sx(x) - 7f, top + hh - 1f)
        lineTo(c.sx(x), top + hh + 9f)
        lineTo(c.sx(x) + 7f, top + hh - 1f)
        close()
    }
    drawPath(tail, col(0xFFF8F4FF).copy(alpha = 0.95f * a))
    drawText(lay, topLeft = Offset(left + pad, top + pad * 0.7f))
}

private fun DrawScope.drawFx(c: Ctx, e: Fx) {
    val p = (e.t / e.dur).coerceIn(0f, 1f)
    val color = Color(e.color)
    val sx0 = c.sx(e.x0)
    val sy0 = c.sy(e.y0)
    val sx1 = c.sx(e.x1)
    val sy1 = c.sy(e.y1)
    val u = c.tp * e.size
    when (e.kind) {
        "lunge" -> if (p > 0.55f) {
            val q = (p - 0.55f) / 0.45f
            for (i in 0 until 3) {
                val a = i * 2.1f + 0.4f
                drawLine(Color.White.copy(alpha = 1f - q), Offset(sx1 + cos(a) * u * 0.15f, sy1 + sin(a) * u * 0.15f), Offset(sx1 + cos(a) * u * (0.25f + q * 0.4f), sy1 + sin(a) * u * (0.25f + q * 0.4f)), strokeWidth = u / 14f, cap = StrokeCap.Round)
            }
            drawCircle(color.copy(alpha = 0.6f * (1f - q)), u * (0.15f + q * 0.35f), Offset(sx1, sy1))
        }
        "shot", "bolt" -> {
            val k = min(1f, p / 0.8f)
            val px = sx0 + (sx1 - sx0) * k
            val py = sy0 + (sy1 - sy0) * k - sin(k * PI.toFloat()) * u * 0.3f
            for (i in 1..4) {
                val kk = (k - i * 0.06f).coerceAtLeast(0f)
                val tx = sx0 + (sx1 - sx0) * kk
                val ty = sy0 + (sy1 - sy0) * kk - sin(kk * PI.toFloat()) * u * 0.3f
                drawCircle(color.copy(alpha = 0.45f - i * 0.09f), u * (0.13f - i * 0.02f), Offset(tx, ty))
            }
            drawCircle(Color.White, u * 0.07f, Offset(px, py))
            drawCircle(color.copy(alpha = 0.85f), u * 0.14f, Offset(px, py), style = Stroke(u / 16f))
            if (p > 0.8f) drawCircle(color.copy(alpha = (1f - p) * 4f), u * 0.4f * (p - 0.6f) * 3f, Offset(sx1, sy1))
        }
        "beam" -> {
            val a = if (p < 0.2f) p / 0.2f else (1f - p) / 0.8f
            drawLine(color.copy(alpha = a * 0.5f), Offset(sx0, sy0), Offset(sx1, sy1), strokeWidth = u * 0.28f, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = a), Offset(sx0, sy0), Offset(sx1, sy1), strokeWidth = u * 0.08f, cap = StrokeCap.Round)
            drawCircle(color.copy(alpha = a * 0.7f), u * 0.3f, Offset(sx1, sy1))
        }
        "burst" -> {
            val r = u * (0.2f + p * 0.9f)
            drawCircle(color.copy(alpha = (1f - p) * 0.35f), r, Offset(sx1, sy1))
            drawCircle(color.copy(alpha = 1f - p), r, Offset(sx1, sy1), style = Stroke(u / 10f * (1f - p) + 1f))
            for (i in 0 until 8) {
                val a = i * PI.toFloat() / 4f
                drawCircle(Color.White.copy(alpha = 1f - p), u * 0.05f, Offset(sx1 + cos(a) * r * 1.1f, sy1 + sin(a) * r * 1.1f))
            }
        }
        "rain" -> for (i in 0 until 7) {
            val delay = i * 0.08f
            val q = ((p - delay) / 0.5f).coerceIn(0f, 1f)
            if (q <= 0f || q >= 1f) continue
            val dx = ((i * 37) % 9 - 4) * u * 0.12f
            val px = sx1 + dx
            val py = sy1 - u * 1.8f * (1f - q)
            drawLine(color.copy(alpha = 0.9f), Offset(px, py - u * 0.25f), Offset(px, py), strokeWidth = u / 9f, cap = StrokeCap.Round)
            if (q > 0.9f) drawCircle(color.copy(alpha = 0.5f), u * 0.2f, Offset(px, sy1))
        }
        "swirl" -> for (i in 0 until 10) {
            val a = p * 9f + i * PI.toFloat() / 5f
            val r = u * (0.7f * (1f - p) + 0.1f)
            drawCircle(color.copy(alpha = 0.8f * (1f - p * 0.6f)), u * 0.07f, Offset(sx1 + cos(a) * r, sy1 + sin(a) * r * 0.6f))
        }
        "heal" -> for (i in 0 until 8) {
            val q = (p + i * 0.12f) % 1f
            val px = sx0 + sin(i * 2.3f) * u * 0.35f
            val py = sy0 - u * 0.2f - q * u * 1.1f
            drawLine(color.copy(alpha = 1f - q), Offset(px - u * 0.06f, py), Offset(px + u * 0.06f, py), strokeWidth = u / 18f)
            drawLine(color.copy(alpha = 1f - q), Offset(px, py - u * 0.06f), Offset(px, py + u * 0.06f), strokeWidth = u / 18f)
        }
        "chains" -> {
            val a = if (p < 0.85f) 1f else (1f - p) / 0.15f
            val links = 10
            for (i in 0..links) {
                val k = i / links.toFloat() * min(1f, p * 2.5f)
                val px = sx0 + (sx1 - sx0) * k
                val py = sy0 + (sy1 - sy0) * k + sin(k * PI.toFloat() * 3f + p * 10f) * u * 0.08f
                drawCircle(color.copy(alpha = a), u * 0.06f, Offset(px, py), style = Stroke(u / 30f))
            }
            if (p > 0.4f) runeCircle(c, e.x1, e.y1 + 0.4f, 0.7f, color, a, p * 8f)
            drawCircle(color.copy(alpha = 0.25f * a), u * 0.55f, Offset(sx1, sy1))
        }
        "bound" -> for (i in 0 until 12) {
            val a = i * PI.toFloat() / 6f
            val k = p
            val px = sx0 + (sx1 - sx0) * k + cos(a) * u * 0.4f * (1f - k)
            val py = sy0 + (sy1 - sy0) * k + sin(a) * u * 0.4f * (1f - k)
            drawCircle(color.copy(alpha = 1f - p * 0.5f), u * 0.05f, Offset(px, py))
        }
        "summon" -> {
            drawCircle(color.copy(alpha = (1f - p) * 0.5f), u * (0.2f + p * 0.6f), Offset(sx1, sy1 - u * 0.4f))
            drawCircle(Color.White.copy(alpha = 1f - p), u * (0.2f + p * 0.6f), Offset(sx1, sy1 - u * 0.4f), style = Stroke(u / 14f))
        }
        "rift" -> {
            val open = if (p < 0.3f) p / 0.3f else if (p > 0.8f) (1f - p) / 0.2f else 1f
            riftOval(c, e.x0, e.y0, 0.45f * open * e.size, 0.9f * open * e.size)
        }
        "evolve" -> {
            val a = sin(p * PI.toFloat())
            for (i in 0 until 12) {
                val ang = i * PI.toFloat() / 6f + p * 3f
                drawLine(Color.White.copy(alpha = a * 0.6f), Offset(sx0, sy0 - u * 0.5f), Offset(sx0 + cos(ang) * u * 2f, sy0 - u * 0.5f + sin(ang) * u * 2f), strokeWidth = u / 12f)
            }
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a), color.copy(alpha = a * 0.4f), Color.Transparent), Offset(sx0, sy0 - u * 0.5f), u * 1.6f), u * 1.6f, Offset(sx0, sy0 - u * 0.5f))
        }
        "levelup" -> for (i in 0 until 2) {
            val q = (p + i * 0.4f) % 1f
            val rx = u * (0.4f + q * 0.4f)
            drawOval(color.copy(alpha = 1f - q), Offset(sx0 - rx, sy0 - q * u * 1.2f - rx * 0.35f), Size(rx * 2, rx * 0.7f), style = Stroke(u / 18f))
        }
        "faint" -> for (i in 0 until 6) {
            val a = i * PI.toFloat() / 3f
            drawCircle(Color.White.copy(alpha = (1f - p) * 0.5f), u * (0.1f + p * 0.15f), Offset(sx0 + cos(a) * u * p * 0.6f, sy0 - u * 0.3f + sin(a) * u * p * 0.4f))
        }
        "shield" -> {
            val a = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
            drawCircle(color.copy(alpha = 0.2f * a), u * 0.75f, Offset(sx0, sy0 - u * 0.4f))
            drawCircle(color.copy(alpha = 0.8f * a), u * 0.75f, Offset(sx0, sy0 - u * 0.4f), style = Stroke(u / 18f))
        }
    }
}

private fun DrawScope.weather(c: Ctx, b: Biome, x0: Int, y0: Int, x1: Int, y1: Int) {
    val w = size.width
    val h = size.height
    when (b) {
        Biome.SNOW -> for (i in 0 until 70) {
            val sx = ((i * 97.3f + c.t * (12f + i % 5 * 4f)) % w + w) % w
            val sy = ((i * 53.7f + c.t * (30f + i % 7 * 6f)) % h + h) % h
            drawCircle(Color.White.copy(alpha = 0.7f), 1.5f + (i % 3), Offset(sx + sin(c.t + i) * 8f, sy))
        }
        Biome.ASH -> for (i in 0 until 50) {
            val sx = ((i * 131.3f - c.t * (10f + i % 4 * 5f)) % w + w) % w
            val sy = ((i * 71.7f + c.t * (14f + i % 5 * 3f)) % h + h) % h
            drawCircle(if (i % 6 == 0) col(0xFFFF8A3A).copy(alpha = 0.8f) else Color.Gray.copy(alpha = 0.5f), 1.4f + (i % 2), Offset(sx, sy))
        }
        Biome.SWAMP -> for (i in 0 until 6) {
            val sx = ((i * 211f + c.t * 9f) % (w + 400f)) - 200f
            val sy = h * (0.15f + i * 0.14f)
            drawOval(Color.White.copy(alpha = 0.06f), Offset(sx, sy), Size(380f, 90f))
        }
        Biome.CITADEL -> for (i in 0 until 30) {
            val sx = ((i * 157.3f + sin(c.t * 0.5f + i) * 30f) % w + w) % w
            val sy = ((i * 89.1f - c.t * (8f + i % 4 * 3f)) % h + h) % h
            drawCircle(col(0xFFB46CFF).copy(alpha = 0.5f), 1.6f, Offset(sx, sy))
        }
        else -> Unit
    }
    // fireflies in the green lands at night
    if ((b == Biome.MEADOW || b == Biome.FOREST) && c.g.night) {
        for (i in 0 until 24) {
            val fx = x0 + ((i * 7.31f) % (x1 - x0).coerceAtLeast(1)) + sin(c.t * 0.7f + i) * 1.2f
            val fy = y0 + ((i * 4.17f) % (y1 - y0).coerceAtLeast(1)) + cos(c.t * 0.5f + i * 2f) * 0.8f
            val a = 0.5f + 0.5f * sin(c.t * 3f + i * 1.7f)
            drawCircle(col(0xFFEFFF8A).copy(alpha = a), c.tp / 22f, Offset(c.sx(fx), c.sy(fy)))
        }
    }
}

private fun DrawScope.night(c: Ctx, g: Game, x0: Int, y0: Int, x1: Int, y1: Int) {
    val t = g.tod
    // 0 = noon light, 1 = full night
    val dark = when {
        t > 0.84f || t < 0.0f -> 1f
        t > 0.7f -> (t - 0.7f) / 0.14f
        t < 0.06f -> 1f - t / 0.06f
        else -> 0f
    }.coerceIn(0f, 1f)
    val dusk = when {
        t in 0.62f..0.78f -> sin((t - 0.62f) / 0.16f * PI.toFloat())
        t < 0.12f -> sin(t / 0.12f * PI.toFloat()) * 0.7f
        else -> 0f
    }
    if (dusk > 0.01f) drawRect(col(0xFFFF7A3A).copy(alpha = dusk * 0.12f))
    val base = when (g.map.def.biome) {
        Biome.CITADEL -> 0.25f
        Biome.ASH -> 0.12f
        else -> 0f
    }
    val a = (dark * 0.72f + base).coerceAtMost(0.8f)
    if (a <= 0.01f) return
    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint())
        drawRect(col(0xFF070B22).copy(alpha = a))
        fun light(x: Float, y: Float, r: Float, k: Float) {
            val cx = c.sx(x)
            val cy = c.sy(y)
            val rr = r * c.tp
            drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = k), Color.Transparent), Offset(cx, cy), rr), rr, Offset(cx, cy), blendMode = BlendMode.DstOut)
        }
        light(g.hero.x, g.hero.y - 0.5f, 3.2f, 0.75f)
        if (g.camping) light(g.campfireX, g.campfireY, 5f + sin(c.t * 9f) * 0.2f, 1f)
        val m = g.map
        for (ty in y0 until y1) for (tx in x0 until x1) {
            val o = m.o(tx, ty)
            if (o == Obj.HOUSE) light(tx + 0.5f, ty + 0.4f, 2.4f, 0.8f)
            else if (o == Obj.CRYSTAL) light(tx + 0.5f, ty + 0.3f, 1.6f, 0.6f)
            else if (m.g(tx, ty) == Ground.LAVA && (tx + ty) % 2 == 0) light(tx + 0.5f, ty + 0.5f, 1.8f, 0.5f)
        }
        for (e in g.fx) light(e.x1, e.y1, 1.6f * e.size, 0.6f * (1f - e.t / e.dur))
        for (f in g.foes) if (f.riftTouched && !f.dead) light(f.x, f.y - 0.4f, 1.2f, 0.35f)
        canvas.restore()
    }
    // a warm tint around the fire
    if (g.camping) glow(c, g.campfireX, g.campfireY - 0.2f, 3f, col(0xFFFF9A3A), 0.22f * dark)
}
