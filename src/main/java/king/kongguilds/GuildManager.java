package king.kongguilds;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class GuildManager {
    private final JavaPlugin plugin;
    private final Map<String, Guild> guilds = new HashMap<>();
    private final File guildFolder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<UUID, TimedInvite> pendingInvites = new HashMap<>();
    private final Map<String, UUID> guildStorageLock = new HashMap<>();

    public GuildManager(JavaPlugin plugin) {
        this.plugin = plugin; // <- thêm dòng này
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


    public void kickMember(String guildName, UUID memberId) {
        Guild guild = getGuild(guildName);
        if (guild != null && guild.hasMember(memberId)) {
            guild.kickMember(memberId);
        }
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

}
