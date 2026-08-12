package de.withfriends;

import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

final class ConfigValues {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final FileConfiguration config;

    private ConfigValues(FileConfiguration config) {
        this.config = config;
    }

    static ConfigValues from(FileConfiguration config) {
        return new ConfigValues(config);
    }

    boolean enabled(String feature) {
        return config.getBoolean("features." + feature, true);
    }

    boolean joinMessageEnabled() {
        return config.getBoolean("messages.join.enabled", true);
    }

    boolean leaveMessageEnabled() {
        return config.getBoolean("messages.leave.enabled", true);
    }

    boolean nametagHeartsEnabled() {
        return enabled("nametag-hearts");
    }

    boolean tablistWorldPrefixEnabled() {
        return enabled("tablist-world-prefix");
    }

    boolean deathsInTablist() {
        String display = deathsDisplay();
        return enabled("deaths") && config.getBoolean("deaths.enabled", true)
                && (display.equals("tablist") || display.equals("both"));
    }

    boolean deathsInScoreboard() {
        String display = deathsDisplay();
        return enabled("deaths") && config.getBoolean("deaths.enabled", true)
                && (display.equals("scoreboard") || display.equals("both"));
    }

    int tablistUpdateIntervalSeconds() {
        return config.getInt("tablist.update-interval-seconds", 5);
    }

    String heartsObjectiveName() {
        return config.getString("nametag.hearts-objective-name", "wf_health");
    }

    Component heartsDisplayName() {
        return render(config.getString("nametag.hearts-display-name", "<red>❤</red>"));
    }

    String deathsTablistObjectiveName() {
        return config.getString("deaths.tablist-objective-name", "wf_deaths_tab");
    }

    String deathsScoreboardObjectiveName() {
        return config.getString("deaths.scoreboard-objective-name", "wf_deaths_sb");
    }

    Component deathsDisplayName() {
        return render(config.getString("deaths.display-name", "<gray>Deaths</gray>"));
    }

    String sleepMode() {
        return config.getString("sleep.mode", "percent").toLowerCase(Locale.ROOT);
    }

    int sleepPercent() {
        return config.getInt("sleep.percent", 50);
    }

    int fixedSleepers() {
        return config.getInt("sleep.fixed-players", 1);
    }

    boolean ignoreSpectators() {
        return config.getBoolean("sleep.ignore-spectators", true);
    }

    boolean clearWeather() {
        return config.getBoolean("sleep.clear-weather", true);
    }

    boolean announceSleepProgress() {
        return config.getBoolean("sleep.announce-progress", true);
    }

    boolean announceSleepSuccess() {
        return config.getBoolean("sleep.announce-success", true);
    }

    String phantomResetMode() {
        return config.getString("sleep.phantom-reset", "sleeping").toLowerCase(Locale.ROOT);
    }

    boolean sleepAnimationEnabled() {
        return config.getBoolean("sleep.skip-animation.enabled", true);
    }

    boolean joinSummaryOnline() {
        return config.getBoolean("join-summary.show-online", true);
    }

    boolean joinSummaryTime() {
        return config.getBoolean("join-summary.show-time", true);
    }

    boolean joinSummaryDeaths() {
        return config.getBoolean("join-summary.show-deaths", true);
    }

    int joinSummaryMaxNames() {
        return config.getInt("join-summary.max-player-names", 20);
    }

    boolean deathShowCoordinates() {
        return config.getBoolean("death-messages.show-coordinates", true);
    }

    boolean deathShowDimension() {
        return config.getBoolean("death-messages.show-dimension", true);
    }

    boolean deathClickToCopy() {
        return config.getBoolean("death-messages.click-to-copy", true);
    }

    boolean afkAnnouncements() {
        return config.getBoolean("afk.announce", true);
    }

    int sleepAnimationParticles() {
        return config.getInt("sleep.skip-animation.particles", 30);
    }

    int afkTimeoutSeconds() {
        return config.getInt("afk.timeout-seconds", 300);
    }

    boolean afkIgnoredBySleep() {
        return config.getBoolean("afk.ignore-for-sleep", true);
    }

    boolean afkDamageImmunity() {
        return config.getBoolean("afk.damage-immunity", true);
    }

    boolean afkKnockbackImmunity() {
        return config.getBoolean("afk.knockback-immunity", true);
    }

    Component afkSuffix() {
        return render(config.getString("afk.tab-suffix", " <dark_gray>[AFK]</dark_gray>"));
    }

    boolean enderChestEditable() {
        return config.getBoolean("enderchest.allow-edit", false);
    }

    String deathChestMode() {
        return config.getString("death-chest.mode", "always").toLowerCase(Locale.ROOT);
    }

    int deathChestLavaRadius() {
        return config.getInt("death-chest.lava-radius", 3);
    }

    String deathChestPersistence() {
        return config.getString("death-chest.persistence", "permanent").toLowerCase(Locale.ROOT);
    }

    int deathChestExpireSeconds() {
        return config.getInt("death-chest.expire-seconds", 600);
    }

    int deathChestWarnBeforeExpireSeconds() {
        return config.getInt("death-chest.warn-before-expire-seconds", 60);
    }

    boolean deathChestOwnerOnly() {
        return config.getBoolean("death-chest.owner-only", false);
    }

    int deathChestSearchRadius() {
        return config.getInt("death-chest.search-radius", 4);
    }

    boolean deathChestRemoveWhenEmpty() {
        return config.getBoolean("death-chest.remove-when-empty", true);
    }

    boolean sitOnlyEmptyHand() {
        return config.getBoolean("sitting.only-empty-hand", true);
    }

    Component enderChestTitle(Player player) {
        return message("enderchest.title", "<dark_gray>Ender Chest: </dark_gray><white>{player}</white>", Map.of("player", player.getName()), false);
    }

    Component joinMessage(Player player) {
        return message("messages.join.text", "<green>+ </green><white>{player}</white>", Map.of("player", player.getName()), false);
    }

    Component leaveMessage(Player player) {
        return message("messages.leave.text", "<red>- </red><white>{player}</white>", Map.of("player", player.getName()), false);
    }

    Component worldPrefix(String worldKey, String worldName) {
        String raw = config.getString("tablist.world-prefixes." + worldKey,
                config.getString("tablist.world-prefixes.custom", "<gray>[{world}]</gray> "));
        return render(replace(raw, Map.of("world", worldName)));
    }

    String chatFormat() {
        return config.getString("chat.format", "{world}<white>{player}</white><dark_gray>:</dark_gray> ");
    }

    Component chatWorldPrefix(String worldKey, String worldName) {
        if (!config.getBoolean("chat.show-world", true)) {
            return Component.empty();
        }
        String raw = config.getString("chat.world-prefixes." + worldKey,
                config.getString("chat.world-prefixes.custom", "<gray>[{world}]</gray> "));
        return render(replace(raw, Map.of("world", worldName)));
    }

    Component chatHover(Map<String, String> placeholders) {
        return message("chat.player-hover", "<gray>Deaths:</gray> <white>{deaths}</white><newline><gray>Playtime:</gray> <white>{playtime}</white>", placeholders, false);
    }

    Component format(String input) {
        return render(input);
    }

    Component message(String path, String fallback, Map<String, String> placeholders, boolean withPrefix) {
        Component result = render(replace(config.getString(path, fallback), placeholders));
        return withPrefix ? prefix().append(result) : result;
    }

    Component message(String path, String fallback, Map<String, String> placeholders) {
        return message(path, fallback, placeholders, true);
    }

    private Component prefix() {
        return render(config.getString("messages.prefix", ""));
    }

    private String deathsDisplay() {
        return config.getString("deaths.display", "tablist").toLowerCase(Locale.ROOT);
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
