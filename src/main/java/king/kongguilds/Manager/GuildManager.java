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
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import king.kongguilds.Base.BaseData;
import king.kongguilds.Model.Guild;
import king.kongguilds.Model.Member;
import king.kongguilds.Model.TimedInvite;
import king.kongguilds.WorldGuard.BaseRegionManager;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

public class GuildManager {
    private final JavaPlugin plugin;
    private final Map<String, Guild> guilds = new HashMap<>();
    private final File guildFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, TimedInvite> pendingInvites = new HashMap<>();
    private final Map<String, UUID> guildStorageLock = new HashMap<>();
    private final MessageManager messageManager;
    private final Set<UUID> guildChatMode = new HashSet<>();
    private static final Map<String, Hologram> hologramMap = new HashMap<>();
    private final Map<String, List<UUID>> itemRequests = new HashMap<>();

    public GuildManager(JavaPlugin plugin, MessageManager messageManager) {
        this.plugin = plugin; // <- thêm dòng này
        this.messageManager = messageManager;
        this.guildFolder = new File(plugin.getDataFolder(), "Guilds");
        createGuildFolder();
        loadGuilds();
    }


    private void createGuildFolder() {
        if (!guildFolder.exists()) {
            guildFolder.mkdirs();
        }
    }

    public boolean createGuild(String name, Player leader) {
        if (guilds.containsKey(name)) return false;
        Guild guild = new Guild(name, leader.getUniqueId(), plugin);
        guilds.put(name, guild);
        return true;
    }


    public void removeGuild(String name) {
        guilds.remove(name);
        File guildFile = new File(guildFolder, name + ".json");
        if (guildFile.exists()) {
            guildFile.delete();
        }
    }

    public Guild getGuild(String name) {
        return guilds.get(name);
    }

    public List<Guild> getAllGuilds() {
        return new ArrayList<>(guilds.values());
    }

    public void loadGuilds() {
        // Tạo Gson với custom UUID adapter
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(UUID.class, new UUIDAdapter())
                .create();

        File[] files = guildFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    Guild guild = gson.fromJson(reader, Guild.class);

                    // Kiểm tra và sửa UUID null
                    validateAndFixGuildUUIDs(guild);

                    guild.setPlugin(plugin); // tạo inventory kho chung
                    guild.loadSharedStorage(); // load kho chung từ base64
                    guilds.put(guild.getName(), guild);

                    Bukkit.getLogger().info("Loaded guild: " + guild.getName() +
                            " with leader UUID: " + guild.getLeader());
                } catch (IOException e) {
                    Bukkit.getLogger().warning("Error loading guild from file: " + file.getName());
                    e.printStackTrace();
                }
            }
        }
    }
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

    public void saveGuild(String name) {
        Guild guild = guilds.get(name);
        if (guild != null) {
            guild.saveSharedStorage(); // cập nhật base64 mới từ inventory
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
            BaseRegionManager.removeMemberFromRegion(guild.getBaseID(), member.getName(), world);
        } else {
            Bukkit.getLogger().info("Không tìm thấy world cho baseID: " + guild.getBaseID());
        }

        guild.kickMember(targetId);
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

        guild.kickMember(targetId);
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

    public void updateMemberPoints(String guildName, UUID memberId, int points) {
        Guild guild = getGuild(guildName);
        if (guild != null && guild.hasMember(memberId)) {
            Member info = guild.getMembers().get(memberId);
            // Tạo Member mới giữ nguyên uuid, name, joinDate, rank, cập nhật điểm (score)
            int newScore = info.getScore() + points;

            Member updatedMember = new Member(
                    info.getUUID(),
                    info.getName(),
                    info.getJoinDate(),
                    info.getRank(),
                    newScore
            );

            guild.getMembers().put(memberId, updatedMember);
        }
    }


    public void addExpToGuild(Player player, int amount) {
        Guild guild = getPlayerGuild(player);
        if (guild != null) {
            guild.addExp(amount);
            saveGuild(guild.getName());
        }
    }

    public UUID getGuildStorageLocker(String guildName) {
        return guildStorageLock.get(guildName);
    }

    public void lockGuildStorage(String guildName, UUID playerId) {
        guildStorageLock.put(guildName, playerId);
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

    public void sendGuildMessage(Guild guild, Player sender, String message) {
        Member senderInfo = guild.getMembers().get(sender.getUniqueId());
        String rankName = senderInfo != null ? senderInfo.getRank() : "Member";

        String format = messageManager.getMessage("member", "guild_chat_format", Map.of(
                "player", sender.getName(),
                "rank", rankName,
                "message", message
        ));

        for (UUID memberId : guild.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                // Xử lý PlaceholderAPI cho từng người nhận
                String parsedMessage = PlaceholderAPI.setPlaceholders(member, format);
                member.sendMessage(parsedMessage);
            }
        }
    }

    public void broadcastToGuild(Guild guild, String message) {
        for (UUID memberId : guild.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) {
                // Xử lý PlaceholderAPI cho từng người nhận
                String parsedMessage = PlaceholderAPI.setPlaceholders(member, message);
                member.sendMessage(parsedMessage);
            }
        }
    }

    public boolean isInGuildChat(UUID playerId) {
        return guildChatMode.contains(playerId);
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
        double zMin = Math.min(pos1.getZ(), pos2.getZ());
        double zMax = Math.max(pos1.getZ(), pos2.getZ());

        return (point.getX() >= xMin && point.getX() <= xMax) &&
                (point.getZ() >= zMin && point.getZ() <= zMax);
    }

    public static BlockVector3 pasteSchematicAtBase(JavaPlugin plugin, String baseId, String fileName) {
        Bukkit.getLogger().info("[DEBUG] Bắt đầu paste schematic tại base: " + baseId);

        try {
            // Đọc dữ liệu base từ file JSON
            Map<String, BaseData.BaseClaim> bases = BaseData.loadBases();
            BaseData.BaseClaim baseClaim = bases.get(baseId);

            if (baseClaim == null) {
                throw new IllegalArgumentException("Không tìm thấy base với ID: " + baseId);
            }

            // Tính toán center của base từ pos1 và pos2
            double centerX = (baseClaim.pos1.getX() + baseClaim.pos2.getX()) / 2.0;
            double centerY = (baseClaim.pos1.getY() + baseClaim.pos2.getY()) / 2.0;
            double centerZ = (baseClaim.pos1.getZ() + baseClaim.pos2.getZ()) / 2.0;

            Bukkit.getLogger().info("[DEBUG] Base pos1: " + baseClaim.pos1.getX() + ", " + baseClaim.pos1.getY() + ", " + baseClaim.pos1.getZ());
            Bukkit.getLogger().info("[DEBUG] Base pos2: " + baseClaim.pos2.getX() + ", " + baseClaim.pos2.getY() + ", " + baseClaim.pos2.getZ());
            Bukkit.getLogger().info("[DEBUG] Base center: " + centerX + ", " + centerY + ", " + centerZ);

            // Tạo Location tại center của base
            World world = baseClaim.pos1.getWorld();
            if (world == null) {
                throw new IllegalArgumentException("World không tồn tại: " + baseClaim.pos1.getWorld().getName());
            }

            Location centerLocation = new Location(world, centerX, centerY, centerZ);

            // Gọi phương thức paste schematic
            return pasteSchematicAtLocation(plugin, centerLocation, fileName);

        } catch (Exception e) {
            Bukkit.getLogger().severe("[ERROR] Lỗi khi paste schematic tại base: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Lỗi khi paste schematic tại base: " + e.getMessage(), e);
        }
    }

    public static BlockVector3 pasteSchematicAtLocation(JavaPlugin plugin, Location centerLocation, String fileName) {
        Bukkit.getLogger().info("[DEBUG] Bắt đầu paste schematic: " + fileName);
        Bukkit.getLogger().info("[DEBUG] Tại vị trí: " + centerLocation.getX() + ", " + centerLocation.getY() + ", " + centerLocation.getZ());

        try {
            File schematicFile = new File(plugin.getDataFolder(), "schem/" + fileName);
            Bukkit.getLogger().info("[DEBUG] Đường dẫn schematic: " + schematicFile.getAbsolutePath());

            if (!schematicFile.exists() || !schematicFile.isFile()) {
                throw new IllegalArgumentException("Schematic file không tồn tại: " + schematicFile.getPath());
            }

            World world = centerLocation.getWorld();
            if (world == null) {
                throw new IllegalArgumentException("World không hợp lệ (null)");
            }

            com.sk89q.worldedit.world.World faweWorld = FaweAPI.getWorld(world.getName());
            if (faweWorld == null) {
                throw new IllegalStateException("Không tìm thấy FAWE World cho: " + world.getName());
            }

            Clipboard clipboard;
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                throw new IllegalArgumentException("Không xác định được định dạng schematic file");
            }

            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                clipboard = reader.read();
            }

            Region region = clipboard.getRegion();
            int width = region.getWidth();
            int height = region.getHeight();
            int length = region.getLength();

            Bukkit.getLogger().info("[DEBUG] Kích thước schematic: " + width + "x" + height + "x" + length);

            // Kiểm tra số block không phải air
            int nonAirBlocks = 0;
            for (BlockVector3 point : region) {
                BaseBlock block = clipboard.getFullBlock(point);
                if (!block.getBlockType().getId().equals("minecraft:air")) {
                    nonAirBlocks++;
                }
            }
            Bukkit.getLogger().info("[DEBUG] Số block không phải air: " + nonAirBlocks);

            // Tính center thực sự của schematic (bao gồm cả Y)
            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            BlockVector3 schematicCenter = BlockVector3.at(
                    (min.getBlockX() + max.getBlockX()) / 2,
                    (min.getBlockY() + max.getBlockY()) / 2,
                    (min.getBlockZ() + max.getBlockZ()) / 2
            );

            int offsetX = schematicCenter.getX() - min.getX();
            int offsetY = schematicCenter.getY() - min.getY();
            int offsetZ = schematicCenter.getZ() - min.getZ();

            // Nếu bị lệch về west 1 block, +1 vào X
            BlockVector3 pasteLocation = BlockVector3.at(
                    (int) Math.floor(centerLocation.getX()) - offsetX,
                    (int) Math.floor(centerLocation.getY()) - offsetY,
                    (int) Math.floor(centerLocation.getZ()) - offsetZ + 3
            );


            Bukkit.getLogger().info("[DEBUG] Offset X,Y,Z: " + offsetX + ", " + offsetY + ", " + offsetZ);

            Bukkit.getLogger().info("[DEBUG] Min point: " + min);
            Bukkit.getLogger().info("[DEBUG] Max point: " + max);
            Bukkit.getLogger().info("[DEBUG] Schematic center: " + schematicCenter);
            Bukkit.getLogger().info("[DEBUG] Base center location: " + centerLocation.getX() + ", " + centerLocation.getY() + ", " + centerLocation.getZ());
            Bukkit.getLogger().info("[DEBUG] Final paste location: " + pasteLocation);

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(faweWorld)) {
                ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);
                Operation operation = clipboardHolder
                        .createPaste(editSession)
                        .to(pasteLocation)
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
                Bukkit.getLogger().info("[DEBUG] Paste schematic thành công tại base center!");
            }

            return BlockVector3.at(width, height, length);

        } catch (Exception e) {
            Bukkit.getLogger().severe("[ERROR] Lỗi khi paste schematic: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Lỗi khi xử lý schematic file: " + e.getMessage(), e);
        }
    }

    // Thêm method để có thể điều chỉnh offset Y nếu cần
    public static BlockVector3 pasteSchematicAtLocationWithOffset(JavaPlugin plugin, Location centerLocation, String fileName, int yOffset) {
        Bukkit.getLogger().info("[DEBUG] Bắt đầu paste schematic với Y offset: " + yOffset);

        // Điều chỉnh centerLocation với offset Y
        Location adjustedLocation = centerLocation.clone();
        adjustedLocation.setY(centerLocation.getY() + yOffset);

        return pasteSchematicAtLocation(plugin, adjustedLocation, fileName);
    }

    // Method paste tại vị trí player đứng (thay vì center của base)
    public static BlockVector3 pasteSchematicAtPlayer(JavaPlugin plugin, Player player, String fileName) {
        Bukkit.getLogger().info("[DEBUG] Paste schematic tại vị trí player: " + player.getName());

        Location playerLocation = player.getLocation();
        // Căn chỉnh vào center của block
        Location centerLocation = new Location(
                playerLocation.getWorld(),
                Math.floor(playerLocation.getX()) + 0.5,
                Math.floor(playerLocation.getY()),
                Math.floor(playerLocation.getZ()) + 0.5
        );

        return pasteSchematicAtLocation(plugin, centerLocation, fileName);
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
    public Map<String, List<UUID>> getItemRequestMap() {
        return this.itemRequests;
    }

    public void requestItem(String guildName, ItemStack item, UUID playerId) {
        String key = itemKey(item);
        itemRequests.putIfAbsent(key, new ArrayList<>());
        if (!itemRequests.get(key).contains(playerId)) {
            itemRequests.get(key).add(playerId);
        }
    }

    public List<UUID> getItemRequesters(String guildName, ItemStack item) {
        return itemRequests.getOrDefault(itemKey(item), new ArrayList<>());
    }

    public void clearItemRequest(String guildName, ItemStack item) {
        itemRequests.remove(itemKey(item));
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

}
