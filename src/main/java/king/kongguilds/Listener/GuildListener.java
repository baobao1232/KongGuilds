package king.kongguilds.Listener;

import king.kongguilds.Gui.*;
import king.kongguilds.Main;
import king.kongguilds.Manager.*;
import king.kongguilds.Model.AuctionItem;
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
import org.bukkit.event.player.PlayerJoinEvent;
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

        if (slot == 16) {
            GuildGUI.openGuildSkillGUI(player, guild);
            return;
        }

        if (slot == 25) {
            GuildGUI.openAuctionGUI(
                    player,
                    guild,
                    Main.getInstance()
                            .getAuctionManager()
                            .getGuildAuctions(guild.getName())
            );
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

        if (slot == 35) {
            Member self = guild.getMembers().get(player.getUniqueId());
            if (self == null) return;

            if (!self.getRank().equalsIgnoreCase("Leader")
                    && !self.getRank().equalsIgnoreCase("Co-Leader")) {
                player.sendMessage("§cBạn không có quyền duyệt yêu cầu vật phẩm.");
                return;
            }

            GuildGUI.openApproveRequestGUI(player, guild, guildManager);
            return;
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

        if (rawSlot == 16) {
            GuildGUI.openGuildSkillGUI(player, guild);
            return;
        }
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
        if (rawSlot == 26) {
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

        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        int rawSlot = e.getRawSlot();

        // ===== MENU =====
        if (rawSlot == 8) {
            GuildGUI.openGuildInfoGUI(player, guild, 0);
            return;
        }
        if (rawSlot == 16) {
            GuildGUI.openGuildSkillGUI(player, guild);
            return;
        }
        if (rawSlot == 25) {
            GuildGUI.openStorageGUI(player, guild);
            return;
        }
        if (rawSlot == 29) {
            player.closeInventory();
            return;
        }
        if (rawSlot == 34) {
            GuildGUI.openDonateGUI(player, guild);
            return;
        }

        // ===== CLICK ITEM TRONG STORAGE =====
        if (!isDonateSlot(rawSlot)) return;

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // 🔥 MỞ GUI XIN ITEM
        GuildGUI.openRequestItemGUI(player, guild, clicked);
    }

    @EventHandler
    public void onClickRequestGui(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.REQUEST_ITEM) return;

        e.setCancelled(true);

        Player player = (Player) e.getWhoClicked();
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        int slot = e.getRawSlot();

        if (slot == 16) { // Huỷ
            player.closeInventory();
            return;
        }

        if (slot != 15) return; // Chỉ confirm

        ItemStack item = e.getInventory().getItem(13);
        if (item == null) return;

        double cost = ItemValueCalculator.calculateItemValue(item);
        Member member = guild.getMembers().get(player.getUniqueId());

        if (member.getScore() < cost) {
            player.sendMessage("§cKhông đủ điểm để xin vật phẩm này!");
            return;
        }

        // 🔥 TRỪ ĐIỂM TRƯỚC
        guildManager.updateMemberPoints(
                guild.getName(),
                player.getUniqueId(),
                (int) -cost
        );

        // 🔥 ĐÁNH DẤU ITEM ĐANG CHỜ
        guildManager.requestItem(guild.getName(), item, player.getUniqueId());

        // 🔥 SAVE NGAY – CHỐNG 2 NGƯỜI XIN
        guildManager.saveGuildSync(guild.getName());

        player.sendMessage("§aĐã gửi yêu cầu xin vật phẩm!");
        player.closeInventory();
    }

    @EventHandler
    public void onClickApproveRequestGui(InventoryClickEvent e) {

        if (!(e.getInventory().getHolder() instanceof GUIHolder holder)) {
            return;
        }

        if (holder.getType() != GUIType.APPROVE_REQUEST) {
            return;
        }

        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player leader)) {
            return;
        }

        Guild guild =
                guildManager.getPlayerGuild(leader);

        if (guild == null) {
            return;
        }

        int slot = e.getRawSlot();

        // =====================================================
        // CHECK QUYỀN
        // =====================================================

        Member info =
                guild.getMembers()
                        .get(leader.getUniqueId());

        if (info == null
                || (!info.getRank().equalsIgnoreCase("Leader")
                && !info.getRank().equalsIgnoreCase("Co-Leader"))) {

            leader.sendMessage(
                    "§cBạn không có quyền duyệt."
            );

            return;
        }

        // =====================================================
        // MENU CHUNG
        // =====================================================

        // Quay về Guild Info
        if (slot == 8) {
            GuildGUI.openGuildInfoGUI(
                    leader,
                    guild,
                    0
            );
            return;
        }

        if (slot == 16) {
            GuildGUI.openGuildSkillGUI(leader, guild);
            return;
        }

        // Thông tin căn cứ
        if (slot == 17) {
            // Nếu chưa có GUI căn cứ thì không làm gì
            return;
        }

        // =====================================================
        // AUCTION
        // SLOT 25
        // =====================================================

        if (slot == 25) {

            GuildGUI.openAuctionGUI(
                    leader,
                    guild,
                    Main.getInstance()
                            .getAuctionManager()
                            .getGuildAuctions(
                                    guild.getName()
                            )
            );

            return;
        }

        // =====================================================
        // KHO GUILD
        // =====================================================

        if (slot == 26) {

            GuildGUI.openStorageGUI(
                    leader,
                    guild
            );

            return;
        }

        // =====================================================
        // DONATE
        // =====================================================

        if (slot == 34) {

            GuildGUI.openDonateGUI(
                    leader,
                    guild
            );

            return;
        }

        // =====================================================
        // DUYỆT YÊU CẦU
        // SLOT 35
        // =====================================================

        if (slot == 35) {
            return;
        }

        // =====================================================
        // CLICK ITEM REQUEST
        // =====================================================

        ItemStack clicked =
                e.getCurrentItem();

        if (clicked == null
                || clicked.getType().isAir()) {

            return;
        }

        // =====================================================
        // LẤY KEY TỪ ITEM HIỂN THỊ
        // =====================================================

        ItemStack cleanClicked =
                GuildGUI.stripAllGuildLore(
                        clicked
                );

        String key =
                guildManager.itemKey(
                        cleanClicked
                );

        // =====================================================
        // LẤY REQUEST
        // =====================================================

        List<UUID> requesters =
                guildManager
                        .getItemRequestMap(
                                guild.getName()
                        )
                        .get(key);

        if (requesters == null
                || requesters.isEmpty()) {

            leader.sendMessage(
                    "§cKhông có ai xin item này."
            );

            return;
        }

        UUID targetId =
                requesters.get(0);

        // =====================================================
        // NGƯỜI NHẬN
        // =====================================================

        Player target =
                Bukkit.getPlayer(targetId);

        if (target == null) {

            leader.sendMessage(
                    "§cNgười xin đang offline."
            );

            return;
        }

        // =====================================================
        // LẤY ITEM THẬT TỪ KHO
        // =====================================================

        ItemStack storedItem =
                guildManager.getItemFromStorageByKey(
                        guild,
                        key
                );

        if (storedItem == null
                || storedItem.getType().isAir()) {

            leader.sendMessage(
                    "§cKhông tìm thấy vật phẩm trong kho Guild."
            );

            return;
        }

        // =====================================================
        // CLEAN ITEM
        // =====================================================

        ItemStack reward =
                GuildGUI.stripAllGuildLore(
                        storedItem
                );

        // =====================================================
        // TÍNH GIÁ
        // =====================================================

        double score =
                ItemValueCalculator.calculateItemValue(
                        storedItem
                );

        Member targetInfo =
                guild.getMembers()
                        .get(targetId);

        if (targetInfo == null) {
            return;
        }

        if (targetInfo.getScore() < score) {

            leader.sendMessage(
                    "§cNgười xin không đủ điểm."
            );

            return;
        }

        // =====================================================
        // TRỪ ĐIỂM
        // =====================================================

        guildManager.updateMemberPoints(
                guild.getName(),
                targetId,
                -(int) score
        );

        // =====================================================
        // TRAO ITEM
        // =====================================================

        Map<Integer, ItemStack> leftovers =
                target.getInventory()
                        .addItem(
                                reward.clone()
                        );

        // =====================================================
        // XOÁ ITEM KHỎI KHO
        // =====================================================

        guildManager.removeItemFromStorage(
                guild,
                storedItem
        );

        // =====================================================
        // XOÁ REQUEST
        // =====================================================

        guildManager.clearItemRequest(
                guild.getName(),
                storedItem
        );

        // =====================================================
        // SAVE
        // =====================================================

        guildManager.saveGuildSync(
                guild.getName()
        );

        // =====================================================
        // THÔNG BÁO
        // =====================================================

        target.sendMessage(
                "§aYêu cầu vật phẩm của bạn đã được duyệt."
        );

        // =====================================================
        // ITEM THỪA
        // =====================================================

        if (!leftovers.isEmpty()) {

            for (ItemStack leftover :
                    leftovers.values()) {

                if (leftover == null
                        || leftover.getType().isAir()) {
                    continue;
                }

                target.getWorld().dropItemNaturally(
                        target.getLocation(),
                        leftover
                );
            }
        }

        // =====================================================
        // RELOAD GUI
        // =====================================================

        GuildGUI.openApproveRequestGUI(
                leader,
                guild,
                guildManager
        );
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
       GUILD SKILL TREE
       ========================= */
    @EventHandler
    public void onGuildSkillClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GUIHolder holder)) return;
        if (holder.getType() != GUIType.GUILD_SKILLS) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;

        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        int slot = event.getRawSlot();

        if (slot == 49) {
            GuildGUI.openGuildInfoGUI(player, guild, 0);
            return;
        }

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;

        String skillId = clicked.getItemMeta()
                .getPersistentDataContainer()
                .get(new org.bukkit.NamespacedKey(plugin, "guild_skill"),
                        org.bukkit.persistence.PersistentDataType.STRING);

        if (skillId == null) return;

        Member self = guild.getMembers().get(player.getUniqueId());
        if (self == null) return;

        if (!self.getRank().equalsIgnoreCase("Leader")
                && !self.getRank().equalsIgnoreCase("Co-Leader")) {
            player.sendMessage("§cChỉ Leader hoặc Co-Leader mới có thể nâng cấp Guild Skill.");
            return;
        }

        GuildSkillManager.Skill skill = GuildSkillManager.get(skillId);
        if (skill == null) return;

        if (guild.getSkillLevel(skillId) >= skill.getMaxLevel()) {
            player.sendMessage("§eSkill này đã đạt cấp tối đa.");
            return;
        }

        if (guild.getLevel() < skill.getRow()) {
            player.sendMessage("§cCần Guild Level " + skill.getRow() + " để nâng cấp skill này.");
            return;
        }

        if (skill.getPrerequisite() != null
                && guild.getSkillLevel(skill.getPrerequisite()) <= 0) {
            GuildSkillManager.Skill req = GuildSkillManager.get(skill.getPrerequisite());
            player.sendMessage("§cCần mở khóa " + (req == null ? skill.getPrerequisite() : req.getName()) + " trước.");
            return;
        }

        long cost = GuildSkillManager.getUpgradeCost(guild, skillId);
        if (guild.getAvailableContribution() < cost) {
            player.sendMessage("§cKhông đủ điểm cống hiến. Cần " + cost + " điểm.");
            return;
        }

        if (!GuildSkillManager.upgrade(guild, skillId)) {
            player.sendMessage("§cKhông thể nâng cấp skill này.");
            return;
        }

        guildManager.saveGuildSync(guild.getName());

        player.sendMessage("§aĐã nâng cấp " + skill.getName()
                + " §alên cấp " + guild.getSkillLevel(skillId) + ".");

        GuildGUI.openGuildSkillGUI(player, guild);
    }

    @EventHandler
    public void onGuildSkillJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Guild guild = guildManager.getPlayerGuild(player);
            if (guild != null) {
                GuildSkillManager.apply(player, guild);
            }
        });
    }

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
        GuildGUI.openStorageGUI(player, guild);
    }

    @EventHandler
    public void onAuctionClick(InventoryClickEvent event) {

        if (!(event.getInventory().getHolder() instanceof GUIHolder holder)) {
            return;
        }

        if (holder.getType() != GUIType.AUCTION) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Guild guild = guildManager.getPlayerGuild(player);

        if (guild == null) {
            return;
        }

        int slot = event.getRawSlot();

        // =====================================================
        // QUAY VỀ GUILD
        // =====================================================

        if (slot == 8) {
            GuildGUI.openGuildInfoGUI(
                    player,
                    guild,
                    0
            );
            return;
        }

        // =====================================================
        // THÔNG TIN CĂN CỨ
        // =====================================================

        if (slot == 17) {
            return;
        }

        // =====================================================
        // AUCTION
        // SLOT 25
        // =====================================================

        if (slot == 25) {
            return;
        }

        // =====================================================
        // KHO GUILD
        // =====================================================

        if (slot == 26) {
            GuildGUI.openStorageGUI(
                    player,
                    guild
            );
            return;
        }

        // =====================================================
        // DONATE
        // =====================================================

        if (slot == 34) {
            GuildGUI.openDonateGUI(
                    player,
                    guild
            );
            return;
        }

        // =====================================================
        // DUYỆT YÊU CẦU
        // =====================================================

        if (slot == 35) {

            Member self =
                    guild.getMembers()
                            .get(player.getUniqueId());

            if (self == null ||
                    (!self.getRank().equalsIgnoreCase("Leader")
                            && !self.getRank().equalsIgnoreCase("Co-Leader"))) {

                player.sendMessage(
                        "§cBạn không có quyền."
                );

                return;
            }

            GuildGUI.openApproveRequestGUI(
                    player,
                    guild,
                    guildManager
            );

            return;
        }

        // =====================================================
        // LẤY AUCTION MANAGER
        // =====================================================

        AuctionManager auctionManager =
                Main.getInstance()
                        .getAuctionManager();

        if (auctionManager == null) {

            player.sendMessage(
                    "§cHệ thống đấu giá chưa sẵn sàng."
            );

            return;
        }

        // =====================================================
        // LẤY AUCTION TỪ ITEM GUI
        // =====================================================

        AuctionItem auction =
                auctionManager.getAuctionFromGUI(
                        event.getInventory(),
                        slot
                );

        if (auction == null) {
            return;
        }

        // =====================================================
        // KIỂM TRA AUCTION
        // =====================================================

        if (auction.isExpired()) {

            player.sendMessage(
                    "§cPhiên đấu giá này đã kết thúc."
            );

            return;
        }

        // =====================================================
        // KIỂM TRA MEMBER
        // =====================================================

        if (!guild.hasMember(player.getUniqueId())) {

            player.sendMessage(
                    "§cBạn không phải thành viên Guild."
            );

            return;
        }

        // =====================================================
        // MỞ CHI TIẾT ĐẤU GIÁ
        // =====================================================

        AuctionDetailGUI.open(
                player,
                guild,
                auction
        );
    }

    @EventHandler
    public void onAuctionDetailClick(
            InventoryClickEvent event
    ) {

        if (!(event.getInventory().getHolder()
                instanceof GUIHolder holder)) {
            return;
        }

        if (holder.getType() != GUIType.AUCTION_DETAIL) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();

        AuctionManager auctionManager =
                Main.getInstance()
                        .getAuctionManager();

        if (auctionManager == null) {
            player.sendMessage(
                    "§cHệ thống đấu giá chưa sẵn sàng."
            );
            return;
        }

        // =====================================================
        // LẤY AUCTION TỪ ITEM SLOT 13
        // =====================================================

        AuctionItem auction =
                auctionManager.getAuctionFromGUI(
                        event.getInventory(),
                        13
                );

        if (auction == null) {

            player.sendMessage(
                    "§cKhông tìm thấy phiên đấu giá."
            );

            return;
        }

        // =====================================================
        // LẤY GUILD CỦA PLAYER
        // =====================================================

        Guild guild = guildManager.getPlayerGuild(player);

        if (guild == null) {
            player.closeInventory();

            player.sendMessage(
                    "§cBạn không còn ở trong Guild."
            );

            return;
        }

        // =====================================================
        // KIỂM TRA MEMBER
        // =====================================================

        if (!guild.hasMember(
                player.getUniqueId()
        )) {

            player.sendMessage(
                    "§cBạn không phải thành viên Guild."
            );

            return;
        }

        // =====================================================
        // KIỂM TRA HẾT HẠN
        // =====================================================

        if (auction.isExpired()) {

            player.closeInventory();

            player.sendMessage(
                    "§cPhiên đấu giá này đã kết thúc."
            );

            return;
        }

        // =====================================================
        // GIÁ HIỆN TẠI
        // =====================================================

        double current =
                auction.getHighestBid();

        double amount;

        // =====================================================
        // +100
        // =====================================================

        if (slot == 10) {

            amount =
                    current <= 0
                            ? 100
                            : current + 100;
        }

        // =====================================================
        // +500
        // =====================================================

        else if (slot == 11) {

            amount =
                    current <= 0
                            ? 500
                            : current + 500;
        }

        // =====================================================
        // +1000
        // =====================================================

        else if (slot == 15) {

            amount =
                    current <= 0
                            ? 1000
                            : current + 1000;
        }

        // =====================================================
        // LỊCH SỬ
        // =====================================================

        else if (slot == 17) {

            AuctionHistoryGUI.open(
                    player,
                    guild,
                    auction
            );

            return;
        }

        // =====================================================
        // QUAY LẠI
        // =====================================================

        else if (slot == 22) {

            GuildGUI.openAuctionGUI(
                    player,
                    guild,
                    auctionManager.getGuildAuctions(
                            guild.getName()
                    )
            );

            return;
        }

        else {
            return;
        }

        // =====================================================
        // ĐẶT GIÁ
        // =====================================================

        boolean success =
                auctionManager.placeBid(
                        player,
                        auction.getId(),
                        amount
                );

        if (!success) {
            return;
        }

        player.sendMessage(
                "§aĐã đặt giá §e$"
                        + String.format(
                        "%,.0f",
                        amount
                )
                        + " §athành công."
        );

        // =====================================================
        // REFRESH GUI
        // =====================================================

        AuctionItem updatedAuction =
                auctionManager.getAuction(
                        auction.getId()
                );

        if (updatedAuction == null) {
            player.closeInventory();
            return;
        }

        AuctionDetailGUI.open(
                player,
                guild,
                updatedAuction
        );
    }

    @EventHandler
    public void onAuctionHistoryClick(
            InventoryClickEvent event
    ) {

        if (!(event.getInventory().getHolder()
                instanceof GUIHolder holder)) {

            return;
        }

        if (holder.getType() != GUIType.AUCTION_HISTORY) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getRawSlot() != 49) {
            return;
        }

        Guild guild =
                guildManager.getPlayerGuild(player);

        if (guild == null) {
            player.closeInventory();
            return;
        }

        AuctionManager auctionManager =
                Main.getInstance()
                        .getAuctionManager();

        if (auctionManager == null) {
            player.closeInventory();
            return;
        }

        /*
         * History GUI không có auction_id riêng,
         * nên lấy auction từ item slot 4.
         */
        AuctionItem auction =
                auctionManager.getAuctionFromGUI(
                        event.getInventory(),
                        4
                );

        if (auction == null) {
            player.closeInventory();

            player.sendMessage(
                    "§cPhiên đấu giá không còn tồn tại."
            );

            return;
        }

        AuctionDetailGUI.open(
                player,
                guild,
                auction
        );
    }

    private String getAuctionItemName(ItemStack item) {

        if (item.hasItemMeta()
                && item.getItemMeta().hasDisplayName()) {

            return item.getItemMeta().getDisplayName();
        }

        return item.getType().name();
    }

    private String formatMoney(double money) {

        return String.format(
                Locale.US,
                "%,.0f",
                money
        );
    }

    @EventHandler
    public void testSkillTreeClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!event.getView()
                .getTitle()
                .equals("§d§lGuild Skill Tree")) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();

        switch (slot) {

            case 47: // ←
                GuildSkillTreeTest.move(player, -1, 0);
                break;

            case 48: // ↑
                GuildSkillTreeTest.move(player, 0, -1);
                break;

            case 49: // CAMERA - reset
                GuildSkillTreeTest.resetCamera(player);
                break;

            case 50: // ↓
                GuildSkillTreeTest.move(player, 0, 1);
                break;

            case 51: // →
                GuildSkillTreeTest.move(player, 1, 0);
                break;
        }
    }

}
