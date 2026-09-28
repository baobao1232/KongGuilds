package king.kongguilds.Command;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import king.kongguilds.Base.BaseData;
import king.kongguilds.Base.BaseHologramManager;
import king.kongguilds.Fawe.SelectedPositionsManager;
import king.kongguilds.Gui.GuildGUI;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Manager.MessageManager;
import king.kongguilds.Model.Guild;
import king.kongguilds.Model.Member;
import king.kongguilds.WorldGuard.BaseRegionManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class GuildCommands implements CommandExecutor {

    private final GuildManager guildManager;
    private final MessageManager messageManager;
    private final JavaPlugin plugin;

    public GuildCommands(JavaPlugin plugin, GuildManager guildManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(messageManager.getMessage("member", "only_players"));
            return true;
        }

        Guild guild = guildManager.getPlayerGuild(player);
        boolean isAdmin = player.hasPermission("guild.admin");

        if (args.length == 0) {
            guildManager.showHelp(player, isAdmin);
            return true;
        }

        /* =========================
           MEMBER COMMANDS
           ========================= */
        switch (args[0].toLowerCase()) {
            case "create":
                switch (guildManager.createGuild(player, args[1])) {
                    case SUCCESS -> {}
                    case ALREADY_IN_GUILD -> player.sendMessage("§cBạn đã có guild.");
                    case NAME_EXISTS -> player.sendMessage("§cTên guild đã tồn tại.");
                    case INVALID_NAME -> player.sendMessage("§cTên guild không hợp lệ.");
                }
                return true;

            case "leave":
                guildManager.leaveGuild(player);
                return true;

            case "disband":
                guildManager.disbandGuild(player);
                return true;


            case "info":
                if (guild == null) {
                    player.sendMessage(messageManager.getMessage("member", "not_in_guild"));
                    return true;
                }
                GuildGUI.openGuildInfoGUI(player, guild, 0);
                return true;
            case "invite":
                if (guild == null) {
                    player.sendMessage(messageManager.getMessage("member", "not_in_guild"));
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage("§cSử dụng: /g invite <player>");
                    return true;
                }

                Member inviter = guild.getMembers().get(player.getUniqueId());
                if (inviter == null) return true;

                if (!inviter.getRank().equalsIgnoreCase("Leader")
                        && !inviter.getRank().equalsIgnoreCase("Officer")
                        && !inviter.getRank().equalsIgnoreCase("Co-Leader")) {
                    player.sendMessage("§cBạn không có quyền mời.");
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(messageManager.getMessage("member", "player_not_found"));
                    return true;
                }

                if (guildManager.getPlayerGuild(target) != null) {
                    player.sendMessage("§cNgười chơi này đã có guild.");
                    return true;
                }

                guildManager.inviteMember(target.getUniqueId(), guild.getName());

                player.sendMessage("§aĐã mời " + target.getName() + " vào guild.");
                target.sendMessage("§eBạn được mời vào guild §a" + guild.getName()
                        + "§e. Dùng §a/g accept §ehoặc §c/g reject");

                return true;

            case "donate":
                if (guild == null) {
                    player.sendMessage("§cBạn chưa tham gia guild nào.");
                    return true;
                }

                if (guild.getBaseID() == null || BaseData.getBaseClaim(guild.getBaseID()) == null) {
                    player.sendMessage("§cGuild của bạn chưa sở hữu base.");
                    return true;
                }

                if (!guildManager.isPlayerInTheirOwnBase(player)) {
                    player.sendMessage("§cBạn phải đứng trong base guild để donate.");
                    return true;
                }

                GuildGUI.openDonateGUI(player, guild);
                return true;

            case "storage":
                if (guild == null) {
                    player.sendMessage(messageManager.getMessage("member", "not_in_guild"));
                    return true;
                }

                GuildGUI.openStorageGUI(player, guild);
                player.sendMessage(messageManager.getMessage("member", "storage_opened"));
                return true;

            case "accept":
                guildManager.acceptInvite(player);
                player.sendMessage(messageManager.getMessage("member", "accept_success"));
                return true;

            case "reject":
                guildManager.rejectInvite(player.getUniqueId());
                player.sendMessage(messageManager.getMessage("member", "reject_success"));
                return true;

            case "chat":
                if (guild == null) {
                    player.sendMessage("§cBạn không thuộc guild nào.");
                    return true;
                }

                if (args.length == 1) {
                    guildManager.toggleGuildChat(player);
                    return true;
                }

                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                guildManager.sendGuildMessage(plugin, guild, player, message);
                return true;
        }

        /* =========================
           ADMIN COMMANDS
           ========================= */
        if (args[0].equalsIgnoreCase("admin")) {

            if (!isAdmin) {
                player.sendMessage(messageManager.getMessage("admin", "no_permission"));
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§c/guild admin <disband|add|kick|addexp|addlevel|reload>");
                return true;
            }

            switch (args[1].toLowerCase()) {

                case "reload":
                    try {
                        guildManager.asyncLoadGuilds(() ->
                                player.sendMessage("§aReload guilds thành công!")
                        );
                        messageManager.load(new File(plugin.getDataFolder(), "messages.yml"));
                    } catch (IOException e) {
                        player.sendMessage("§cReload thất bại.");
                        e.printStackTrace();
                    }
                    return true;

                case "addexp":
                    if (args.length < 4) return true;
                    Guild gExp = guildManager.getGuild(args[2]);
                    if (gExp == null) return true;

                    int exp = Integer.parseInt(args[3]);
                    gExp.addExp(exp);
                    player.sendMessage("§aĐã cộng " + exp + " exp cho guild.");
                    return true;

                case "addlevel":
                    if (args.length < 4) return true;
                    Guild gLvl = guildManager.getGuild(args[2]);
                    if (gLvl == null) return true;

                    int lvl = Integer.parseInt(args[3]);
                    gLvl.addLevel(lvl);
                    player.sendMessage("§aĐã cộng " + lvl + " level cho guild.");
                    return true;
            }
        }

        player.sendMessage(messageManager.getMessage("member", "usage"));
        return true;
    }
}
