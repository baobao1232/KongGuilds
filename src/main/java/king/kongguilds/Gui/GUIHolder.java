package king.kongguilds.Gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GUIHolder implements InventoryHolder {

    private final GUIType type;
    private final int page;

    public GUIHolder(GUIType type, int page) {
        this.type = type;
        this.page = page;
    }

    public GUIType getType() {
        return type;
    }

    public int getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
