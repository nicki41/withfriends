package de.withfriends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/**
 * Places a player's dropped items into a chest on death instead of scattering them on the ground.
 * Supports an "always"/"lava-only" trigger and a "permanent"/"timed" persistence mode, both configurable.
 */
final class DeathChestManager {
    private static final Type JSON_TYPE = new TypeToken<Map<String, ChestRecord>>() { }.getType();
    private static final BlockFace[] HORIZONTAL_FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final WithFriendsPlugin plugin;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<String, ChestRecord> records = new ConcurrentHashMap<>();
    private final Map<String, String> secondaryIndex = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> expiryTasks = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> warningTasks = new ConcurrentHashMap<>();

    DeathChestManager(WithFriendsPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("deathchests.json");
    }

    void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, ChestRecord> loaded = gson.fromJson(json, JSON_TYPE);
            if (loaded == null) {
                return;
            }
            long now = System.currentTimeMillis();
            loaded.forEach((key, record) -> {
                if (record == null || record.world == null) {
                    return;
                }
                records.put(key, record);
                if (record.x2 != Integer.MIN_VALUE) {
                    secondaryIndex.put(record.world + "," + record.x2 + "," + record.y2 + "," + record.z2, key);
                }
                if (record.expireAt > 0) {
                    long remaining = Math.max(0, record.expireAt - now);
                    Bukkit.getScheduler().runTask(plugin, () -> scheduleExpiry(key, remaining));
                }
            });
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().warning("Could not read deathchests.json: " + exception.getMessage());
        }
    }

    void save() {
        try {
            Files.createDirectories(file.getParent());
            Map<String, ChestRecord> serializable = new HashMap<>(records);
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(serializable), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save deathchests.json: " + exception.getMessage());
        }
    }

    void handleDeath(PlayerDeathEvent event) {
        if (event.getDrops().isEmpty()) {
            return;
        }
        ConfigValues values = plugin.configValues();
        Player player = event.getEntity();
        Location deathLocation = player.getLocation();
        if (values.deathChestMode().equals("lava-only") && !hasLavaNearby(deathLocation, values.deathChestLavaRadius())) {
            return;
        }

        List<ItemStack> items = new ArrayList<>(event.getDrops());
        boolean needsDouble = items.size() > 27;
        Placement placement = findPlacement(deathLocation, needsDouble, Math.max(1, values.deathChestSearchRadius()));
        if (placement == null) {
            player.sendMessage(values.message("messages.death-chest.no-space",
                    "<red>No free spot was found for a death chest - your items dropped instead.</red>", Map.of()));
            return;
        }

        event.getDrops().clear();
        Inventory inventory = placeChests(placement, horizontalFacing(player));
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            overflow.addAll(inventory.addItem(item).values());
        }
        Block primary = placement.primary();
        for (ItemStack item : overflow) {
            primary.getWorld().dropItemNaturally(primary.getLocation(), item);
        }

        registerChest(placement, player, values);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("coordinates", primary.getX() + " " + primary.getY() + " " + primary.getZ());
        placeholders.put("dimension", friendlyWorldName(primary.getWorld()));
        player.sendMessage(values.message("messages.death-chest.placed",
                "<gray>[</gray><gold>⛃</gold><gray>]</gray> <gray>Your items were placed in a chest at </gray><yellow>{coordinates}</yellow> <dark_gray>({dimension})</dark_gray>",
                placeholders, false));
    }

    void onInventoryOpen(InventoryOpenEvent event) {
        if (!plugin.configValues().deathChestOwnerOnly()) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        String key = trackedKeyFor(event.getInventory().getHolder());
        if (key == null) {
            return;
        }
        ChestRecord record = records.get(key);
        if (record == null || record.ownerId.equals(player.getUniqueId().toString())
                || player.hasPermission(WithFriendsPlugin.ADMIN_PERMISSION)) {
            return;
        }
        event.setCancelled(true);
        player.sendMessage(plugin.configValues().message("messages.death-chest.locked",
                "<red>This death chest belongs to </red><white>{player}</white><red> - you may not open it.</red>",
                Map.of("player", record.ownerName)));
    }

    void onInventoryClose(InventoryCloseEvent event) {
        if (!plugin.configValues().deathChestRemoveWhenEmpty()) {
            return;
        }
        String key = trackedKeyFor(event.getInventory().getHolder());
        if (key == null) {
            return;
        }
        ChestRecord record = records.get(key);
        if (record != null && isEmpty(event.getInventory())) {
            removeTrackedChest(key, record);
        }
    }

    private void registerChest(Placement placement, Player player, ConfigValues values) {
        ChestRecord record = new ChestRecord();
        Block primary = placement.primary();
        record.world = primary.getWorld().getName();
        record.x = primary.getX();
        record.y = primary.getY();
        record.z = primary.getZ();
        if (placement.secondary() != null) {
            Block secondary = placement.secondary();
            record.x2 = secondary.getX();
            record.y2 = secondary.getY();
            record.z2 = secondary.getZ();
        }
        record.ownerId = player.getUniqueId().toString();
        record.ownerName = player.getName();
        record.createdAt = System.currentTimeMillis();

        String key = key(primary.getLocation());
        boolean timed = values.deathChestPersistence().equals("timed");
        int expireSeconds = Math.max(1, values.deathChestExpireSeconds());
        record.expireAt = timed ? record.createdAt + expireSeconds * 1000L : 0L;

        records.put(key, record);
        if (placement.secondary() != null) {
            secondaryIndex.put(key(placement.secondary().getLocation()), key);
        }
        save();

        if (timed) {
            scheduleExpiry(key, expireSeconds * 1000L);
            int warnSeconds = values.deathChestWarnBeforeExpireSeconds();
            if (warnSeconds > 0 && warnSeconds < expireSeconds) {
                scheduleWarning(key, (expireSeconds - warnSeconds) * 1000L, warnSeconds);
            }
        }
    }

    private void scheduleExpiry(String key, long delayMillis) {
        if (!records.containsKey(key)) {
            return;
        }
        long ticks = Math.max(1, delayMillis / 50L);
        expiryTasks.put(key, Bukkit.getScheduler().runTaskLater(plugin, () -> expireChest(key), ticks));
    }

    private void scheduleWarning(String key, long delayMillis, int warnSeconds) {
        long ticks = Math.max(1, delayMillis / 50L);
        warningTasks.put(key, Bukkit.getScheduler().runTaskLater(plugin, () -> sendExpireWarning(key, warnSeconds), ticks));
    }

    private void sendExpireWarning(String key, int warnSeconds) {
        warningTasks.remove(key);
        ChestRecord record = records.get(key);
        Player owner = record == null ? null : Bukkit.getPlayer(UUID.fromString(record.ownerId));
        if (owner == null) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("coordinates", record.x + " " + record.y + " " + record.z);
        placeholders.put("seconds", String.valueOf(warnSeconds));
        owner.sendMessage(plugin.configValues().message("messages.death-chest.expire-warning",
                "<gray>[</gray><gold>⛃</gold><gray>]</gray> <gray>Your death chest at </gray><yellow>{coordinates}</yellow><gray> will expire in </gray><yellow>{seconds}s</yellow><gray>!</gray>",
                placeholders, false));
    }

    private void expireChest(String key) {
        ChestRecord record = records.remove(key);
        cancelTasks(key);
        if (record == null) {
            return;
        }
        World world = Bukkit.getWorld(record.world);
        if (world != null) {
            Block primary = world.getBlockAt(record.x, record.y, record.z);
            if (primary.getState() instanceof Chest chest) {
                for (ItemStack item : chest.getInventory().getContents()) {
                    if (item != null && item.getType() != Material.AIR) {
                        world.dropItemNaturally(primary.getLocation(), item);
                    }
                }
            }
            removeChestBlock(primary);
            if (record.x2 != Integer.MIN_VALUE) {
                Block secondary = world.getBlockAt(record.x2, record.y2, record.z2);
                removeChestBlock(secondary);
                secondaryIndex.remove(key(secondary.getLocation()));
            }
        }
        notifyOwnerExpired(record);
        save();
    }

    private void notifyOwnerExpired(ChestRecord record) {
        Player owner = Bukkit.getPlayer(UUID.fromString(record.ownerId));
        if (owner == null) {
            return;
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("coordinates", record.x + " " + record.y + " " + record.z);
        owner.sendMessage(plugin.configValues().message("messages.death-chest.expired",
                "<gray>[</gray><gold>⛃</gold><gray>]</gray> <gray>Your death chest at </gray><yellow>{coordinates}</yellow><gray> expired, its items dropped on the ground.</gray>",
                placeholders, false));
    }

    private void removeTrackedChest(String key, ChestRecord record) {
        records.remove(key);
        cancelTasks(key);
        World world = Bukkit.getWorld(record.world);
        if (world != null) {
            removeChestBlock(world.getBlockAt(record.x, record.y, record.z));
            if (record.x2 != Integer.MIN_VALUE) {
                Block secondary = world.getBlockAt(record.x2, record.y2, record.z2);
                removeChestBlock(secondary);
                secondaryIndex.remove(key(secondary.getLocation()));
            }
        }
        save();
    }

    private void cancelTasks(String key) {
        BukkitTask expiry = expiryTasks.remove(key);
        if (expiry != null) {
            expiry.cancel();
        }
        BukkitTask warning = warningTasks.remove(key);
        if (warning != null) {
            warning.cancel();
        }
    }

    private void removeChestBlock(Block block) {
        if (block.getType() == Material.CHEST) {
            block.setType(Material.AIR, false);
        }
    }

    private String trackedKeyFor(InventoryHolder holder) {
        for (Location location : holderLocations(holder)) {
            String key = key(location);
            if (records.containsKey(key)) {
                return key;
            }
            String primaryKey = secondaryIndex.get(key);
            if (primaryKey != null) {
                return primaryKey;
            }
        }
        return null;
    }

    private List<Location> holderLocations(InventoryHolder holder) {
        if (holder instanceof Chest chest) {
            return List.of(chest.getLocation());
        }
        if (holder instanceof DoubleChest doubleChest) {
            List<Location> locations = new ArrayList<>();
            if (doubleChest.getLeftSide() instanceof Chest left) {
                locations.add(left.getLocation());
            }
            if (doubleChest.getRightSide() instanceof Chest right) {
                locations.add(right.getLocation());
            }
            return locations;
        }
        return List.of();
    }

    private boolean isEmpty(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    private Placement findPlacement(Location deathLocation, boolean needsDouble, int radius) {
        Block origin = deathLocation.getBlock();
        List<Block> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    candidates.add(origin.getRelative(dx, dy, dz));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(block -> block.getLocation().distanceSquared(deathLocation)));

        if (needsDouble) {
            for (Block candidate : candidates) {
                if (!isReplaceable(candidate)) {
                    continue;
                }
                for (BlockFace face : HORIZONTAL_FACES) {
                    Block neighbor = candidate.getRelative(face);
                    if (isReplaceable(neighbor)) {
                        return new Placement(candidate, neighbor);
                    }
                }
            }
        }
        for (Block candidate : candidates) {
            if (isReplaceable(candidate)) {
                return new Placement(candidate, null);
            }
        }
        return null;
    }

    private boolean isReplaceable(Block block) {
        return block.getType().isAir() || block.isLiquid();
    }

    private boolean hasLavaNearby(Location location, int radius) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        int r = Math.max(0, radius);
        Block center = location.getBlock();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (center.getRelative(dx, dy, dz).getType() == Material.LAVA) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Inventory placeChests(Placement placement, BlockFace facing) {
        placeChestBlock(placement.primary(), facing);
        if (placement.secondary() != null) {
            placeChestBlock(placement.secondary(), facing);
        }
        return ((Chest) placement.primary().getState()).getInventory();
    }

    private void placeChestBlock(Block block, BlockFace facing) {
        block.setType(Material.CHEST, false);
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Chest chestData) {
            chestData.setFacing(facing);
            block.setBlockData(chestData, false);
        }
    }

    private BlockFace horizontalFacing(Player player) {
        float yaw = (player.getLocation().getYaw() % 360 + 360) % 360;
        if (yaw >= 45 && yaw < 135) {
            return BlockFace.WEST;
        }
        if (yaw >= 135 && yaw < 225) {
            return BlockFace.NORTH;
        }
        if (yaw >= 225 && yaw < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.SOUTH;
    }

    private String friendlyWorldName(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> "Nether";
            case THE_END -> "End";
            default -> "Overworld";
        };
    }

    private String key(Location location) {
        return location.getWorld().getName() + "," + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private record Placement(Block primary, Block secondary) {
    }

    static final class ChestRecord {
        String world;
        int x;
        int y;
        int z;
        int x2 = Integer.MIN_VALUE;
        int y2;
        int z2;
        String ownerId;
        String ownerName;
        long createdAt;
        long expireAt;
    }
}
