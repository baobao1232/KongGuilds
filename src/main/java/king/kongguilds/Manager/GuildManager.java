package king.kongguilds.Manager;

import com.fastasyncworldedit.core.FaweAPI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BlockTypes;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import king.kongguilds.Base.BaseData;
import king.kongguilds.Base.BaseHologramManager;
import king.kongguilds.Gui.GUIHolder;
import king.kongguilds.Gui.GUIType;
import king.kongguilds.Gui.GuildGUI;
import king.kongguilds.Model.Guild;
import king.kongguilds.Model.Member;
import king.kongguilds.Model.TimedInvite;
import king.kongguilds.WorldGuard.BaseRegionManager;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.*;

public class GuildManager {
    private static GuildManager instance;
    private final JavaPlugin plugin;
    private static final Map<String, Guild> guilds = new HashMap<>();
    private final File guildFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, TimedInvite> pendingInvites = new HashMap<>();
    private final Map<String, UUID> guildStorageLock = new HashMap<>();
    private final MessageManager messageManager;
    private final Set<UUID> guildChatMode = new HashSet<>();
    private static final Map<String, Hologram> hologramMap = new HashMap<>();
    private final Map<String, Map<String, List<UUID>>> itemRequests = new HashMap<>();
    public static final int[] DONATE_SLOTS = {
            0,1,2,3,4,5,6,
            9,10,11,12,13,14,15,
            18,19,20,21,22,23,24,
            27,28,29,30,31,32,33
    };


    public GuildManager(JavaPlugin plugin, MessageManager messageManager) {
        this.plugin = plugin;
        instance = this; // Gán instance
        this.messageManager = messageManager;
        this.guildFolder = new File(plugin.getDataFolder(), "Guilds");
        createGuildFolder();
    }

    public static Map<String, Guild> getGuilds() {
        return guilds;
    }


    public static GuildManager getInstance() {
        return instance;
    }

    private void createGuildFolder() {
        if (!guildFolder.exists()) guildFolder.mkdirs();
    }

    public enum CreateResult {
        SUCCESS,
        ALREADY_IN_GUILD,
        NAME_EXISTS,
        INVALID_NAME
    }

    public CreateResult createGuild(Player leader, String rawName) {
        if (getPlayerGuild(leader) != null) {
            return CreateResult.ALREADY_IN_GUILD;
        }

        String name = rawName.trim();

        if (name.length() < 3 || name.length() > 16) {
            return CreateResult.INVALID_NAME;
        }

        if (guilds.containsKey(name)) {
            return CreateResult.NAME_EXISTS;
        }

        Guild guild = new Guild(name, leader.getUniqueId(), plugin);
        guilds.put(name, guild);

        saveGuildSync(name);

        leader.sendMessage("§aTạo guild §e" + name + " §athành công!");
        return CreateResult.SUCCESS;
    }

    public boolean leaveGuild(Player player) {
        Guild guild = getPlayerGuild(player);
        if (guild == null) return false;

        UUID uuid = player.getUniqueId();

        // ❌ Leader không được leave
        if (guild.getLeader().equals(uuid)) {
            player.sendMessage("§cLeader không thể rời guild. Hãy disband hoặc chuyển quyền.");
            return false;
        }

        Member member = guild.getMembers().get(uuid);
        if (member == null) return false;

        // Remove WG region
        if (guild.getBaseID() != null) {
            World world = BaseData.getWorldByBaseID(guild.getBaseID());
            if (world != null) {
                BaseRegionManager.removeMemberFromRegion(
                        guild.getBaseID(),
                        member.getName(),
                        world
                );
            }
        }
        GuildSkillManager.remove(player);
        guild.kickMember(uuid);
        saveGuildSync(guild.getName());

        player.sendMessage("§aBạn đã rời guild §e" + guild.getName());
        return true;
    }

    public boolean disbandGuild(Player leader) {
        Guild guild = getPlayerGuild(leader);
        if (guild == null) return false;

        if (!guild.getLeader().equals(leader.getUniqueId())) {
            leader.sendMessage("§cChỉ Leader mới có thể disband guild.");
            return false;
        }

        // Kick toàn bộ member khỏi region
        if (guild.getBaseID() != null) {
            World world = BaseData.getWorldByBaseID(guild.getBaseID());

            if (world != null) {
                for (Member m : guild.getMembers().values()) {
                    BaseRegionManager.removeMemberFromRegion(
                            guild.getBaseID(),
                            m.getName(),
                            world
                    );
                }
            }
        }

        // REMOVE GUILD SKILLS CỦA TẤT CẢ MEMBER ONLINE
        for (UUID memberId : guild.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);

            if (member != null && member.isOnline()) {
                GuildSkillManager.remove(member);
            }
        }

        broadcastToGuild(
                plugin,
                guild,
                "§cGuild đã bị disband bởi §e" + leader.getName()
        );

        removeGuild(guild.getName());

        return true;
    }

    public void removeGuild(String name) {
        // 1. Xóa khỏi danh sách bộ nhớ
        Guild guild = guilds.remove(name);

        // 3. Nếu có base, xóa base
        if (guild != null && guild.getBaseID() != null) {
            String baseId = guild.getBaseID();

            // Xóa hologram
            BaseHologramManager.removeHologram(baseId);
            BaseHologramManager.removeHologram("chest_" + baseId); // Nếu có hologram trên chest

            // Xóa vùng bảo vệ nếu có (WorldGuard)
            World baseWorld = BaseData.getWorldByBaseID(baseId);
            if (baseWorld != null) {
                BaseRegionManager.removeBaseRegion(baseId, baseWorld);
            }

            // Xóa base khỏi hệ thống
            BaseData.removeBaseById(baseId);
        }

        File guildFile = new File(guildFolder, name + ".json");
        if (guildFile.exists()) {
            guildFile.delete();
        }

        // Log
        Bukkit.getLogger().info("Đã xóa guild '" + name + "' cùng với base và hologram liên quan.");
    }

    public Guild getGuild(String name) {
        synchronized (guilds) {
            return guilds.get(name);
        }
    }

    public List<Guild> getAllGuilds() {
        synchronized (guilds) {
            return new ArrayList<>(guilds.values());
        }
    }

    public void asyncLoadGuilds(Runnable onComplete) {
        File[] files = guildFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            if (onComplete != null) onComplete.run();
            return;
        }

        List<File> fileList = Arrays.asList(files);
        Iterator<File> iterator = fileList.iterator();

        new BukkitRunnable() {
            int loaded = 0;
            final int MAX_PER_TICK = 3;

            @Override
            public void run() {
                int count = 0;
                while (iterator.hasNext() && count < MAX_PER_TICK) {
                    File file = iterator.next();
                    try (FileReader reader = new FileReader(file)) {
                        Guild guild = gson.fromJson(reader, Guild.class);
                        guild.setPlugin(plugin);
                        guild.loadSharedStorage();
                        synchronized (guilds) {
                            guilds.put(guild.getName(), guild); // ✅ normalize key
                        }
                        loaded++;
                        Bukkit.getLogger().info("[GuildLoad] Loaded: " + guild.getName());
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("[GuildLoad] Error: " + file.getName());
                        e.printStackTrace();
                    }
                    count++;
                }

                if (!iterator.hasNext()) {
                    Bukkit.getLogger().info("[GuildLoad] Hoàn tất load " + loaded + " guilds.");
                    if (onComplete != null) {
                        // ✅ Chạy callback trên main thread
                        Bukkit.getScheduler().runTask(plugin, onComplete);
                    }
                    this.cancel();
                }
            }
        }.runTaskTimerAsynchronously(plugin, 0L, 2L);
    }


//    public void asyncLoadGuilds() {
//        new BukkitRunnable() {
//            @Override
//            public void run() {
//                File[] files = guildFolder.listFiles((dir, name) -> name.endsWith(".json"));
//                if (files != null) {
//                    for (File file : files) {
//                        try (FileReader reader = new FileReader(file)) {
//                            Guild guild = gson.fromJson(reader, Guild.class);
//                            guild.setPlugin(plugin);
//                            guild.loadSharedStorage();
//                            synchronized (guilds) {
//                                guilds.put(guild.getName(), guild);
//                            }
//                            Bukkit.getLogger().info("[AsyncLoad] Loaded guild: " + guild.getName());
//                        } catch (Exception e) {
//                            Bukkit.getLogger().warning("[AsyncLoad] Failed to load guild from " + file.getName());
//                            e.printStackTrace();
//                        }
//                    }
//                }
//            }
//        }.runTaskAsynchronously(plugin);
//    }

    private void validateAndFixGuildUUIDs(Guild guild) {
        // Fix leader UUID nếu null
        if (guild.getLeader() == null) {
            Bukkit.getLogger().warning("Guild " + guild.getName() + " has null leader UUID!");
            // Có thể tìm UUID từ tên leader nếu có
            // guild.setLeader(Bukkit.getOfflinePlayer(leaderName).getUniqueId());
        }

        // Fix member UUIDs nếu null
        if (guild.getMembers() != null) {
            for (Member member : guild.getMembers().values()) {
                if (member != null && member.getUUID() == null && member.getName() != null) {
                    UUID fixedUUID = Bukkit.getOfflinePlayer(member.getName()).getUniqueId();
                    member.setUUID(fixedUUID);
                    Bukkit.getLogger().info("Fixed UUID for member: " + member.getName() + " -> " + fixedUUID);
                }
            }
        }
    }

    public void saveGuildSync(String name) {
        Guild guild = guilds.get(name);
        if (guild != null) {
            guild.saveSharedStorage();

            try (FileWriter writer = new FileWriter(new File(guildFolder, name + ".json"))) {
                gson.toJson(guild, writer);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void addMember(String guildName, String playerName, UUID memberId, String rankName, String memberType, int points) {
        Guild guild = getGuild(guildName);
        if (guild != null && !guild.hasMember(memberId)) {
            Guild.RankType rank;
            try {
                // Chuyển rankName string thành RankType enum (chuyển "-" thành "_" và uppercase)
                rank = Guild.RankType.valueOf(rankName.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                // Nếu rank không hợp lệ thì dùng MEMBER mặc định
                rank = Guild.RankType.MEMBER;
            }
            guild.addMember(memberId, playerName, rank, memberType, points);

            Player target = Bukkit.getPlayer(memberId);

            if (target != null && target.isOnline()) {
                GuildSkillManager.apply(target, guild);
            }

            if (guild.getBaseID() != null && !guild.getBaseID().isEmpty()) {
                String baseID = guild.getBaseID();
                World world = BaseData.getWorldByBaseID(guild.getBaseID());
                if (world != null) {
                    BaseRegionManager.addMemberToRegion(baseID, playerName, world);
                } else {
                    // Xử lý khi không tìm thấy world, ví dụ log hoặc bỏ qua
                    Bukkit.getLogger().info("Không tìm thấy world cho baseID: " + baseID);
                }
            }
        }
    }

    public boolean kickMemberAsAdmin(String guildName, UUID targetId) {
        Guild guild = getGuild(guildName);
        if (guild == null || !guild.hasMember(targetId)) return false;

        Member member = guild.getMembers().get(targetId);

        World world = BaseData.getWorldByBaseID(guild.getBaseID());

        if (world != null) {
            BaseRegionManager.removeMemberFromRegion(
                    guild.getBaseID(),
                    member.getName(),
                    world
            );
        } else {
            Bukkit.getLogger().info(
                    "Không tìm thấy world cho baseID: " + guild.getBaseID()
            );
        }

        // Nếu đang online → tháo Guild Skill
        Player target = Bukkit.getPlayer(targetId);

        if (target != null && target.isOnline()) {
            GuildSkillManager.remove(target);
        }

        // Sau đó mới kick khỏi Guild
        guild.kickMember(targetId);

        // Lưu Guild
        saveGuildSync(guild.getName());

        return true;
    }

    public void kickMember(Player kicker, String guildName, UUID targetId) {
        Guild guild = getGuild(guildName);
        if (guild == null || !guild.hasMember(targetId)) return;

        UUID kickerId = kicker.getUniqueId();
        if (!guild.hasMember(kickerId)) {
            kicker.sendMessage("§cBạn không phải thành viên của guild này.");
            return;
        }

        if (kickerId.equals(targetId)) {
            kicker.sendMessage("§cBạn không thể kick chính mình!");
            return;
        }

        Member kickerInfo = guild.getMembers().get(kickerId);
        Member targetInfo = guild.getMembers().get(targetId);

        // Giờ gọi getRank() của Member trả về String rank chứ không phải Guild.RankType
        String kickerRankString = kickerInfo.getRank();
        String targetRankString = targetInfo.getRank();

        // Nếu muốn so sánh với enum RankType, cần chuyển String sang enum
        Guild.RankType kickerRank = Guild.RankType.valueOf(kickerRankString);
        Guild.RankType targetRank = Guild.RankType.valueOf(targetRankString);


        if (kickerRank != Guild.RankType.LEADER && kickerRank != Guild.RankType.OFFICER) {
            kicker.sendMessage("§cChỉ Leader hoặc Officer mới có quyền kick!");
            return;
        }

        if (targetRank == Guild.RankType.LEADER) {
            kicker.sendMessage("§cKhông thể kick Leader!");
            return;
        }
        if (guild.getBaseID() != null && !guild.getBaseID().isEmpty()) {
            String baseID = guild.getBaseID();
            World world = BaseData.getWorldByBaseID(baseID);
            if (world != null) {
                BaseRegionManager.removeMemberFromRegion(baseID, targetInfo.getName(), world);
            } else {
                Bukkit.getLogger().info("Không tìm thấy world cho baseID: " + baseID);
            }
        }

        Player target = Bukkit.getPlayer(targetId);

        if (target != null && target.isOnline()) {
            GuildSkillManager.remove(target);
        }

        guild.kickMember(targetId);
        saveGuildSync(guild.getName());
        kicker.sendMessage("§aĐã kick thành viên " + targetInfo.getName());
    }


    public void inviteMember(UUID playerId, String guildName) {
        // Thời gian timeout là 60 giây (60 * 1000 ms)
        pendingInvites.put(playerId, new TimedInvite(guildName, 60_000));
    }

    public void acceptInvite(Player player) {
        UUID playerId = player.getUniqueId();
        TimedInvite invite = pendingInvites.get(playerId);

        if (invite != null) {
            if (invite.isExpired()) {
                pendingInvites.remove(playerId);
                player.sendMessage("§cLời mời đã hết hạn!");
                return;
            }

            addMember(invite.getGuildName(), player.getName(), playerId, "Rank 1", "Member", 0);
            pendingInvites.remove(playerId);
            player.sendMessage("§aBạn đã gia nhập bang hội " + invite.getGuildName());
        } else {
            player.sendMessage("§cKhông có lời mời nào!");
        }
    }

    public void rejectInvite(UUID playerId) {
        pendingInvites.remove(playerId);
    }

    public Map<UUID, TimedInvite> getPendingInvites() {
        return pendingInvites;
    }

    public Guild getPlayerGuild(Player player) {
        UUID playerId = player.getUniqueId();
        for (Guild guild : guilds.values()) {
            if (guild.hasMember(playerId)) {
                return guild;
            }
        }
        return null;
    }

    public Guild getGuildByName(String name) {
        if (name == null) return null;
        return guilds.get(name.toLowerCase());
    }

    public boolean hasGuildGUI(Player player) {
        Guild guild = getPlayerGuild(player);
        return guild != null;
    }

    public boolean isGuildLeader(Player player) {
        UUID playerId = player.getUniqueId();
        for(Guild guild : guilds.values()){
            if(guild.getLeader().equals(playerId)){
                return true;
            }
        }
        return false;
    }

    public boolean isCoLeader(Player player) {
        Guild guild = getPlayerGuild(player);
        if (guild == null) return false;

        Member info = guild.getMembers().get(player.getUniqueId());
        // Ở đây, rank là String lưu trong field 'rank', nếu bạn dùng "Co-Leader" làm rank
        return info != null && info.getRank().equalsIgnoreCase("Co-Leader");
    }

    public void updateMemberPoints(
            String guildName,
            UUID memberId,
            int points
    ) {

        Guild guild = getGuild(guildName);

        if (guild == null) {
            Bukkit.getLogger().warning(
                    "[DonateDebug] Guild null: " + guildName
            );
            return;
        }

        if (!guild.hasMember(memberId)) {
            Bukkit.getLogger().warning(
                    "[DonateDebug] Member không tồn tại trong guild: "
                            + memberId
            );
            return;
        }

        Member info = guild.getMembers().get(memberId);

        if (info == null) {
            Bukkit.getLogger().warning(
                    "[DonateDebug] Member object null: "
                            + memberId
            );
            return;
        }

        int oldScore = info.getScore();
        int newScore = oldScore + points;

        Bukkit.getLogger().info(
                "[DonateDebug] Update điểm cho "
                        + info.getName()
                        + " | Cũ=" + oldScore
                        + " | +=" + points
                        + " | Mới=" + newScore
        );

        Member updatedMember = new Member(
                info.getUUID(),
                info.getName(),
                info.getJoinDate(),
                info.getRank(),
                newScore
        );

        guild.getMembers().put(
                memberId,
                updatedMember
        );
    }

    public void addExpToGuild(Player player, int amount) {

        if (player == null || amount <= 0)
            return;

        Guild guild = getPlayerGuild(player);

        if (guild == null)
            return;

        String baseID = guild.getBaseID();

        // addExp() sẽ:
        // 1. Cộng totalContribution
        // 2. Cộng EXP level hiện tại
        // 3. Xử lý level up
        // 4. Giữ lại EXP dư
        guild.addExp(amount);

        saveGuildSync(guild.getName());

        if (baseID != null && !baseID.isEmpty()) {
            BaseHologramManager.updateBaseHologram(
                    baseID,
                    guild
            );
        }

        // Refresh GUI SAU KHI score + EXP + level
        // đã được cập nhật đầy đủ
        refreshGuildGUIs(guild);
    }

    public void toggleGuildChat(Player player) {
        UUID uuid = player.getUniqueId();
        if (guildChatMode.contains(uuid)) {
            guildChatMode.remove(uuid);
            player.sendMessage(ChatColor.YELLOW + "Đã tắt chế độ chat guild.");
        } else {
            guildChatMode.add(uuid);
            player.sendMessage(ChatColor.GREEN + "Đã bật chế độ chat guild.");
        }
    }

    public boolean isInGuildChatMode(UUID uuid) {
        return guildChatMode.contains(uuid);
    }

    public String formatGuildChatMessage(Guild guild, Player sender, String message) {
        Member senderInfo = guild.getMembers().get(sender.getUniqueId());
        String rankName = senderInfo != null ? senderInfo.getRank() : "Member";

        String format = "§7[§b{rank}§7] §e{player}§7: §f{message}";
        return format
                .replace("{player}", sender.getName())
                .replace("{rank}", rankName)
                .replace("{message}", message);
    }

    // ✅ Gửi tin nhắn guild chat đã xử lý placeholder
    public void sendGuildMessage(JavaPlugin plugin, Guild guild, Player sender, String message) {
        String formatted = formatGuildChatMessage(guild, sender, message);

        // Duyệt danh sách người chơi online trong guild, xử lý Placeholder và gửi trong 1 task
        List<Player> onlineMembers = new ArrayList<>();
        for (UUID memberId : guild.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                onlineMembers.add(member);
            }
        }

        // Duyệt offline → không tạo task nếu rỗng
        if (onlineMembers.isEmpty()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player member : onlineMembers) {
                String parsedMessage = PlaceholderAPI.setPlaceholders(member, formatted);
                member.sendMessage(parsedMessage);
            }
        });
    }

    public void broadcastToGuild(JavaPlugin plugin, Guild guild, String message) {
        List<Player> onlineMembers = new ArrayList<>();
        for (UUID memberId : guild.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                onlineMembers.add(member);
            }
        }

        if (onlineMembers.isEmpty()) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player member : onlineMembers) {
                String parsedMessage = PlaceholderAPI.setPlaceholders(member, message);
                member.sendMessage(parsedMessage);
            }
        });
    }

    public static Location getCenter(Location pos1, Location pos2) {
        if (!pos1.getWorld().equals(pos2.getWorld())) return null;
        double x = (pos1.getX() + pos2.getX()) / 2.0;
        double y = (pos1.getY() + pos2.getY()) / 2.0 + 1.5;  // nâng cao lên một chút
        double z = (pos1.getZ() + pos2.getZ()) / 2.0;
        return new Location(pos1.getWorld(), x, y, z);
    }

    public static boolean isInside(Location point, Location pos1, Location pos2) {
        if (!point.getWorld().getName().equals(pos1.getWorld().getName()) ||
                !pos1.getWorld().getName().equals(pos2.getWorld().getName())) {
            return false;
        }

        double xMin = Math.min(pos1.getX(), pos2.getX());
        double xMax = Math.max(pos1.getX(), pos2.getX());
        double yMin = Math.min(pos1.getY(), pos2.getY());
        double yMax = Math.max(pos1.getY(), pos2.getY());
        double zMin = Math.min(pos1.getZ(), pos2.getZ());
        double zMax = Math.max(pos1.getZ(), pos2.getZ());

        return (point.getX() >= xMin && point.getX() <= xMax) &&
                (point.getY() >= yMin && point.getY() <= yMax) &&
                (point.getZ() >= zMin && point.getZ() <= zMax);
    }

    public static void pasteSchematicAtBaseAsync(Player player, JavaPlugin plugin, String baseId, String fileName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Map<String, BaseData.BaseClaim> bases = BaseData.loadBases();
                    BaseData.BaseClaim baseClaim = bases.get(baseId);

                    if (baseClaim == null) {
                        throw new IllegalArgumentException("Không tìm thấy base với ID: " + baseId);
                    }

                    double centerX = (baseClaim.pos1.getX() + baseClaim.pos2.getX()) / 2.0;
                    double centerY = (baseClaim.pos1.getY() + baseClaim.pos2.getY()) / 2.0;
                    double centerZ = (baseClaim.pos1.getZ() + baseClaim.pos2.getZ()) / 2.0;

                    World world = baseClaim.pos1.getWorld();
                    if (world == null) {
                        throw new IllegalArgumentException("World không tồn tại: " + baseClaim.pos1.getWorld().getName());
                    }

                    Location centerLocation = new Location(world, centerX, centerY, centerZ);

                    // Gọi paste async
                    pasteSchematicAtLocationAsync(player, plugin, centerLocation, fileName);
                } catch (Exception e) {
                    Bukkit.getLogger().severe("[ERROR] Lỗi khi paste schematic tại base: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public static void pasteSchematicAtLocationAsync(Player player, JavaPlugin plugin, Location centerLocation, String fileName) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    File schematicFile = new File(plugin.getDataFolder(), "schem/" + fileName);
                    if (!schematicFile.exists()) {
                        throw new IllegalArgumentException("Schematic file không tồn tại: " + schematicFile.getPath());
                    }

                    World world = centerLocation.getWorld();
                    if (world == null) throw new IllegalArgumentException("World không hợp lệ");

                    com.sk89q.worldedit.world.World weWorld = FaweAPI.getWorld(world.getName());
                    if (weWorld == null) throw new IllegalStateException("Không tìm thấy FAWE World");

                    ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                    if (format == null) throw new IllegalArgumentException("Không xác định định dạng schematic");

                    Clipboard clipboard;
                    try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                        clipboard = reader.read();
                    }

                    Region region = clipboard.getRegion();
                    BlockVector3 min = region.getMinimumPoint();
                    BlockVector3 max = region.getMaximumPoint();
                    BlockVector3 schematicCenter = BlockVector3.at(
                            (min.getBlockX() + max.getBlockX()) / 2,
                            (min.getBlockY() + max.getBlockY()) / 2,
                            (min.getBlockZ() + max.getBlockZ()) / 2
                    );

                    Location playerLoc = player.getLocation();
                    BlockVector3 pasteLocation = BlockVector3.at(
                            centerLocation.getBlockX() + 1,
                            playerLoc.getBlockY(),
                            centerLocation.getBlockZ()
                    );

                    // Dán async bằng FAWE
                    EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                            .world(weWorld)
                            .build();

                    ClipboardHolder holder = new ClipboardHolder(clipboard);
                    Operation operation = holder.createPaste(editSession)
                            .to(pasteLocation)
                            .ignoreAirBlocks(false)
                            .build();

                    Operations.complete(operation);
                    editSession.flushSession();

                    Bukkit.getLogger().info("[Schematic] Dán schematic thành công tại: " + pasteLocation);

                } catch (Exception e) {
                    Bukkit.getLogger().severe("[Schematic] Lỗi async: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    // Phương thức cập nhật level cho base sau khi paste thành công
    public static void updateBaseLevel(String baseId, int newLevel) {
        try {
            Map<String, BaseData.BaseClaim> bases = BaseData.loadBases();
            BaseData.BaseClaim baseClaim = bases.get(baseId);

            if (baseClaim != null) {
                baseClaim.level = newLevel;

                // Lưu lại vào file
                try (Writer writer = new FileWriter(BaseData.getBaseFile())) {
                    Gson gson = new GsonBuilder()
                            .registerTypeAdapter(Location.class, new BaseData.LocationAdapter())
                            .setPrettyPrinting()
                            .create();
                    gson.toJson(bases, writer);

                    Bukkit.getLogger().info("[DEBUG] Đã cập nhật level " + newLevel + " cho base " + baseId);
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("[ERROR] Lỗi khi cập nhật level base: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static boolean saveBaseData(Map<String, BaseData.BaseClaim> allBases, Player player) {
        try (Writer writer = new FileWriter(BaseData.getBaseFile())) {
            new GsonBuilder()
                    .registerTypeAdapter(Location.class, new BaseData.LocationAdapter())
                    .setPrettyPrinting()
                    .create()
                    .toJson(allBases, writer);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            player.sendMessage("§cĐã xảy ra lỗi khi lưu base.");
            return false;
        }
    }


    public static int getMaxBaseLevel() {
        return 10; // Hoặc đọc từ config
    }

    public static String getSchematicForLevel(int level) {
        // Tên file schematic theo level
        return "base_level_" + level + ".schem";
    }

    public static boolean canUpgradeBase(Guild guild, int currentLevel, int nextLevel) {
        // Kiểm tra điều kiện upgrade
        UpgradeRequirement req = getUpgradeRequirement(nextLevel);

        // Kiểm tra số thành viên
        if (guild.getMembers().size() < req.minMembers) {
            return false;
        }

        // Kiểm tra tiền guild (nếu có hệ thống economy)
        // if (guild.getMoney() < req.cost) return false;

        // Kiểm tra items trong kho guild (nếu có)
        // if (!guild.hasItems(req.requiredItems)) return false;

        return true;
    }

    public static void consumeUpgradeResources(Guild guild, int level) {
        // Trừ resources khi upgrade thành công
        UpgradeRequirement req = getUpgradeRequirement(level);

        // guild.removeMoney(req.cost);
        // guild.removeItems(req.requiredItems);

        // Lưu lại guild data
    }

    public static UpgradeRequirement getUpgradeRequirement(int level) {
        // Định nghĩa điều kiện upgrade cho từng level
        UpgradeRequirement req = new UpgradeRequirement();

        switch (level) {
            case 1:
                req.minMembers = 3;
                req.cost = 1000;
                break;
            case 2:
                req.minMembers = 5;
                req.cost = 2500;
                break;
            case 3:
                req.minMembers = 8;
                req.cost = 5000;
                break;
            case 4:
                req.minMembers = 12;
                req.cost = 10000;
                break;
            case 5:
                req.minMembers = 15;
                req.cost = 20000;
                break;
            default:
                req.minMembers = level * 3;
                req.cost = level * level * 1000;
                break;
        }

        return req;
    }

    public static void showUpgradeRequirements(Player player, int level) {
        UpgradeRequirement req = getUpgradeRequirement(level);

        player.sendMessage("§c§l=== ĐIỀU KIỆN UPGRADE LEVEL " + level + " ===");
        player.sendMessage("§7• Số thành viên tối thiểu: §f" + req.minMembers);
        player.sendMessage("§7• Chi phí: §6" + req.cost + " coins");
        // Thêm các điều kiện khác nếu có
    }

    // Class để lưu điều kiện upgrade
    public static class UpgradeRequirement {
        public int minMembers;
        public int cost;
        // Có thể thêm Map<Material, Integer> requiredItems;
    }


    //STORAGE
    public Map<String, List<UUID>> getItemRequestMap(String guildName) {
        return itemRequests.getOrDefault(guildName, new HashMap<>());
    }



    public void requestItem(String guildName, ItemStack item, UUID playerId) {
        String key = itemKey(item);

        itemRequests
                .computeIfAbsent(guildName, g -> new HashMap<>())
                .computeIfAbsent(key, k -> new ArrayList<>())
                .add(playerId);
    }

    public List<UUID> getItemRequesters(String guildName, ItemStack item) {
        Map<String, List<UUID>> guildMap = itemRequests.get(guildName);
        if (guildMap == null) return new ArrayList<>();
        return guildMap.getOrDefault(itemKey(item), new ArrayList<>());
    }


    public void clearItemRequest(String guildName, ItemStack item) {
        Map<String, List<UUID>> guildMap = itemRequests.get(guildName);
        if (guildMap == null) return;

        guildMap.remove(itemKey(item));

        if (guildMap.isEmpty()) {
            itemRequests.remove(guildName);
        }
    }

    public String itemKey(ItemStack item) {
        return item.getType().toString() + ":" + item.getDurability();
    }

    public void approveRequest(Player leader, ItemStack item) {
        Guild g = getPlayerGuild(leader);
        List<UUID> requesters = getItemRequesters(g.getName(), item);

        if (requesters.isEmpty()) {
            leader.sendMessage("§cKhông có ai xin item này.");
            return;
        }

        UUID approved = requesters.get(0);
        Player approvedPlayer = Bukkit.getPlayer(approved);
        if (approvedPlayer != null) {
            approvedPlayer.getInventory().addItem(item);
            approvedPlayer.sendMessage("§aYêu cầu item của bạn đã được duyệt.");
        }

        // Tự động từ chối các người còn lại
        for (int i = 1; i < requesters.size(); i++) {
            UUID denied = requesters.get(i);
            Player deniedPlayer = Bukkit.getPlayer(denied);
            if (deniedPlayer != null) {
                deniedPlayer.sendMessage("§cYêu cầu item của bạn bị từ chối (người khác đã được duyệt).");
            }
        }

        clearItemRequest(g.getName(), item);
    }

    public ItemStack getItemFromStorageByKey(Guild guild, String key) {
        if (guild == null) return null;

        Inventory storage = guild.getSharedStorageInventory();
        if (storage == null) return null;

        for (ItemStack item : storage.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (itemKey(item).equals(key)) {
                return item;
            }
        }
        return null;
    }

    public void removeItemFromStorage(Guild guild, ItemStack item) {
        Inventory storage = guild.getSharedStorageInventory();

        for (int i = 0; i < storage.getSize(); i++) {
            ItemStack s = storage.getItem(i);
            if (s == null) continue;

            if (itemKey(s)
                    .equals(itemKey(item))) {
                storage.setItem(i, null);
                return;
            }
        }
    }


    public static void clearBaseAreaAsync(JavaPlugin plugin, String baseId, BaseData.BaseClaim base) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (base == null || base.pos1 == null || base.pos2 == null || base.pos1.getWorld() == null) {
                    Bukkit.getLogger().warning("[BaseClear] Debug base: base=" + base +
                            ", pos1=" + (base != null ? base.pos1 : "null") +
                            ", pos2=" + (base != null ? base.pos2 : "null") +
                            ", world=" + (base != null && base.pos1 != null ? base.pos1.getWorld() : "null"));
                    Bukkit.getLogger().warning("[BaseClear] Dữ liệu base không hợp lệ cho: " + baseId);
                    return;
                }

                World world = base.pos1.getWorld();
                com.sk89q.worldedit.world.World weWorld = FaweAPI.getWorld(world.getName());
                if (weWorld == null) {
                    Bukkit.getLogger().warning("[BaseClear] Không thể lấy FAWE world cho: " + world.getName());
                    return;
                }

                BlockVector3 min = BlockVector3.at(
                        Math.min(base.pos1.getBlockX(), base.pos2.getBlockX()),
                        Math.min(base.pos1.getBlockY(), base.pos2.getBlockY()),
                        Math.min(base.pos1.getBlockZ(), base.pos2.getBlockZ())
                );

                BlockVector3 max = BlockVector3.at(
                        Math.max(base.pos1.getBlockX(), base.pos2.getBlockX()),
                        Math.max(base.pos1.getBlockY(), base.pos2.getBlockY()) + 20,
                        Math.max(base.pos1.getBlockZ(), base.pos2.getBlockZ())
                );

                CuboidRegion region = new CuboidRegion(weWorld, min, max);

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                            editSession.setFastMode(true);
                            editSession.setReorderMode(EditSession.ReorderMode.MULTI_STAGE);
                            editSession.setBlocks(region, BlockTypes.AIR.getDefaultState());
                            Bukkit.getLogger().info("[BaseClear] Đã xoá thành công công trình trong base '" + baseId + "'");
                        } catch (Exception e) {
                            Bukkit.getLogger().severe("[BaseClear] Lỗi khi xoá base: " + baseId);
                            e.printStackTrace();
                        }
                    }
                }.runTask(plugin);
            }
        }.runTaskAsynchronously(plugin);
    }

    public void upgradeBaseAsync(Player player, Guild guild, String baseId, int nextLevel) {
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    String schematicName = getSchematicForLevel(nextLevel);
                    Map<String, BaseData.BaseClaim> bases = BaseData.loadBases();
                    BaseData.BaseClaim baseClaim = bases.get(baseId);

                    if (baseClaim == null) {
                        player.sendMessage("§cKhông tìm thấy base để upgrade.");
                        return;
                    }

                    double centerX = (baseClaim.pos1.getX() + baseClaim.pos2.getX()) / 2.0;
                    double centerY = (baseClaim.pos1.getY() + baseClaim.pos2.getY()) / 2.0;
                    double centerZ = (baseClaim.pos1.getZ() + baseClaim.pos2.getZ()) / 2.0;
                    Location centerLocation = new Location(baseClaim.pos1.getWorld(), centerX, centerY, centerZ);

                    File schematicFile = new File(plugin.getDataFolder(), "schem/" + schematicName);
                    ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                    if (format == null) throw new IllegalArgumentException("Định dạng schematic không hợp lệ");

                    Clipboard clipboard;
                    try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                        clipboard = reader.read();
                    }

                    Region region = clipboard.getRegion();
                    int width = region.getWidth();
                    int height = region.getHeight();
                    int length = region.getLength();

                    Location playerLoc = player.getLocation();
                    BlockVector3 pasteLocation = BlockVector3.at(
                            (int) Math.floor(centerLocation.getX()),
                            (int) playerLoc.getY(),
                            (int) Math.floor(centerLocation.getZ()));

                    com.sk89q.worldedit.world.World weWorld = FaweAPI.getWorld(centerLocation.getWorld().getName());
                    if (weWorld == null) throw new IllegalStateException("FAWE world không tồn tại");

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            try (EditSession editSession = WorldEdit.getInstance().newEditSession(weWorld)) {
                                ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);
                                Operation operation = clipboardHolder.createPaste(editSession)
                                        .to(pasteLocation)
                                        .ignoreAirBlocks(false)
                                        .build();
                                Operations.complete(operation);

                                // ✅ Các bước sau khi paste xong
                                updateBaseLevel(baseId, nextLevel);
                                consumeUpgradeResources(guild, nextLevel);

                                Location holoLoc = centerLocation.clone().add(0, 2, 0);
                                BaseHologramManager.updateBaseHologram(baseId, guild);

                                player.sendMessage("§a§lUPGRADE THÀNH CÔNG!");
                                player.sendMessage("§eBase đã được nâng cấp lên level §6" + nextLevel);
                                player.sendMessage("§7Kích thước schematic: §f" + width + "x" + height + "x" + length);

                                broadcastToGuild(plugin, guild,
                                        "§6" + player.getName() + " §ađã nâng cấp base lên level §6" + nextLevel + "§a!");

                            } catch (Exception e) {
                                player.sendMessage("§cLỗi khi paste schematic: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }.runTask(plugin);

                } catch (Exception e) {
                    player.sendMessage("§cLỗi khi chuẩn bị upgrade: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public static boolean isPlayerInsideBase(Player player, BaseData.BaseClaim base) {
        if (player == null || base == null || base.pos1 == null || base.pos2 == null) return false;

        Location loc = player.getLocation();
        Location pos1 = base.pos1;
        Location pos2 = base.pos2;

        // Kiểm tra cùng thế giới
        if (!loc.getWorld().getName().equals(pos1.getWorld().getName())) return false;

        // Lấy min/max cho từng trục
        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX());
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 50;
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ());

        // Kiểm tra tọa độ người chơi có nằm trong vùng không
        return loc.getX() >= minX && loc.getX() <= maxX &&
                loc.getY() >= minY && loc.getY() <= maxY &&
                loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    public static boolean isPlayerInTheirOwnBase(Player player) {
        if (player == null) return false;

        Guild guild = GuildManager.getInstance().getPlayerGuild(player);
        if (guild == null || guild.getBaseID() == null) return false;

        BaseData.BaseClaim base = BaseData.getBaseClaim(guild.getBaseID());
        if (base == null) return false;

        return isPlayerInsideBase(player, base);
    }

    //api public
    public boolean isInGuildChat(UUID playerId) {
        return guildChatMode.contains(playerId);
    }

    public static boolean hasGuildWithBase(Player player) {
        if (player == null) return false;

        Guild guild = GuildManager.getInstance().getPlayerGuild(player);
        if (guild == null) return false;

        String baseId = guild.getBaseID();
        if (baseId == null || BaseData.getBaseClaim(baseId) == null) return false;

        return true;
    }

    public static Location getSafeRandomLocationInOwnBase(Player player) {
        if (player == null) return null;

        Guild guild = GuildManager.getInstance().getPlayerGuild(player);
        if (guild == null || guild.getBaseID() == null) return null;

        BaseData.BaseClaim base = BaseData.getBaseClaim(guild.getBaseID());
        if (base == null || base.pos1 == null || base.pos2 == null) return null;

        Location pos1 = base.pos1;
        Location pos2 = base.pos2;

        World world = pos1.getWorld();
        if (world == null || !world.equals(pos2.getWorld())) return null;

        int minX = Math.min(pos1.getBlockX(), pos2.getBlockX());
        int maxX = Math.max(pos1.getBlockX(), pos2.getBlockX());
        int minZ = Math.min(pos1.getBlockZ(), pos2.getBlockZ());
        int maxZ = Math.max(pos1.getBlockZ(), pos2.getBlockZ());

        Random random = new Random();
        int tries = 0, maxTries = 30;

        while (tries < maxTries) {
            int x = random.nextInt(maxX - minX + 1) + minX;
            int z = random.nextInt(maxZ - minZ + 1) + minZ;
            int y = world.getHighestBlockYAt(x, z);

            Location checkLoc = new Location(world, x + 0.5, y, z + 0.5);
            Material ground = checkLoc.clone().subtract(0, 1, 0).getBlock().getType();
            Material head = checkLoc.getBlock().getType();
            Material above = checkLoc.clone().add(0, 1, 0).getBlock().getType();

            // Điều kiện an toàn:
            boolean solidGround = ground.isSolid() && !ground.name().toLowerCase().contains("lava") && !ground.name().toLowerCase().contains("water");
            boolean hasHeadroom = head == Material.AIR && above == Material.AIR;

            if (solidGround && hasHeadroom) {
                return checkLoc;
            }

            tries++;
        }

        Bukkit.getLogger().warning("[BaseSpawn] Không tìm được vị trí an toàn trong base sau " + maxTries + " lần thử.");
        return null;
    }

    public static Location getFirstChestInBase(BaseData.BaseClaim base) {
        if (base == null || base.pos1 == null || base.pos2 == null) return null;

        World world = base.pos1.getWorld();
        if (world == null || !world.equals(base.pos2.getWorld())) return null;

        int minX = Math.min(base.pos1.getBlockX(), base.pos2.getBlockX());
        int maxX = Math.max(base.pos1.getBlockX(), base.pos2.getBlockX());
        int minZ = Math.min(base.pos1.getBlockZ(), base.pos2.getBlockZ());
        int maxZ = Math.max(base.pos1.getBlockZ(), base.pos2.getBlockZ());

        int minY = -64;
        int maxY = 80;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Chunk chunk = world.getChunkAt(x >> 4, z >> 4);
                    if (!chunk.isLoaded()) chunk.load(true);

                    Location loc = new Location(world, x, y, z);
                    Material type = loc.getBlock().getType();

                    if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
                        Bukkit.getLogger().info("[DEBUG] Đã tìm thấy chest tại: " + loc);
                        return loc;
                    }
                }
            }
        }

        Bukkit.getLogger().warning("[DEBUG] Không tìm thấy chest nào trong base.");
        return null;
    }

    public static Guild getGuildByBaseId(String baseId) {
        for (Guild guild : guilds.values()) {
            String guildBaseId = guild.getBaseID();
            if (guildBaseId == null) {
                Bukkit.getLogger().warning("[DEBUG] Guild '" + guild.getName() + "' chưa có baseID.");
                continue;
            }

            if (guildBaseId.equals(baseId)) {
                return guild;
            }
        }

        return null;
    }

    private void refreshGuildGUIs(Guild guild) {

        if (guild == null)
            return;

        for (UUID memberId : guild.getMembers().keySet()) {

            Player player = Bukkit.getPlayer(memberId);

            if (player == null || !player.isOnline())
                continue;

            Inventory inventory =
                    player.getOpenInventory().getTopInventory();

            if (!(inventory.getHolder() instanceof GUIHolder holder))
                continue;

            if (holder.getType() != GUIType.GUILD_INFO
                    && holder.getType() != GUIType.DONATE)
                continue;

            GuildGUI.refreshGuildInfoGUI(
                    player,
                    guild
            );
        }
    }
}
