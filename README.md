<p align="center">
  <img src="assets/withfriends-logo.svg" width="140" alt="withfriends logo">
</p>

<h1 align="center">withfriends</h1>

<p align="center">A clean, configurable social-quality-of-life plugin for Paper 26.2 servers.</p>

## Features

- Clean join and leave messages plus a personal join summary with online players, in-game day/time, and deaths.
- Name-tag health hearts, world prefixes in the tab list, and JSON-backed deaths in the tab list, sidebar, or both.
- Configurable death messages with scope, dimension, clickable coordinates, and clipboard copying.
- AFK marking in the tab list, sleep-vote exclusion, damage immunity, and knockback immunity.
- `/playtime`, `/seen`, `/coords`, `/distance`, formatted `/msg`, and a configurable Ender Chest preview.
- Optional chat format with world prefix and hover details for deaths and playtime.
- Flexible sleep vote: percentage or fixed count, thunderstorm skipping, phantom reset, and a particle/sound skip effect.
- Sit on slabs and stairs with an empty hand.
- Death chests: dropped items are placed in a chest instead of scattering, with configurable trigger (always/lava-only), persistence (permanent/timed with an expiry warning), owner-only access, and automatic double chests for large inventories.

Every feature has an independent switch in `config.yml`.

## Requirements

- Paper `26.2`
- Java `25`

## Installation

1. Download `withfriends-1.0.1.jar` from the release or build it yourself.
2. Put the JAR into the server's `plugins` directory.
3. Start the server once. The configuration is created at `plugins/withfriends/config.yml`.
4. Adjust the configuration and run `/withfriends reload`, or restart the server.

## Commands

| Command | Description |
| --- | --- |
| `/withfriends reload` | Reload the plugin configuration. |
| `/withfriends status` | Show online player count and sleep mode. |
| `/playtime [player]` | Show your own or another player's playtime. |
| `/seen <player>` | Show when a player was last online. |
| `/coords [player]` | Broadcast your coordinates or send them privately. |
| `/distance <player>` | Show distance to a player in the same dimension. |
| `/msg <player> <message>` | Send a formatted private message. |
| `/enderchest` or `/ec` | Open your Ender Chest preview. |

`/withfriends reload` and `/withfriends status` require `withfriends.admin` (default: OP). Other commands use Paper's normal command permissions and can be disabled in the config.

## Configuration

The default [config.yml](src/main/resources/config.yml) is extensively commented and uses [MiniMessage](https://docs.advntr.dev/minimessage/format.html) for all text formatting.

Useful toggles:

- `features`: enable or disable each module independently.
- `deaths.display`: `tablist`, `scoreboard`, `both`, or `none`.
- `death-messages.scope`: `server` or `player`.
- `enderchest.allow-edit`: keep the Ender Chest read-only or allow editing.
- `sleep.mode`: `percent` or `fixed`.
- `death-chest.mode`: `always` or `lava-only`.
- `death-chest.persistence`: `permanent` or `timed`.

## Player data

Deaths, playtime, last join, and last seen timestamps are saved by UUID in `plugins/withfriends/players.json`. The file is human-readable JSON and survives name changes.

Active death chests (location, owner, and expiry time) are saved in `plugins/withfriends/deathchests.json` so timed expiry and owner-only locks survive a server restart.

## Building

```powershell
mvn package
```

The compiled plugin is written to `target/withfriends-1.0.1.jar`.

## Compatibility

Another plugin may already provide commands such as `/msg` or `/ec`. In that case, use the namespaced version such as `/withfriends:msg` or adjust the conflicting plugin.

## License

Distributed under the [GNU General Public License v3.0](LICENSE).
