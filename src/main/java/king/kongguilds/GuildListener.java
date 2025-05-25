package king.kongguilds;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.*;

public class GuildListener implements Listener {

    private final GuildManager guildManager;

    public GuildListener(GuildManager guildManager) {
        this.guildManager = guildManager;
    }

    // ---------- KHO CHUNG ----------

    @EventHandler
    public void onSharedInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        Inventory inv = event.getInventory();
        if (inv.equals(guild.getSharedStorageInventory())) {
            UUID locker = guildManager.getGuildStorageLocker(guild.getName());

            if (locker != null && !locker.equals(player.getUniqueId())) {
                player.sendMessage("§cKho chung đang được thao tác bởi người khác, vui lòng chờ.");
                event.setCancelled(true);
                return;
            }

            guildManager.lockGuildStorage(guild.getName(), player.getUniqueId());
        }
    }

    @EventHandler
    public void onSharedInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        Inventory inv = event.getInventory();
        if (inv.equals(guild.getSharedStorageInventory())) {
            guildManager.handleSharedStorageClose(player);
        }
    }

    // ---------- MỞ GUI CHỈNH THÀNH VIÊN ----------

    @EventHandler
    public void onClickMemberGui(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryView view = event.getView();
        String title = ChatColor.stripColor(view.getTitle());

        if (title.startsWith("Guild: ")) {
            event.setCancelled(true);

            Guild guild = guildManager.getPlayerGuild(player);
            if (guild == null) return;

            List<Map.Entry<UUID, Guild.MemberInfo>> sortedMembers = guild.getSortedMembers();
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= sortedMembers.size()) return;

            UUID clickedMemberId = sortedMembers.get(slot).getKey();
            Guild.MemberInfo clickedMember = sortedMembers.get(slot).getValue();

            UUID playerId = player.getUniqueId();
            boolean isLeader = guild.getLeader().equals(playerId);
            boolean isCoLeader = guild.getMembers().containsKey(playerId)
                    && guild.getMembers().get(playerId).getRankName().equalsIgnoreCase("CoLeader");

            if (isLeader) {
                GuildMemberEditGUI.openEditGUI(player, guild, clickedMemberId);
            } else if (isCoLeader) {
                String targetRank = clickedMember.getRankName();
                if (!targetRank.equalsIgnoreCase("Leader") && !targetRank.equalsIgnoreCase("CoLeader")) {
                    GuildMemberEditGUI.openEditGUI(player, guild, clickedMemberId);
                } else {
                    player.sendMessage(ChatColor.RED + "Bạn không có quyền chỉnh sửa thành viên này.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Bạn không có quyền chỉnh sửa thành viên.");
            }
        }
    }

    // ---------- XỬ LÝ CLICK TRONG GUI CHỈNH THÀNH VIÊN ----------

    @EventHandler
    public void onEditMemberGUI(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        String title = ChatColor.stripColor(event.getView().getTitle());
        if (!title.startsWith("EditMember:")) return;

        event.setCancelled(true); // Không cho move item

        UUID targetId;
        try {
            targetId = UUID.fromString(title.substring("EditMember:".length()));
        } catch (IllegalArgumentException e) {
            player.sendMessage(ChatColor.RED + "Không xác định được thành viên.");
            return;
        }

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null || !guild.hasMember(targetId)) {
            player.sendMessage(ChatColor.RED + "Thành viên không tồn tại trong guild.");
            return;
        }

        int slot = event.getRawSlot();

        if (slot == 11) {
            player.sendMessage("§a(TODO) Tăng rank cho " + guild.getMembers().get(targetId).getName());
            // TODO: Tăng rank ở đây
        } else if (slot == 15) {
            player.sendMessage("§c(TODO) Giảm rank cho " + guild.getMembers().get(targetId).getName());
            // TODO: Giảm rank ở đây
        } else if (slot == 22) {
            String name = guild.getMembers().get(targetId).getName();
            guild.kickMember(targetId);
            player.sendMessage("§cĐã kick " + name + " khỏi guild.");
            player.closeInventory();
        }
    }
}
