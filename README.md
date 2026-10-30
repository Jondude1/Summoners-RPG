# Summoners RPG

An Android RPG you watch. Each journey follows one random villager from [Summoner's Village](https://github.com/Jondude1/DeskGlass-Pokemon-Edition) on the day of their gift test. A critter answers through the rift gate, rift wolves hit the village that night, and the hero sets out east to find where the rifts begin. The AI makes every decision. You watch, and now and then you speak to them as the Voice.

When a journey ends, in victory or death, it goes into the Hall of Legends and another random villager steps up to the gate.

## The road

| Chapter | Land | Town | Boss |
|---|---|---|---|
| 0 | Summoner's Village | home | the rift wolf raid |
| 1 | Whisperwood | Mossford | Grisk the Brigand King |
| 2 | Ironpeak Pass | Cragmouth | Gorrum the Ogre Warlord |
| 3 | Mirefen | Reedholm | Sedge, the Drowned Witch |
| 4 | Frostreach | Whitecairn | Hoarfang the Rift Drake (drops a legend) |
| 5 | The Ashen Rift | Last Lantern | the rival summoner |
| 6 | The Rift Citadel | | The Hollow King |

Every run rolls a new hero: name, village trade (which decides the starter critter's type), personality (bold, kind, grumpy, curious, shy, hot-headed: it changes how they talk and how brave the AI plays), reason for leaving (a lost sibling, the elder's warning, proving the village wrong, or wanderlust) and a rival.

## What the AI does on its own

- Walks the road, finds each town, takes a job there (bandit camps, lost children, relics, herbs) and comes back for the reward.
- Fights in real time: the lead critter closes to its move's range and attacks with its own moves; the hero casts glyph bolts, heals the critter, drinks potions and flees when it is hopeless.
- Picks the critter with the best type matchup before a fight, swaps when one faints.
- Weakens wild critters and casts binding chains on ones worth keeping. Rift-touched critters (purple glow) attack on sight and can't be bound until beaten.
- Reads summoning scrolls at rift shrines, opens chests, explores ruins, shops for potions, sleeps at inns, camps at night.
- Trains until the party is ready for the boss, then marches in.
- Evolves critters when their trust is deep enough (18 for the first change, 40 for the second).

## Watching

- Pinch to zoom, drag to look around, double-tap (or ◎) to follow the hero again.
- Tap anyone to learn about them.
- **Dex**: your party, critters sent home, and all 62 species, seen and bound across every journey.
- **Log**: the hero's journal.
- **☰**: Hall of Legends, start a new journey, the road ahead, updates.
- ❚❚ / 1× 2× 4× 8× to pause or speed up.

## The Voice

Type an order in the box at the bottom, or open the chips with the rift orb. The hero follows it for about 90 seconds. Words it understands include attack, defend, flee, rest, bind, summon, explore, train, onward, town, swap, potion, sneak, bond and evolve (and many synonyms).

## Updates

Every push to `main` is built by GitHub Actions and published as a GitHub Release. The app checks this public repo's latest release and offers to install it. First install: download the APK from the latest release once and allow installs from your browser.

The signing key in `keystore/` is committed on purpose so each new APK installs over the last one. Do not replace it.

## Testing headless

`tools/simtest/run.sh [runs] [max-game-hours] [v] [first]` plays whole journeys with nobody watching and reports how far each hero got, including a save/load round trip.
