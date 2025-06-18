package king.kongguilds.Command;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
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

import java.io.*;
import java.util.*;

public class GuildCommands implements CommandExecutor {
    private final GuildManager guildManager;
    private final MessageManager messageManager;
    private final JavaPlugin plugin;
    private static final Map<String, Location[]> guildBases = new HashMap<>();

    public GuildCommands(JavaPlugin plugin, GuildManager guildManager, MessageManager messageManager) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.messageManager = messageManager;
    }


    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageManager.getMessage("member", "only_players")); // fallback cho console
            return true;
        }

        Player player = (Player) sender;
        Guild guild = guildManager.getPlayerGuild(player);
        boolean isAdmin = player.hasPermission("guild.admin");
        String group = isAdmin ? "admin" : "member";

        if (args.length == 0) {
            player.sendMessage(messageManager.getMessage("member", "usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("guild.admin")) {
                player.sendMessage(messageManager.getMessage("admin", "no_permission"));
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin <disband|add|kick|accept|join|addexp|addlevel>");
                return true;
            }

            String adminCmd = args[1].toLowerCase();

            switch (adminCmd) {
                case "disband":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin disband <guildName>");
                        return true;
                    }

                    String guildNameToDisband = args[2];
                    Guild ADguildToDisband = guildManager.getGuild(guildNameToDisband);
                    if (ADguildToDisband == null) {
                        player.sendMessage(messageManager.getMessage("admin", "guild_not_exist"));
                        return true;
                    }

                    guildManager.removeGuild(guildNameToDisband);

                    for (UUID memberId : ADguildToDisband.getMembers().keySet()) {
                        Player member = Bukkit.getPlayer(memberId);
                        if (member != null && member.isOnline()) {
                            member.sendMessage(messageManager.getMessage("admin", "guild_admin_disband", Map.of("guild", guildNameToDisband)));
                        }
                    }

                    player.sendMessage(messageManager.getMessage("admin", "guild_admin_disband", Map.of("guild", guildNameToDisband)));
                    break;

                case "add":
                    if (args.length < 5) {
                        player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin add <guildName> <playerName> <rankName>");
                        return true;
                    }

                    String guildNameToAdd = args[2];
                    String playerNameToAdd = args[3];
                    String rankNameToAdd = args[4];

                    Guild guildToAdd = guildManager.getGuild(guildNameToAdd);
                    if (guildToAdd == null) {
                        player.sendMessage(messageManager.getMessage("admin", "guild_not_exist"));
                        return true;
                    }

                    Player targetToAdd = Bukkit.getPlayerExact(playerNameToAdd);
                    if (targetToAdd == null) {
                        player.sendMessage(messageManager.getMessage("admin", "player_not_found"));
                        return true;
                    }

                    if (guildToAdd.hasMember(targetToAdd.getUniqueId())) {
                        player.sendMessage(messageManager.getMessage("admin", "already_member"));
                        return true;
                    }

                    guildManager.addMember(guildNameToAdd, playerNameToAdd, targetToAdd.getUniqueId(), rankNameToAdd, "Member", 0);
                    player.sendMessage(ChatColor.GREEN + "Đã thêm " + playerNameToAdd + " vào guild " + guildNameToAdd + " với rank " + rankNameToAdd + ".");
                    targetToAdd.sendMessage(ChatColor.YELLOW + "Bạn đã được admin thêm vào guild " + guildNameToAdd + ".");
                    break;

                case "kick":
                    if (args.length < 4) {
                        player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin kick <guildName> <playerUUID>");
                        return true;
                    }

                    String guildNameToKick = args[2];
                    UUID playerIdToKick;
                    try {
                        playerIdToKick = UUID.fromString(args[3]);
                    } catch (IllegalArgumentException e) {
                        player.sendMessage(ChatColor.RED + "UUID không hợp lệ.");
                        return true;
                    }

                    Guild guildToKick = guildManager.getGuild(guildNameToKick);
                    if (guildToKick == null) {
                        player.sendMessage(messageManager.getMessage("admin", "guild_not_exist"));
                        return true;
                    }

                    if (!guildToKick.hasMember(playerIdToKick)) {
                        player.sendMessage(ChatColor.RED + "Người chơi không phải thành viên guild.");
                        return true;
                    }

                    boolean kicked = guildManager.kickMemberAsAdmin(guildNameToKick, playerIdToKick);
                    if (kicked) {
                        player.sendMessage(messageManager.getMessage("admin", "guild_admin_kick", Map.of("guild", guildNameToKick)));

                        Player kickedPlayer = Bukkit.getPlayer(playerIdToKick);
                        if (kickedPlayer != null) {
                            kickedPlayer.sendMessage(messageManager.getMessage("admin", "guild_admin_kick", Map.of("guild", guildNameToKick)));
                        }
                    } else {
                        player.sendMessage("§cKhông thể kick người chơi này.");
                    }
                    break;


                case "accept":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin accept <playerName>");
                        return true;
                    }

                    Player targetAccept = Bukkit.getPlayerExact(args[2]);
                    if (targetAccept == null) {
                        player.sendMessage(messageManager.getMessage("admin", "player_not_found"));
                        return true;
                    }

                    guildManager.acceptInvite(targetAccept);
                    player.sendMessage(ChatColor.GREEN + "Đã cho phép " + args[2] + " chấp nhận lời mời guild.");
                    targetAccept.sendMessage(ChatColor.YELLOW + "Bạn đã được admin chấp nhận gia nhập guild.");
                    break;

                case "join":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin join <guildName>");
                        return true;
                    }

                    String guildNameToJoin = args[2];
                    Guild guildToJoin = guildManager.getGuild(guildNameToJoin);
                    if (guildToJoin == null) {
                        player.sendMessage(messageManager.getMessage("admin", "guild_not_exist"));
                        return true;
                    }

                    if (guildToJoin.hasMember(player.getUniqueId())) {
                        player.sendMessage(ChatColor.YELLOW + "Bạn đã là thành viên guild này rồi.");
                        return true;
                    }

                    guildManager.addMember(guildNameToJoin, player.getName(), player.getUniqueId(), "Member", "Member", 0);
                    player.sendMessage(ChatColor.GREEN + "Bạn đã được admin thêm vào guild '" + guildNameToJoin + "' mà không cần chấp nhận.");
                    break;

                case "addexp":
                    if (args.length < 4) {
                        player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin addexp <guildName> <amount>");
                        return true;
                    }
                    String guildNameForExp = args[2];
                    int expAmount;
                    try {
                        expAmount = Integer.parseInt(args[3]);
                        if (expAmount <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Số EXP phải là số nguyên dương hợp lệ.");
                        return true;
                    }
                    Guild guildForExp = guildManager.getGuild(guildNameForExp);
                    if (guildForExp == null) {
                        player.sendMessage(messageManager.getMessage("admin", "guild_not_exist"));
                        return true;
                    }
                    guildForExp.addExp(expAmount);
                    player.sendMessage(messageManager.getMessage("admin", "exp_added", Map.of("exp", String.valueOf(expAmount), "guild", guildNameForExp)));
                    break;

                case "addlevel":
                    if (args.length < 4) {
                        player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin addlevel <guildName> <amount>");
                        return true;
                    }
                    String guildNameForLevel = args[2];
                    int levelAmount;
                    try {
                        levelAmount = Integer.parseInt(args[3]);
                        if (levelAmount <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Số level phải là số nguyên dương hợp lệ.");
                        return true;
                    }
                    Guild guildForLevel = guildManager.getGuild(guildNameForLevel);
                    if (guildForLevel == null) {
                        player.sendMessage(messageManager.getMessage("admin", "guild_not_exist"));
                        return true;
                    }
                    guildForLevel.addLevel(levelAmount);
                    player.sendMessage(messageManager.getMessage("admin", "level_added", Map.of("level", String.valueOf(levelAmount), "guild", guildNameForLevel)));
                    break;

                case "reload":
                    if (!player.hasPermission("guilds.admin.reload")) {
                        player.sendMessage(messageManager.getMessage("admin", "no_permission"));
                        return true;
                    }
                    // Reload config, guilds, messages
                    try {
                        guildManager.loadGuilds(); // Reload guilds từ file (bạn phải chắc GuildManager có method này)
                        messageManager.load(new File(plugin.getDataFolder(), "messages.yml"));
                        player.sendMessage(ChatColor.GREEN + "§aPlugin đã được reload thành công!");
                    } catch (IOException e) {
                        player.sendMessage(ChatColor.RED + "§cLỗi khi reload plugin!");
                        e.printStackTrace();
                    }
                    return true;

                case "base":
                    if (args.length < 3) {
                        player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin base <pos1|pos2|claim|remove>");
                        return true;
                    }

                    String subCmd = args[2].toLowerCase();

                    if (subCmd.equals("pos1")) {
                        SelectedPositionsManager.setPosition(player, player.getLocation(), 1);
                        player.sendMessage(ChatColor.GREEN + "Đã lưu vị trí base pos1 của bạn.");
                        return true;
                    }

                    if (subCmd.equals("pos2")) {
                        SelectedPositionsManager.setPosition(player, player.getLocation(), 2);
                        player.sendMessage(ChatColor.GREEN + "Đã lưu vị trí base pos2 của bạn.");
                        return true;
                    }

                    if (subCmd.equalsIgnoreCase("claim")) {
                        if (args.length < 4) {
                            player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin base claim <id>");
                            return true;
                        }

                        String id = args[3];
                        Location[] positions = SelectedPositionsManager.getSelectedPositions(player);
                        if (positions == null || positions[0] == null || positions[1] == null) {
                            player.sendMessage(ChatColor.RED + "Bạn chưa set pos1 hoặc pos2 base!");
                            return true;
                        }

                        if (!BaseData.canClaimBase(positions[0], positions[1])) {
                            player.sendMessage(ChatColor.RED + "Vùng base này đã bị claim rồi!");
                            return true;
                        }

                        if (BaseData.getBaseClaim(id) != null) {
                            player.sendMessage(ChatColor.RED + "ID base này đã tồn tại! Vui lòng chọn ID khác.");
                            return true;
                        }

                        String guildName = ""; // TODO: lấy tên guild thật từ player
                        String admincreator = player.getName();
                        String owner = "";
                        int level = 1;

                        boolean success = BaseData.saveBaseToFileWithId(id, guildName, admincreator, owner, level, positions[0], positions[1]);

                        if (!success) {
                            player.sendMessage(ChatColor.RED + "Không thể lưu base. Có thể ID đã tồn tại.");
                            return true;
                        }

                        Location center = guildManager.getCenter(positions[0], positions[1]);

                        if (center != null) {
                            BaseHologramManager.createUnclaimedBaseHologram(id, center);
                        }

                        if (center == null) {
                            player.sendMessage("§cKhông tính được trung tâm base.");
                            return true;
                        }

                        File schematicFile = new File(plugin.getDataFolder(), "schem/baseb0.schem");
                        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                        if (format == null) {
                            player.sendMessage("§cKhông xác định được định dạng schematic.");
                            return true;
                        }

                        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                            Clipboard clipboard = reader.read();

                            int width = clipboard.getRegion().getWidth();
                            int length = clipboard.getRegion().getLength();

                            Location pasteLocation = center.clone().subtract(width / 2, 0, length / 2);

                            // Paste schematic
                            guildManager.pasteSchematicAtBase(player, plugin, id, "baseb0.schem");

                        } catch (IOException e) {
                            player.sendMessage("§cLỗi khi đọc schematic.");
                            e.printStackTrace();
                            return true;
                        }

                        SelectedPositionsManager.clearPositions(player);
                        player.sendMessage(ChatColor.GREEN + "Đã claim base ID " + id + " cho guild " + guildName + " và tạo hologram.");
                        return true;
                    }



                    if (subCmd.equalsIgnoreCase("remove")) {
                        if (args.length < 4) {
                            player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin base remove <id>");
                            return true;
                        }

                        String baseIdToRemove = args[3];

                        // Load lại danh sách base
                        Map<String, BaseData.BaseClaim> allBases = BaseData.loadBases();
                        BaseData.BaseClaim base = allBases.get(baseIdToRemove);

                        if (base == null) {
                            player.sendMessage(ChatColor.RED + "Base '" + baseIdToRemove + "' không tồn tại.");
                            return true;
                        }

                        // Lấy world từ pos1 hoặc worldName
                        World world = null;
                        if (base.pos1 != null && base.pos1.getWorld() != null) {
                            world = base.pos1.getWorld();
                        }

                        if (world == null) {
                            player.sendMessage(ChatColor.RED + "Không thể xác định world của base.");
                            return true;
                        }

                        guildManager.clearBaseArea(plugin, baseIdToRemove);
                        boolean result = BaseData.removeBaseById(baseIdToRemove);

                        if (result) {
                            BaseHologramManager.removeHologram(baseIdToRemove);
                            BaseRegionManager.removeBaseRegion(baseIdToRemove, world);
                            player.sendMessage(ChatColor.GREEN + "Base '" + baseIdToRemove + "' đã được xóa thành công.");
                        } else {
                            player.sendMessage(ChatColor.RED + "Không thể xóa base '" + baseIdToRemove + "'.");
                        }

                        return true;
                    }


                    player.sendMessage(ChatColor.RED + "Sử dụng: /guilds admin base <pos1|pos2|claim|remove>");
                    break;

                default:
                    player.sendMessage(messageManager.getMessage("admin", "usage"));
                    break;
            }


            return true;
        }

        // Lệnh cho member
        switch (args[0].toLowerCase()) {
            case "create":
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("member", "usage"));
                    return true;
                }
                guildManager.createGuild(args[1], player);
                player.sendMessage(messageManager.getMessage("member", "guild_created", Map.of("guild", args[1])));
                break;

            case "invite":
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("member", "usage"));
                    return true;
                }

                Guild playerGuild = guildManager.getPlayerGuild(player);
                if (playerGuild == null) {
                    player.sendMessage(messageManager.getMessage("member", "guild_not_exist"));
                    return true;
                }
                Member member = playerGuild.getMembers().get(player.getUniqueId());
                Guild.RankType rank = null;

                if (member != null) {
                    try {
                        rank = Guild.RankType.valueOf(member.getRank().toUpperCase().replace("-", ""));
                    } catch (IllegalArgumentException e) {
                        // Rank không hợp lệ, gán mặc định MEMBER
                        rank = Guild.RankType.MEMBER;
                    }
                } else {
                    // Nếu không phải thành viên guild
                    rank = Guild.RankType.MEMBER;
                }

                if (rank != Guild.RankType.LEADER && rank != Guild.RankType.OFFICER) {
                    player.sendMessage("§cChỉ Leader hoặc Officer mới có quyền mời thành viên.");
                    return true;
                }


                Player targetPlayer = Bukkit.getPlayerExact(args[1]);
                if (targetPlayer == null) {
                    player.sendMessage(messageManager.getMessage("member", "player_not_found"));
                    return true;
                }

                UUID targetUUID = targetPlayer.getUniqueId();

                if (playerGuild.hasMember(targetUUID)) {
                    player.sendMessage(messageManager.getMessage("member", "already_member"));
                    return true;
                }

                if (guildManager.getPlayerGuild(targetPlayer) != null) {
                    player.sendMessage("§cNgười chơi này đã ở trong một guild khác.");
                    return true;
                }

                guildManager.inviteMember(targetUUID, playerGuild.getName());

                player.sendMessage(messageManager.getMessage("member", "invite_sent", Map.of("player", targetPlayer.getName())));
                targetPlayer.sendMessage(messageManager.getMessage("member", "invite_received", Map.of("guild", playerGuild.getName())));
                break;

            case "accept":
                guildManager.acceptInvite(player);
                player.sendMessage(messageManager.getMessage("member", "accept_success"));
                break;

            case "reject":
                guildManager.rejectInvite(player.getUniqueId());
                player.sendMessage(messageManager.getMessage("member", "reject_success"));
                break;

            case "info":
                if (guild == null) {
                    player.sendMessage(messageManager.getMessage("member", "not_in_guild"));
                    return true;
                }

                // Kiểm tra xem người chơi có GUI cho guild không
                if (!guildManager.hasGuildGUI(player)) {
                    player.sendMessage(messageManager.getMessage("member", "not_in_guild"));
                    return true;
                }

                GuildGUI.displayGuildInfo(player, guild, 0);
                break;
            case "kick":
                if (args.length < 2) {
                    player.sendMessage(messageManager.getMessage("member", "usage"));
                    return true;
                }

                Guild guildKick = guildManager.getPlayerGuild(player);
                if (guildKick == null) {
                    player.sendMessage(messageManager.getMessage("member", "guild_not_exist"));
                    return true;
                }

                Player targetKick = Bukkit.getPlayerExact(args[1]);
                if (targetKick == null) {
                    player.sendMessage(messageManager.getMessage("member", "player_not_found"));
                    return true;
                }

                if (!guildKick.hasMember(targetKick.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("member", "player_not_found"));
                    return true;
                }

                guildManager.kickMember(player, guildKick.getName(), targetKick.getUniqueId());
                break;

            case "disband":
                Guild guildToDisband = guildManager.getPlayerGuild(player);

                if (guildToDisband == null) {
                    player.sendMessage(messageManager.getMessage("member", "not_guild_leader")); // Bạn không thuộc guild nào.
                    return true;
                }

                if (!guildManager.isGuildLeader(player)) {
                    player.sendMessage(messageManager.getMessage("member", "not_guild_leader")); // Chỉ leader mới có thể giải tán guild.
                    return true;
                }

                String nameToRemove = guildToDisband.getName();
                guildManager.removeGuild(nameToRemove);

                // Gửi thông báo cho tất cả thành viên nếu online
                for (UUID memberId : guildToDisband.getMembers().keySet()) {
                    Player members = Bukkit.getPlayer(memberId);
                    if (members != null && members.isOnline()) {
                        player.sendMessage(messageManager.getMessage("member", "disband_success", Map.of("guild", nameToRemove)));
                    }
                }

                player.sendMessage(messageManager.getMessage("member", "disband_success", Map.of("guild", nameToRemove)));
                break;

            case "storage":
                Guild guildForStorage = guildManager.getPlayerGuild(player);
                if (guildForStorage == null) {
                    player.sendMessage(messageManager.getMessage("member", "not_guild_leader")); // Bạn không thuộc guild nào.
                    return true;
                }

                // Kiểm tra xem kho chung có đang bị khóa (bởi người khác) hay không
                UUID locker = guildManager.getGuildStorageLocker(guildForStorage.getName());
                if (locker != null && !locker.equals(player.getUniqueId())) {
                    player.sendMessage(messageManager.getMessage("member", "storage_locked")); // Kho chung đang được sử dụng bởi người chơi khác. Vui lòng thử lại sau.
                    return true;
                }

                // Lock kho chung cho player này
                guildManager.lockGuildStorage(guildForStorage.getName(), player.getUniqueId());

                // Mở inventory kho chung cho player
                player.openInventory(guildForStorage.getSharedStorageInventory());

                player.sendMessage(messageManager.getMessage("member", "storage_opened")); // Đã mở kho chung guild.

                break;

            case "chat":
                if (guild == null) {
                    player.sendMessage("§cBạn không thuộc guild nào.");
                    return true;
                }

                if (args.length == 1) {
                    // toggle chat mode
                    guildManager.toggleGuildChat(player);
                    return true;
                }

                String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                guildManager.sendGuildMessage(guild, player, message);
                break;

            case "base":
                if (args.length < 2 || !args[1].equalsIgnoreCase("claim")) {
                    player.sendMessage("§cSử dụng: /g base claim");
                    return true;
                }

                Guild g = guildManager.getPlayerGuild(player);
                if (g == null) {
                    player.sendMessage("§cBạn chưa tham gia guild nào.");
                    return true;
                }

                Member memberInfo = g.getMembers().get(player.getUniqueId());
                if (memberInfo == null) {
                    player.sendMessage("§cBạn không phải thành viên guild.");
                    return true;
                }

                Guild.RankType ranks;
                try {
                    ranks = Guild.RankType.valueOf(memberInfo.getRank().toUpperCase().replace("-", ""));
                } catch (IllegalArgumentException e) {
                    ranks = Guild.RankType.MEMBER;
                }

                if (ranks != Guild.RankType.LEADER) {
                    player.sendMessage("§cChỉ leader mới được phép claim base.");
                    return true;
                }

                Map<String, BaseData.BaseClaim> allBases = BaseData.loadBases();
                for (BaseData.BaseClaim base : allBases.values()) {
                    if (base.guildName != null && base.guildName.equalsIgnoreCase(g.getName()) && base.owner != null && !base.owner.isEmpty()) {
                        player.sendMessage("§cGuild của bạn đã sở hữu một base.");
                        return true;
                    }
                }

                Location playerLoc = player.getLocation();
                boolean claimed = false;

                for (Map.Entry<String, BaseData.BaseClaim> entry : allBases.entrySet()) {
                    BaseData.BaseClaim base = entry.getValue();

                    if ((base.owner == null || base.owner.isEmpty()) &&
                            GuildManager.isInside(playerLoc, base.pos1, base.pos2)) {

                        String baseId = entry.getKey();

                        // Tạm gán (chưa lưu)
                        base.owner = player.getName();
                        base.guildName = g.getName();
                        g.setBaseID(baseId);

                        // Tạo region
                        if (!BaseRegionManager.createBaseRegion(baseId, base.pos1, base.pos2, g, player.getWorld())) {
                            player.sendMessage("§cLỗi khi tạo region bảo vệ cho base.");
                            base.owner = null;
                            base.guildName = null;
                            g.setBaseID(null);
                            return true;
                        }

                        // Tính center và paste schematic
                        Location center = guildManager.getCenter(base.pos1, base.pos2);
                        if (center == null) {
                            player.sendMessage("§cKhông tính được trung tâm base.");
                            base.owner = null;
                            base.guildName = null;
                            g.setBaseID(null);
                            BaseRegionManager.removeBaseRegion(baseId, player.getWorld());
                            return true;
                        }

                        File schematicFile = new File(plugin.getDataFolder(), "schem/baseb1.schem");
                        ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                        if (format == null) {
                            player.sendMessage("§cKhông xác định được định dạng schematic.");
                            base.owner = null;
                            base.guildName = null;
                            g.setBaseID(null);
                            BaseRegionManager.removeBaseRegion(baseId, player.getWorld());
                            return true;
                        }

                        try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                            Clipboard clipboard = reader.read();

                            int width = clipboard.getRegion().getWidth();
                            int length = clipboard.getRegion().getLength();

                            Location pasteLocation = center.clone().subtract(width / 2, 0, length / 2);

                            // Paste schematic
                            guildManager.pasteSchematicAtBase(player, plugin, baseId, "baseb1.schem");

                            // Tạo hologram
                            Location holoLoc = center.clone().add(0, 2, 0);
                            BaseHologramManager.createClaimedBaseHologram(baseId, holoLoc, g.getName(), g.getLevel());

                        } catch (IOException e) {
                            player.sendMessage("§cLỗi khi đọc schematic.");
                            e.printStackTrace();
                            base.owner = null;
                            base.guildName = null;
                            g.setBaseID(null);
                            BaseRegionManager.removeBaseRegion(baseId, player.getWorld());
                            return true;
                        }

                        // ✅ Mọi thứ thành công → giờ mới lưu
                        if (!guildManager.saveBaseData(allBases, player)) {
                            player.sendMessage("§cLỗi khi lưu dữ liệu base.");
                            base.owner = null;
                            base.guildName = null;
                            g.setBaseID(null);
                            BaseRegionManager.removeBaseRegion(baseId, player.getWorld());
                            return true;
                        }

                        player.sendMessage("§aBạn đã chiếm base thành công cho guild '" + g.getName() + "'.");
                        player.sendMessage("§aRegion bảo vệ đã được tạo! Không có PvP trong khu vực này.");
                        claimed = true;
                        break;
                    }
                }

                if (!claimed) {
                    player.sendMessage("§cKhông có base trống nào ở vị trí bạn đang đứng.");
                }

                return true;

            // Thêm vào switch case của command handler
            case "upgrade":
                if (args.length < 2 || !args[1].equalsIgnoreCase("base")) {
                    player.sendMessage("§cSử dụng: /g upgrade base");
                    return true;
                }

                if (guild == null) {
                    player.sendMessage("§cBạn chưa tham gia guild nào.");
                    return true;
                }

                Member memberInfos = guild.getMembers().get(player.getUniqueId());
                if (memberInfos == null) {
                    player.sendMessage("§cBạn không phải thành viên guild.");
                    return true;
                }

                Guild.RankType rankss;
                try {
                    rankss = Guild.RankType.valueOf(memberInfos.getRank().toUpperCase().replace("-", ""));
                } catch (IllegalArgumentException e) {
                    rankss = Guild.RankType.MEMBER; // mặc định nếu không đúng rank
                }

                if (rankss != Guild.RankType.LEADER) {
                    player.sendMessage("§cChỉ leader mới được phép upgrade base.");
                    return true;
                }

                // Tìm base của guild
                Map<String, BaseData.BaseClaim> allBasess = BaseData.loadBases();
                String baseId = null;
                BaseData.BaseClaim playerBase = null;

                for (Map.Entry<String, BaseData.BaseClaim> entry : allBasess.entrySet()) {
                    BaseData.BaseClaim base = entry.getValue();
                    if (base.guildName.equalsIgnoreCase(guild.getName()) &&
                            base.owner != null && !base.owner.isEmpty()) {
                        baseId = entry.getKey();
                        playerBase = base;
                        break;
                    }
                }

                if (playerBase == null) {
                    player.sendMessage("§cGuild của bạn chưa sở hữu base nào.");
                    return true;
                }

                // Kiểm tra level hiện tại và điều kiện upgrade
                int currentLevel = playerBase.level;
                int nextLevel = currentLevel + 1;

                if (nextLevel > guildManager.getMaxBaseLevel()) {
                    player.sendMessage("§cBase đã đạt level tối đa!");
                    return true;
                }

                // Kiểm tra điều kiện upgrade (ví dụ: tiền, item, thành viên...)
                if (!guildManager.canUpgradeBase(guild, currentLevel, nextLevel)) {
                    player.sendMessage("§cKhông đủ điều kiện để upgrade base lên level " + nextLevel);
                    guildManager.showUpgradeRequirements(player, nextLevel);
                    return true;
                }

                try {
                    // Paste schematic level mới
                    String schematicName = guildManager.getSchematicForLevel(nextLevel);
                    BlockVector3 size = guildManager.pasteSchematicAtBase(player, plugin, baseId, schematicName);

                    // Cập nhật level trong database
                    guildManager.updateBaseLevel(baseId, nextLevel);

                    // Trừ resources cần thiết
                    guildManager.consumeUpgradeResources(guild, nextLevel);

                    // Cập nhật hologram
                    Location center = guildManager.getCenter(playerBase.pos1, playerBase.pos2);
                    if (center != null) {
                        Location holoLoc = center.clone().add(0, 2, 0);
                        BaseHologramManager.updateBaseHologram(baseId, holoLoc, guild.getName(), nextLevel);
                    }

                    player.sendMessage("§a§lUPGRADE THÀNH CÔNG!");
                    player.sendMessage("§eBased đã được nâng cấp lên level §6" + nextLevel);
                    player.sendMessage("§7Kích thước schematic: §f" + size.getX() + "x" + size.getY() + "x" + size.getZ());

                    // Thông báo cho toàn guild
                    guildManager.broadcastToGuild(guild,"§6" + player.getName() + " §ađã nâng cấp base lên level §6" + nextLevel + "§a!");

                } catch (Exception e) {
                    player.sendMessage("§cLỗi khi upgrade base: " + e.getMessage());
                    e.printStackTrace();
                }

                return true;

            default:
                player.sendMessage(messageManager.getMessage("member", "usage"));
                break;
        }

        return true;
    }
}
