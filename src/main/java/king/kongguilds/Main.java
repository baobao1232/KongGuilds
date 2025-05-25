package king.kongguilds;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public class Main extends JavaPlugin {
    private GuildManager guildManager;
    private MessageManager messageManager;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        saveDefaultConfig();

        GuildConfig.load(this);
        guildManager = new GuildManager(this);
        guildManager.loadGuilds();

        messageManager = new MessageManager();
        try {
            messageManager.load(new File(getDataFolder(), "messages.yml"));
        } catch (IOException e) {
            getLogger().severe("Không thể load messages.yml!");
            e.printStackTrace();
        }

        this.getCommand("guilds").setExecutor(new GuildCommands(this, guildManager, messageManager));

        getServer().getPluginManager().registerEvents(new GuildListener(guildManager), this);
        new TaskManager().startInviteCleanupTask(this, guildManager);
    }

    @Override
    public void onDisable() {
        for (Guild guild : guildManager.getAllGuilds()) {
            guildManager.saveGuild(guild.getName());
        }
    }
}
