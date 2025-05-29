package king.kongguilds;

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
import com.sk89q.worldedit.session.ClipboardHolder;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import king.kongguilds.BASE.BaseData;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.*;

import static king.kongguilds.BASE.BaseData.loadBases;

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
        File[] files = guildFolder.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try (FileReader reader = new FileReader(file)) {
                    Guild guild = gson.fromJson(reader, Guild.class);
                    guild.setPlugin(plugin); // tạo inventory kho chung
                    guild.loadSharedStorage(); // load kho chung từ base64
                    guilds.put(guild.getName(), guild);
                } catch (IOException e) {
                    e.printStackTrace();
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
        }
    }

    public boolean kickMemberAsAdmin(String guildName, UUID targetId) {
        Guild guild = getGuild(guildName);
        if (guild == null || !guild.hasMember(targetId)) return false;

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

        Guild.MemberInfo kickerInfo = guild.getMembers().get(kickerId);
        Guild.MemberInfo targetInfo = guild.getMembers().get(targetId);

        Guild.RankType kickerRank = kickerInfo.getRank();
        Guild.RankType targetRank = targetInfo.getRank();

        if (kickerRank != Guild.RankType.LEADER && kickerRank != Guild.RankType.OFFICER) {
            kicker.sendMessage("§cChỉ Leader hoặc Officer mới có quyền kick!");
            return;
        }

        if (targetRank == Guild.RankType.LEADER) {
            kicker.sendMessage("§cKhông thể kick Leader!");
            return;
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

        Guild.MemberInfo info = guild.getMembers().get(player.getUniqueId());
        return info != null && info.getMemberType().equalsIgnoreCase("Co-Leader");
    }

    public void updateMemberPoints(String guildName, UUID memberId, int points) {
        Guild guild = getGuild(guildName);
        if (guild != null && guild.hasMember(memberId)) {
            Guild.MemberInfo info = guild.getMembers().get(memberId);
            guild.getMembers().put(memberId, new Guild.MemberInfo(
                    info.getName(), info.getRankName(), info.getMemberType(), points
            ));
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

    public void unlockGuildStorage(String guildName) {
        guildStorageLock.remove(guildName);
    }

    public boolean openSharedStorage(Player player) {
        Guild guild = getPlayerGuild(player);
        if (guild == null) {
            player.sendMessage("§cBạn không thuộc guild nào!");
            return false;
        }

        String guildName = guild.getName();

        if (guildStorageLock.containsKey(guildName)) {
            player.sendMessage("§cKho chung đang được sử dụng, vui lòng chờ.");
            return false;
        }

        // Mở kho, khóa kho cho player đó
        guildStorageLock.put(guildName, player.getUniqueId());

        Inventory inv = guild.getSharedStorageInventory();
        player.openInventory(inv);
        return true;
    }

    public void handleSharedStorageClose(Player player) {
        Guild guild = getPlayerGuild(player);
        if (guild == null) return;

        Inventory inv = guild.getSharedStorageInventory();

        // Tính điểm dựa trên item bỏ vào
        int pointsGained = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null) continue;
            pointsGained += item.getAmount();
        }

        // Cập nhật điểm cho member
        updateMemberPoints(guild.getName(), player.getUniqueId(), pointsGained);

        // Lưu kho chung lại
        guild.saveSharedStorage();

        // Mở khóa kho
        unlockGuildStorage(guild.getName());
    }

    private int calculatePointsFromInventory(Inventory inv) {
        int points = 0;
        for (ItemStack item : inv.getContents()) {
            if (item == null) continue;
            points += item.getAmount();
        }
        return points;
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
        Guild.MemberInfo senderInfo = guild.getMembers().get(sender.getUniqueId());
        String rankName = senderInfo != null ? senderInfo.getRankName() : "Member";

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

    public static BlockVector3 pasteSchematic(JavaPlugin plugin, Location centerLocation, String fileName) {
        try {
            File schematicFile = new File(plugin.getDataFolder(), "schem/" + fileName);

            if (!schematicFile.exists() || !schematicFile.isFile()) {
                throw new IllegalArgumentException("Schematic file không tồn tại: " + schematicFile.getPath());
            }

            World world = centerLocation.getWorld();
            if (world == null) {
                throw new IllegalArgumentException("World không hợp lệ");
            }

            com.sk89q.worldedit.world.World faweWorld = FaweAPI.getWorld(world.getName());

            Clipboard clipboard;
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                throw new IllegalArgumentException("Không thể xác định định dạng schematic file");
            }
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                clipboard = reader.read();
            }

            int width = clipboard.getRegion().getWidth();
            int height = clipboard.getRegion().getHeight();
            int length = clipboard.getRegion().getLength();

            // Tính vị trí bắt đầu paste: lấy center trừ nửa kích thước schematic
            BlockVector3 pasteLocation = BlockVector3.at(
                    centerLocation.getBlockX() - width / 2,
                    centerLocation.getBlockY(),
                    centerLocation.getBlockZ() - length / 2
            );

            try (EditSession editSession = WorldEdit.getInstance().newEditSession(faweWorld)) {
                ClipboardHolder clipboardHolder = new ClipboardHolder(clipboard);
                Operation operation = clipboardHolder
                        .createPaste(editSession)
                        .to(pasteLocation)
                        .ignoreAirBlocks(false)
                        .build();

                Operations.complete(operation);
                editSession.flushQueue();
            }

            return BlockVector3.at(width, height, length);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Lỗi khi xử lý schematic file: " + e.getMessage(), e);
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


}
