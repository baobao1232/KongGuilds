package king.kongguilds;

import eu.decentsoftware.holograms.api.holograms.HologramManager;
import king.kongguilds.BASE.BaseData;
import king.kongguilds.BASE.BaseHologramManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class Main extends JavaPlugin {
    private static Main instance;
    private GuildManager guildManager;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        BaseData.setBaseFile(this.getDataFolder());
        instance = this;
        getDataFolder().mkdirs();
        saveDefaultConfig();

        // Load config trước
        GuildConfig.load(this);

        // Khởi tạo MessageManager trước
        messageManager = new MessageManager();
        try {
            messageManager.load(new File(getDataFolder(), "messages.yml"));
        } catch (IOException e) {
            getLogger().severe("Không thể load messages.yml!");
            e.printStackTrace();
            // Nếu messages.yml lỗi, ta nên dừng plugin
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        BaseHologramManager.cleanupOrphanHolograms();

        // Sau đó mới truyền vào GuildManager
        guildManager = new GuildManager(this, messageManager);

        this.getCommand("guilds").setExecutor(new GuildCommands(this, guildManager, messageManager));
        getServer().getPluginManager().registerEvents(new GuildListener(guildManager, messageManager), this);
        new TaskManager().startInviteCleanupTask(this, guildManager);
    }


    @Override
    public void onDisable() {
        for (Guild guild : guildManager.getAllGuilds()) {
            guildManager.saveGuild(guild.getName());
        }
        instance = null;
    }

    public static Main getInstance() {
        return instance;
    }

    public GuildManager getGuildManager() {
        return guildManager;
    }
}
