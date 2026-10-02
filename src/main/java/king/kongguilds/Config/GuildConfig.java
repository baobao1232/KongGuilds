package king.kongguilds.Config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GuildConfig {

    private static final Map<Integer, Integer> levelExpMap =
            new HashMap<>();

    private static final Map<Integer, Integer> maxMembersMap =
            new HashMap<>();

    private static final Set<String> worldBossNames =
            new HashSet<>();

    private static final Set<String> guildBossNames =
            new HashSet<>();

    public static void load(JavaPlugin plugin) {

        // =====================================================
        // LOAD GUILD LEVEL
        // =====================================================

        ConfigurationSection section =
                plugin.getConfig()
                        .getConfigurationSection("guild-levels");

        if (section != null) {

            levelExpMap.clear();

            for (String key : section.getKeys(false)) {

                int level = Integer.parseInt(key);
                int exp = section.getInt(key);

                levelExpMap.put(level, exp);
            }
        }

        // =====================================================
        // LOAD WORLD BOSS
        // =====================================================

        worldBossNames.clear();

        for (String name :
                plugin.getConfig()
                        .getStringList("bosses.world")) {

            if (name == null || name.isBlank()) {
                continue;
            }

            worldBossNames.add(
                    name.toLowerCase()
            );
        }

        // =====================================================
        // LOAD GUILD BOSS
        // =====================================================

        guildBossNames.clear();

        for (String name :
                plugin.getConfig()
                        .getStringList("bosses.guild")) {

            if (name == null || name.isBlank()) {
                continue;
            }

            guildBossNames.add(
                    name.toLowerCase()
            );
        }

        ConfigurationSection memberSection =
                plugin.getConfig()
                        .getConfigurationSection("guild-max-members");

        maxMembersMap.clear();

        if (memberSection != null) {

            for (String key : memberSection.getKeys(false)) {

                int level = Integer.parseInt(key);
                int maxMembers = memberSection.getInt(key);

                maxMembersMap.put(
                        level,
                        maxMembers
                );
            }
        }
    }

    // =========================================================
    // GUILD LEVEL
    // =========================================================

    public static int getExpForLevel(int level) {

        return levelExpMap.getOrDefault(
                level,
                Integer.MAX_VALUE
        );
    }

    public static int getMaxLevel() {

        return levelExpMap.keySet()
                .stream()
                .max(Integer::compare)
                .orElse(1);
    }

    public static int getMaxMembers(int level) {

        return maxMembersMap.getOrDefault(
                level,
                0
        );
    }

    // =========================================================
    // WORLD BOSS
    // =========================================================

    public static boolean isWorldBoss(String internalName) {

        if (internalName == null
                || internalName.isBlank()) {

            return false;
        }

        return worldBossNames.contains(
                internalName.toLowerCase()
        );
    }

    public static Set<String> getWorldBossNames() {

        return Set.copyOf(worldBossNames);
    }

    // =========================================================
    // GUILD BOSS
    // =========================================================

    public static boolean isGuildBoss(String internalName) {

        if (internalName == null
                || internalName.isBlank()) {

            return false;
        }

        return guildBossNames.contains(
                internalName.toLowerCase()
        );
    }

    public static Set<String> getGuildBossNames() {

        return Set.copyOf(guildBossNames);
    }

    // =========================================================
    // BOSS
    // =========================================================

    public static boolean isBoss(String internalName) {

        return isWorldBoss(internalName)
                || isGuildBoss(internalName);
    }


}