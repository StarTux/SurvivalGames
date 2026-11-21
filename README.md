# SurvivalGames

SurvivalGames is Cavetale’s last-player-standing minigame for Paper. When at least two players are in the lobby, they can vote on an arena. Each round runs in a temporary copy of the winning map.

Players start on two teams and have 45 seconds to loot before PvP begins. The locator bar shows nearby players in team colors. Eliminated players can spectate, and a teammate with a respawn totem can bring them back. Once only one team remains, its survivors turn on each other for the individual win. A shrinking border and sudden death bring long rounds to a close.

## Commands

`/sg` is short for `/survivalgames`. The admin permission defaults to server operators.

| Command | Permission | Description |
| --- | --- | --- |
| `/sg start <world>` | `survivalgames.admin` | Start a round on a specific Survival Games map. Use tab completion for available map paths. |
| `/sg stop` | `survivalgames.admin` | End the round and return arena players to the lobby. |
| `/sg pause [true/false]` | `survivalgames.admin` | Pause or resume automatic lobby voting. Omit the argument to check its status. |
| `/sg event [true/false]` | `survivalgames.admin` | Enable or disable event scoring and rewards. Omit the argument to check its status. |
| `/sg score add <player> <amount>` | `survivalgames.admin` | Adjust a player’s event score. Use a negative amount to subtract points. |
| `/sg score reward` | `survivalgames.admin` | Award medals and money from the event standings and create the participant kit. |
| `/sg reload` | `survivalgames.admin` | Reload the plugin configuration, custom items, and loot tables. |

## Server setup

SurvivalGames requires Paper and the Cavetale plugins **Core, Creative, Mytems, AFK, Title, Fam, Spawn, and Kit**. Register arenas as Survival Games maps in Creative so they appear in map voting.

In `plugins/SurvivalGames/`, edit `items.yml` to define custom items and `phases.yml` to change the starting kit and weighted chest loot. Apply changes with `/sg reload`.

Licensed under the [GNU General Public License v2](LICENSE).
