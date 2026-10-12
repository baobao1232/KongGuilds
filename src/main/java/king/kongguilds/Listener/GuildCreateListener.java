package king.kongguilds.Listener;

import king.kongguilds.Gui.GUIHolder;
import king.kongguilds.Gui.GUIType;
import king.kongguilds.Gui.GuildCreateGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class GuildCreateListener implements Listener {

    private final JavaPlugin plugin;

    public GuildCreateListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        GuildCreateGUI.Session s = GuildCreateGUI.getSession(p);
        if (s == null || !s.awaitingName) return;

        e.setCancelled(true);
        String msg = e.getMessage().trim();

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("huy")) {
                GuildCreateGUI.cancel(p, "§cĐã huỷ tạo guild.");
                return;
            }
            GuildCreateGUI.submitName(p, msg);
        });
    }

    @EventHandler
    public void onColorClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.CREATE_COLOR) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() >= e.getInventory().getSize()) return;

        GuildCreateGUI.handleColorClick(p, e.getRawSlot());
    }

    @EventHandler
    public void onIconClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.CREATE_ICON) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getRawSlot() >= e.getInventory().getSize()) return;

        GuildCreateGUI.handleIconClick(p, e.getRawSlot(), holder.getPage());
    }

    /** Đóng GUI bằng ESC thì huỷ session (trừ khi đang chuyển sang GUI tạo guild khác). */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.CREATE_COLOR && holder.getType() != GUIType.CREATE_ICON) return;
        if (!(e.getPlayer() instanceof Player p)) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            GuildCreateGUI.Session s = GuildCreateGUI.getSession(p);
            if (s == null || s.awaitingName) return;

            if (p.getOpenInventory().getTopInventory().getHolder() instanceof GUIHolder h
                    && (h.getType() == GUIType.CREATE_COLOR || h.getType() == GUIType.CREATE_ICON)) {
                return; // đang chuyển bước
            }
            GuildCreateGUI.removeSession(p);
            p.sendMessage("§cĐã huỷ tạo guild.");
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        GuildCreateGUI.removeSession(e.getPlayer());
    }
}