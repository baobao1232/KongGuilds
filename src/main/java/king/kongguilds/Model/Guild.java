package king.kongguilds.Model;

import king.kongguilds.Config.GuildConfig;
import king.kongguilds.Gui.GUIHolder;
import king.kongguilds.Gui.GUIType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

import static king.kongguilds.Gui.GuildGUI.renderCommonGuildMenu;

public class Guild {
    private String name;
    private UUID leader;
    private Map<UUID, Member> members = new HashMap<>();

    // Dùng base64 lưu kho chung trong file JSON
    private String sharedStorageBase64 = "";

    // Inventory kho chung (transient, không serialize)
    private transient Inventory sharedStorageInventory;

    private int level = 1;
    private int exp = 0;
    private String baseID = null;
    private double money = 0.0;
    private transient JavaPlugin plugin;
    private String createDate; // yyyy-MM-dd

    public Guild(String name, UUID leader, JavaPlugin plugin) {
        this.name = name;
        this.leader = leader;
        this.plugin = plugin;
        this.createDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());

        // Đưa leader vào members với rank LEADER
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(leader);
        String playerName = offlinePlayer.getName();
        addMember(leader, playerName, Guild.RankType.LEADER, "Leader", 0);

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

    public String getCreateDate() {
        return createDate;
    }

    public int getLevel() {
        return level;
    }

    public int getExpToNextLevel() {
        int nextExp = GuildConfig.getExpForLevel(level + 1);
        return Math.max(0, nextExp - exp);
    }

    public double getMoney() { return money; }

    public void setMoney(double money) { this.money = money; }

    public void addMoney(double amount) {
        if (amount > 0) {
            this.money += amount;
        }
    }

    public boolean withdrawMoney(double amount) {
        if (amount > 0 && this.money >= amount) {
            this.money -= amount;
            return true;
        }
        return false;
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

    public Map<UUID, Member> getMembers() {
        return members;
    }

    public void addMember(UUID memberId, String name, RankType rank, String memberType, int points) {
        if (name == null || name.isEmpty()) {
            name = Bukkit.getOfflinePlayer(memberId).getName();
        }

        String joinDate = new java.text.SimpleDateFormat("yyyy-MM-dd").format(new java.util.Date());
        members.put(memberId, new Member(memberId, name, joinDate, rank.name(), points));
    }

    public String getBaseID() {
        return baseID;
    }

    public void setBaseID(String baseID) {
        this.baseID = baseID;
    }

    public void kickMember(UUID memberId) {
        members.remove(memberId);
    }

    public boolean hasMember(UUID memberId) {
        return members.containsKey(memberId);
    }

    public List<Map.Entry<UUID, Member>> getSortedMembers() {
        List<Map.Entry<UUID, Member>> sorted = new ArrayList<>(members.entrySet());
        sorted.sort((a, b) -> {
            if (a.getKey().equals(leader)) return -1;
            if (b.getKey().equals(leader)) return 1;
            return Integer.compare(b.getValue().getScore(), a.getValue().getScore());
        });
        return sorted;
    }

    // ---------------- KHO CHUNG ------------------

    private void createSharedStorageInventory() {
        // Tạo inventory 27 slots cho kho chung, tiêu chuẩn
        this.sharedStorageInventory = Bukkit.createInventory(null, 36, "Kho Chung Guild " + name);
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
