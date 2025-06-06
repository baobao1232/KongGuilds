package king.kongguilds.Gui;

import king.kongguilds.Model.Guild;
import king.kongguilds.Model.Member;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GuildGUI {

    public static void displayGuildInfo(Player player, Guild guild, int page) {
        if (guild == null) {
            player.sendMessage(ChatColor.RED + "Guild không tồn tại hoặc chưa có GUI.");
            return;
        }

        Inventory gui = Bukkit.createInventory(
                null,
                36,
                ChatColor.GREEN + "Guild: " + guild.getName() +
                        ChatColor.YELLOW + " | Cấp: " + guild.getLevel() +
                        ChatColor.GRAY + " | Trang: " + page
        );


        List<Map.Entry<UUID, Member>> sortedMembers = guild.getSortedMembers();

        // Các slot hợp lệ để hiển thị member
        List<Integer> displaySlots = new ArrayList<>();
        for (int i = 0; i <= 25; i++) {
            if (i != 8 && i != 17 && i != 26) {
                displaySlots.add(i);
            }
        }

        int membersPerPage = displaySlots.size();
        int startIndex = page * membersPerPage;
        int endIndex = Math.min(startIndex + membersPerPage, sortedMembers.size());

        for (int i = startIndex, slotIndex = 0; i < endIndex; i++, slotIndex++) {
            Map.Entry<UUID, Member> memberEntry = sortedMembers.get(i);
            UUID memberId = memberEntry.getKey();
            Member info = memberEntry.getValue();

            ItemStack item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(memberId);
                meta.setOwningPlayer(offlinePlayer);
                meta.setDisplayName(ChatColor.YELLOW + offlinePlayer.getName());

                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Rank: " + info.getRank());
                lore.add(ChatColor.GRAY + "Ngày tham gia: " + info.getJoinDate());
                lore.add(ChatColor.GRAY + "Điểm: " + info.getScore());
                meta.setLore(lore);

                item.setItemMeta(meta);
            }

            gui.setItem(displaySlots.get(slotIndex), item);
        }

        // Slot 8: Close
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(ChatColor.RED + "Đóng GUI");
            closeItem.setItemMeta(closeMeta);
        }
        gui.setItem(8, closeItem);

        // Slot 17: Base
        ItemStack baseItem = new ItemStack(Material.BEACON);
        ItemMeta baseMeta = baseItem.getItemMeta();
        if (baseMeta != null) {
            baseMeta.setDisplayName(ChatColor.AQUA + "Thông tin căn cứ");
            baseMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Xem căn cứ của Guild"));
            baseItem.setItemMeta(baseMeta);
        }
        gui.setItem(17, baseItem);

        // Slot 26: Storage
        ItemStack storageItem = new ItemStack(Material.CHEST);
        ItemMeta storageMeta = storageItem.getItemMeta();
        if (storageMeta != null) {
            storageMeta.setDisplayName(ChatColor.GOLD + "Kho Guild");
            storageMeta.setLore(Collections.singletonList(ChatColor.GRAY + "Mở kho vật phẩm chung"));
            storageItem.setItemMeta(storageMeta);
        }
        gui.setItem(26, storageItem);

        // Slot 30: Back page
        if (page > 0) {
            ItemStack backItem = new ItemStack(Material.ARROW);
            ItemMeta backMeta = backItem.getItemMeta();
            if (backMeta != null) {
                backMeta.setDisplayName(ChatColor.YELLOW + "Trang trước");
                backItem.setItemMeta(backMeta);
            }
            gui.setItem(30, backItem);
        }

        // Slot 32: Next page
        if (endIndex < sortedMembers.size()) {
            ItemStack nextItem = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextItem.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(ChatColor.YELLOW + "Trang sau");
                nextItem.setItemMeta(nextMeta);
            }
            gui.setItem(32, nextItem);
        }

        Member selfInfo = guild.getMembers().get(player.getUniqueId());
        String rank = selfInfo != null ? selfInfo.getRank() : "";
        // Slot 34: Donate GUI
        ItemStack donate = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta donateMeta = donate.getItemMeta();
        donateMeta.setDisplayName("§aDonate vật phẩm");
        donate.setItemMeta(donateMeta);
        gui.setItem(34, donate);

        // Slot 35: Duyệt yêu cầu (nếu là Leader/Co-Leader)
        if (rank.equalsIgnoreCase("Leader") || rank.equalsIgnoreCase("Co-Leader")) {
            ItemStack approve = new ItemStack(Material.BOOK);
            ItemMeta m = approve.getItemMeta();
            m.setDisplayName("§bDuyệt yêu cầu vật phẩm");
            approve.setItemMeta(m);
            gui.setItem(35, approve);
        }


        player.openInventory(gui);
    }

    public static void openDonateGUI(Player player) {
        Inventory donate = Bukkit.createInventory(null, 27, "Donate vật phẩm");
        player.openInventory(donate);
    }


}
