package king.kongguilds.Manager;

import king.kongguilds.Model.TimedInvite;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class TaskManager {

    public void startInviteCleanupTask(JavaPlugin plugin, GuildManager guildManager) {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, TimedInvite>> iterator = guildManager.getPendingInvites().entrySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getValue().isExpired()) {
                    iterator.remove();
                }
            }
        }, 20L * 30, 20L * 30);
    }
}