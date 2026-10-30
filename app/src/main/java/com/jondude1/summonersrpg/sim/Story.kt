package com.jondude1.summonersrpg.sim

import java.util.Random

// Who the hero is, why they leave home, and what gets said along the way. Every line is built from the real run:
// names, critters, places and what actually happened.

class Line(val who: String, val speaker: String, val text: String)

class Persona(
    val key: String,
    val trait: String,
    val battle: List<String>,
    val hurt: List<String>,
    val win: List<String>,
    val bind: List<String>,
    val camp: List<String>,
    val obey: List<String>,
    val fear: List<String>,
    /** How this hero leans when the AI weighs choices. */
    val bravery: Float,
    val curiosity: Float,
)

object Lore {
    val NAMES = listOf(
        "Mira", "Tobin", "Edda", "Rook", "Wren", "Bram", "Isla", "Finn", "Odette", "Cass", "Hugo", "Nell",
        "Pike", "Sorrel", "Dov", "Lark", "Ember", "Quill", "Tamsin", "Gideon", "Juno", "Alder", "Pip", "Vesna",
        "Maren", "Oswin", "Thea", "Corin", "Lio", "Yara", "Bastian", "Fenna", "Rhys", "Saga", "Orla", "Kit",
    )
    val JOBS = listOf(
        "Woodcutter", "Miner", "Forager", "Farmer", "Builder", "Scout", "Storyteller", "Hunter", "Bronzesmith", "Blacksmith",
    )

    /** The village trade a hero grew up in decides which kind of critter answers their gift test. */
    fun starterTypes(job: String): List<CType> = when (job) {
        "Woodcutter" -> listOf(CType.LEAF, CType.GALE)
        "Miner" -> listOf(CType.STONE, CType.IRON)
        "Forager" -> listOf(CType.TIDE, CType.LEAF)
        "Farmer" -> listOf(CType.LEAF, CType.GLOW)
        "Builder" -> listOf(CType.STONE, CType.IRON)
        "Scout" -> listOf(CType.GALE, CType.SHADE)
        "Storyteller" -> listOf(CType.SPIRIT, CType.GLOW)
        "Hunter" -> listOf(CType.SHADE, CType.VENOM, CType.EMBER)
        "Bronzesmith" -> listOf(CType.EMBER, CType.SPARK)
        else -> listOf(CType.EMBER, CType.IRON)
    }

    val CRITTER_NAMES = listOf(
        "Pip", "Bramble", "Soot", "Juniper", "Tumble", "Moss", "Cinder", "Biscuit", "Nettle", "Pebble", "Wisp", "Rune",
        "Sable", "Fig", "Echo", "Tansy", "Ash", "Murk", "Nimbus", "Clover", "Flint", "Ripple", "Sprocket", "Hazel",
        "Ember", "Quince", "Thistle", "Glim", "Basil", "Kettle", "Brook", "Saffron", "Ivo", "Lumen", "Puddle", "Tinder",
    )

    val RIVALS = listOf("Morwen", "Kaz", "Selka", "Draven", "Ilse", "Corvid", "Vey", "Tarn")
    val ELDERS = listOf("Elder Hesk", "Elder Marrow", "Old Nan Ivy", "Elder Bracken")
    val MENTORS = listOf("Aunt Rosa", "Master Teague", "Old Wynn", "Sister Calla")

    val PERSONAS = listOf(
        Persona(
            "bold", "bold and stubborn",
            listOf("Stand your ground, %c!", "We don't run. Not today.", "Come on then!"),
            listOf("I'm fine. I'm fine!", "That one stung."),
            listOf("Ha! Told you we could.", "That's how it's done, %c."),
            listOf("You're with me now, %n.", "Welcome to the team, %n. Try to keep up."),
            listOf("Tomorrow we push harder.", "One day they'll sing about us, %c."),
            listOf("Right. On it.", "Heard you loud and clear."),
            listOf("So that's %b. Big. Good."),
            0.85f, 0.45f,
        ),
        Persona(
            "kind", "gentle and caring",
            listOf("Careful, %c. I've got you.", "I'm sorry it has to be like this."),
            listOf("Ow... keep going, I'll manage.", "Don't worry about me."),
            listOf("Are you hurt, %c? Let me look.", "Rest easy, wild one."),
            listOf("You're safe with us now, %n.", "Hello, %n. No one will hurt you here."),
            listOf("Thank you for staying with me, %c.", "Let's share the last of the berries."),
            listOf("If you think it's best, I'll do it.", "All right. I trust you."),
            listOf("Please let us all come home from this."),
            0.45f, 0.55f,
        ),
        Persona(
            "grumpy", "dry and grumbling",
            listOf("Oh good. More fighting.", "Of course this happens now."),
            listOf("Wonderful. Bleeding. Again.", "Next time, someone else goes first."),
            listOf("Hmph. Could've been worse.", "Don't let it go to your head, %c."),
            listOf("Fine. You can come, %n. Don't chew anything.", "%n, is it. Try not to be trouble."),
            listOf("My feet hurt. Your feet hurt? Course they don't.", "Wake me if the world ends, %c."),
            listOf("Yes, yes. Doing it.", "Bossy voice. Fine."),
            listOf("That is a terrible, terrible idea, and we're doing it anyway."),
            0.55f, 0.3f,
        ),
        Persona(
            "curious", "endlessly curious",
            listOf("Ooh, what are you? Never mind, fight first!", "Watch how it moves, %c!"),
            listOf("Interesting. Painful, but interesting.", "Note to self: that hurts."),
            listOf("Did you see that? Did you SEE that?", "We learned something today, %c."),
            listOf("A %s! I've only read about you, %n!", "Welcome aboard, %n! Tell me everything."),
            listOf("I wonder what's past the next ridge.", "%c, look at all those stars."),
            listOf("Oh! Good thinking.", "Let's try it your way."),
            listOf("I have to know what's behind that door. Even if it's %b."),
            0.6f, 0.95f,
        ),
        Persona(
            "shy", "quiet and nervous",
            listOf("O-okay, %c, together.", "Please just go away..."),
            listOf("I... I can still stand.", "%c, I'm scared."),
            listOf("We did it? We did it.", "Thank you, %c. Really."),
            listOf("H-hi, %n. I'm glad you came.", "You can stay close to me, %n."),
            listOf("I talk to you more than anyone back home, %c.", "The fire's nice. It's quiet."),
            listOf("I'll try. I'll really try.", "If you say so, Voice."),
            listOf("My hands won't stop shaking. We go anyway."),
            0.3f, 0.6f,
        ),
        Persona(
            "hothead", "hot-headed and loud",
            listOf("You picked the WRONG road!", "Get 'em, %c! No mercy!"),
            listOf("That all you've got?!", "Ugh! Hit me again, I dare you!"),
            listOf("And STAY down!", "Who's next?!"),
            listOf("%n! You're mine now! In a good way!", "Ha! Got you, %n!"),
            listOf("I'm not tired. You're tired.", "Tomorrow I'm fighting the biggest thing out there."),
            listOf("Finally, a plan I like!", "Yeah! Let's go!"),
            listOf("%b. I've been waiting for this all my life."),
            0.95f, 0.5f,
        ),
    )

    fun persona(key: String): Persona = PERSONAS.firstOrNull { it.key == key } ?: PERSONAS[0]

    val MOTIVES = listOf("sibling", "elder", "prove", "dream")

    fun motiveText(h: Hero): String = when (h.motive) {
        "sibling" -> "to find ${h.kin}, who walked into a rift two winters ago and never came back"
        "elder" -> "to carry ${h.elder}'s warning to the Rift Citadel before the rifts swallow the valley"
        "prove" -> "to prove the village wrong. Everyone said a ${h.job.lowercase()}'s child would never have the gift"
        else -> "to see the world past the valley, and every critter in it"
    }

    fun fill(s: String, h: Hero, crit: String = "", newName: String = "", species: String = "", boss: String = ""): String =
        s.replace("%c", crit.ifBlank { "friend" }).replace("%n", newName).replace("%s", species).replace("%b", boss.ifBlank { "this" })
            .replace("%h", h.name)

    fun pick(r: Random, l: List<String>): String = l[r.nextInt(l.size)]

    // ---- the story, land by land ----

    fun prologue(h: Hero): List<Line> = listOf(
        Line("narrator", "", "In Summoner's Village, every child stands before the rift gate the year they come of age. Most walk away with nothing."),
        Line("narrator", "", "${h.name}, the ${h.job.lowercase()}'s child, is ${h.persona.trait}. Today is ${h.their} gift test."),
        Line("npc", h.elder, "Step up to the gate, ${h.name}. Read the scroll. If the gift is in you, something will answer."),
    )

    fun voiceIntro(h: Hero, crit: Critter): List<Line> = listOf(
        Line("narrator", "", "The gate flares violet. A ${crit.species.name} tumbles out of the rift and blinks up at ${h.name}."),
        Line("hero", h.name, "It... answered. It answered ME."),
        Line("voice", "A Voice", "And so did I. I am the Voice beyond the rift. I will watch over you."),
        Line("hero", h.name, "A voice in my head and a ${crit.species.name} at my feet. I'll call you ${crit.nick}."),
        Line("npc", h.elder, "The gift is real. ${h.mentor} will want to see this."),
    )

    fun raid(h: Hero): List<Line> = listOf(
        Line("narrator", "", "A howl splits the evening. Rift wolves pour out of a tear in the air above the fields."),
        Line("npc", h.mentor, "${h.name}! Your ${h.crit} is the only summoned thing in the village tonight. Help us!"),
    )

    fun sendOff(h: Hero): List<Line> = listOf(
        Line("npc", h.elder, "Wolves out of thin air. The rifts are tearing wider every season. Someone must go east, to the Citadel where they begin."),
        Line("hero", h.name, "I'll go. I have to. ${motiveText(h).replaceFirstChar { it.uppercase() }}."),
        Line("npc", h.mentor, "Then take these: three potions, two summoning scrolls, and the road east through Whisperwood. Come back to us."),
        Line("voice", "A Voice", "Speak to me any time. I'll be watching the whole way."),
    )

    fun arrive(h: Hero, r: RegionDef): List<Line> {
        val l = ArrayList<Line>()
        l.add(Line("narrator", "", r.blurb))
        when (r.idx) {
            1 -> l.add(Line("hero", h.name, "So this is Whisperwood. ${h.crit}, stay close. Everyone says the trees remember faces."))
            2 -> l.add(Line("hero", h.name, "Ironpeak. The air is thin and it smells like a forge. Ogre country."))
            3 -> l.add(Line("hero", h.name, "Mirefen. Keep to the boards, ${h.crit}. Whatever that light is, don't follow it."))
            4 -> l.add(Line("hero", h.name, "Look up, ${h.crit}. The sky is torn. Something big is coming through."))
            5 -> l.add(Line("hero", h.name, "The ground is still warm. Ash on everything. ${h.rival} came this way."))
            6 -> l.add(Line("hero", h.name, "The Citadel. Every rift in the valley leads here. This is the end of the road."))
        }
        return l
    }

    fun townHello(h: Hero, r: RegionDef, keeper: String): List<Line> = listOf(
        Line("npc", keeper, "A summoner! In ${r.town}? Come in out of the road, there's bread and a bed at the inn."),
        Line("hero", h.name, "Thank you. We've walked a long way."),
    )

    val QUESTS = listOf(
        "bell" to "Bandits took the chapel bell. We can't call the village in from the fields without it.",
        "child" to "Little %k wandered off chasing a critter. If you see a child out there, bring them home.",
        "den" to "Something nests near the road and it grows bolder every night. Clear it out and we'll pay.",
        "relic" to "There's an old relic in the ruins. The rifts grow quiet near it. Fetch it and it's yours to keep.",
        "herbs" to "My husband's fever won't break. The moonleaf grows only out by the ruins.",
    )

    val KIDS = listOf("Tam", "Bea", "Olli", "Ness", "Rudi", "Mae")
    val KEEPERS = listOf("Innkeeper Dorrit", "Goodwife Alma", "Old Fennick", "Reeve Hollis", "Sister Mabry", "Trader Joss")

    fun questGive(h: Hero, giver: String, text: String): List<Line> = listOf(
        Line("npc", giver, text),
        Line("hero", h.name, when (h.persona.key) {
            "grumpy" -> "Fine. But I'm eating first."
            "hothead" -> "Say no more. We'll handle it!"
            "shy" -> "I... I'll try. We'll try."
            "curious" -> "Oh, a mystery! We'll take it."
            "kind" -> "Of course. That's what we're here for."
            else -> "Consider it done."
        }),
    )

    fun bossIntro(h: Hero, r: RegionDef, bossName: String): List<Line> = when (r.idx) {
        1 -> listOf(
            Line("boss", bossName, "A summoner brat with a pet. My lads said you were coming. Hand over the critter and the coin and you can walk."),
            Line("hero", h.name, Lore.fill(pick(Random(), h.persona.fear), h, h.crit, boss = bossName)),
        )
        2 -> listOf(
            Line("boss", bossName, "GORRUM SMELLS SUMMONER. GORRUM HATES RIFTS. RIFTS BRING YOU. SO GORRUM HATES YOU."),
            Line("hero", h.name, "We're trying to close them, you big lump!"),
        )
        3 -> listOf(
            Line("boss", bossName, "I let the rift into my pond and it made me beautiful. ${h.rival} promised it would make the whole world beautiful."),
            Line("hero", h.name, "${h.rival}? What does ${h.rival} have to do with this?"),
            Line("boss", bossName, "Everything, child. Now drown."),
        )
        4 -> listOf(
            Line("narrator", "", "The torn sky ripples. A drake of blue ice folds out of the rift, wings crackling with frost."),
            Line("hero", h.name, Lore.fill(pick(Random(), h.persona.fear), h, h.crit, boss = bossName)),
        )
        5 -> listOf(
            Line("rival", h.rival, "${h.name}. The gate-test kid who got lucky. I've opened more rifts than you've ever seen."),
            Line("rival", h.rival, "The Hollow King promised me the gift for everyone. No more tests. No more children walking away empty-handed."),
            Line("hero", h.name, "By tearing the world open? Look at this place!"),
            Line("rival", h.rival, "Then stop me."),
        )
        else -> listOf(
            Line("boss", bossName, "Little summoner. Every critter you have ever bound came through MY door."),
            Line("boss", bossName, "Kneel, and I will let you keep them."),
            Line("hero", h.name, Lore.fill(pick(Random(), h.persona.fear), h, h.crit, boss = bossName)),
        )
    }

    fun bossWin(h: Hero, r: RegionDef, bossName: String): List<Line> = when (r.idx) {
        1 -> listOf(
            Line("boss", bossName, "Enough! Enough... the rift-folk paid us to watch this road. Someone called ${h.rival}. Didn't say why."),
            Line("hero", h.name, "${h.rival}. Remember that name, ${h.crit}."),
        )
        2 -> listOf(
            Line("boss", bossName, "Gorrum... yields. Rifts hurt Gorrum's mountain too. Go. Close them."),
        )
        3 -> listOf(
            Line("narrator", "", "The witch sinks into the black water. The mist thins, and for the first time the stars show over Mirefen."),
            Line("hero", h.name, "${h.rival} is behind all of it. Then that's who we find."),
        )
        4 -> listOf(
            Line("narrator", "", "Hoarfang crashes into the snow. Above, the torn sky knits itself closed with a sound like cracking ice."),
        )
        5 -> listOf(
            Line("rival", h.rival, "I just... wanted everyone to have what you have."),
            Line("hero", h.name, if (h.motive == "sibling") "Then help me. ${h.kin} is in there, isn't ${h.kinThey}?" else "Then help me close it. Properly."),
            Line("rival", h.rival, "The Citadel is east. The King is waiting. I'm sorry, ${h.name}."),
        )
        else -> listOf(
            Line("narrator", "", "The Hollow King comes apart like smoke in wind. Every rift in the valley sighs shut at once."),
        )
    }

    fun ending(h: Hero): List<Line> {
        val l = ArrayList<Line>()
        when (h.motive) {
            "sibling" -> {
                l.add(Line("narrator", "", "In the silence where the King stood, someone is sitting up and rubbing their eyes."))
                l.add(Line("npc", h.kin, "${h.name}? You came all this way? You... you have the gift."))
                l.add(Line("hero", h.name, "I had help. Come on, ${h.kin}. Let's go home."))
            }
            "elder" -> l.add(Line("hero", h.name, "${h.elder}'s warning came too late for the Citadel, but not for the valley. We did it."))
            "prove" -> l.add(Line("hero", h.name, "Let them say a ${h.job.lowercase()}'s child can't summon. Let them say it now."))
            else -> l.add(Line("hero", h.name, "The whole valley, every critter in it, and one Citadel. Not bad for a first trip."))
        }
        l.add(Line("voice", "A Voice", "You never needed me, ${h.name}. But I'm glad I watched."))
        l.add(Line("narrator", "", "${h.name} walks home with ${h.crit} at ${h.their} heels. Summoner's Village will tell this story for a hundred years."))
        return l
    }

    fun death(h: Hero, where: String, by: String): List<Line> = listOf(
        Line("narrator", "", "${h.name} falls in $where, struck down by $by."),
        Line("hero", h.name, "Tell them... tell them I tried, ${h.crit}."),
        Line("voice", "A Voice", "Rest now. Another child will stand before the gate."),
    )

    fun campLine(h: Hero, c: Critter?, r: Random, regionName: String, wins: Int): Line {
        val crit = c?.nick ?: "friend"
        val options = ArrayList<String>()
        options.addAll(h.persona.camp.map { fill(it, h, crit) })
        if (c != null && c.bond >= 10) options.add("You trust me now, don't you, $crit? I can tell.")
        if (wins > 10) options.add("$wins fights since we left home. I stopped counting the scrapes.")
        options.add("$regionName at night. I wonder if anyone back home can see the same moon.")
        if (h.motive == "sibling") options.add("${h.kin} used to sing by fires like this. I'll find you. I swear it.")
        return Line("hero", h.name, options[r.nextInt(options.size)])
    }
}
