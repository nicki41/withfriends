package de.withfriends;

import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

final class ConfigValues {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final FileConfiguration settings;
    private final FileConfiguration lang;

    private ConfigValues(FileConfiguration settings, FileConfiguration lang) {
        this.settings = settings;
        this.lang = lang;
    }

    static ConfigValues from(FileConfiguration settings, FileConfiguration lang) {
        return new ConfigValues(settings, lang);
    }

    boolean enabled(String feature) {
        return settings.getBoolean("features." + feature, true);
    }

    boolean joinMessageEnabled() {
        return settings.getBoolean("join.enabled", true);
    }

    boolean leaveMessageEnabled() {
        return settings.getBoolean("leave.enabled", true);
    }

    boolean nametagHeartsEnabled() {
        return enabled("nametag-hearts");
    }

    boolean tablistWorldPrefixEnabled() {
        return enabled("tablist-world-prefix");
    }

    boolean deathsInTablist() {
        String display = deathsDisplay();
        return enabled("deaths") && settings.getBoolean("deaths.enabled", true)
                && (display.equals("tablist") || display.equals("both"));
    }

    boolean deathsInScoreboard() {
        String display = deathsDisplay();
        return enabled("deaths") && settings.getBoolean("deaths.enabled", true)
                && (display.equals("scoreboard") || display.equals("both"));
    }

    int tablistUpdateIntervalSeconds() {
        return settings.getInt("tablist.update-interval-seconds", 5);
    }

    String heartsObjectiveName() {
        return settings.getString("nametag.hearts-objective-name", "wf_health");
    }

    Component heartsDisplayName() {
        return render(settings.getString("nametag.hearts-display-name", "<red>❤</red>"));
    }

    String deathsTablistObjectiveName() {
        return settings.getString("deaths.tablist-objective-name", "wf_deaths_tab");
    }

    String deathsScoreboardObjectiveName() {
        return settings.getString("deaths.scoreboard-objective-name", "wf_deaths_sb");
    }

    Component deathsDisplayName() {
        return render(lang.getString("deaths.display-name", "<gray>Deaths</gray>"));
    }

    String sleepMode() {
        return settings.getString("sleep.mode", "percent").toLowerCase(Locale.ROOT);
    }

    int sleepPercent() {
        return settings.getInt("sleep.percent", 50);
    }

    int fixedSleepers() {
        return settings.getInt("sleep.fixed-players", 1);
    }

    boolean ignoreSpectators() {
        return settings.getBoolean("sleep.ignore-spectators", true);
    }

    boolean clearWeather() {
        return settings.getBoolean("sleep.clear-weather", true);
    }

    boolean announceSleepProgress() {
        return settings.getBoolean("sleep.announce-progress", true);
    }

    boolean announceSleepSuccess() {
        return settings.getBoolean("sleep.announce-success", true);
    }

    String phantomResetMode() {
        return settings.getString("sleep.phantom-reset", "sleeping").toLowerCase(Locale.ROOT);
    }

    boolean sleepAnimationEnabled() {
        return settings.getBoolean("sleep.skip-animation.enabled", true);
    }

    boolean joinSummaryOnline() {
        return settings.getBoolean("join-summary.show-online", true);
    }

    boolean joinSummaryTime() {
        return settings.getBoolean("join-summary.show-time", true);
    }

    boolean joinSummaryDeaths() {
        return settings.getBoolean("join-summary.show-deaths", true);
    }

    int joinSummaryMaxNames() {
        return settings.getInt("join-summary.max-player-names", 20);
    }

    boolean deathShowCoordinates() {
        return settings.getBoolean("death-messages.show-coordinates", true);
    }

    boolean deathShowDimension() {
        return settings.getBoolean("death-messages.show-dimension", true);
    }

    boolean deathClickToCopy() {
        return settings.getBoolean("death-messages.click-to-copy", true);
    }

    boolean afkAnnouncements() {
        return settings.getBoolean("afk.announce", true);
    }

    int sleepAnimationParticles() {
        return settings.getInt("sleep.skip-animation.particles", 30);
    }

    int afkTimeoutSeconds() {
        return settings.getInt("afk.timeout-seconds", 300);
    }

    boolean afkIgnoredBySleep() {
        return settings.getBoolean("afk.ignore-for-sleep", true);
    }

    boolean afkDamageImmunity() {
        return settings.getBoolean("afk.damage-immunity", true);
    }

    boolean afkKnockbackImmunity() {
        return settings.getBoolean("afk.knockback-immunity", true);
    }

    boolean afkCollisionImmunity() {
        return settings.getBoolean("afk.collision-immunity", true);
    }

    Component afkSuffix() {
        return render(lang.getString("afk.tab-suffix", " <gray>[AFK]</gray>"));
    }

    boolean enderChestEditable() {
        return settings.getBoolean("enderchest.allow-edit", false);
    }

    boolean sitOnlyEmptyHand() {
        return settings.getBoolean("sitting.only-empty-hand", true);
    }

    String deathChestMode() {
        return settings.getString("death-chest.mode", "always").toLowerCase(Locale.ROOT);
    }

    int deathChestLavaRadius() {
        return settings.getInt("death-chest.lava-radius", 3);
    }

    String deathChestPersistence() {
        return settings.getString("death-chest.persistence", "permanent").toLowerCase(Locale.ROOT);
    }

    int deathChestExpireSeconds() {
        return settings.getInt("death-chest.expire-seconds", 600);
    }

    int deathChestWarnBeforeExpireSeconds() {
        return settings.getInt("death-chest.warn-before-expire-seconds", 60);
    }

    boolean deathChestOwnerOnly() {
        return settings.getBoolean("death-chest.owner-only", false);
    }

    int deathChestSearchRadius() {
        return settings.getInt("death-chest.search-radius", 4);
    }

    boolean deathChestRemoveWhenEmpty() {
        return settings.getBoolean("death-chest.remove-when-empty", true);
    }

    Component enderChestTitle(Player player) {
        return message("enderchest.title", "<dark_gray>Ender Chest: </dark_gray><white>{player}</white>", Map.of("player", player.getName()), false);
    }

    Component joinMessage(Player player) {
        return message("join.text", "<green>+ </green><white>{player}</white>", Map.of("player", player.getName()), false);
    }

    Component leaveMessage(Player player) {
        return message("leave.text", "<red>- </red><white>{player}</white>", Map.of("player", player.getName()), false);
    }

    /**
     * The colored, bracketed world tag (e.g. a green "[Overworld] ") used consistently everywhere a
     * world/dimension is shown: tab list, chat, /coords, /msg, and death (chest) messages. Returned
     * as raw, unparsed MiniMessage text so callers can splice it into other server-built templates.
     */
    String worldTagText(String worldKey, String rawWorldName) {
        String raw = settings.getString("world-prefixes." + worldKey,
                settings.getString("world-prefixes.custom", "<gray>[{world}]</gray> "));
        return replace(raw, Map.of("world", worldLabel(worldKey, rawWorldName)));
    }

    Component worldTag(String worldKey, String rawWorldName) {
        return render(worldTagText(worldKey, rawWorldName));
    }

    String chatFormat() {
        return settings.getString("chat.format", "{world}<white>{player}</white><dark_gray>:</dark_gray> ");
    }

    Component chatWorldPrefix(String worldKey, String rawWorldName) {
        if (!settings.getBoolean("chat.show-world", true)) {
            return Component.empty();
        }
        return worldTag(worldKey, rawWorldName);
    }

    Component chatHover(Map<String, String> placeholders) {
        return message("chat.player-hover", "<gray>Deaths:</gray> <white>{deaths}</white><newline><gray>Playtime:</gray> <white>{playtime}</white>", placeholders, false);
    }

    /**
     * Translated display name for a known world key ("overworld"/"nether"/"end"); falls back to the
     * raw Bukkit world name for anything else so custom/extra worlds still get a sensible label.
     */
    String worldLabel(String worldKey, String rawWorldName) {
        return lang.getString("worlds." + worldKey, rawWorldName);
    }

    Component format(String input) {
        return render(input);
    }

    Component message(String path, String fallback, Map<String, String> placeholders, boolean withPrefix) {
        Component result = render(replace(lang.getString(path, fallback), placeholders));
        return withPrefix ? prefix().append(result) : result;
    }

    Component message(String path, String fallback, Map<String, String> placeholders) {
        return message(path, fallback, placeholders, true);
    }

    private Component prefix() {
        return render(lang.getString("prefix", ""));
    }

    private String deathsDisplay() {
        return settings.getString("deaths.display", "tablist").toLowerCase(Locale.ROOT);
    }

    private String replace(String input, Map<String, String> placeholders) {
        String result = input == null ? "" : input;
        for (Map.Entry<String, String> placeholder : placeholders.entrySet()) {
            result = result.replace("{" + placeholder.getKey() + "}", placeholder.getValue());
        }
        return result;
    }

    private Component render(String input) {
        return MINI_MESSAGE.deserialize(input == null ? "" : input);
    }
}
