package king.kongguilds.Base;

import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.*;
import java.util.stream.Collectors;

public class BaseHologramManager {
    private static final Map<String, Hologram> holograms = new HashMap<>();

    public static void createUnclaimedBaseHologram(String baseId, Location location) {
        removeHologram(baseId); // Xoá nếu có sẵn

        Hologram hologram = DHAPI.createHologram("base_" + baseId, location, List.of(
                "§6§lBASE AREA",
                "§eDùng lệnh §b/g base claim §eđể chiếm base này"
        ));

        holograms.put(baseId, hologram);
    }

    public static void createClaimedBaseHologram(String baseId, Location location, String guildName, int level) {
        removeHologram(baseId); // Xoá nếu có sẵn

        Hologram hologram = DHAPI.createHologram("base_" + baseId, location, List.of(
                "§6§l=== BASE ===",
                "§eGuild: §f" + guildName,
                "§eLevel: §6" + level,
                "§7ID: " + baseId
        ));

        holograms.put(baseId, hologram);
    }

    public static void removeHologram(String baseId) {
        String hologramName = "base_" + baseId;
        Hologram holo = DHAPI.getHologram(hologramName); // Lấy lại hologram từ API
        if (holo != null) {
            holo.delete();
        }
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

    public static void updateBaseHologram(String baseId, Location location, String guildName, int level) {
        // Xóa hologram cũ và tạo mới với level updated
        // hologramAPI.removeHologram(baseId);
        createClaimedBaseHologram(baseId, location, guildName, level);
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
                createClaimedBaseHologram(baseId, location, baseClaim.getGuildName(), baseClaim.getLevel());
            } else {
                createUnclaimedBaseHologram(baseId, location);
            }
        }

        Bukkit.getLogger().info("[BaseHologram] Đã load lại tất cả hologram base.");
    }


}
