package king.kongguilds.Model;

import king.kongguilds.Config.GuildConfig;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

public class Guild {
    private String name;
    private UUID leader;
    private Map<UUID, MemberInfo> members = new HashMap<>();

    // Dùng base64 lưu kho chung trong file JSON
    private String sharedStorageBase64 = "";

    // Inventory kho chung (transient, không serialize)
    private transient Inventory sharedStorageInventory;

    private int level = 1;
    private int exp = 0;

    private transient JavaPlugin plugin;

    public Guild(String name, UUID leader, JavaPlugin plugin) {
        this.name = name;
        this.leader = leader;
        this.plugin = plugin;

        // Đưa leader vào members với rank LEADER
        addMember(leader, name, Guild.RankType.LEADER, "Leader", 0);

        // Tạo inventory kho chung 27 slot
        sharedStorageInventory = Bukkit.createInventory(null, 27, "Kho chung guild: " + name);

        // Load kho chung từ base64 nếu có
        loadSharedStorage();
    }


    public void setPlugin(JavaPlugin plugin) {
        this.plugin = plugin;

        // Tạo inventory nếu null (khi load lại từ file)
        if (sharedStorageInventory == null) {
            sharedStorageInventory = Bukkit.createInventory(null, 27, "Kho chung guild: " + name);
        }

        loadSharedStorage();
    }

    public String getName() {
        return name;
    }

    public UUID getLeader() {
        return leader;
    }

    public int getLevel() {
        return level;
    }

    public int getExp() {
        return exp;
    }

    public void addExp(int amount) {
        if (amount <= 0) return;
        this.exp += amount;
        while (exp >= getNextLevelExp()) {
            if (level >= GuildConfig.getMaxLevel()) {
                exp = getNextLevelExp(); // cap lại EXP
                break;
            }
            level++;
            plugin.getLogger().info("Guild " + name + " leveled up to " + level + "!");
        }
    }

    public void addLevel(int amount) {
        if (amount <= 0) return;
        this.level += amount;
        int maxLevel = GuildConfig.getMaxLevel();
        if (this.level > maxLevel) this.level = maxLevel;
        plugin.getLogger().info("Guild " + name + " được tăng level lên " + level);
    }


    public int getNextLevelExp() {
        return GuildConfig.getExpForLevel(level + 1);
    }

    public Map<UUID, MemberInfo> getMembers() {
        return members;
    }

    public void addMember(UUID memberId, String name, RankType rank, String memberType, int points) {
        if (name == null || name.isEmpty()) {
            name = Bukkit.getOfflinePlayer(memberId).getName();
        }
        members.put(memberId, new MemberInfo(name, rank, memberType, points));
    }



    public void kickMember(UUID memberId) {
        members.remove(memberId);
    }

    public boolean hasMember(UUID memberId) {
        return members.containsKey(memberId);
    }

    public List<Map.Entry<UUID, MemberInfo>> getSortedMembers() {
        List<Map.Entry<UUID, MemberInfo>> sorted = new ArrayList<>(members.entrySet());
        sorted.sort((a, b) -> {
            if (a.getKey().equals(leader)) return -1;
            if (b.getKey().equals(leader)) return 1;
            return Integer.compare(b.getValue().getPoints(), a.getValue().getPoints());
        });
        return sorted;
    }

    // ---------------- KHO CHUNG ------------------

    private void createSharedStorageInventory() {
        // Tạo inventory 27 slots cho kho chung, tiêu chuẩn
        this.sharedStorageInventory = Bukkit.createInventory(null, 27, "Kho Chung Guild " + name);
        // **Không gọi loadSharedStorage() ở đây!**
    }


    public Inventory getSharedStorageInventory() {
        if (sharedStorageInventory == null) {
            createSharedStorageInventory();
            loadSharedStorage();  // Nên gọi load sau khi tạo inventory
        }
        return sharedStorageInventory;
    }


    // Load kho chung từ base64 string -> inventory
    public void loadSharedStorage() {
        if (sharedStorageBase64 == null || sharedStorageBase64.isEmpty()) return;

        try {
            byte[] data = Base64.getDecoder().decode(sharedStorageBase64);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            BukkitObjectInputStream bois = new BukkitObjectInputStream(bais);

            ItemStack[] items = (ItemStack[]) bois.readObject();
            bois.close();

            createSharedStorageInventory();  // Tạo mới inventory
            sharedStorageInventory.setContents(items);  // Set items vào inventory

        } catch (Exception e) {
            plugin.getLogger().warning("Lỗi khi load kho chung guild " + name + ": " + e.getMessage());
        }
    }


    // Lưu kho chung inventory -> base64 string
    public void saveSharedStorage() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            BukkitObjectOutputStream boos = new BukkitObjectOutputStream(baos);

            boos.writeObject(sharedStorageInventory.getContents());
            boos.flush();
            boos.close();

            sharedStorageBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().warning("Lỗi khi save kho chung guild " + name + ": " + e.getMessage());
        }
    }

    public static class MemberInfo {
        private String name;
        private RankType rank;
        private String memberType;
        private int points;

        public MemberInfo(String name, String rankName, String memberType, int points) {
            this.name = name;
            this.rank = RankType.valueOf(rankName.toUpperCase().replace("-", "_")); // fallback xử lý
            this.memberType = memberType;
            this.points = points;
        }

        public MemberInfo(String name, RankType rank, String memberType, int points) {
            this.name = name;
            this.rank = rank;
            this.memberType = memberType;
            this.points = points;
        }

        public String getName() { return name; }
        public RankType getRank() { return rank; }
        public String getRankName() { return rank.getDisplayName(); }
        public String getMemberType() { return memberType; }
        public int getPoints() { return points; }

        public void setRank(RankType newRank) {
            this.rank = newRank;
        }
    }


    public enum RankType {
        MEMBER(1, "Member"),
        OFFICER(2, "Officer"),
        MODERATOR(3, "Moderator"),
        COLEADER(4, "Co-Leader"),
        LEADER(5, "Leader");

        private final int level;
        private final String displayName;

        RankType(int level, String displayName) {
            this.level = level;
            this.displayName = displayName;
        }

        public int getLevel() {
            return level;
        }

        public String getDisplayName() {
            return displayName;
        }

        public static RankType fromLevel(int level) {
            for (RankType r : values()) {
                if (r.level == level) return r;
            }
            return MEMBER; // fallback
        }
    }

}
