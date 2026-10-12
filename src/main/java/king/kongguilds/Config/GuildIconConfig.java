package king.kongguilds.Config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class GuildIconConfig {

    public record Icon(String id, String glyph, String name, Material material, int customModelData) {}

    private static final Map<String, Icon> icons = new LinkedHashMap<>();

    public static void load(JavaPlugin plugin) {
        icons.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("guild-icons");
        if (sec == null) return;

        for (String id : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(id);
            if (s == null) continue;

            String glyph = s.getString("glyph", "");
            if (glyph.isEmpty()) continue;

            Material mat = Material.matchMaterial(s.getString("material", "PAPER"));
            if (mat == null) mat = Material.PAPER;

            icons.put(id, new Icon(id, glyph, s.getString("name", id), mat, s.getInt("custom-model-data", 0)));
        }
    }

    public static List<Icon> getAll() { return new ArrayList<>(icons.values()); }
    public static Icon get(String id) { return icons.get(id); }
}