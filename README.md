<p align="center">
  <img src="https://github.com/nicki41/withfriends/raw/master/assets/withfriends-logo.svg" alt="withfriends logo" width="140">
</p>

**withfriends** is a lightweight and fully configurable quality-of-life plugin designed for small and community-focused Minecraft servers. It enhances Minecraft with useful social features, clear player information, a flexible sleep system, and many other convenient additions.

Every module can be enabled or disabled independently in the `config.yml`.

## ✨ Features

### Player Information

* Clean and customizable join and leave messages
* Personal join summary showing:

  * currently online players
  * the current in-game day and time
  * your death count
* Player health displayed above name tags
* World prefixes in the player list
* Death counts in the player list, scoreboard, or both
* Persistent tracking of playtime, deaths, and player activity

### Communication and Coordinates

* Formatted private messages using `/msg`
* View your own or another player's playtime with `/playtime`
* Check when a player was last online with `/seen`
* Broadcast your coordinates or send them privately
* Display the distance to another player in the same dimension
* Optional chat formatting with world prefixes
* Hover information for deaths and playtime
* Clickable coordinates in death messages with clipboard support

### AFK System

AFK players are automatically detected and can be:

* marked in the player list
* excluded from sleep votes
* protected from damage
* protected from knockback

### Flexible Sleep System

* Sleep voting based on a percentage or fixed player count
* Automatic exclusion of AFK players
* Thunderstorm skipping
* Phantom timer reset
* Particle and sound effects when the night is skipped

### Death Chests

* Dropped items are placed in a chest instead of scattering on death
* Configurable trigger: always create a chest, or only when lava is nearby
* Configurable persistence: permanent chests, or timed chests that expire with an advance warning
* Optional owner-only access so only the player who died (and admins) can open it
* Automatic double chests when an inventory doesn't fit into a single chest
* Chests are removed automatically once emptied

### Additional Quality-of-Life Features

* Sit on slabs and stairs with an empty hand
* Open your Ender Chest with `/enderchest` or `/ec`
* Configure the Ender Chest as read-only or editable
* Fully customizable text formatting using [MiniMessage](https://docs.advntr.dev/minimessage/format.html)

## 📋 Commands

| Command                   | Description                                          |
| ------------------------- | ---------------------------------------------------- |
| `/withfriends reload`     | Reloads the plugin configuration                     |
| `/withfriends status`     | Shows the online player count and active sleep mode  |
| `/playtime [player]`      | Shows your own or another player's playtime          |
| `/seen <player>`          | Shows when a player was last online                  |
| `/coords [player]`        | Broadcasts coordinates or sends them privately       |
| `/distance <player>`      | Shows the distance to a player in the same dimension |
| `/msg <player> <message>` | Sends a formatted private message                    |
| `/enderchest`             | Opens your Ender Chest                               |
| `/ec`                     | Shortcut for `/enderchest`                           |

The `/withfriends reload` and `/withfriends status` commands require the `withfriends.admin` permission, which is granted to server operators by default.

## ⚙️ Configuration

All features can be configured independently. The generated `config.yml` contains detailed comments and examples.

Available settings include:

* enabling or disabling individual modules
* death count display location
* death message visibility
* death chest trigger (always / lava-only) and persistence (permanent / timed)
* formatting for all plugin messages
* Ender Chest editing permissions
* percentage-based or fixed sleep voting
* AFK protection
* chat and player-list formatting

After making changes, reload the configuration with `/withfriends reload`.

## 📦 Installation

1. Download the latest JAR file.
2. Place it inside your server's `plugins` directory.
3. Start or restart the server.
4. Adjust `plugins/withfriends/config.yml` if needed.
5. Run `/withfriends reload` or restart the server again.

## ✅ Requirements

* **Minecraft:** Java Edition 26.2
* **Server software:** Paper
* **Java:** 25

## 💾 Player Data

Death counts, playtime, last join times, and last-seen timestamps are stored by UUID.

The data is saved in:

```text
plugins/withfriends/players.json
```

The human-readable JSON format keeps player data correctly assigned even when a player changes their username.

Active death chests (location, owner, and expiry time) are stored the same way in:

```text
plugins/withfriends/deathchests.json
```

so timed expiry and owner-only locks survive a server restart.

## ⚠️ Compatibility

Other plugins may already provide commands such as `/msg` or `/ec`. If a conflict occurs, use the namespaced command instead:

```text
/withfriends:msg
/withfriends:ec
```

## 💡 Feature Requests

New ideas and feature requests are always very welcome! If you have a suggestion for improving **withfriends**, feel free to [open an issue](https://github.com/nicki41/withfriends/issues) and share it.

## 🔗 Links

* [Source code on GitHub](https://github.com/nicki41/withfriends)
* [Report an issue or suggest a feature](https://github.com/nicki41/withfriends/issues)

## 📜 License

**withfriends** is free and open-source software distributed under the [GNU General Public License v3.0](https://github.com/nicki41/withfriends/blob/master/LICENSE).
