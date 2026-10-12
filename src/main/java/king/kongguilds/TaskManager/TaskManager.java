package king.kongguilds.TaskManager;

import eu.decentsoftware.holograms.api.holograms.Hologram;
import king.kongguilds.Base.BaseHologramManager;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Model.Guild;
import king.kongguilds.Model.TimedInvite;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import static king.kongguilds.Base.BaseHologramManager.updateBaseHologram;
import static king.kongguilds.Manager.GuildManager.getGuildByBaseId;

public class TaskManager {
    public static void startBaseHologramUpdateTask(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            //Bukkit.getLogger().info("[DEBUG] Base hologram task loop is running...");
            for (Map.Entry<String, Hologram> entry : BaseHologramManager.getHolograms().entrySet()) {
                String baseId = entry.getKey();

                // ❗ Bỏ qua những hologram không phải của base chính
                if (!baseId.startsWith("base_")) continue;

                String realBaseId = baseId.substring("base_".length()); // loại bỏ prefix

                Guild guild = getGuildByBaseId(realBaseId);
                if (guild != null) {
                    updateBaseHologram(realBaseId, guild);
                }
            }

        }, 0L, 100L);
    }

    public static void startInviteCleanupTask(JavaPlugin plugin, GuildManager guildManager) {
        //Bukkit.getLogger().info("Clean task ddax call");
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, TimedInvite>> iterator = guildManager.getPendingInvites().entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().isExpired()) {
                    iterator.remove();
                }
            }
        }, 20L * 30, 20L * 30);
    }

    public static void startGuildTagTask(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, GuildManager::tickAll, 1L, 1L);
    }
}
