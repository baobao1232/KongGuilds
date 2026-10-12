package king.kongguilds.Gui;

import king.kongguilds.Config.GuildIconConfig;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Model.Guild;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class GuildCreateGUI {

    public static class Session {
        public String name;
        public String colorCode = "a";
        public boolean awaitingName = false;
    }

    private record ColorOption(Material material, char code, String label) {}

    private static final ColorOption[] COLORS = {
            new ColorOption(Material.RED_WOOL, 'c', "Đỏ"),
            new ColorOption(Material.ORANGE_WOOL, '6', "Cam"),
            new ColorOption(Material.YELLOW_WOOL, 'e', "Vàng"),
            new ColorOption(Material.LIME_WOOL, 'a', "Xanh lá"),
            new ColorOption(Material.GREEN_WOOL, '2', "Xanh lá đậm"),
            new ColorOption(Material.LIGHT_BLUE_WOOL, 'b', "Xanh ngọc"),
            new ColorOption(Material.CYAN_WOOL, '3', "Cyan"),
            new ColorOption(Material.BLUE_WOOL, '9', "Xanh dương"),
            new ColorOption(Material.PURPLE_WOOL, '5', "Tím"),
            new ColorOption(Material.MAGENTA_WOOL, 'd', "Hồng"),
            new ColorOption(Material.WHITE_WOOL, 'f', "Trắng"),
            new ColorOption(Material.LIGHT_GRAY_WOOL, '7', "Xám"),
            new ColorOption(Material.GRAY_WOOL, '8', "Xám đậm"),
            new ColorOption(Material.BROWN_WOOL, '4', "Đỏ đậm")
    };
    private static final int[] COLOR_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private static final int[] ICON_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private static final Map<UUID, Session> sessions = new HashMap<>();

    /* =========================
       SESSION
       ========================= */
    public static Session getSession(Player p) { return sessions.get(p.getUniqueId()); }
    public static void removeSession(Player p) { sessions.remove(p.getUniqueId()); }

    public static void cancel(Player p, String message) {
        if (sessions.remove(p.getUniqueId()) != null && message != null) {
            p.sendMessage(message);
        }
        p.closeInventory();
    }

    /* =========================
       BƯỚC 1: NHẬP TÊN
       ========================= */
    public static void start(Player player, String presetName) {
        if (GuildManager.getInstance().getPlayerGuild(player) != null) {
            player.sendMessage("§cBạn đã có guild.");
            return;
        }

        Session s = new Session();
        sessions.put(player.getUniqueId(), s);

        if (presetName != null) {
            submitName(player, presetName);
            return;
        }

        askName(player, s);
    }

    private static void askName(Player player, Session s) {
        s.awaitingName = true;
        player.closeInventory();
        player.sendMessage("§a§l[TẠO GUILD] §eNhập tên guild vào chat §7(3-16 ký tự, chữ/số/_). Gõ §ccancel §7để huỷ.");
        player.sendTitle("§a§lTẠO GUILD", "§7Nhập tên guild vào chat", 5, 100, 10);
    }

    /** Gọi trên main thread. */
    public static void submitName(Player player, String raw) {
        Session s = getSession(player);
        if (s == null) return;

        String name = raw.trim();
        String error = validateName(name);
        if (error != null) {
            player.sendMessage(error);
            askName(player, s);
            return;
        }

        s.name = name;
        s.awaitingName = false;
        openColorGUI(player, s);
    }

    private static String validateName(String name) {
        if (name.length() < 3 || name.length() > 16) return "§cTên guild phải từ 3-16 ký tự.";
        if (!name.matches("[A-Za-z0-9_]+")) return "§cTên chỉ được chứa chữ, số và dấu _.";
        for (Guild g : GuildManager.getInstance().getAllGuilds()) {
            if (g.getName().equalsIgnoreCase(name)) return "§cTên guild đã tồn tại.";
        }
        return null;
    }

    /* =========================
       BƯỚC 2: CHỌN MÀU
       ========================= */
    public static void openColorGUI(Player player, Session s) {
        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.CREATE_COLOR, 0), 36, "§8Tạo Guild §7- §0Chọn màu");

        gui.setItem(4, item(Material.NAME_TAG, "§" + s.colorCode + "§l" + s.name,
                "§7Tên guild của bạn", "§7Chọn màu bên dưới"));

        for (int i = 0; i < COLORS.length; i++) {
            ColorOption c = COLORS[i];
            gui.setItem(COLOR_SLOTS[i], item(c.material(), "§" + c.code() + "§l" + c.label(),
                    "§7Xem trước: §" + c.code() + "§l" + s.name, "", "§eClick để chọn"));
        }

        player.openInventory(gui);
    }

    public static void handleColorClick(Player player, int slot) {
        Session s = getSession(player);
        if (s == null) { player.closeInventory(); return; }

        for (int i = 0; i < COLOR_SLOTS.length; i++) {
            if (COLOR_SLOTS[i] == slot) {
                s.colorCode = String.valueOf(COLORS[i].code());
                openIconGUI(player, s, 0);
                return;
            }
        }
    }

    /* =========================
       BƯỚC 3: CHỌN ICON
       ========================= */
    public static void openIconGUI(Player player, Session s, int page) {
        List<GuildIconConfig.Icon> icons = GuildIconConfig.getAll();

        // Chưa cấu hình icon nào thì tạo luôn
        if (icons.isEmpty()) {
            finish(player, s, null);
            return;
        }

        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.CREATE_ICON, page), 54, "§8Tạo Guild §7- §0Chọn icon");

        gui.setItem(4, item(Material.NAME_TAG, "§" + s.colorCode + "§l" + s.name,
                "§7Chọn icon cho guild"));

        int start = page * ICON_SLOTS.length;
        for (int i = 0; i < ICON_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= icons.size()) break;

            GuildIconConfig.Icon icon = icons.get(idx);
            ItemStack it = item(icon.material(),
                    "§" + s.colorCode + icon.glyph() + " §f" + icon.name(),
                    "§7Xem trước: " + icon.glyph() + " §" + s.colorCode + "§l" + s.name,
                    "", "§eClick để chọn và tạo guild");

            if (icon.customModelData() > 0) {
                ItemMeta m = it.getItemMeta();
                m.setCustomModelData(icon.customModelData());
                it.setItemMeta(m);
            }
            gui.setItem(ICON_SLOTS[i], it);
        }

        if (page > 0) gui.setItem(45, item(Material.ARROW, "§eTrang trước"));
        if ((page + 1) * ICON_SLOTS.length < icons.size()) gui.setItem(53, item(Material.ARROW, "§eTrang sau"));
        gui.setItem(47, item(Material.BARRIER, "§cQuay lại chọn màu"));
        gui.setItem(49, item(Material.GRAY_DYE, "§7Không dùng icon", "§8Tạo guild luôn"));

        player.openInventory(gui);
    }

    public static void handleIconClick(Player player, int slot, int page) {
        Session s = getSession(player);
        if (s == null) { player.closeInventory(); return; }

        List<GuildIconConfig.Icon> icons = GuildIconConfig.getAll();

        if (slot == 45 && page > 0) { openIconGUI(player, s, page - 1); return; }
        if (slot == 53 && (page + 1) * ICON_SLOTS.length < icons.size()) { openIconGUI(player, s, page + 1); return; }
        if (slot == 47) { openColorGUI(player, s); return; }
        if (slot == 49) { finish(player, s, null); return; }

        for (int i = 0; i < ICON_SLOTS.length; i++) {
            if (ICON_SLOTS[i] != slot) continue;
            int idx = page * ICON_SLOTS.length + i;
            if (idx < icons.size()) finish(player, s, icons.get(idx));
            return;
        }
    }

    /* =========================
       HOÀN TẤT
       ========================= */
    private static void finish(Player player, Session s, GuildIconConfig.Icon icon) {
        sessions.remove(player.getUniqueId());
        player.closeInventory();

        GuildManager.CreateResult result = GuildManager.getInstance().createGuild(
                player, s.name, s.colorCode,
                icon == null ? null : icon.id(),
                icon == null ? "" : icon.glyph());

        switch (result) {
            case SUCCESS -> player.sendTitle("§a§lTẠO GUILD THÀNH CÔNG",
                    (icon == null ? "" : icon.glyph() + " ") + "§" + s.colorCode + s.name, 5, 60, 10);
            case ALREADY_IN_GUILD -> player.sendMessage("§cBạn đã có guild.");
            case NAME_EXISTS -> player.sendMessage("§cTên guild đã tồn tại.");
            case INVALID_NAME -> player.sendMessage("§cTên guild không hợp lệ.");
        }
    }

    private static ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore.length > 0) m.setLore(Arrays.asList(lore));
            it.setItemMeta(m);
        }
        return it;
    }
}