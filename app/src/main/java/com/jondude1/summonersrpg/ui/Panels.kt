package com.jondude1.summonersrpg.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.summonersrpg.BuildConfig
import com.jondude1.summonersrpg.MainViewModel
import com.jondude1.summonersrpg.UpdateUi
import com.jondude1.summonersrpg.sim.Critter
import com.jondude1.summonersrpg.sim.Dex
import com.jondude1.summonersrpg.sim.Game
import com.jondude1.summonersrpg.sim.Regions

/** A full-screen sheet over the world, with a title and a close button. */
@Composable
private fun Sheet(title: String, sub: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xB0050309))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(14.dp).widthIn(max = 820.dp).fillMaxWidth().fillMaxSize().panel()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = Pal.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (sub.isNotEmpty()) Text(sub, color = Pal.Dim, fontSize = 12.sp)
                }
                Text(
                    "Close",
                    color = Pal.Ink,
                    fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x30FFFFFF)).clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Pal.Rift, fontSize = 11.sp, letterSpacing = 3.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
}

// ======================= Critterdex =======================

@Composable
fun DexPanel(game: Game, vm: MainViewModel, cart: CritterArt, onClose: () -> Unit) {
    val meta = vm.meta
    val seen = meta.seen + game.seenNow
    val caught = meta.caught + game.caughtNow
    var pick by remember { mutableStateOf<Int?>(null) }
    Sheet("Critterdex", "Seen ${seen.size} · bound ${caught.size} of ${Dex.COUNT} across every journey", onClose) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionTitle("${game.hero.name.uppercase()}'S PARTY")
            for ((i, c) in game.party.withIndex()) PartyRow(c, cart, i == game.active)
            if (game.stored.isNotEmpty()) {
                SectionTitle("SENT HOME TO THE VILLAGE")
                for (c in game.stored) PartyRow(c, cart, false)
            }
            SectionTitle("EVERY SPECIES")
            BoxWithConstraints {
                val cols = (maxWidth.value / 92f).toInt().coerceIn(3, 8)
                val all = Dex.species
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (row in all.chunked(cols)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (s in row) {
                                val known = s.id in caught
                                val met = s.id in seen
                                Column(
                                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                        .background(if (pick == s.id) Pal.Rift.copy(alpha = 0.25f) else Color(0x22FFFFFF))
                                        .clickable { pick = if (pick == s.id) null else s.id }
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    PixelImage(
                                        if (known) cart.frame(s.id, 0) else cart.white(s.id), 52,
                                        tint = if (known) null else if (met) Color(0xFF6C7488) else Color(0xFF1B2030),
                                    )
                                    Text("#${s.id}", color = Pal.Faint, fontSize = 9.sp)
                                    Text(if (known || met) s.name else "???", color = if (known) Pal.Ink else Pal.Dim, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    if (known || met) Text(s.typeText, color = Color(s.t1.color), fontSize = 9.sp, maxLines = 1)
                                }
                            }
                            for (k in row.size until cols) Spacer(Modifier.weight(1f))
                        }
                        val p = pick
                        if (p != null && row.any { it.id == p }) {
                            val s = Dex.byId(p)
                            val known = p in caught || p in seen
                            Text(
                                if (known) "${s.name} · ${s.typeText} · ${if (s.rarity >= 5) "Legend" else "rarity ${s.rarity}"} · lives in ${s.habitat}. ${s.blurb}" +
                                    (if (s.evolveTo > 0) " Evolves into ${Dex.byId(s.evolveTo).name} once trust reaches ${if (s.stage >= 2) 40 else 18}." else "")
                                else "Not yet seen. Somewhere out there, past the rift gate.",
                                color = Pal.Dim, fontSize = 12.sp,
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x22000000)).padding(10.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PartyRow(c: Critter, cart: CritterArt, active: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) Pal.Rift.copy(alpha = 0.18f) else Color(0x18FFFFFF)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PixelImage(cart.frame(c.sp, 0), 56, alpha = if (c.hp > 0) 1f else 0.4f)
        Column(Modifier.weight(1f)) {
            Text((if (active) "★ " else "") + "${c.nick} the ${c.species.name} · Lv ${c.level}", color = Pal.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(c.species.typeText + " · ${c.wins} wins · trust ${c.bond}" + (if (c.species.evolveTo > 0) "/${c.trustNeeded()} → ${Dex.byId(c.species.evolveTo).name}" else ""), color = Color(c.species.t1.color), fontSize = 12.sp)
            Text("HP ${c.hp}/${c.maxHp} · ATK ${c.atk} DEF ${c.def} SPD ${c.spd}", color = Pal.Dim, fontSize = 11.sp)
            Text(c.moves.joinToString(" · ") { "${it.name} (${it.type.label} ${it.power})" }, color = Pal.Faint, fontSize = 11.sp)
        }
    }
}

// ======================= journal =======================

@Composable
fun JournalPanel(game: Game, onClose: () -> Unit) {
    val lines = remember { game.log.toList().asReversed() }
    val h = game.hero
    Sheet("${h.name}'s Journal", "Chapter ${game.def.idx}: ${game.def.name} · Day ${h.days}", onClose) {
        Text(
            "${h.name} is ${h.persona.trait}, a ${h.job.lowercase()}'s child from Summoner's Village, out ${com.jondude1.summonersrpg.sim.Lore.motiveText(h)}.",
            color = Pal.Dim, fontSize = 13.sp, fontStyle = FontStyle.Italic,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(lines) { l ->
                val color = when (l.kind) {
                    "story" -> Pal.Ink
                    "talk" -> Color(0xFF9DD8FF)
                    "voice", "order" -> Pal.Rift
                    "bind" -> Color(0xFFE8B4FF)
                    "level" -> Color(0xFF8FE8FF)
                    "loot" -> Pal.Gold
                    "danger" -> Color(0xFFFF8A8A)
                    else -> Pal.Dim
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("D${l.day}", color = Pal.Faint, fontSize = 10.sp, modifier = Modifier.width(30.dp).padding(top = 2.dp))
                    Text(l.text, color = color, fontSize = 13.sp, fontStyle = if (l.kind == "story") FontStyle.Italic else FontStyle.Normal)
                }
            }
        }
    }
}

// ======================= Hall of Legends =======================

@Composable
fun LegendsPanel(vm: MainViewModel, onClose: () -> Unit) {
    val list = vm.meta.legends
    Sheet("Hall of Legends", "${list.count { it.won }} of ${list.size} heroes closed the rifts", onClose) {
        if (list.isEmpty()) {
            Text("No journey has ended yet. The first is still on the road.", color = Pal.Dim, fontSize = 13.sp)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list) { g ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (g.won) Pal.Gold.copy(alpha = 0.12f) else Color(0x18FFFFFF)).padding(10.dp)) {
                    Text((if (g.won) "★ " else "") + "${g.name}, the ${g.job.lowercase()}'s child", color = if (g.won) Pal.Gold else Pal.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (g.won) "Closed the rifts on day ${g.days}" else "${g.cause.ifBlank { "Lost in ${g.reached}" }} · day ${g.days}", color = Pal.Dim, fontSize = 12.sp)
                    Text("${g.persona} · Summoner Lv ${g.level} · ${g.bound} critters" + (if (g.best.isNotEmpty()) " · best: ${g.best}" else ""), color = Pal.Faint, fontSize = 11.sp)
                }
            }
        }
    }
}

// ======================= menu =======================

@Composable
fun MenuPanel(game: Game, vm: MainViewModel, onClose: () -> Unit, onNewRun: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var legends by remember { mutableStateOf(false) }
    val status by vm.updateStatus.collectAsState()
    if (legends) {
        LegendsPanel(vm) { legends = false }
        return
    }
    Sheet("Summoners RPG", "Journey ${vm.meta.runs + 1} · ${game.hero.name}", onClose) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuItem("Hall of Legends", "Every hero who stood before the rift gate") { legends = true }
            if (!confirm) {
                MenuItem("Begin a new journey", "A different villager, a new critter, a new road") { confirm = true }
            } else {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x30FF5A5A)).padding(12.dp)) {
                    Text("Leave ${game.hero.name}'s story here? It goes into the Hall of Legends.", color = Pal.Ink, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Text("Yes, new journey", color = Color(0xFF14101E), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.Rift).clickable(onClick = onNewRun).padding(horizontal = 14.dp, vertical = 8.dp))
                        Text("Keep watching", color = Pal.Ink, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x30FFFFFF)).clickable { confirm = false }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
            SectionTitle("THE ROAD")
            for (r in Regions.all) {
                val here = r.idx == game.regionIdx
                val done = r.idx < game.regionIdx
                Text(
                    (if (done) "✓ " else if (here) "▸ " else "   ") + "${r.idx}. ${r.name}" + (if (r.bossName.isNotEmpty()) " — ${r.bossName}" else if (r.boss != null) " — ${game.hero.rival}" else ""),
                    color = if (here) Pal.Ink else if (done) Pal.Dim else Pal.Faint, fontSize = 13.sp,
                )
            }
            SectionTitle("UPDATES")
            Text("Installed: v${BuildConfig.VERSION_NAME}", color = Pal.Dim, fontSize = 13.sp)
            MenuItem("Check for updates", status ?: "Updates also arrive on their own when the app opens") { vm.checkForUpdate(quiet = false) }
            SectionTitle("HOW TO WATCH")
            Text(
                "Pinch to zoom, drag to look around, double-tap to follow the hero again. Tap anyone to learn about them. " +
                    "The hero decides everything, but they hear the Voice: type an order (attack, defend, bind, rest, explore, train, onward, flee, potion, swap, summon, bond, town, sneak, evolve) and they'll follow it for a while.",
                color = Pal.Dim, fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MenuItem(title: String, sub: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x20FFFFFF)).clickable(onClick = onClick).padding(12.dp)) {
        Text(title, color = Pal.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        Text(sub, color = Pal.Dim, fontSize = 12.sp)
    }
}

// ======================= update banner =======================

@Composable
fun UpdateBanner(vm: MainViewModel, modifier: Modifier = Modifier) {
    val state by vm.update.collectAsState()
    val status by vm.updateStatus.collectAsState()
    AnimatedVisibility(
        visible = state !is UpdateUi.Idle,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Column(Modifier.padding(top = 10.dp).widthIn(max = 560.dp).panel().padding(horizontal = 18.dp, vertical = 12.dp)) {
            when (val s = state) {
                is UpdateUi.Available -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(status ?: "Update available — v${s.info.versionName}", color = Pal.Ink, fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                    Text("Later", color = Pal.Ink, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x30FFFFFF)).clickable { vm.dismissUpdate() }.padding(horizontal = 12.dp, vertical = 7.dp))
                    Text("Update", color = Color(0xFF14101E), fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Pal.Rift).clickable { vm.startUpdate() }.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                is UpdateUi.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Downloading v${s.info.versionName}…", color = Pal.Ink, fontSize = 14.sp)
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.width(260.dp), color = Pal.Rift, trackColor = Color(0x30FFFFFF))
                }
                UpdateUi.Idle -> Unit
            }
        }
    }
}
