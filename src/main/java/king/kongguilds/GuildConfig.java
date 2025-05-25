package king.kongguilds;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class GuildConfig {

    private static final Map<Integer, Integer> levelExpMap = new HashMap<>();

    public static void load(JavaPlugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("guild-levels");
        if (section != null) {
            levelExpMap.clear();
            for (String key : section.getKeys(false)) {
                int level = Integer.parseInt(key);
                int exp = section.getInt(key);
                levelExpMap.put(level, exp);
            }
        }
    }

    public static int getExpForLevel(int level) {
        return levelExpMap.getOrDefault(level, Integer.MAX_VALUE); // MAX nếu vượt cấp cuối
    }

    public static int getMaxLevel() {
        return levelExpMap.keySet().stream().max(Integer::compare).orElse(1);
    }
}
