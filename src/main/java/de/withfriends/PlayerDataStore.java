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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

final class PlayerDataStore {
    private static final Type JSON_TYPE = new TypeToken<Map<String, PlayerRecord>>() { }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final Map<UUID, PlayerRecord> players = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessions = new ConcurrentHashMap<>();

    PlayerDataStore(Path file) {
        this.file = file;
    }

    void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Map<String, PlayerRecord> loaded = gson.fromJson(json, JSON_TYPE);
            if (loaded == null) {
                return;
            }
            loaded.forEach((uuid, data) -> {
                try {
                    players.put(UUID.fromString(uuid), data == null ? new PlayerRecord() : data);
                } catch (IllegalArgumentException ignored) {
                    // Ignore malformed UUID entries instead of preventing plugin startup.
                }
            });
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not read players.json", exception);
        }
    }

    void save() {
        try {
            Files.createDirectories(file.getParent());
            Map<String, PlayerRecord> serializable = new HashMap<>();
            players.forEach((uuid, data) -> serializable.put(uuid.toString(), data));
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, gson.toJson(serializable), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not save players.json", exception);
        }
    }

    void joined(Player player) {
        PlayerRecord data = data(player.getUniqueId());
        data.name = player.getName();
        data.lastJoin = Instant.now().toString();
        sessions.put(player.getUniqueId(), System.currentTimeMillis());
    }

    void left(Player player) {
        addSessionTime(player.getUniqueId());
        PlayerRecord data = data(player.getUniqueId());
        data.name = player.getName();
        data.lastSeen = Instant.now().toString();
    }

    void closeSessions() {
        sessions.keySet().forEach(this::addSessionTime);
    }

    int incrementDeaths(Player player) {
        PlayerRecord data = data(player.getUniqueId());
        data.name = player.getName();
        return ++data.deaths;
    }

    int deaths(UUID uuid) {
        return data(uuid).deaths;
    }

    long playtimeSeconds(UUID uuid) {
        PlayerRecord data = data(uuid);
        Long started = sessions.get(uuid);
        long activeSession = started == null ? 0 : Math.max(0, (System.currentTimeMillis() - started) / 1000);
        return data.playtimeSeconds + activeSession;
    }

    Optional<PlayerRecord> findByName(String name) {
        return players.values().stream().filter(data -> data.name != null && data.name.equalsIgnoreCase(name)).findFirst();
    }

    PlayerRecord data(UUID uuid) {
        return players.computeIfAbsent(uuid, ignored -> new PlayerRecord());
    }

    static final class PlayerRecord {
        String name = "Unknown";
        int deaths;
        long playtimeSeconds;
        String lastJoin;
        String lastSeen;
    }

    private void addSessionTime(UUID uuid) {
        Long started = sessions.remove(uuid);
        if (started != null) {
            data(uuid).playtimeSeconds += Math.max(0, (System.currentTimeMillis() - started) / 1000);
        }
    }
}
