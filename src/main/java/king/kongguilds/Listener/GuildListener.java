package king.kongguilds.Listener;

import king.kongguilds.Gui.GUIHolder;
import king.kongguilds.Gui.GUIType;
import king.kongguilds.Gui.GuildGUI;
import king.kongguilds.Gui.GuildMemberEditGUI;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Manager.ItemValueCalculator;
import king.kongguilds.Manager.MessageManager;
import king.kongguilds.Model.Guild;
import king.kongguilds.Model.Member;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class GuildListener implements Listener {

    private final GuildManager guildManager;
    private final MessageManager messageManager;
    private final JavaPlugin plugin;

    public GuildListener(JavaPlugin plugin, GuildManager guildManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.messageManager = messageManager;
    }

    /* =========================
       DONATE SLOT CHECK
       ========================= */
    private boolean isDonateSlot(int slot) {
        if (slot == 29) return false;
        return (slot >= 0 && slot <= 6)
                || (slot >= 9 && slot <= 15)
                || (slot >= 18 && slot <= 24)
                || (slot >= 27 && slot <= 33);
    }

    /* =========================
       GUILD INFO GUI
       ========================= */
    @EventHandler
    public void onGuildInfoClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.GUILD_INFO) return;

        event.setCancelled(true);

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        int slot = event.getRawSlot();
        int page = holder.getPage();

        if (slot == 8) {
            player.closeInventory();
            return;
        }

        if (slot == 26) {
            openStorage(player, guild);
            return;
        }

        if (slot == 30 && page > 0) {
            GuildGUI.openGuildInfoGUI(player, guild, page - 1);
            return;
        }

        if (slot == 32) {
            int perPage = 23;
            int maxPage = (int) Math.ceil((double) guild.getSortedMembers().size() / perPage);
            if (page + 1 < maxPage) {
                GuildGUI.openGuildInfoGUI(player, guild, page + 1);
            }
            return;
        }

        if (slot == 34) {
            GuildGUI.openDonateGUI(player, guild);
        }
    }

    /* =========================
       DONATE GUI CLICK
       ========================= */
    @EventHandler
    public void onDonateClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.DONATE) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        int rawSlot = event.getRawSlot();
        boolean top = rawSlot < event.getInventory().getSize();

        ItemStack current = event.getCurrentItem();

        if (!top) {
            if (current == null || current.getType().isAir()) return;

            double value = ItemValueCalculator.calculateItemValue(current);
            if (value <= 0 || Double.isNaN(value)) {
                player.sendMessage("§cItem này không donate được.");
                return;
            }

            int empty = findEmptyDonateSlot(event.getInventory());
            if (empty == -1) {
                player.sendMessage("§cDonate đã đầy.");
                return;
            }

            ItemStack clone = current.clone();
            int points = (int) (value * clone.getAmount());

            ItemMeta meta = clone.getItemMeta();
            List<String> lore = meta != null && meta.hasLore()
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            lore.add("§a+ " + points + " điểm Donate");
            if (meta != null) {
                meta.setLore(lore);
                clone.setItemMeta(meta);
            }

            event.getInventory().setItem(empty, clone);
            event.setCurrentItem(null);
            return;
        }

        if (rawSlot == 8) {
            GuildGUI.openGuildInfoGUI(player, guild, 0);
        }
        if (rawSlot == 25) {
            openStorage(player, guild);
        }
        if (rawSlot == 29) {
            player.closeInventory();
        }
    }

    private int findEmptyDonateSlot(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            if (isDonateSlot(i)) {
                ItemStack it = inv.getItem(i);
                if (it == null || it.getType().isAir()) return i;
            }
        }
        return -1;
    }

    /* =========================
       DONATE CLOSE
       ========================= */
    @EventHandler
    public void onDonateClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.DONATE) return;

        Player player = (Player) event.getPlayer();
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        int total = 0;
        Inventory donate = event.getInventory();
        Inventory storage = guild.getSharedStorageInventory();

        for (int i = 0; i < donate.getSize(); i++) {
            if (!isDonateSlot(i)) continue;

            ItemStack item = donate.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            double value = ItemValueCalculator.calculateItemValue(item) * 10;
            if (value <= 0) continue;

            total += (int) (value * item.getAmount());
            storage.addItem(item.clone());
        }

        if (total > 0) {
            guildManager.updateMemberPoints(guild.getName(), player.getUniqueId(), total);
            guild.saveSharedStorage();
            guildManager.addExpToGuild(player, total);
            player.sendMessage("§aDonate thành công +" + total + " điểm.");
        }
    }

    /* =========================
       STORAGE GUI CLOSE (FIX QUAN TRỌNG)
       ========================= */

    @EventHandler
    public void onClickStorageGui(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.STORAGE) return;

        e.setCancelled(true); // ❌ CẤM MOVE ITEM

        Player player = (Player) e.getWhoClicked();
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        int rawSlot = e.getRawSlot();

        // MENU
        if (rawSlot == 8) {
            GuildGUI.openGuildInfoGUI(player, guild, 0);
        }
        if (rawSlot == 25) {
            GuildGUI.openStorageGUI(player, guild);
        }
        if (rawSlot == 29) {
            player.closeInventory();
        }
    }

//
//    @EventHandler
//    public void onStorageClose(InventoryCloseEvent event) {
//        if (!(event.getInventory().getHolder() instanceof GUIHolder holder)) return;
//        if (holder.getType() != GUIType.STORAGE) return;
//
//        Player player = (Player) event.getPlayer();
//        Guild guild = guildManager.getPlayerGuild(player);
//        if (guild == null) return;
//
//        Inventory gui = event.getInventory();
//        Inventory storage = guild.getSharedStorageInventory();
//
//        for (int i = 0; i < 27; i++) {
//            storage.setItem(i, gui.getItem(18 + i));
//        }
//
//        guild.saveSharedStorage();
//    }

    /* =========================
       BASE CHEST → STORAGE GUI
       ========================= */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBaseChestClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) return;

        Player player = event.getPlayer();
        if (!guildManager.isPlayerInTheirOwnBase(player)) return;

        event.setCancelled(true);
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild != null) {
            openStorage(player, guild);
        }
    }

    /* =========================
       GUILD CHAT
       ========================= */
    @EventHandler
    public void onGuildChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!guildManager.isInGuildChatMode(player.getUniqueId())) return;

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        event.setCancelled(true);
        guildManager.sendGuildMessage(plugin, guild, player, event.getMessage());
    }

    /* =========================
       STORAGE OPEN HELPER
       ========================= */
    private void openStorage(Player player, Guild guild) {
        UUID locker = guildManager.getGuildStorageLocker(guild.getName());
        if (locker != null && !locker.equals(player.getUniqueId())) {
            player.sendMessage(messageManager.getMessage("member", "storage_locked"));
            return;
        }
        guildManager.lockGuildStorage(guild.getName(), player.getUniqueId());
        GuildGUI.openStorageGUI(player, guild);
    }
}
