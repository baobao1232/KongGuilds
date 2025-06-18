package king.kongguilds.License;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class LicenseManager {
    private final File file;
    private final YamlConfiguration config;
    private final JavaPlugin plugin; // đổi tên class chính
    private final String scriptURL = "https://script.google.com/macros/s/AKfycbwga1aeZi84Nl-oyZJNJn_EjmX4w2WqQILNLt9S2SG9BsTUSpPtek0k_zQb1MYgNUyX/exec";

    public LicenseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "license.yml");

        if (!file.exists()) {
            plugin.getLogger().info("🔐 Chưa có license.yml, tạo mới...");
            try {
                file.createNewFile();
                YamlConfiguration temp = YamlConfiguration.loadConfiguration(file);
                temp.set("license", "");
                temp.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    public boolean isLicenseValid() {
        String key = config.getString("license");
        String pluginName = plugin.getDescription().getName(); // lấy tên plugin từ plugin.yml

        if (key == null || key.trim().isEmpty()) {
            plugin.getLogger().warning("❌ License trống. Hãy điền key vào license.yml");
            return false;
        }

        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            //Bukkit.getLogger().info("plugin name: " + pluginName);

            String urlStr = scriptURL + "?key=" + URLEncoder.encode(key, "UTF-8")
                    + "&ip=" + URLEncoder.encode(ip, "UTF-8")
                    + "&plugin=" + URLEncoder.encode(pluginName, "UTF-8");
            //Bukkit.getLogger().info("🔗 License check URL: " + urlStr); // 👈 CỰC QUAN TRỌNG
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String response = in.readLine();
            in.close();

            switch (response) {
                case "VALID":
                case "VALID_FIRST_TIME":
                    plugin.getLogger().info("✅ License hợp lệ.");
                    return true;

                case "INVALID":
                    plugin.getLogger().warning("❌ License không hợp lệ.");
                    break;

                case "INVALID_USED_BY_OTHER":
                    plugin.getLogger().warning("❌ License đã bị dùng ở IP khác.");
                    break;

                default:
                    if (response.startsWith("DISABLED:")) {
                        String reason = response.split(":")[1];
                        plugin.getLogger().warning("❌ License đã bị vô hiệu hóa (trạng thái: " + reason + ").");
                    } else {
                        plugin.getLogger().warning("⚠️ Không xác định phản hồi từ server: " + response);
                    }
                    break;
            }
            return false;

        } catch (IOException e) {
            plugin.getLogger().warning("⚠️ Không thể kết nối đến server license.");
            e.printStackTrace();
            return false;
        }
    }
}