package king.kongguilds;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.*;

public class GuildListener implements Listener {

    private final GuildManager guildManager;
    private final MessageManager messageManager;

    public GuildListener(GuildManager guildManager, MessageManager messManager) {
        this.guildManager = guildManager;
        this.messageManager = messManager;
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

        if (!title.startsWith("Guild: ")) return;

        event.setCancelled(true);

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        List<Map.Entry<UUID, Guild.MemberInfo>> sortedMembers = guild.getSortedMembers();
        int slot = event.getRawSlot();

        if (slot < 0 || slot >= sortedMembers.size()) return;

        UUID clickedMemberId = sortedMembers.get(slot).getKey();
        Guild.MemberInfo clickedMember = sortedMembers.get(slot).getValue();

        UUID playerId = player.getUniqueId();

        // ❌ Không cho click vào chính mình
        if (clickedMemberId.equals(playerId)) {
            player.sendMessage(messageManager.getMessage("member", "not_edit_yourself"));
            return;
        }

        Guild.MemberInfo selfInfo = guild.getMembers().get(playerId);
        if (selfInfo == null) return;

        Guild.RankType selfRank = selfInfo.getRank();
        Guild.RankType targetRank = clickedMember.getRank();

        // 🎖️ Leader có toàn quyền chỉnh sửa
        if (selfRank == Guild.RankType.LEADER) {
            GuildMemberEditGUI.openEditGUI(player, guild, clickedMemberId);
            return;
        }

        // 🛡️ Officer chỉ có thể chỉnh sửa Member
        if (selfRank == Guild.RankType.OFFICER) {
            if (targetRank == Guild.RankType.MEMBER) {
                GuildMemberEditGUI.openEditGUI(player, guild, clickedMemberId);
            } else {
                player.sendMessage(messageManager.getMessage("member", "cannot_edit_higher_rank"));
            }
            return;
        }

        // 🙅 Member không có quyền
        player.sendMessage(ChatColor.RED + "Bạn không có quyền chỉnh sửa thành viên.");
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
            String guildName = guild.getName();

            guildManager.kickMember(player, guildName, targetId); // dùng hàm mới có kiểm tra quyền

            player.closeInventory();
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (!guildManager.isInGuildChatMode(uuid)) return;

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        event.setCancelled(true); // huỷ chat bình thường
        String message = event.getMessage();
        guildManager.sendGuildMessage(guild, player, message);
    }

}
