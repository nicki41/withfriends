package de.withfriends;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class EnderChestViewer implements InventoryHolder {
    private final UUID ownerId;
    private final boolean selfView;

    EnderChestViewer(UUID ownerId, boolean selfView) {
        this.ownerId = ownerId;
        this.selfView = selfView;
    }

    UUID ownerId() {
        return ownerId;
    }

    /**
     * True when the viewer is looking at their own Ender Chest. Chests belonging to someone else are
     * always opened read-only, regardless of the "allow-edit" setting.
     */
    boolean selfView() {
        return selfView;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("This holder only identifies a viewing inventory.");
    }
}
