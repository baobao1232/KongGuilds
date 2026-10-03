package king.kongguilds.Gui;

import king.kongguilds.Config.GuildConfig;
import king.kongguilds.Main;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Manager.GuildSkillManager;
import king.kongguilds.Manager.ItemValueCalculator;
import king.kongguilds.Model.AuctionItem;
import king.kongguilds.Model.Guild;
import king.kongguilds.Model.Member;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

import static king.kongguilds.Manager.GuildManager.DONATE_SLOTS;

public class GuildGUI {

   /* =========================
   GUILD SKILL TREE
   ========================= */
    public static void openGuildSkillTree(
            Player player,
            Guild guild
    ) {
        if (guild == null) {
            player.sendMessage("§cBạn không thuộc Guild.");
            return;
        }

        GuiSkillTree.open(player, guild);
    }

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
       GUILD SKILL TREE
       ========================= */
    public static void openGuildSkillGUI(Player player, Guild guild) {
        if (guild == null) {
            player.sendMessage("§cBạn không thuộc Guild.");
            return;
        }

        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.GUILD_SKILLS, 0),
                54,
                "§5§lGuild Skill Tree"
        );

        gui.setItem(4, createItem(
                Material.NETHER_STAR,
                "§d§lĐIỂM CỐNG HIẾN",
                "§7Tổng đã đóng góp: §f" + guild.getTotalContribution(),
                "§7Đang có thể dùng: §a" + guild.getAvailableContribution(),
                "§7Đã dùng cho Skill: §e" + guild.getSpentContribution()
        ));

        gui.setItem(49, createItem(
                Material.BARRIER,
                "§cQuay về Guild"
        ));

        String[] branches = {"WAR", "FORTRESS", "ARCANE", "DEVELOPMENT"};
        Material[] branchMaterials = {
                Material.IRON_SWORD, Material.SHIELD, Material.ENCHANTING_TABLE, Material.OAK_SAPLING
        };
        int[] columns = {1, 3, 5, 7};

        for (int i = 0; i < branches.length; i++) {
            gui.setItem(columns[i], createItem(
                    branchMaterials[i],
                    branchName(branches[i]),
                    "§7Cấp Guild hiện tại: §a" + guild.getLevel()
            ));

            List<GuildSkillManager.Skill> skills =
                    GuildSkillManager.getBranch(branches[i]);

            for (int j = 0; j < skills.size(); j++) {
                int slot = (j + 1) * 9 + columns[i];
                gui.setItem(slot, createSkillItem(guild, skills.get(j)));
            }
        }

        player.openInventory(gui);
    }

    private static String branchName(String branch) {
        return switch (branch) {
            case "WAR" -> "§c§l⚔ WAR";
            case "FORTRESS" -> "§b§l🛡 FORTRESS";
            case "ARCANE" -> "§d§l🔮 ARCANE";
            default -> "§a§l🌲 DEVELOPMENT";
        };
    }

    private static ItemStack createSkillItem(
            Guild guild,
            GuildSkillManager.Skill skill
    ) {
        int level = guild.getSkillLevel(skill.getId());
        boolean max = level >= skill.getMaxLevel();
        boolean can = GuildSkillManager.canUpgrade(guild, skill.getId());
        long cost = GuildSkillManager.getUpgradeCost(guild, skill.getId());

        Material material = switch (skill.getBranch()) {
            case "WAR" -> Material.IRON_SWORD;
            case "FORTRESS" -> Material.SHIELD;
            case "ARCANE" -> Material.EMERALD;
            default -> Material.EXPERIENCE_BOTTLE;
        };

        String current = formatSkillValue(skill, level);
        String next = formatSkillValue(skill, Math.min(level + 1, skill.getMaxLevel()));

        List<String> lore = new ArrayList<>();
        lore.add(" ");
        lore.add("§7Cấp: §f" + level + " §8/ §f" + skill.getMaxLevel());
        lore.add("§7Hiện tại: §a" + current);

        if (!max) {
            lore.add("§7Cấp kế: §e" + next);
            lore.add("§7Chi phí: §6" + cost + " điểm");
            lore.add(" ");

            if (skill.getPrerequisite() != null
                    && guild.getSkillLevel(skill.getPrerequisite()) <= 0) {
                GuildSkillManager.Skill req = GuildSkillManager.get(skill.getPrerequisite());
                lore.add("§cCần mở khóa: §f" + (req == null ? skill.getPrerequisite() : req.getName()));
            } else if (guild.getLevel() < skill.getRow()) {
                lore.add("§cCần Guild Level " + skill.getRow());
            } else if (guild.getAvailableContribution() < cost) {
                lore.add("§cKhông đủ điểm cống hiến");
            } else {
                lore.add("§aClick để nâng cấp");
            }
        } else {
            lore.add(" ");
            lore.add("§6§lĐÃ ĐẠT CẤP TỐI ĐA");
        }

        ItemStack item = createItem(material, skill.getName(), lore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(Main.getInstance(), "guild_skill"),
                    PersistentDataType.STRING,
                    skill.getId()
            );
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String formatSkillValue(
            GuildSkillManager.Skill skill,
            int level
    ) {
        double value = skill.getPerLevel() * level;
        if (skill.isPercent()) return String.format(Locale.US, "+%.2f%%", value);
        if (Math.abs(value - Math.rint(value)) < 0.0001) return "+" + (long) value;
        return String.format(Locale.US, "+%.2f", value);
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

        // Đóng / quay về
        gui.setItem(8, createItem(
                Material.BARRIER,
                closeName
        ));

        // Thông tin căn cứ
        gui.setItem(
                17,
                createGuildStatsItem(guild)
        );

        // Skill Tree
        gui.setItem(16, createItem(
                Material.ENCHANTED_BOOK,
                "§d§lGuild Skill Tree",
                "§7Nâng cấp kỹ năng chung của Guild",
                "§7Dùng điểm cống hiến để nâng cấp"
        ));

        // Kho Guild
        gui.setItem(26, createItem(
                Material.CHEST,
                "§6Kho Guild",
                "§7Kho vật phẩm chung"
        ));

        // Đấu giá
        gui.setItem(25, createItem(
                Material.GOLD_INGOT,
                "§6Đấu giá Guild",
                "§7Xem các vật phẩm đang đấu giá",
                "§7và tham gia đấu giá"
        ));

        // Donate
        gui.setItem(34, createItem(
                Material.EMERALD_BLOCK,
                "§aDonate vật phẩm"
        ));

        // Duyệt yêu cầu
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
            if (item == null || item.getType() == Material.AIR) continue;

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

        // =========================
        // MENU GUILD
        // =========================
        renderCommonGuildMenu(gui, leader, guild);

        // =========================
        // LẤY DANH SÁCH YÊU CẦU
        // =========================
        Map<String, List<UUID>> requests =
                guildManager.getItemRequestMap(guild.getName());

        // Các slot dành riêng cho item yêu cầu.
        // Chừa các slot menu: 8, 17, 26, 34, 35
        int[] requestSlots = {
                0, 1, 2, 3, 4, 5, 6, 7,
                9, 10, 11, 12, 13, 14, 15, 16,
                18, 19, 20, 21, 22, 23, 24, 25,
                27, 28, 29, 30, 31, 32, 33
        };

        int slotIndex = 0;

        // =========================
        // HIỂN THỊ CÁC YÊU CẦU
        // =========================
        for (Map.Entry<String, List<UUID>> entry : requests.entrySet()) {

            // Hết chỗ hiển thị
            if (slotIndex >= requestSlots.length) {
                break;
            }

            String itemKey = entry.getKey();
            List<UUID> requesters = entry.getValue();

            // Lấy item thật từ kho Guild
            ItemStack item =
                    guildManager.getItemFromStorageByKey(guild, itemKey);

            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            // =========================
            // TẠO ITEM HIỂN THỊ
            // =========================
            ItemStack view = item.clone();

            ItemMeta meta = view.getItemMeta();
            if (meta == null) {
                continue;
            }

            List<String> lore = meta.hasLore()
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();

            lore.add(" ");
            lore.add("§eNgười xin:");

            // Danh sách người đang xin item
            if (requesters != null && !requesters.isEmpty()) {

                for (UUID uuid : requesters) {

                    OfflinePlayer off =
                            Bukkit.getOfflinePlayer(uuid);

                    String name = off.getName();

                    if (name == null) {
                        name = uuid.toString();
                    }

                    lore.add(" §7- §a" + name);
                }

            } else {
                lore.add(" §7- Không có người xin");
            }

            lore.add(" ");
            lore.add("§aClick để duyệt (người đầu tiên)");

            meta.setLore(lore);
            view.setItemMeta(meta);

            // =========================
            // ĐẶT ITEM VÀO SLOT
            // =========================
            gui.setItem(
                    requestSlots[slotIndex],
                    view
            );

            slotIndex++;
        }

        // =========================
        // MỞ GUI
        // =========================
        leader.openInventory(gui);
    }

    public static void openAuctionGUI(
            Player player,
            Guild guild,
            List<AuctionItem> auctions
    ) {
        if (guild == null) {
            player.sendMessage("§cBạn không thuộc Guild.");
            return;
        }

        Inventory gui = Bukkit.createInventory(
                new GUIHolder(GUIType.AUCTION, 0),
                36,
                "§6§lĐấu giá Guild"
        );

        renderCommonGuildMenu(gui, player, guild);

        int[] auctionSlots = {
                0, 1, 2, 3, 4, 5, 6, 7,
                9, 10, 11, 12, 13, 14, 15, 16,
                18, 19, 20, 21, 22, 23, 24,
                27, 28, 29, 30, 31, 32, 33
        };

        NamespacedKey auctionKey =
                new NamespacedKey(Main.getInstance(), "auction_id");

        int slotIndex = 0;

        for (AuctionItem auction : auctions) {

            if (slotIndex >= auctionSlots.length) {
                break;
            }

            if (auction == null) {
                continue;
            }

            ItemStack item = auction.getItem().clone();
            ItemMeta meta = item.getItemMeta();

            if (meta == null) {
                continue;
            }

            List<String> lore = meta.hasLore()
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();

            lore.add(" ");
            lore.add("§6§lĐẤU GIÁ");

            lore.add(
                    "§7Giá hiện tại: §e"
                            + auction.getHighestBid()
            );

            if (auction.getHighestBidder() != null) {

                OfflinePlayer bidder =
                        Bukkit.getOfflinePlayer(
                                auction.getHighestBidder()
                        );

                lore.add(
                        "§7Người giữ giá: §a"
                                + (bidder.getName() != null
                                ? bidder.getName()
                                : "Không rõ")
                );

            } else {

                lore.add("§7Người giữ giá: §cChưa có");
            }

            // =========================
            // THỜI GIAN
            // =========================

            lore.add(
                    "§7Thời gian còn lại: §e"
                            + formatAuctionTime(
                            auction.getRemainingMillis()
                    )
            );

            lore.add(" ");
            lore.add("§aClick để đặt giá");

            meta.setLore(lore);

            // =========================
            // GẮN AUCTION ID
            // =========================

            meta.getPersistentDataContainer().set(
                    auctionKey,
                    PersistentDataType.STRING,
                    auction.getId()
            );

            item.setItemMeta(meta);

            item.setAmount(
                    Math.min(
                            64,
                            Math.max(1, item.getAmount())
                    )
            );

            gui.setItem(
                    auctionSlots[slotIndex],
                    item
            );

            slotIndex++;
        }

        if (slotIndex == 0) {

            gui.setItem(
                    13,
                    createItem(
                            Material.BARRIER,
                            "§cKhông có vật phẩm đấu giá",
                            "§7Hiện tại Guild chưa có",
                            "§7vật phẩm nào đang đấu giá."
                    )
            );
        }

        player.openInventory(gui);

        // =========================
        // AUTO REFRESH MỖI GIÂY
        // =========================

        final int[] slots = auctionSlots;

        Bukkit.getScheduler().runTaskTimer(
                Main.getInstance(),
                () -> {

                    // Người chơi đã đóng GUI
                    if (!player.isOnline()
                            || player.getOpenInventory().getTopInventory() != gui) {

                        return;
                    }

                    for (int slot : slots) {

                        ItemStack current =
                                gui.getItem(slot);

                        if (current == null
                                || current.getType() == Material.AIR) {
                            continue;
                        }

                        ItemMeta currentMeta =
                                current.getItemMeta();

                        if (currentMeta == null) {
                            continue;
                        }

                        String auctionId =
                                currentMeta
                                        .getPersistentDataContainer()
                                        .get(
                                                auctionKey,
                                                PersistentDataType.STRING
                                        );

                        if (auctionId == null) {
                            continue;
                        }

                        AuctionItem auction =
                                Main.getInstance()
                                        .getAuctionManager()
                                        .getAuction(auctionId);

                        // Auction đã kết thúc / bị xóa
                        if (auction == null) {

                            gui.setItem(slot, null);
                            continue;
                        }

                        // =========================
                        // TẠO LẠI ITEM
                        // =========================

                        ItemStack updated =
                                auction.getItem().clone();

                        ItemMeta updatedMeta =
                                updated.getItemMeta();

                        if (updatedMeta == null) {
                            continue;
                        }

                        List<String> updatedLore =
                                updatedMeta.hasLore()
                                        ? new ArrayList<>(
                                        updatedMeta.getLore())
                                        : new ArrayList<>();

                        updatedLore.add(" ");
                        updatedLore.add("§6§lĐẤU GIÁ");

                        updatedLore.add(
                                "§7Giá hiện tại: §e"
                                        + auction.getHighestBid()
                        );

                        if (auction.getHighestBidder() != null) {

                            OfflinePlayer bidder =
                                    Bukkit.getOfflinePlayer(
                                            auction.getHighestBidder()
                                    );

                            updatedLore.add(
                                    "§7Người giữ giá: §a"
                                            + (bidder.getName() != null
                                            ? bidder.getName()
                                            : "Không rõ")
                            );

                        } else {

                            updatedLore.add(
                                    "§7Người giữ giá: §cChưa có"
                            );
                        }

                        // =========================
                        // COUNTDOWN
                        // =========================

                        long remaining =
                                auction.getRemainingMillis();

                        if (remaining <= 0) {

                            updatedLore.add(
                                    "§7Thời gian còn lại: §cĐã hết"
                            );

                        } else {

                            updatedLore.add(
                                    "§7Thời gian còn lại: §e"
                                            + formatAuctionTime(remaining)
                            );
                        }

                        updatedLore.add(" ");
                        updatedLore.add("§aClick để đặt giá");

                        updatedMeta.setLore(updatedLore);

                        updatedMeta
                                .getPersistentDataContainer()
                                .set(
                                        auctionKey,
                                        PersistentDataType.STRING,
                                        auction.getId()
                                );

                        updated.setItemMeta(updatedMeta);

                        updated.setAmount(
                                Math.min(
                                        64,
                                        Math.max(
                                                1,
                                                updated.getAmount()
                                        )
                                )
                        );

                        gui.setItem(slot, updated);
                    }

                },
                20L,   // delay 1 giây
                20L    // chạy mỗi 1 giây
        );
    }

    private static String formatAuctionTime(long millis) {

        long totalSeconds =
                Math.max(0, millis / 1000);

        long days = totalSeconds / 86400;

        totalSeconds %= 86400;

        long hours = totalSeconds / 3600;

        totalSeconds %= 3600;

        long minutes = totalSeconds / 60;

        long seconds = totalSeconds % 60;

        if (days > 0) {
            return days + "d "
                    + hours + "h "
                    + minutes + "m";
        }

        if (hours > 0) {
            return hours + "h "
                    + minutes + "m "
                    + seconds + "s";
        }

        return minutes + "m "
                + seconds + "s";
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
        if (original == null || original.getType() == Material.AIR) return original;

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

        if (meta == null || !meta.hasLore()) {
            return clean;
        }

        List<String> oldLore = meta.getLore();
        List<String> newLore = new ArrayList<>();

        for (String line : oldLore) {

            if (line == null) {
                continue;
            }

            String stripped =
                    ChatColor.stripColor(line);

            // Xóa lore hệ thống Guild
            if (stripped.contains("Giá trị:")) {
                continue;
            }

            if (stripped.contains("điểm Donate")) {
                continue;
            }

            if (stripped.contains("điểm Donate")) {
                continue;
            }

            newLore.add(line);
        }

        meta.setLore(
                newLore.isEmpty()
                        ? null
                        : newLore
        );

        clean.setItemMeta(meta);

        return clean;
    }

    private static ItemStack createGuildStatsItem(Guild guild) {

        int online = 0;

        for (UUID uuid : guild.getMembers().keySet()) {

            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                online++;
            }
        }

        OfflinePlayer leader =
                Bukkit.getOfflinePlayer(
                        guild.getLeader()
                );

        String leaderName =
                leader.getName() != null
                        ? leader.getName()
                        : "Không rõ";

        int memberCount =
                guild.getMembers().size();

        int maxMembers =
                GuildConfig.getMaxMembers(
                        guild.getLevel()
                );

        int currentExp =
                guild.getExp();

        int nextLevelExp =
                guild.getNextLevelExp();

        int expRemaining =
                guild.getExpToNextLevel();

        return createItem(
                Material.BEACON,
                "§b§lTHÔNG TIN GUILD",

                " ",
                "§7Guild       §f" + guild.getName(),
                "§7Chủ Guild   §e" + leaderName,

                " ",
                "§7Cấp Guild   §a" + guild.getLevel(),
                "§7EXP         §b"
                        + currentExp
                        + " §8/ §b"
                        + nextLevelExp,
                "§7Còn thiếu   §e"
                        + expRemaining
                        + " EXP",

                " ",
                "§7Thành viên  §f"
                        + memberCount
                        + " §8/ §f"
                        + maxMembers,
                "§7Trực tuyến   §a"
                        + online
                        + " §7người"
        );
    }

    public static void refreshGuildInfoGUI(
            Player player,
            Guild guild
    ) {
        if (player == null
                || guild == null
                || !player.isOnline()) {
            return;
        }

        Inventory gui =
                player.getOpenInventory().getTopInventory();

        if (!(gui.getHolder() instanceof GUIHolder holder)) {
            return;
        }

        if (holder.getType() != GUIType.GUILD_INFO
                && holder.getType() != GUIType.DONATE) {
            return;
        }

        int page = holder.getPage();

        // Cập nhật danh sách thành viên
        renderMemberList(
                gui,
                guild,
                page
        );

        // Cập nhật thông tin Guild
        renderCommonGuildMenu(
                gui,
                player,
                guild
        );
    }
}
