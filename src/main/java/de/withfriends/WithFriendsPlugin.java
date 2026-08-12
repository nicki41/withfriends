package de.withfriends;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.RenderType;
import org.bukkit.scoreboard.Scoreboard;

public final class WithFriendsPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final String ADMIN_PERMISSION = "withfriends.admin";
    private static final DateTimeFormatter SEEN_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Map<UUID, Long> lastActivity = new HashMap<>();
    private final Map<UUID, ArmorStand> seats = new HashMap<>();
    private final Map<UUID, Boolean> afkPlayers = new HashMap<>();

    private ConfigValues configValues;
    private PlayerDataStore playerData;
    private Scoreboard scoreboard;
    private BukkitTask tablistTask;
    private BukkitTask afkTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        playerData = new PlayerDataStore(getDataFolder().toPath().resolve("players.json"));
        try {
            playerData.load();
        } catch (IllegalStateException exception) {
            getLogger().warning(exception.getMessage() + "; starting with an empty player cache.");
        }
        reloadWithFriends();

        Bukkit.getPluginManager().registerEvents(this, this);
        registerCommands();
        startAfkTask();
        getLogger().info("withfriends enabled.");
    }

    @Override
    public void onDisable() {
        if (tablistTask != null) {
            tablistTask.cancel();
        }
        if (afkTask != null) {
            afkTask.cancel();
        }
        seats.values().forEach(ArmorStand::remove);
        seats.clear();
        playerData.closeSessions();
        savePlayerData();
    }

    private void registerCommands() {
        for (String name : java.util.List.of("withfriends", "playtime", "seen", "coords", "distance", "msg", "enderchest")) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }
    }

    private void reloadWithFriends() {
        reloadConfig();
        configValues = ConfigValues.from(getConfig());
        setupScoreboard();
        restartTablistTask();
        updateAllTabNames();
        updateAllDeathScores();
    }

    private void setupScoreboard() {
        scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        if (configValues.nametagHeartsEnabled()) {
            Objective hearts = getOrCreateObjective(configValues.heartsObjectiveName(), Criteria.HEALTH,
                    configValues.heartsDisplayName(), RenderType.HEARTS);
            hearts.setDisplaySlot(DisplaySlot.BELOW_NAME);
        } else {
            hideObjective(configValues.heartsObjectiveName());
        }

        if (configValues.deathsInTablist()) {
            Objective deaths = getOrCreateObjective(configValues.deathsTablistObjectiveName(), Criteria.DUMMY,
                    configValues.deathsDisplayName(), RenderType.INTEGER);
            deaths.setDisplaySlot(DisplaySlot.PLAYER_LIST);
        } else {
            hideObjective(configValues.deathsTablistObjectiveName());
        }

        if (configValues.deathsInScoreboard()) {
            Objective deaths = getOrCreateObjective(configValues.deathsScoreboardObjectiveName(), Criteria.DUMMY,
                    configValues.deathsDisplayName(), RenderType.INTEGER);
            deaths.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            hideObjective(configValues.deathsScoreboardObjectiveName());
        }
    }

    private Objective getOrCreateObjective(String name, Criteria criteria, Component displayName, RenderType renderType) {
        Objective objective = scoreboard.getObjective(name);
        if (objective != null && !objective.getTrackedCriteria().equals(criteria)) {
            objective.unregister();
            objective = null;
        }
        if (objective != null) {
            objective.displayName(displayName);
            objective.setRenderType(renderType);
            return objective;
        }
        return scoreboard.registerNewObjective(name, criteria, displayName, renderType);
    }

    private void hideObjective(String name) {
        Objective objective = scoreboard.getObjective(name);
        if (objective != null) {
            objective.setDisplaySlot(null);
        }
    }

    private void updateAllDeathScores() {
        Bukkit.getOnlinePlayers().forEach(this::updateDeathScore);
    }

    private void updateDeathScore(Player player) {
        int deaths = playerData.deaths(player.getUniqueId());
        updateObjectiveScore(configValues.deathsTablistObjectiveName(), player.getName(), deaths);
        updateObjectiveScore(configValues.deathsScoreboardObjectiveName(), player.getName(), deaths);
    }

    private void updateObjectiveScore(String objectiveName, String playerName, int value) {
        Objective objective = scoreboard.getObjective(objectiveName);
        if (objective != null) {
            objective.getScore(playerName).setScore(value);
        }
    }

    private void restartTablistTask() {
        if (tablistTask != null) {
            tablistTask.cancel();
            tablistTask = null;
        }
        long intervalTicks = Math.max(1, configValues.tablistUpdateIntervalSeconds()) * 20L;
        tablistTask = Bukkit.getScheduler().runTaskTimer(this, this::updateAllTabNames, 20L, intervalTicks);
    }

    private void startAfkTask() {
        afkTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            cleanupSeats();
            if (!configValues.enabled("afk")) {
                return;
            }
            long timeoutMillis = Math.max(30, configValues.afkTimeoutSeconds()) * 1000L;
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!isAfk(player) && now - lastActivity.getOrDefault(player.getUniqueId(), now) >= timeoutMillis) {
                    setAfk(player, true);
                }
            }
        }, 20L, 20L);
    }

    private void updateAllTabNames() {
        Bukkit.getOnlinePlayers().forEach(this::updateTabName);
    }

    private void updateTabName(Player player) {
        Component name = Component.text(player.getName());
        if (configValues.tablistWorldPrefixEnabled()) {
            name = configValues.worldPrefix(worldKey(player.getWorld()), player.getWorld().getName()).append(name);
        }
        if (configValues.enabled("afk") && isAfk(player)) {
            name = name.append(configValues.afkSuffix());
        }
        player.playerListName(name);
    }

    private String worldKey(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "nether";
            case THE_END -> "end";
            default -> "overworld";
        };
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        playerData.joined(player);
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        updateDeathScore(player);
        if (configValues.joinMessageEnabled()) {
            event.joinMessage(configValues.joinMessage(player));
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            updateTabName(player);
            sendJoinSummary(player);
        }, 1L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (configValues.leaveMessageEnabled()) {
            event.quitMessage(configValues.leaveMessage(player));
        }
        removeSeat(player.getUniqueId());
        playerData.left(player);
        savePlayerData();
        lastActivity.remove(player.getUniqueId());
        afkPlayers.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, this::checkSleep, 1L);
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        updateTabName(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!configValues.enabled("deaths")) {
            return;
        }
        Player player = event.getEntity();
        int deaths = playerData.incrementDeaths(player);
        updateDeathScore(player);
        savePlayerData();

        if (!configValues.enabled("death-messages")) {
            return;
        }
        event.deathMessage(null);
        Map<String, String> placeholders = locationPlaceholders(player.getLocation());
        placeholders.put("player", player.getName());
        placeholders.put("deaths", String.valueOf(deaths));
        placeholders.put("location", deathLocationSuffix(placeholders));
        String messagePath = getConfig().getString("death-messages.scope", "server").equalsIgnoreCase("player")
                ? "messages.death.player" : "messages.death.server";
        Component message = configValues.message(messagePath,
                "<gray>[</gray><red>✖</red><gray>]</gray> <white>{player}</white><gray> died.</gray>{location}",
                placeholders, false)
                ;
        if (configValues.deathClickToCopy() && configValues.deathShowCoordinates()) {
            message = message.clickEvent(ClickEvent.copyToClipboard(placeholders.get("coordinates")))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy coordinates")));
        }
        if (getConfig().getString("death-messages.scope", "server").equalsIgnoreCase("player")) {
            player.sendMessage(message);
        } else {
            Bukkit.broadcast(message);
        }
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
            Bukkit.getScheduler().runTaskLater(this, this::checkSleep, 1L);
        }
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Bukkit.getScheduler().runTaskLater(this, this::checkSleep, 1L);
    }

    private void checkSleep() {
        if (!configValues.enabled("sleep")) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL || !isNightOrThunder(world)) {
                continue;
            }

            int total = 0;
            int sleeping = 0;
            for (Player player : world.getPlayers()) {
                if (!countsForSleep(player)) {
                    continue;
                }
                total++;
                if (player.isSleeping()) {
                    sleeping++;
                }
            }

            int required = requiredSleepers(total);
            if (total == 0 || required == 0 || sleeping == 0) {
                continue;
            }
            if (sleeping >= required) {
                skipNight(world);
                resetPhantoms(world);
                playSleepAnimation(world);
                if (configValues.announceSleepSuccess()) {
                    world.sendMessage(configValues.message("messages.sleep.skipped", "<gold>Good morning!</gold>", Map.of(), true));
                }
            } else if (configValues.announceSleepProgress()) {
                world.sendMessage(configValues.message("messages.sleep.progress",
                        "<yellow>{sleeping}/{required}</yellow> <gray>players are sleeping. </gray><yellow>{remaining}</yellow><gray> remaining.</gray>",
                        Map.of("sleeping", String.valueOf(sleeping), "required", String.valueOf(required),
                                "remaining", String.valueOf(Math.max(0, required - sleeping))), true));
            }
        }
    }

    private boolean countsForSleep(Player player) {
        if (player.isSleepingIgnored()) {
            return false;
        }
        if (configValues.afkIgnoredBySleep() && isAfk(player)) {
            return false;
        }
        return !configValues.ignoreSpectators() || player.getGameMode() != GameMode.SPECTATOR;
    }

    private int requiredSleepers(int totalPlayers) {
        if (totalPlayers <= 0) {
            return 0;
        }
        if (configValues.sleepMode().equals("fixed")) {
            return Math.max(1, Math.min(totalPlayers, configValues.fixedSleepers()));
        }
        int percent = Math.max(1, Math.min(100, configValues.sleepPercent()));
        return Math.max(1, (int) Math.ceil(totalPlayers * (percent / 100.0)));
    }

    private boolean isNightOrThunder(World world) {
        return world.getTime() >= 12542 || world.isThundering();
    }

    private void skipNight(World world) {
        if (world.getTime() >= 12542) {
            world.setTime(0);
        }
        if (configValues.clearWeather()) {
            world.setStorm(false);
            world.setThundering(false);
        }
    }

    private void resetPhantoms(World world) {
        String mode = configValues.phantomResetMode();
        if (mode.equals("none")) {
            return;
        }
        for (Player player : mode.equals("all") ? Bukkit.getOnlinePlayers() : world.getPlayers()) {
            if (mode.equals("sleeping") && !player.isSleeping()) {
                continue;
            }
            player.setStatistic(Statistic.TIME_SINCE_REST, 0);
        }
    }

    private void playSleepAnimation(World world) {
        if (!configValues.sleepAnimationEnabled()) {
            return;
        }
        int particles = Math.max(1, configValues.sleepAnimationParticles());
        for (Player player : world.getPlayers()) {
            if (!player.isSleeping()) {
                continue;
            }
            Location location = player.getLocation().add(0, 1, 0);
            world.spawnParticle(Particle.END_ROD, location, particles, 0.7, 0.5, 0.7, 0.03);
            player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.4F);
        }
    }

    @EventHandler
    public void onActivity(PlayerMoveEvent event) {
        if (!event.hasChangedPosition()) {
            return;
        }
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onActivity(PlayerInteractEvent event) {
        markActive(event.getPlayer());
        if (!configValues.enabled("sitting") || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        if (configValues.sitOnlyEmptyHand() && event.getItem() != null && event.getItem().getType() != Material.AIR) {
            return;
        }
        Block block = event.getClickedBlock();
        BlockData data = block.getBlockData();
        if (!(data instanceof Slab) && !(data instanceof Stairs) || event.getPlayer().isInsideVehicle()) {
            return;
        }
        event.setCancelled(true);
        sit(event.getPlayer(), block, data instanceof Slab);
    }

    @EventHandler
    public void onActivity(PlayerCommandPreprocessEvent event) {
        markActive(event.getPlayer());
    }

    @EventHandler
    public void onAfkDamage(EntityDamageEvent event) {
        if (configValues.enabled("afk") && configValues.afkDamageImmunity() && event.getEntity() instanceof Player player && isAfk(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAfkVelocity(PlayerVelocityEvent event) {
        if (configValues.enabled("afk") && configValues.afkKnockbackImmunity() && isAfk(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        if (!configValues.enabled("custom-chat")) {
            return;
        }
        Player player = event.getPlayer();
        Map<String, String> hover = Map.of(
                "deaths", String.valueOf(playerData.deaths(player.getUniqueId())),
                "playtime", formatDuration(playerData.playtimeSeconds(player.getUniqueId()))
        );
        Component name = Component.text(player.getName()).hoverEvent(HoverEvent.showText(configValues.chatHover(hover)));
        String format = configValues.chatFormat();
        Component world = configValues.chatWorldPrefix(worldKey(player.getWorld()), player.getWorld().getName());
        event.renderer((source, sourceDisplayName, message, viewer) -> chatComponent(format, world, name, message));
        Bukkit.getScheduler().runTask(this, () -> markActive(player));
    }

    private Component chatComponent(String format, Component world, Component playerName, Component message) {
        String marker = "{player}";
        int playerIndex = format.indexOf(marker);
        if (playerIndex < 0) {
            return renderChatSection(format, world).append(playerName).append(Component.text(": ")).append(message);
        }
        String before = format.substring(0, playerIndex);
        String after = format.substring(playerIndex + marker.length());
        return renderChatSection(before, world)
                .append(playerName)
                .append(renderChatSection(after, world))
                .append(message);
    }

    private Component renderChatSection(String section, Component world) {
        Component result = Component.empty();
        String[] segments = section.split("\\\\{world}", -1);
        for (int index = 0; index < segments.length; index++) {
            result = result.append(configValues.format(segments[index]));
            if (index < segments.length - 1) {
                result = result.append(world);
            }
        }
        return result;
    }

    private void markActive(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
        if (isAfk(player)) {
            setAfk(player, false);
        }
    }

    private boolean isAfk(Player player) {
        return afkPlayers.getOrDefault(player.getUniqueId(), false);
    }

    private void setAfk(Player player, boolean afk) {
        afkPlayers.put(player.getUniqueId(), afk);
        updateTabName(player);
        if (!configValues.afkAnnouncements()) {
            return;
        }
        String path = afk ? "messages.afk.now" : "messages.afk.back";
        String fallback = afk ? "<gray>{player} is now AFK.</gray>" : "<gray>{player} is no longer AFK.</gray>";
        Bukkit.broadcast(configValues.message(path, fallback, Map.of("player", player.getName()), false));
    }

    private void sit(Player player, Block block, boolean slab) {
        removeSeat(player.getUniqueId());
        Location location = block.getLocation().add(0.5, slab ? 0.15 : 0.05, 0.5);
        ArmorStand seat = block.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
        });
        seat.addPassenger(player);
        seats.put(player.getUniqueId(), seat);
    }

    private void cleanupSeats() {
        seats.entrySet().removeIf(entry -> {
            ArmorStand seat = entry.getValue();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !seat.isValid() || !seat.getPassengers().contains(player)) {
                seat.remove();
                return true;
            }
            return false;
        });
    }

    private void removeSeat(UUID playerId) {
        ArmorStand seat = seats.remove(playerId);
        if (seat != null) {
            seat.remove();
        }
    }

    @EventHandler
    public void onEnderChestClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EnderChestViewer && !configValues.enderChestEditable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnderChestDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof EnderChestViewer && !configValues.enderChestEditable()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEnderChestClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof EnderChestViewer viewer) || !configValues.enderChestEditable()) {
            return;
        }
        Player owner = Bukkit.getPlayer(viewer.ownerId());
        if (owner != null) {
            owner.getEnderChest().setContents(event.getInventory().getContents());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "withfriends" -> handleAdminCommand(sender, label, args);
            case "playtime" -> handlePlaytime(sender, args);
            case "seen" -> handleSeen(sender, args);
            case "coords" -> handleCoords(sender, args);
            case "distance" -> handleDistance(sender, args);
            case "msg" -> handleMessage(sender, args);
            case "enderchest" -> handleEnderChest(sender);
            default -> false;
        };
    }

    private boolean handleAdminCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(configValues.message("messages.no-permission", "<red>You do not have permission to do that.</red>", Map.of()));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(configValues.message("messages.status", "<gray>Online:</gray> <white>{online}</white> <gray>| Sleep mode:</gray> <white>{mode}</white>",
                    Map.of("online", String.valueOf(Bukkit.getOnlinePlayers().size()), "mode", configValues.sleepMode())));
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadWithFriends();
            sender.sendMessage(configValues.message("messages.reload", "<green>Config reloaded.</green>", Map.of()));
            return true;
        }
        sender.sendMessage(Component.text("Use /" + label + " reload or /" + label + " status."));
        return true;
    }

    private boolean handlePlaytime(CommandSender sender, String[] args) {
        if (!configValues.enabled("playtime-command")) {
            return commandDisabled(sender);
        }
        Player target = args.length == 0 && sender instanceof Player player ? player : findOnline(args.length == 0 ? "" : args[0]);
        if (target != null) {
            sender.sendMessage(configValues.message("messages.playtime", "<gray>{player}'s playtime:</gray> <white>{playtime}</white>",
                    Map.of("player", target.getName(), "playtime", formatDuration(playerData.playtimeSeconds(target.getUniqueId())))));
            return true;
        }
        PlayerDataStore.PlayerRecord data = args.length == 0 ? null : playerData.findByName(args[0]).orElse(null);
        if (data == null) {
            return playerNotFound(sender, args.length == 0 ? "player" : args[0]);
        }
        sender.sendMessage(configValues.message("messages.playtime", "<gray>{player}'s playtime:</gray> <white>{playtime}</white>",
                Map.of("player", data.name, "playtime", formatDuration(data.playtimeSeconds))));
        return true;
    }

    private boolean handleSeen(CommandSender sender, String[] args) {
        if (!configValues.enabled("seen-command")) {
            return commandDisabled(sender);
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /seen <player>"));
            return true;
        }
        Player online = findOnline(args[0]);
        if (online != null) {
            sender.sendMessage(configValues.message("messages.seen-online", "<white>{player}</white><gray> is online right now.</gray>", Map.of("player", online.getName())));
            return true;
        }
        PlayerDataStore.PlayerRecord data = playerData.findByName(args[0]).orElse(null);
        if (data == null) {
            return playerNotFound(sender, args[0]);
        }
        String lastSeen = data.lastSeen == null ? "unknown" : formatInstant(data.lastSeen);
        sender.sendMessage(configValues.message("messages.seen", "<white>{player}</white><gray> was last seen:</gray> <white>{last-seen}</white>",
                Map.of("player", data.name, "last-seen", lastSeen)));
        return true;
    }

    private boolean handleCoords(CommandSender sender, String[] args) {
        if (!configValues.enabled("coords-command")) {
            return commandDisabled(sender);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players."));
            return true;
        }
        Map<String, String> placeholders = locationPlaceholders(player.getLocation());
        placeholders.put("player", player.getName());
        Component coordinates = configValues.message("messages.coords", "<gray>[</gray><aqua>⌖</aqua><gray>]</gray> <white>{player}</white><gray>: </gray><yellow>{coordinates}</yellow> <dark_gray>({dimension})</dark_gray>",
                placeholders, false).clickEvent(ClickEvent.copyToClipboard(placeholders.get("coordinates")))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy coordinates")));
        if (args.length == 0) {
            Bukkit.broadcast(coordinates);
            return true;
        }
        Player target = findOnline(args[0]);
        if (target == null) {
            return playerNotFound(sender, args[0]);
        }
        target.sendMessage(coordinates);
        sender.sendMessage(configValues.message("messages.coords-sent", "<gray>Coordinates sent to </gray><white>{player}</white><gray>.</gray>",
                Map.of("player", target.getName())));
        return true;
    }

    private boolean handleDistance(CommandSender sender, String[] args) {
        if (!configValues.enabled("distance-command")) {
            return commandDisabled(sender);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players."));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(Component.text("Usage: /distance <player>"));
            return true;
        }
        Player target = findOnline(args[0]);
        if (target == null) {
            return playerNotFound(sender, args[0]);
        }
        if (!target.getWorld().equals(player.getWorld())) {
            sender.sendMessage(configValues.message("messages.distance-different-world", "<white>{player}</white><gray> is in another dimension.</gray>",
                    Map.of("player", target.getName())));
            return true;
        }
        int distance = (int) Math.round(player.getLocation().distance(target.getLocation()));
        sender.sendMessage(configValues.message("messages.distance", "<gray>Distance to </gray><white>{player}</white><gray>:</gray> <yellow>{distance} blocks</yellow>",
                Map.of("player", target.getName(), "distance", String.valueOf(distance))));
        return true;
    }

    private boolean handleMessage(CommandSender sender, String[] args) {
        if (!configValues.enabled("private-messages")) {
            return commandDisabled(sender);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /msg <player> <message>"));
            return true;
        }
        Player target = findOnline(args[0]);
        if (target == null) {
            return playerNotFound(sender, args[0]);
        }
        Component content = Component.text(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
        target.sendMessage(configValues.message("messages.msg-to", "<gray>[</gray><light_purple>msg</light_purple><gray>]</gray> <white>{player}</white><dark_gray>:</dark_gray> ",
                Map.of("player", player.getName()), false).append(content));
        player.sendMessage(configValues.message("messages.msg-from", "<gray>[</gray><light_purple>msg</light_purple><gray>]</gray> <gray>to </gray><white>{player}</white><dark_gray>:</dark_gray> ",
                Map.of("player", target.getName()), false).append(content));
        return true;
    }

    private boolean handleEnderChest(CommandSender sender) {
        if (!configValues.enabled("enderchest")) {
            return commandDisabled(sender);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players."));
            return true;
        }
        Inventory inventory = Bukkit.createInventory(new EnderChestViewer(player.getUniqueId()), player.getEnderChest().getSize(), configValues.enderChestTitle(player));
        inventory.setContents(player.getEnderChest().getContents());
        player.openInventory(inventory);
        return true;
    }

    private void sendJoinSummary(Player player) {
        if (!configValues.enabled("join-summary")) {
            return;
        }
        java.util.List<String> onlinePlayers = Bukkit.getOnlinePlayers().stream().map(Player::getName).sorted().toList();
        int maxNames = Math.max(0, configValues.joinSummaryMaxNames());
        String online = onlinePlayers.stream().limit(maxNames).reduce((left, right) -> left + ", " + right).orElse("-");
        if (onlinePlayers.size() > maxNames) {
            online += " +" + (onlinePlayers.size() - maxNames);
        }
        if (configValues.joinSummaryOnline()) {
            player.sendMessage(configValues.message("messages.join-summary.online", "<gray>Online </gray><white>{count}</white><gray>:</gray> <white>{players}</white>",
                    Map.of("count", String.valueOf(Bukkit.getOnlinePlayers().size()), "players", online)));
        }
        long time = player.getWorld().getTime();
        int hour = (int) ((time / 1000 + 6) % 24);
        int minute = (int) ((time % 1000) * 60 / 1000);
        long day = player.getWorld().getFullTime() / 24000 + 1;
        if (configValues.joinSummaryTime()) {
            player.sendMessage(configValues.message("messages.join-summary.time", "<gray>Day </gray><white>{day}</white><gray>, </gray><white>{time}</white>",
                    Map.of("day", String.valueOf(day), "time", String.format(Locale.ROOT, "%02d:%02d", hour, minute))));
        }
        if (configValues.joinSummaryDeaths()) {
            player.sendMessage(configValues.message("messages.join-summary.deaths", "<gray>Deaths:</gray> <white>{deaths}</white>",
                    Map.of("deaths", String.valueOf(playerData.deaths(player.getUniqueId())))));
        }
    }

    private Map<String, String> locationPlaceholders(Location location) {
        Map<String, String> values = new HashMap<>();
        values.put("coordinates", location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
        values.put("x", String.valueOf(location.getBlockX()));
        values.put("y", String.valueOf(location.getBlockY()));
        values.put("z", String.valueOf(location.getBlockZ()));
        values.put("dimension", friendlyWorldName(location.getWorld()));
        return values;
    }

    private String deathLocationSuffix(Map<String, String> placeholders) {
        String suffix = "";
        if (configValues.deathShowDimension()) {
            suffix += " <gray>in </gray><white>" + placeholders.get("dimension") + "</white>";
        }
        if (configValues.deathShowCoordinates()) {
            suffix += " <gray>at </gray><yellow>" + placeholders.get("coordinates") + "</yellow>";
        }
        return suffix;
    }

    private String friendlyWorldName(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Overworld";
        };
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    private String formatInstant(String instant) {
        try {
            return SEEN_FORMAT.format(Instant.parse(instant));
        } catch (RuntimeException ignored) {
            return instant;
        }
    }

    private Player findOnline(String name) {
        return Bukkit.getPlayerExact(name);
    }

    private boolean commandDisabled(CommandSender sender) {
        sender.sendMessage(configValues.message("messages.command-disabled", "<red>This feature is currently disabled.</red>", Map.of()));
        return true;
    }

    private boolean playerNotFound(CommandSender sender, String name) {
        sender.sendMessage(configValues.message("messages.player-not-found", "<red>Player </red><white>{player}</white><red> was not found.</red>", Map.of("player", name)));
        return true;
    }

    private void savePlayerData() {
        try {
            playerData.save();
        } catch (IllegalStateException exception) {
            getLogger().warning(exception.getMessage());
        }
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("withfriends")) {
            return args.length == 1 && sender.hasPermission(ADMIN_PERMISSION)
                    ? java.util.List.of("reload", "status").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList()
                    : java.util.List.of();
        }
        if (args.length == 1 && java.util.Set.of("playtime", "seen", "coords", "distance", "msg").contains(command.getName().toLowerCase(Locale.ROOT))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
        }
        return java.util.List.of();
    }
}
