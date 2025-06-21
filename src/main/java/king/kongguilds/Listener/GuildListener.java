package king.kongguilds.Listener;

import king.kongguilds.Gui.GuildGUI;
import king.kongguilds.Gui.GuildMemberEditGUI;
import king.kongguilds.Manager.ItemValueCalculator;
import king.kongguilds.Model.Guild;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Manager.MessageManager;
import king.kongguilds.Model.Member;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class GuildListener implements Listener {

    private final GuildManager guildManager;
    private final MessageManager messageManager;

    private final JavaPlugin plugin; // 🟢 Thêm dòng này

    public GuildListener(JavaPlugin plugin, GuildManager guildManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.messageManager = messageManager;
    }

    // ---------- KHO CHUNG ----------

    @EventHandler
    public void onGuildStorageClick(InventoryClickEvent e) {
        Player p = (Player) e.getWhoClicked();
        InventoryView inv = e.getView();

        if (inv.getTitle().equals("Guild Storage")) {
            Guild g = guildManager.getPlayerGuild(p);
            Member m = g.getMembers().get(p.getUniqueId());
            if (!m.getRank().equalsIgnoreCase("Leader") && !m.getRank().equalsIgnoreCase("Co-Leader")) {
                e.setCancelled(true);

                // Nếu là member, gửi request khi click vào item
                ItemStack clicked = e.getCurrentItem();
                if (clicked != null && clicked.getType() != Material.AIR) {
                    guildManager.requestItem(g.getName(), clicked, p.getUniqueId());
                    p.sendMessage("§aYêu cầu xin item đã được gửi.");
                }
            }
        }
    }

    @EventHandler
    public void onDonateClose(InventoryCloseEvent e) {
        if (!e.getView().getTitle().equals("Donate vật phẩm")) return;

        Player player = (Player) e.getPlayer();
        Guild guild = guildManager.getPlayerGuild(player);
        if (guild == null) return;

        int totalPoints = 0;
        Inventory storage = guild.getSharedStorageInventory();
        List<ItemStack> returnItems = new ArrayList<>(); // <-- danh sách item trả lại

        for (ItemStack item : e.getInventory().getContents()) {
            if (item == null || item.getType().isAir()) continue;

            Bukkit.getLogger().info("[DonateDebug] Kiểm tra item: " + item.getType() + " x" + item.getAmount());

            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                Bukkit.getLogger().info("[DonateDebug] -> Bỏ qua: Không có ItemMeta");
                returnItems.add(item); // <-- đẩy về vì không có meta
                continue;
            }

            for (NamespacedKey key : meta.getPersistentDataContainer().getKeys()) {
                Bukkit.getLogger().info("[DonateDebug] -> PDC key: " + key);
            }

            double baseValue = ItemValueCalculator.calculateItemValue(item);
            double value = baseValue * 10;

            Bukkit.getLogger().info("[DonateDebug] -> Giá trị mỗi item: " + value);

            if (Double.isNaN(value) || value <= 0) {
                Bukkit.getLogger().info("[DonateDebug] -> Bỏ qua: Item không có giá trị");
                returnItems.add(item); // <-- đẩy về nếu item không có giá trị
                continue;
            }

            int itemPoints = (int) Math.floor(value * item.getAmount());
            Bukkit.getLogger().info("[DonateDebug] -> Tổng điểm: " + itemPoints);
            totalPoints += itemPoints;

            // Clone và thêm vào kho
            storage.addItem(item.clone());
        }

        // Đẩy item không hợp lệ về inventory người chơi
        for (ItemStack i : returnItems) {
            player.getInventory().addItem(i);
        }

        // Cập nhật điểm cho player
        if (totalPoints > 0) {
            guildManager.updateMemberPoints(guild.getName(), player.getUniqueId(), totalPoints);
            guild.saveSharedStorage();
            player.sendMessage("§aDonate thành công! +" + totalPoints + " điểm.");
        } else {
            player.sendMessage("§cBạn chưa donate vật phẩm nào có giá trị.");
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

        int slot = event.getRawSlot();

        // 🔒 Click slot 26 để mở kho guild
        if (slot == 26) {
            UUID locker = guildManager.getGuildStorageLocker(guild.getName());
            if (locker != null && !locker.equals(player.getUniqueId())) {
                player.sendMessage(messageManager.getMessage("member", "storage_locked"));
                return;
            }

            guildManager.lockGuildStorage(guild.getName(), player.getUniqueId());
            player.openInventory(guild.getSharedStorageInventory());
            player.sendMessage(messageManager.getMessage("member", "storage_opened"));
            return;
        }

        // ❌ Click slot 8 để đóng GUI
        if (slot == 8) {
            player.closeInventory();
            return;
        }

        // 🔁 Chuyển trang
        if (slot == 30 || slot == 32) {
            int currentPage = 0;
            if (title.contains("| Trang: ")) {
                try {
                    String pagePart = title.split("\\| Trang: ")[1].trim();
                    currentPage = Integer.parseInt(pagePart);
                } catch (Exception ignored) {
                }
            }

            if (slot == 30 && currentPage > 0) {
                GuildGUI.displayGuildInfo(player, guild, currentPage - 1);
            } else if (slot == 32) {
                int membersPerPage = 26 - 3; // 0-25 nhưng trừ 8, 17, 26
                int maxPage = (int) Math.ceil((double) guild.getSortedMembers().size() / membersPerPage);
                if (currentPage + 1 < maxPage) {
                    GuildGUI.displayGuildInfo(player, guild, currentPage + 1);
                }
            }
            return;
        }

        // 🟢 Slot 34: Mở GUI donate vật phẩm
        if (slot == 34) {
            GuildGUI.openDonateGUI(player);
            return;
        }

        // 🔵 Slot 35: Mở GUI duyệt yêu cầu vật phẩm (chỉ Leader/Co-Leader)
        if (slot == 35) {
            String rank = guild.getMembers().get(player.getUniqueId()).getRank();
            if (rank.equalsIgnoreCase("Leader") || rank.equalsIgnoreCase("Co-Leader")) {
                Inventory approveGui = Bukkit.createInventory(null, 54, "Duyệt yêu cầu item");

                // Lấy toàn bộ yêu cầu của guild từ map trong GuildManager
                Map<String, List<UUID>> requestMap = guildManager.getItemRequestMap();
                Inventory storage = guild.getSharedStorageInventory();

                int i = 0;
                for (ItemStack item : storage.getContents()) {
                    if (item == null || item.getType().isAir()) continue;

                    String key = guildManager.itemKey(item);
                    List<UUID> requesters = requestMap.getOrDefault(key, new ArrayList<>());
                    if (requesters.isEmpty()) continue;

                    // Clone và thêm lore người xin
                    ItemStack clone = item.clone();
                    ItemMeta meta = clone.getItemMeta();
                    if (meta != null) {
                        List<String> lore = new ArrayList<>();
                        lore.add("§7Xin bởi:");
                        for (UUID id : requesters) {
                            lore.add("§f- " + Bukkit.getOfflinePlayer(id).getName());
                        }
                        meta.setLore(lore);
                        clone.setItemMeta(meta);
                    }

                    if (i < approveGui.getSize()) {
                        approveGui.setItem(i++, clone);
                    }
                }

                player.openInventory(approveGui);
            } else {
                player.sendMessage("§cBạn không có quyền duyệt yêu cầu.");
            }
            return;
        }

        // 📄 Slot hiển thị thành viên
        List<Integer> displaySlots = new ArrayList<>();
        for (int i = 0; i <= 25; i++) {
            if (i != 8 && i != 17 && i != 26) {
                displaySlots.add(i);
            }
        }
        if (!displaySlots.contains(slot)) return;

        int currentPage = 0;
        if (title.contains("| Trang: ")) {
            try {
                String pagePart = title.split("\\| Trang: ")[1].trim();
                currentPage = Integer.parseInt(pagePart);
            } catch (Exception ignored) {
            }
        }

        int membersPerPage = displaySlots.size();
        int startIndex = currentPage * membersPerPage;
        int memberIndex = startIndex + displaySlots.indexOf(slot);

        List<Map.Entry<UUID, Member>> sortedMembers = guild.getSortedMembers();
        if (memberIndex >= sortedMembers.size()) return;

        UUID clickedMemberId = sortedMembers.get(memberIndex).getKey();
        Member clickedMember = sortedMembers.get(memberIndex).getValue();

        UUID playerId = player.getUniqueId();

        if (clickedMemberId.equals(playerId)) {
            player.sendMessage(messageManager.getMessage("member", "not_edit_yourself"));
            return;
        }

        Member selfInfo = guild.getMembers().get(playerId);
        if (selfInfo == null) return;

        Guild.RankType selfRank = Guild.RankType.valueOf(selfInfo.getRank());
        Guild.RankType targetRank = Guild.RankType.valueOf(clickedMember.getRank());

        if (selfRank == Guild.RankType.LEADER) {
            GuildMemberEditGUI.openEditGUI(player, guild, clickedMemberId);
            return;
        }

        if (selfRank == Guild.RankType.OFFICER) {
            if (targetRank == Guild.RankType.MEMBER) {
                GuildMemberEditGUI.openEditGUI(player, guild, clickedMemberId);
            } else {
                player.sendMessage(messageManager.getMessage("member", "cannot_edit_higher_rank"));
            }
            return;
        }

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

        event.setCancelled(true); // huỷ chat global

        String message = event.getMessage();

        // Gọi hàm mới: không cần truyền format
        guildManager.sendGuildMessage(plugin, guild, player, message);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void clckRuongBase(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Block clickedBlock = event.getClickedBlock();
        Material type = clickedBlock.getType();

        // Chỉ xử lý khi player click vào chest
        if (type != Material.CHEST && type != Material.TRAPPED_CHEST) return;

        Player player = event.getPlayer();
        Guild guild = guildManager.getPlayerGuild(player);
        // Kiểm tra xem player có đứng trong base của chính guild mình không
        if (guildManager.isPlayerInTheirOwnBase(player)) {
            event.setCancelled(true); // Ngăn mở chest mặc định

            player.sendMessage("§aBạn đã mở kho donate của guild!");

            // Mở GUI tùy chỉnh, ví dụ GUI donate
            UUID locker = guildManager.getGuildStorageLocker(guild.getName());
            if (locker != null && !locker.equals(player.getUniqueId())) {
                player.sendMessage(messageManager.getMessage("member", "storage_locked"));
                return;
            }

            guildManager.lockGuildStorage(guild.getName(), player.getUniqueId());
            player.openInventory(guild.getSharedStorageInventory());
        }
    }
}
