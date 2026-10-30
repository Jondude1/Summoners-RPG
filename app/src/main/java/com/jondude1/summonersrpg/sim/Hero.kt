package com.jondude1.summonersrpg.sim

import java.util.Random

/** The villager whose journey this run follows. */
class Hero {
    var name = "Wren"
    var job = "Scout"
    var personaKey = "bold"
    var motive = "dream"
    var pronoun = 2 // 0 she, 1 he, 2 they
    var kin = ""
    var kinPronoun = 2
    var elder = "Elder Hesk"
    var mentor = "Aunt Rosa"
    var rival = "Morwen"
    var rivalLine = 22
    var hair = 0
    var shirt = 0
    var skin = 0

    var level = 1
    var xp = 0
    var hp = 60f
    var maxHp = 60f
    var mana = 30f
    var maxMana = 30f
    var gold = 20
    var potions = 3
    var ethers = 1
    var scrolls = 2

    var x = 0f
    var y = 0f
    var facing = 1
    var walk = 0f
    var moving = false
    var cast = 0f
    var hurt = 0f

    var wins = 0
    var binds = 0
    var days = 1
    var tiles = 0f

    /** The lead critter's name, kept here so story lines can use it. */
    var crit = "friend"

    val persona: Persona get() = Lore.persona(personaKey)
    val their: String get() = when (pronoun) { 0 -> "her"; 1 -> "his"; else -> "their" }
    val they: String get() = when (pronoun) { 0 -> "she"; 1 -> "he"; else -> "they" }
    val kinThey: String get() = when (kinPronoun) { 0 -> "she"; 1 -> "he"; else -> "they" }
    val def: Int get() = 8 + level * 2
    val xpNeed: Int get() = 18 + level * level * 2

    companion object {
        fun random(r: Random): Hero {
            val h = Hero()
            h.name = Lore.NAMES[r.nextInt(Lore.NAMES.size)]
            h.job = Lore.JOBS[r.nextInt(Lore.JOBS.size)]
            h.personaKey = Lore.PERSONAS[r.nextInt(Lore.PERSONAS.size)].key
            h.motive = Lore.MOTIVES[r.nextInt(Lore.MOTIVES.size)]
            h.pronoun = r.nextInt(3)
            h.kin = Lore.NAMES.filter { it != h.name }[r.nextInt(Lore.NAMES.size - 1)]
            h.kinPronoun = r.nextInt(3)
            h.elder = Lore.ELDERS[r.nextInt(Lore.ELDERS.size)]
            h.mentor = Lore.MENTORS[r.nextInt(Lore.MENTORS.size)]
            h.rival = Lore.RIVALS[r.nextInt(Lore.RIVALS.size)]
            // the rival summons from a dark line
            h.rivalLine = listOf(22, 28, 34, 47, 10, 1)[r.nextInt(6)]
            h.hair = r.nextInt(8)
            h.shirt = r.nextInt(10)
            h.skin = r.nextInt(4)
            // a trade shapes the body: smiths and miners are sturdier, storytellers and scouts carry more mana
            when (h.job) {
                "Miner", "Blacksmith", "Bronzesmith", "Woodcutter" -> { h.maxHp = 70f; h.maxMana = 26f }
                "Storyteller", "Scout" -> { h.maxHp = 54f; h.maxMana = 38f }
                "Hunter" -> { h.maxHp = 62f; h.maxMana = 30f; h.potions = 4 }
                "Forager", "Farmer" -> { h.maxHp = 60f; h.maxMana = 32f; h.potions = 4 }
                else -> { h.maxHp = 62f; h.maxMana = 30f }
            }
            h.hp = h.maxHp
            h.mana = h.maxMana
            return h
        }
    }
}

/** One finished run, kept in the Hall of Legends. */
class Legend(
    val name: String,
    val job: String,
    val persona: String,
    val reached: String,
    val won: Boolean,
    val cause: String,
    val bound: Int,
    val best: String,
    val days: Int,
    val level: Int,
)
