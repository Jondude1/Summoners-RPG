# Summoners RPG direction (Jon's standing requirements)

1. **A watched RPG.** Each journey follows one random villager from Summoner's Village from their gift test onward. The AI makes every decision; Jon watches, and can speak to the hero as the Voice. A finished journey (won or lost) goes into the Hall of Legends and a new random villager starts.
2. **Same spirit as Summoner's Village.** Critters are summoned and bound, never caught. One critter out at a time, party of six, trust is the evolution milestone, bound critters have personal names. Pinch to zoom, a Critterdex, and the Voice box that understands many order words.
3. **More story, more action than the village game.** Chapters, towns with jobs, bosses with dialogue, a rival, a reason for leaving home. Fights are real-time with visible moves, projectiles, hits and damage numbers.
4. **Story lines use real facts.** Names, critters, places and events in dialogue come from the actual run.
5. **Test the whole journey headless before shipping** (`tools/simtest/run.sh`). No hero may stall: every run must end in a win or a death. Aim for most heroes to win but deaths to happen in the later lands.
6. **The app updates itself** from GitHub Releases. CI builds every push to `main`; never leave `main` broken. Old saves must keep loading.
