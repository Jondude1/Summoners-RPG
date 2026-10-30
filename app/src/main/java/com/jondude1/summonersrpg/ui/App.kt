package com.jondude1.summonersrpg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.summonersrpg.BuildConfig
import com.jondude1.summonersrpg.MainViewModel
import com.jondude1.summonersrpg.sim.CType
import com.jondude1.summonersrpg.sim.Critter
import com.jondude1.summonersrpg.sim.Dex
import com.jondude1.summonersrpg.sim.Foe
import com.jondude1.summonersrpg.sim.FoeKind
import com.jondude1.summonersrpg.sim.Game
import com.jondude1.summonersrpg.sim.Regions
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object Pal {
    val Panel = Color(0xCC120C1E)
    val PanelHi = Color(0xE01B1430)
    val Edge = Color(0x40E8D8FF)
    val Ink = Color(0xFFF4EEFF)
    val Dim = Color(0xB8F4EEFF)
    val Faint = Color(0x80F4EEFF)
    val Rift = Color(0xFFB46CFF)
    val Gold = Color(0xFFFFD86A)
    val Hp = Color(0xFF6AD86A)
    val HpLow = Color(0xFFFF5A5A)
    val Mana = Color(0xFF6AA8FF)
}

private val panelShape = RoundedCornerShape(16.dp)

@Composable
fun Modifier.panel(): Modifier = this
    .clip(panelShape)
    .background(Brush.verticalGradient(listOf(Pal.PanelHi, Pal.Panel)))
    .border(1.dp, Pal.Edge, panelShape)

@Composable
fun SummonersApp(vm: MainViewModel) {
    val gen by vm.gen.collectAsState()
    val game = remember(gen) { vm.game }
    val cart = remember { CritterArtHolder.get() }
    val heroArt = remember(gen) { Art.heroSprites(game.hero) }
    val cam = remember(gen) { Camera() }
    var frame by remember { mutableLongStateOf(0L) }
    var hud by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf("") }
    var inspect by remember { mutableStateOf<String?>(null) }
    var inspectT by remember { mutableFloatStateOf(0f) }
    var region by remember(gen) { mutableIntStateOf(game.regionIdx) }

    // the paint for the current land is made off the main thread
    val land by produceState<LandArt?>(null, gen, region) {
        val m = game.map
        value = withContext(Dispatchers.Default) {
            LandArt(m.def.idx, game.runSeed, Art.ground(m), Art.props(m.def.biome))
        }
    }

    // the game clock
    LaunchedEffect(gen) {
        var last = 0L
        var hudAcc = 0f
        var mergeAcc = 0f
        var saveAcc = 0f
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceIn(0f, 0.1f)
                last = now
                game.tick(dt)
                frame = now
                hudAcc += dt
                mergeAcc += dt
                saveAcc += dt
                if (inspectT > 0f) inspectT -= dt
                if (hudAcc > 0.2f) {
                    hudAcc = 0f
                    hud++
                    if (game.regionIdx != region) region = game.regionIdx
                }
                if (mergeAcc > 3f) {
                    mergeAcc = 0f
                    vm.merge()
                }
                if (saveAcc > 20f) {
                    saveAcc = 0f
                    vm.saveAll()
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0712))) {
        val l = land
        if (l != null && l.regionIdx == game.regionIdx) {
            WorldView(game, frame, cam, l, heroArt, cart, Modifier.fillMaxSize()) { wx, wy ->
                inspect = describeAt(game, wx, wy)
                inspectT = if (inspect != null) 6f else 0f
            }
        } else {
            Text("…", color = Pal.Faint, modifier = Modifier.align(Alignment.Center))
        }

        @Suppress("UNUSED_VARIABLE") val tick = hud
        Hud(game, cart, heroArt, cam, vm, hud, onPanel = { panel = it })

        val ins = inspect
        if (ins != null && inspectT > 0f && panel.isEmpty()) {
            Text(
                ins,
                color = Pal.Ink,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterStart).padding(12.dp).widthIn(max = 260.dp).panel().padding(12.dp),
            )
        }

        Banner(game, hud, Modifier.align(Alignment.Center))

        if (game.phase == "ended" && game.line == null && game.talk.isEmpty() && panel.isEmpty()) {
            EndCard(game, vm, Modifier.align(Alignment.Center))
        }

        when (panel) {
            "dex" -> DexPanel(game, vm, cart) { panel = "" }
            "journal" -> JournalPanel(game) { panel = "" }
            "legends" -> LegendsPanel(vm) { panel = "" }
            "menu" -> MenuPanel(game, vm, onClose = { panel = "" }, onNewRun = {
                panel = ""
                vm.newRun()
            })
        }

        UpdateBanner(vm, Modifier.align(Alignment.TopCenter))
    }
}

private fun describeAt(g: Game, wx: Float, wy: Float): String? {
    fun dd(x: Float, y: Float) = hypot(x - wx, (y - 0.5f) - wy)
    val h = g.hero
    var best: String? = null
    var bd = 1.1f
    if (dd(h.x, h.y) < bd) {
        bd = dd(h.x, h.y)
        best = "${h.name}, ${h.job.lowercase()}'s child. ${h.persona.trait.replaceFirstChar { it.uppercase() }}. Summoner Lv ${h.level}. Wants ${com.jondude1.summonersrpg.sim.Lore.motiveText(h)}."
    }
    val c = g.party.getOrNull(g.active)
    if (c != null && dd(g.cx, g.cy) < bd) {
        bd = dd(g.cx, g.cy)
        best = critterLine(c)
    }
    for (f in g.foes) {
        if (f.dead) continue
        val d = dd(f.x, f.y)
        if (d < bd) {
            bd = d
            val cr = f.critter
            best = if (cr != null && f.kind == FoeKind.WILD) {
                "${if (f.riftTouched) "Rift-touched" else "Wild"} ${cr.species.name}, Lv ${f.level} (${cr.species.typeText}). ${cr.species.blurb}" +
                    if (f.riftTouched) " It attacks on sight and can't be bound." else ""
            } else {
                "${f.name}, Lv ${f.level}. HP ${f.hp}/${f.maxHp}. Type ${f.types.joinToString("/") { it.label }}."
            }
        }
    }
    return best
}

fun critterLine(c: Critter): String =
    "${c.label}, Lv ${c.level} (${c.species.typeText}). HP ${c.hp}/${c.maxHp}. Trust ${c.bond}" +
        (if (c.species.evolveTo > 0) "/${c.trustNeeded()} to evolve" else "") + ". Moves: ${c.moves.joinToString { it.name }}."

// ======================= HUD =======================

@Composable
private fun Bar(frac: Float, color: Color, modifier: Modifier = Modifier, height: Int = 6) {
    Box(modifier.height(height.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x66000000))) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(height.dp).background(color))
    }
}

@Composable
fun PixelImage(img: ImageBitmap, size: Int, modifier: Modifier = Modifier, alpha: Float = 1f, tint: Color? = null) {
    Image(
        bitmap = img,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.None,
        alpha = alpha,
        colorFilter = tint?.let { androidx.compose.ui.graphics.ColorFilter.tint(it) },
        modifier = modifier.size(size.dp),
    )
}

@Composable
private fun Hud(game: Game, cart: CritterArt, heroArt: Array<ImageBitmap>, cam: Camera, vm: MainViewModel, hud: Int, onPanel: (String) -> Unit) {
    @Suppress("UNUSED_VARIABLE") val t = hud
    val h = game.hero
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth > 700.dp
        // ---- hero card, top left ----
        Column(Modifier.align(Alignment.TopStart).padding(10.dp).width(if (wide) 250.dp else 200.dp)) {
            Column(Modifier.panel().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PixelImage(heroArt[0], 30)
                    Column(Modifier.weight(1f)) {
                        Text(h.name, color = Pal.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("Summoner Lv ${h.level} · Day ${h.days}", color = Pal.Dim, fontSize = 11.sp, maxLines = 1)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("HP", color = Pal.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Bar(h.hp / h.maxHp, if (h.hp < h.maxHp * 0.3f) Pal.HpLow else Pal.Hp, Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MP", color = Pal.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
                    Bar(h.mana / h.maxMana, Pal.Mana, Modifier.weight(1f))
                }
                Text("◆ ${h.gold}   ✚ ${h.potions}   ✦ ${h.ethers}   ⌘ ${h.scrolls}", color = Pal.Gold, fontSize = 11.sp)
            }
            Spacer(Modifier.height(6.dp))
            // the party
            Row(Modifier.panel().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for ((i, c) in game.party.withIndex()) {
                    val active = i == game.active
                    Column(
                        Modifier.width(28.dp).clip(RoundedCornerShape(6.dp)).background(if (active) Pal.Rift.copy(alpha = 0.3f) else Color.Transparent).padding(1.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PixelImage(cart.frame(c.sp, 0), 26, alpha = if (c.hp > 0) 1f else 0.35f)
                        Bar(c.hpFrac, if (c.hpFrac < 0.3f) Pal.HpLow else Pal.Hp, Modifier.fillMaxWidth(), height = 3)
                    }
                }
                if (game.party.isEmpty()) Text("No critters yet", color = Pal.Faint, fontSize = 11.sp, modifier = Modifier.padding(4.dp))
            }
            val lead = game.party.getOrNull(game.active)
            if (lead != null) {
                Spacer(Modifier.height(6.dp))
                Column(Modifier.panel().padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("${lead.nick} · ${lead.species.name} Lv ${lead.level}", color = Pal.Ink, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        lead.species.typeText + " · trust ${lead.bond}" + (if (lead.species.evolveTo > 0) "/${lead.trustNeeded()}" else ""),
                        color = Color(lead.species.t1.color), fontSize = 10.sp, maxLines = 1,
                    )
                    Bar(lead.xp.toFloat() / Dex.xpNeed(lead.level), Pal.Rift.copy(alpha = 0.8f), Modifier.fillMaxWidth().padding(top = 3.dp), height = 3)
                }
            }
        }

        // ---- quest, top center ----
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp).widthIn(max = if (wide) 420.dp else 180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (wide) {
                Column(Modifier.panel().padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CHAPTER ${game.def.idx} · ${game.def.name.uppercase()}", color = Pal.Rift, fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    if (game.quest.isNotEmpty()) Text(game.quest, color = Pal.Ink, fontSize = 13.sp, textAlign = TextAlign.Center)
                    if (game.doing.isNotEmpty()) Text(game.doing, color = Pal.Dim, fontSize = 11.sp, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val b = game.bossFoe
            if (b != null && (b.targetHero || game.line != null)) {
                Spacer(Modifier.height(6.dp))
                Column(Modifier.panel().padding(horizontal = 12.dp, vertical = 6.dp).width(if (wide) 360.dp else 180.dp)) {
                    Text("${b.name} · Lv ${b.level}", color = Color(0xFFFF8A8A), fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Bar(b.hpFrac, Pal.HpLow, Modifier.fillMaxWidth(), height = 8)
                }
            }
        }

        // ---- buttons, top right ----
        Column(Modifier.align(Alignment.TopEnd).padding(10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HudButton("Dex") { onPanel("dex") }
                HudButton("Log") { onPanel("journal") }
                HudButton("☰") { onPanel("menu") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val sp = game.speed
                HudButton(if (sp == 0f) "▶" else "❚❚") {
                    vm.speed = if (sp == 0f) 1f else 0f
                }
                HudButton("${if (sp == 0f) 1 else sp.toInt()}×") {
                    vm.speed = when (sp) { 1f -> 2f; 2f -> 4f; 4f -> 8f; else -> 1f }
                }
                if (!cam.follow) HudButton("◎") { cam.follow = true }
            }
            if (!wide) {
                Column(Modifier.panel().padding(horizontal = 10.dp, vertical = 6.dp).widthIn(max = 170.dp), horizontalAlignment = Alignment.End) {
                    Text("CH ${game.def.idx} · ${game.def.name}", color = Pal.Rift, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (game.quest.isNotEmpty()) Text(game.quest, color = Pal.Ink, fontSize = 11.sp, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ---- dialogue and the Voice, bottom ----
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().imePadding().padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DialogueBox(game, cart, heroArt, Modifier.widthIn(max = 720.dp).fillMaxWidth())
            if (game.phase != "ended") VoiceBox(game, Modifier.widthIn(max = 720.dp).fillMaxWidth())
        }
    }
}

@Composable
private fun HudButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).panel().clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Pal.Ink, fontSize = if (text.length > 2) 12.sp else 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DialogueBox(game: Game, cart: CritterArt, heroArt: Array<ImageBitmap>, modifier: Modifier) {
    val line = game.line
    AnimatedVisibility(
        visible = line != null,
        enter = slideInVertically { it / 2 } + fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val l = line ?: return@AnimatedVisibility
        val total = 2.6f + l.text.length * 0.045f
        val shown = ((total - game.lineT) * 55f).toInt().coerceIn(0, l.text.length)
        Row(Modifier.panel().padding(12.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (l.who != "narrator") Portrait(game, l.who, l.speaker, cart, heroArt)
            Column(Modifier.weight(1f)) {
                if (l.speaker.isNotEmpty()) {
                    Text(
                        l.speaker,
                        color = when (l.who) {
                            "voice" -> Pal.Rift
                            "boss", "rival" -> Color(0xFFFF8A8A)
                            "hero" -> Pal.Gold
                            else -> Color(0xFF9DD8FF)
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    l.text.substring(0, shown),
                    color = Pal.Ink,
                    fontSize = 15.sp,
                    fontStyle = if (l.who == "narrator" || l.who == "voice") FontStyle.Italic else FontStyle.Normal,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun Portrait(game: Game, who: String, speaker: String, cart: CritterArt, heroArt: Array<ImageBitmap>) {
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x66000000)).border(1.dp, Pal.Edge, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        when (who) {
            "hero" -> PixelImage(heroArt[0], 44)
            "voice" -> Canvas(Modifier.size(44.dp)) {
                val r = size.minDimension / 2f
                drawCircle(Brush.radialGradient(listOf(Color.White, Pal.Rift, Color.Transparent), center, r), r)
            }
            "rival" -> PixelImage(Art.foe(FoeKind.RIVAL).frames[0], 44)
            "boss" -> {
                val b = game.bossFoe
                val c = b?.critter
                if (c != null) PixelImage(cart.frame(c.sp, 0), 48)
                else if (b != null) PixelImage(Art.foe(b.kind).frames[0], 44)
                else PixelImage(Art.folk[0][0], 44)
            }
            else -> {
                val i = (speaker.hashCode() and 0x7fffffff) % Art.folk.size
                PixelImage(Art.folk[i][0], 44)
            }
        }
    }
}

private val quick = listOf("Attack", "Defend", "Bind", "Rest", "Explore", "Train", "Onward", "Flee", "Potion", "Swap", "Summon", "Bond")

@Composable
private fun VoiceBox(game: Game, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun send(s: String) {
        if (s.isBlank()) return
        game.command(s)
        text = ""
        focus.clearFocus()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedVisibility(open, enter = fadeIn() + scaleIn(initialScale = 0.95f), exit = fadeOut()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (q in quick) {
                    Text(
                        q,
                        color = Pal.Ink,
                        fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.Rift.copy(alpha = 0.28f))
                            .border(1.dp, Pal.Rift.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .clickable { send(q) }.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Row(Modifier.panel().padding(start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (open) Pal.Rift.copy(alpha = 0.4f) else Color.Transparent).clickable { open = !open },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(18.dp)) {
                    val r = size.minDimension / 2f
                    drawCircle(Brush.radialGradient(listOf(Color.White, Pal.Rift, Color.Transparent), center, r), r)
                }
            }
            Box(Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 12.dp)) {
                if (text.isEmpty()) Text("Speak to ${game.hero.name} as the Voice…", color = Pal.Faint, fontSize = 14.sp)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(120) },
                    singleLine = true,
                    textStyle = TextStyle(color = Pal.Ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(Pal.Rift),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(text) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (text.isNotBlank()) {
                Text(
                    "Speak",
                    color = Color(0xFF14101E),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.Rift).clickable { send(text) }.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        val o = game.order
        if (o != null) {
            Text(
                "Following your word: ${o.label} (${game.orderT.toInt()}s)",
                color = Pal.Rift, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun Banner(game: Game, hud: Int, modifier: Modifier) {
    @Suppress("UNUSED_VARIABLE") val t = hud
    AnimatedVisibility(
        visible = game.bannerT > 0f && game.banner.isNotEmpty(),
        enter = fadeIn() + scaleIn(initialScale = 0.9f),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 120.dp)) {
            Text(
                game.banner,
                color = Pal.Ink,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(shadow = androidx.compose.ui.graphics.Shadow(Pal.Rift, Offset(0f, 0f), 24f)),
            )
            Text(game.bannerSub, color = Pal.Dim, fontSize = 15.sp, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun EndCard(game: Game, vm: MainViewModel, modifier: Modifier) {
    var left by remember(game) { mutableIntStateOf(90) }
    LaunchedEffect(game) {
        while (left > 0) {
            delay(1000)
            left--
        }
        vm.newRun()
    }
    val h = game.hero
    Column(
        modifier.padding(24.dp).widthIn(max = 460.dp).panel().padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (game.won) "A LEGEND IS BORN" else "THE JOURNEY ENDS", color = if (game.won) Pal.Gold else Color(0xFFFF8A8A), fontSize = 13.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold)
        Text(h.name, color = Pal.Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            if (game.won) "${h.persona.trait.replaceFirstChar { it.uppercase() }}, the ${h.job.lowercase()}'s child closed every rift in the valley."
            else game.deathCause,
            color = Pal.Dim, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
        Text(
            "Day ${h.days} · Summoner Lv ${h.level} · ${h.wins} battles · ${h.binds} critters bound",
            color = Pal.Faint, fontSize = 12.sp, textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (c in game.party) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PixelImage(CritterArtHolder.get().frame(c.sp, 0), 40)
                Text(c.nick, color = Pal.Dim, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Begin a new journey",
            color = Color(0xFF14101E),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.Rift).clickable { vm.newRun() }.padding(horizontal = 22.dp, vertical = 12.dp),
        )
        Text("Another villager steps up to the rift gate in ${left}s", color = Pal.Faint, fontSize = 11.sp)
    }
}

/** One shared copy of the critter art for places that don't get it passed in. */
object CritterArtHolder {
    private var art: CritterArt? = null
    fun get(): CritterArt = art ?: CritterArt().also { art = it }
}
