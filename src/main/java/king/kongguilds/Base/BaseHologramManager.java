package king.kongguilds.Base;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import king.kongguilds.Config.GuildConfig;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Model.Guild;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;
import java.util.stream.Collectors;

public class BaseHologramManager {
    private static final Map<String, Hologram> holograms = new HashMap<>();

    public static Map<String, Hologram> getHolograms() {
        return holograms;
    }

    public static void createUnclaimedBaseHologram(String baseId, Location location) {
        removeHologram(baseId); // Xoá nếu có sẵn

        Hologram hologram = DHAPI.createHologram("base_" + baseId, location, List.of(
                "§6§lBASE AREA",
                "§eDùng lệnh §b/g base claim §eđể chiếm base này"
        ));

        holograms.put("base_" + baseId, hologram);
    }

    public static void createClaimedBaseHologram(String baseId, Location location, Guild guild) {
        removeHologram(baseId);

        int level = guild.getLevel();
        int exp = guild.getExp();
        int nextExp = GuildConfig.getExpForLevel(level + 1);

        Hologram hologram = DHAPI.createHologram("base_" + baseId, location, List.of(
                "§6§l=== BASE ===",
                "§eGuild: §f" + guild.getName(),
                "§eLevel: §6" + level,
                "§eEXP: §b" + exp + "§7 / §b" + nextExp,
                "§7ID: " + baseId
        ));

        holograms.put("base_" + baseId, hologram);
    }

    public static void removeHologram(String baseId) {
        // Xoá hologram vùng base
        String baseHologramName = "base_" + baseId;
        Hologram baseHolo = DHAPI.getHologram(baseHologramName);
        if (baseHolo != null) {
            baseHolo.delete();
        }

        // Xoá hologram chest (nếu có)
        String chestHologramName = "chest_" + baseId;
        Hologram chestHolo = DHAPI.getHologram(chestHologramName);
        if (chestHolo != null) {
            chestHolo.delete();
        }

        // Xoá khỏi cache nếu bạn có map `holograms`
        holograms.remove("base_" + baseId);
        holograms.remove("chest_" + baseId);  // xoá chest_... nếu key tồn tại
    }


    public static void removeAllOrphans(Set<String> existingBaseIds) {
        Iterator<Map.Entry<String, Hologram>> it = holograms.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!existingBaseIds.contains(entry.getKey())) {
                entry.getValue().delete();
                it.remove();
            }
        }
    }

    public static void clearAll() {
        for (Hologram h : holograms.values()) {
            h.delete();
        }
        holograms.clear();
    }

    public static void removeOrphanHolograms(Set<String> validBaseIds) {
        int removed = 0;
        Iterator<Map.Entry<String, Hologram>> iterator = holograms.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Hologram> entry = iterator.next();
            String id = entry.getKey();
            Hologram hologram = entry.getValue();

            if (!validBaseIds.contains(id)) {
                hologram.delete();
                iterator.remove();
                removed++;
                Bukkit.getLogger().info("[CLEANUP] Đã xoá hologram mồ côi: base_" + id);
            }
        }

        Bukkit.getLogger().info("[CLEANUP] Tổng số hologram mồ côi đã bị xoá: " + removed);
    }

    public static void cleanupOrphanHolograms() {
        // 1. Load tất cả base hợp lệ
        Map<String, BaseData.BaseClaim> allBases = BaseData.loadBases();

        // 2. Chuyển sang tập hợp ID hologram (format giả định là "base_" + baseId)
        Set<String> validHoloIds = allBases.keySet().stream()
                .map(id -> "base_" + id)
                .collect(Collectors.toSet());

        // 3. Duyệt map holograms hiện có, xóa những hologram không nằm trong validHoloIds
        int removedCount = 0;
        Iterator<Map.Entry<String, Hologram>> iterator = holograms.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Hologram> entry = iterator.next();
            String holoId = entry.getKey();
            if (!validHoloIds.contains(holoId)) {
                entry.getValue().delete();
                iterator.remove();
                removedCount++;
                Bukkit.getLogger().info("[CLEANUP] Đã xoá hologram mồ côi: " + holoId);
            }
        }

        Bukkit.getLogger().info("[CLEANUP] Tổng số hologram mồ côi đã bị xoá: " + removedCount);
    }

    public static void updateBaseHologram(String baseId, Guild guild) {

        BaseData.BaseClaim base = BaseData.getBaseClaim(baseId);
        if (base == null || !base.isClaimed()) {
            return; // bỏ qua base chưa claim
        }

        Hologram hologram = holograms.get("base_" + baseId);
        if (hologram == null) {
            Bukkit.getLogger().warning("[BaseHologram] Không tìm thấy hologram với baseId: " + baseId);
            return;
        }

        if (guild == null) {
            Bukkit.getLogger().warning("[BaseHologram] Guild null khi cập nhật base: " + baseId);
            return;
        }

        int level = guild.getLevel();
        int exp = guild.getExp();
        int nextExp = GuildConfig.getExpForLevel(level + 1);

        //Bukkit.getLogger().info("[BaseHologram] Cập nhật hologram cho baseId: " + baseId);
        //Bukkit.getLogger().info("[BaseHologram] Guild: " + guild.getName() + ", Level: " + level + ", EXP: " + exp + "/" + nextExp);

        DHAPI.setHologramLines(hologram, List.of(
                "§6§l=== BASE ===",
                "§eGuild: §f" + guild.getName(),
                "§eLevel: §6" + level,
                "§eEXP: §b" + exp + "§7 / §b" + nextExp,
                "§7ID: " + baseId
        ));
    }


    public static void loadAllBaseHolograms() {
        clearAll();
        Map<String, BaseData.BaseClaim> bases = BaseData.loadBases();

        for (Map.Entry<String, BaseData.BaseClaim> entry : bases.entrySet()) {
            String baseId = entry.getKey();
            BaseData.BaseClaim baseClaim = entry.getValue();
            Location location = baseClaim.getHologramLocation();


            if (location == null) {
                Bukkit.getLogger().warning("[BaseHologram] Không thể tạo hologram: base " + baseId + " không có location.");
                continue;
            }

            if (baseClaim.isClaimed()) {
                Guild guildClaim = GuildManager.getInstance().getGuildByName(baseClaim.getGuildName());
                if (guildClaim == null) {
                    Bukkit.getLogger().warning("[BaseHologram] Không tìm thấy guild: " + baseClaim.getGuildName());
                    continue;
                }

                createClaimedBaseHologram(baseId, location, guildClaim); // dùng overload mới
            } else {
                createUnclaimedBaseHologram(baseId, location);
            }

        }

        Bukkit.getLogger().info("[BaseHologram] Đã load lại tất cả hologram base.");
    }

    public static void createChestHologram(String baseId, String title) {
        BaseData.BaseClaim base = BaseData.getBaseClaim(baseId);
        if (base == null || !base.isClaimed()) {
            Bukkit.getLogger().warning("[ChestHologram] Không thể tạo hologram vì base không tồn tại hoặc chưa được claim: " + baseId);
            return;
        }

        Location chestLoc = GuildManager.getFirstChestInBase(base);
        //Bukkit.getLogger().info("[DEBUG] Base World: " + (world != null ? world.getName() : "null"));
        Bukkit.getLogger().info("[DEBUG] Pos1: " + base.pos1);
        Bukkit.getLogger().info("[DEBUG] Pos2: " + base.pos2);
        if (chestLoc == null) {
            Bukkit.getLogger().warning("[ChestHologram] Không tìm thấy chest trong base " + baseId);
            return;
        }

        String holoId = "chest_" + baseId;
        Hologram old = DHAPI.getHologram(holoId);
        if (old != null) {
            old.delete();
        }

        Location hologramLoc = chestLoc.clone().add(0.5, 2.5, 0.5); // Hiển thị phía trên chest

        Hologram holo = DHAPI.createHologram(holoId, hologramLoc, List.of(
                "§a§l" + title,
                "§7Click để mở",
                "§a/g donate §7 để donate cho Guild"
        ));

        holograms.put(holoId, holo);
    }

    public static void loadAllChestHolograms() {
        Map<String, BaseData.BaseClaim> bases = BaseData.loadBases();

        for (Map.Entry<String, BaseData.BaseClaim> entry : bases.entrySet()) {
            String baseId = entry.getKey();
            BaseData.BaseClaim base = entry.getValue();

            if (!base.isClaimed()) continue;
            Location center = base.getLocation();
            if (center != null) {
                Bukkit.getLogger().info("[DEBUG] Base Center: " + center);
                Bukkit.getLogger().info("[DEBUG] Highest Y at center: " + center.getWorld().getHighestBlockYAt(center));
            }

            Location chestLoc = GuildManager.getFirstChestInBase(base);
            Bukkit.getLogger().info("[DEBUG] Pos1: " + base.pos1);
            Bukkit.getLogger().info("[DEBUG] Pos2: " + base.pos2);

            if (chestLoc != null) {
                createChestHologram(baseId, "Kho Guild");
            } else {
                Bukkit.getLogger().warning("[BaseHologram] Không tìm thấy chest trong base " + baseId);
            }
        }

        Bukkit.getLogger().info("[BaseHologram] Đã load hologram trên chest cho các base.");
    }


}
