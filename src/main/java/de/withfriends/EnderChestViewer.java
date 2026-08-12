package de.withfriends;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class EnderChestViewer implements InventoryHolder {
    private final UUID ownerId;

    EnderChestViewer(UUID ownerId) {
        this.ownerId = ownerId;
    }

    UUID ownerId() {
        return ownerId;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("This holder only identifies a viewing inventory.");
    }
}
