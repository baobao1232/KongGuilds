package king.kongguilds.Gui;

import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Manager.ItemValueCalculator;
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

        Inventory storage = guild.getSharedStorageInventory();

        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.STORAGE, 0),
                36,
                "Kho Chung Guild " + guild.getName()
        );

        renderCommonGuildMenu(gui, player, guild);

        ItemStack[] realItems = storage.getContents();

        for (int i = 0; i < DONATE_SLOTS.length; i++) {
            ItemStack item = realItems[i];
            if (item == null || item.getType().isAir()) continue;

            // ✅ GẮN SCORE ẢO Ở ĐÂY
            gui.setItem(DONATE_SLOTS[i], applyScoreView(item));
        }

        player.openInventory(gui);
    }

    public static void openRequestItemGUI(Player player, Guild guild, ItemStack item) {

        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.REQUEST_ITEM, 0),
                27,
                "Xin vật phẩm"
        );

        ItemStack preview = item.clone();
        gui.setItem(13, preview);

        double score = ItemValueCalculator.calculateItemValue(item);

        gui.setItem(11, createItem(
                Material.PAPER,
                "§eChi phí",
                "§7Cần: §c" + score + " điểm"
        ));

        gui.setItem(15, createItem(
                Material.LIME_WOOL,
                "§aXác nhận xin"
        ));

        gui.setItem(16, createItem(
                Material.RED_WOOL,
                "§cHuỷ"
        ));

        player.openInventory(gui);
    }

    public static void openApproveRequestGUI(
            Player leader,
            Guild guild,
            GuildManager guildManager
    ) {
        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.APPROVE_REQUEST, 0),
                36,
                "Duyệt yêu cầu vật phẩm"
        );

        Map<String, List<UUID>> requests =
                guildManager.getItemRequestMap(guild.getName());

        int slot = 0;

        for (Map.Entry<String, List<UUID>> entry : requests.entrySet()) {
            String itemKey = entry.getKey();
            List<UUID> requesters = entry.getValue();

            ItemStack item = guildManager.getItemFromStorageByKey(guild, itemKey);
            if (item == null) continue;

            ItemStack view = item.clone();
            ItemMeta meta = view.getItemMeta();
            if (meta == null) continue;

            List<String> lore = meta.hasLore()
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();

            lore.add(" ");
            lore.add("§eNgười xin:");

            for (UUID uuid : requesters) {
                OfflinePlayer off = Bukkit.getOfflinePlayer(uuid);
                lore.add(" §7- §a" + off.getName());
            }

            lore.add(" ");
            lore.add("§aClick để duyệt (người đầu tiên)");

            meta.setLore(lore);
            view.setItemMeta(meta);

            gui.setItem(slot++, view);
        }

        leader.openInventory(gui);
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

    private static ItemStack applyScoreView(ItemStack original) {
        if (original == null || original.getType().isAir()) return original;

        ItemStack item = original.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        double score = ItemValueCalculator.calculateItemValue(item);

        List<String> lore = meta.hasLore()
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        lore.removeIf(s -> s != null && s.contains("điểm"));
        lore.add("§7Giá trị: §e" + score + " điểm");

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack stripAllGuildLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;

        ItemStack clean = item.clone();
        ItemMeta meta = clean.getItemMeta();
        if (meta == null || !meta.hasLore()) return clean;

        List<String> oldLore = meta.getLore();
        List<String> newLore = new ArrayList<>();

        for (String line : oldLore) {
            // gặp mốc → dừng luôn
            if (line != null && line.contains("Giá trị:")) {
                break;
            }
            newLore.add(line);
        }

        meta.setLore(newLore.isEmpty() ? null : newLore);
        clean.setItemMeta(meta);
        return clean;
    }

}
