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

import static king.kongguilds.Manager.GuildManager.DONATE_SLOTS;

public class GuildGUI {

    /* =========================
       MAIN GUILD INFO GUI
       ========================= */
    public static void openGuildInfoGUI(Player player, Guild guild, int page) {
        if (guild == null) {
            player.sendMessage(ChatColor.RED + "Guild không tồn tại.");
            return;
        }

        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.GUILD_INFO, page),
                36,
                ChatColor.GREEN + "Guild: " + guild.getName()
        );

        renderMemberList(gui, guild, page);
        renderCommonGuildMenu(gui, player, guild);

        player.openInventory(gui);
    }

    /* =========================
       DONATE GUI
       ========================= */
    public static void openDonateGUI(Player player, Guild guild) {
        Inventory donate = Bukkit.createInventory(
                new GUIHolder(GUIType.DONATE, 0),
                36,
                "Donate vật phẩm"
        );

        renderCommonGuildMenu(donate, player, guild);
        player.openInventory(donate);
    }

    /* =========================
       MEMBER LIST RENDER
       ========================= */
    private static void renderMemberList(Inventory gui, Guild guild, int page) {
        List<Map.Entry<UUID, Member>> members = guild.getSortedMembers();

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i <= 25; i++) {
            if (i != 8 && i != 17 && i != 26) {
                slots.add(i);
            }
        }

        int perPage = slots.size();
        int start = page * perPage;
        int end = Math.min(start + perPage, members.size());

        for (int i = start, idx = 0; i < end; i++, idx++) {
            Map.Entry<UUID, Member> entry = members.get(i);
            OfflinePlayer off = Bukkit.getOfflinePlayer(entry.getKey());
            Member info = entry.getValue();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(off);
                meta.setDisplayName("§e" + off.getName());
                meta.setLore(Arrays.asList(
                        "§7Rank: " + info.getRank(),
                        "§7Ngày tham gia: " + info.getJoinDate(),
                        "§7Điểm: " + info.getScore()
                ));
                head.setItemMeta(meta);
            }

            gui.setItem(slots.get(idx), head);
        }
    }

    /* =========================
       COMMON GUILD MENU
       ========================= */
    public static void renderCommonGuildMenu(
            Inventory gui,
            Player player,
            Guild guild
    ) {
        String closeName = "§cĐóng";

        if (gui.getHolder() instanceof GUIHolder holder) {
            if (holder.getType() != GUIType.GUILD_INFO) {
                closeName = "§eQuay về";
            }
        }

        gui.setItem(8, createItem(
                Material.BARRIER,
                closeName
        ));

        gui.setItem(17, createItem(
                Material.BEACON,
                "§bThông tin căn cứ",
                "§7Xem căn cứ của Guild"
        ));

        gui.setItem(26, createItem(
                Material.CHEST,
                "§6Kho Guild",
                "§7Kho vật phẩm chung"
        ));

        gui.setItem(34, createItem(
                Material.EMERALD_BLOCK,
                "§aDonate vật phẩm"
        ));

        Member self = guild.getMembers().get(player.getUniqueId());
        if (self != null &&
                (self.getRank().equalsIgnoreCase("Leader")
                        || self.getRank().equalsIgnoreCase("Co-Leader"))) {

            gui.setItem(35, createItem(
                    Material.BOOK,
                    "§bDuyệt yêu cầu vật phẩm"
            ));
        }
    }

    public static void openStorageGUI(Player player, Guild guild) {

        // ✅ INVENTORY THẬT – đã load base64 sẵn
        Inventory storage = guild.getSharedStorageInventory();

        // ✅ GUI HIỂN THỊ (36 slot)
        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.STORAGE, 0),
                36,
                "Kho Chung Guild " + guild.getName()
        );

        // ✅ CHỈ VẼ MENU (KHÔNG LIÊN QUAN BASE64)
        renderCommonGuildMenu(gui, player, guild);

        // ✅ COPY ITEM THẬT → GUI THEO SLOT MAP
        ItemStack[] realItems = storage.getContents();

        for (int i = 0; i < DONATE_SLOTS.length; i++) {
            ItemStack item = realItems[i];
            if (item == null || item.getType().isAir()) continue;

            gui.setItem(DONATE_SLOTS[i], item.clone());
        }

        player.openInventory(gui);
    }


    /* =========================
       ITEM HELPER
       ========================= */
    private static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
