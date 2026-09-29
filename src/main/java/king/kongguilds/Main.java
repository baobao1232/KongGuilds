package king.kongguilds;

import king.kongguilds.Base.BaseData;
import king.kongguilds.Base.BaseHologramManager;
import king.kongguilds.Command.GuildCommands;
import king.kongguilds.Config.GuildConfig;
import king.kongguilds.License.LicenseManager;
import king.kongguilds.Listener.BossLootListener;
import king.kongguilds.Listener.GuildListener;
import king.kongguilds.Listener.MythicMobDropListener;
import king.kongguilds.Manager.AuctionManager;
import king.kongguilds.Manager.BossLootManager;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Manager.MessageManager;
import king.kongguilds.Model.Guild;
import king.kongguilds.TaskManager.TaskManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;

public class Main extends JavaPlugin {
    private static Main instance;
    private GuildManager guildManager;
    private MessageManager messageManager;
    private BossLootManager bossLootManager;
    private AuctionManager auctionManager;

    @Override
    public void onEnable() {
        instance = this;

        //        LicenseManager licenseManager = new LicenseManager(this);
//        if (!licenseManager.isLicenseValid()) {
//            getLogger().severe("❌ Plugin sẽ bị vô hiệu hóa do license không hợp lệ.");
//            getServer().getPluginManager().disablePlugin(this);
//            return;
//        }
//
//        getLogger().info("✅ License OK. Đang bật plugin...");


        createPluginFolder();
        loadPluginConfig();

        if (!loadMessages()) {
            return;
        }

        BaseHologramManager.cleanupOrphanHolograms();

        guildManager = new GuildManager(this, messageManager);

        // Khởi tạo BossLootManager trước khi đăng ký Listener
        bossLootManager = new BossLootManager(this);

        auctionManager = new AuctionManager(this);

        registerCommands();
        registerListeners();

        guildManager.asyncLoadGuilds(() -> {
            BaseHologramManager.loadAllBaseHolograms();
            BaseHologramManager.loadAllChestHolograms();
            startTasks();

            getLogger().info("KongGuilds đã bật thành công!");
        });
    }

    @Override
    public void onDisable() {
        if (guildManager != null) {
            for (Guild guild : guildManager.getAllGuilds()) {
                guildManager.saveGuildSync(guild.getName());
            }
        }
        instance = null;
        getLogger().info("KongGuilds đã tắt.");
    }

    private void createPluginFolder() {
        BaseData.setBaseFile(this.getDataFolder());
        getDataFolder().mkdirs();
        saveDefaultConfig();
    }

    private void loadPluginConfig() {
        GuildConfig.load(this);
    }

    private boolean loadMessages() {
        messageManager = new MessageManager();
        try {
            messageManager.load(new File(getDataFolder(), "messages.yml"));
            return true;
        } catch (IOException e) {
            getLogger().severe("Không thể load messages.yml! Plugin sẽ bị vô hiệu.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    private void registerCommands() {
        getCommand("guilds").setExecutor(new GuildCommands(this, guildManager, messageManager));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new GuildListener(this, guildManager, messageManager), this);
        getServer().getPluginManager().registerEvents(new MythicMobDropListener(), this);
        getServer().getPluginManager().registerEvents(new BossLootListener(bossLootManager), this);
    }

    private void startTasks() {
        TaskManager.startInviteCleanupTask(this, guildManager); // ✅ gọn gàng, đồng bộ
        TaskManager.startBaseHologramUpdateTask(this);          // ✅ như cũ
    }

    public static Main getInstance() {
        return instance;
    }

    public GuildManager getGuildManager() {
        return this.guildManager;
    }

    public BossLootManager getBossLootManager() {
        return bossLootManager;
    }

    public AuctionManager getAuctionManager() { return auctionManager; }
}
