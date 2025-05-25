package king.kongguilds;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class MessageManager {
    private YamlConfiguration messages;

    public void load(File file) throws IOException {
        if (!file.exists()) {
            file.createNewFile();
            // Bạn có thể thêm copy resource mặc định nếu muốn
        }
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String getMessage(String group, String key) {
        if (messages == null) return "";
        String msg = messages.getString("message." + group + "." + key);
        return msg == null ? "" : ChatColor.translateAlternateColorCodes('&', msg);
    }

    public String getMessage(String group, String key, Map<String, String> placeholders) {
        String msg = getMessage(group, key);
        if (msg.isEmpty()) return msg;
        for (var entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }
}
