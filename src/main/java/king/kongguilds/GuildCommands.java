package king.kongguilds;

import com.fastasyncworldedit.core.FaweAPI;
import com.google.gson.GsonBuilder;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.EditSessionBuilder;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.World;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import king.kongguilds.BASE.BaseData;
import king.kongguilds.BASE.BaseHologramManager;
import king.kongguilds.FAWE.SelectedPositionsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
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

                        String guildName = "GuildExample"; // TODO: lấy tên guild thật từ player
                        BaseData.saveBaseToFileWithId(id, guildName, player.getName(), positions[0], positions[1]);

                        Location center = guildManager.getCenter(positions[0], positions[1]);
                        if (center != null) {
                            BaseHologramManager.createUnclaimedBaseHologram(id, center);  // <-- Gọi manager thay vì gọi trực tiếp DHAPI
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
                        boolean result = BaseData.removeBaseById(baseIdToRemove);

                        if (result) {
                            BaseHologramManager.removeHologram(baseIdToRemove);  // Dùng manager xóa hologram
                            player.sendMessage(ChatColor.GREEN + "Base '" + baseIdToRemove + "' đã được xóa thành công.");
                        } else {
                            player.sendMessage(ChatColor.RED + "Không thể xóa base '" + baseIdToRemove + "'. Có thể base không tồn tại.");
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

                // Kiểm tra quyền mời
                Guild.MemberInfo info = playerGuild.getMembers().get(player.getUniqueId());
                Guild.RankType rank = info != null ? info.getRank() : null;
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

                GuildGUI.displayGuildInfo(player, guild);
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
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null && member.isOnline()) {
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

                Guild.MemberInfo infog = g.getMembers().get(player.getUniqueId());
                if (infog == null || infog.getRank() != Guild.RankType.LEADER) {
                    player.sendMessage("§cChỉ leader mới được phép claim base.");
                    return true;
                }

                Map<String, BaseData.BaseClaim> allBases = BaseData.loadBases();
                for (BaseData.BaseClaim base : allBases.values()) {
                    if (base.guildName.equalsIgnoreCase(g.getName()) && base.owner != null && !base.owner.isEmpty()) {
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

                        // Cập nhật dữ liệu base
                        base.owner = player.getName();
                        base.guildName = g.getName();

                        if (!guildManager.saveBaseData(allBases, player)) {
                            player.sendMessage("§cLỗi khi lưu dữ liệu base.");
                            return true;
                        }

                        // Tính trung tâm vùng
                        Location center = guildManager.getCenter(base.pos1, base.pos2);
                        if (center != null) {
                            // Tính toán vị trí paste chính xác dựa trên kích thước schematic
                            File schematicFile = new File(plugin.getDataFolder(), "schem/basetest01.schem");
                            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
                            if (format == null) {
                                player.sendMessage("§cKhông xác định được định dạng schematic.");
                                return true;
                            }

                            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                                Clipboard clipboard = reader.read();

                                int width = clipboard.getRegion().getWidth();
                                int height = clipboard.getRegion().getHeight();
                                int length = clipboard.getRegion().getLength();

                                // Vị trí chính xác để paste schematic (center - 1/2 chiều ngang)
                                Location pasteLocation = center.clone().subtract(width / 2, 0, length / 2);

                                // Paste schematic
                                guildManager.pasteSchematic(plugin, pasteLocation, "basetest01.schem");

                                // Tạo hologram
                                Location holoLoc = center.clone().add(0, 2, 0);
                                BaseHologramManager.createClaimedBaseHologram(entry.getKey(), holoLoc, g.getName());

                            } catch (IOException e) {
                                player.sendMessage("§cLỗi khi đọc schematic.");
                                e.printStackTrace();
                                return true;
                            }
                        }

                        claimed = true;
                        break;
                    }
                }

                if (claimed) {
                    player.sendMessage("§aBạn đã chiếm base thành công cho guild '" + g.getName() + "'.");
                } else {
                    player.sendMessage("§cKhông có base trống nào ở vị trí bạn đang đứng.");
                }
                return true;

            default:
                player.sendMessage(messageManager.getMessage("member", "usage"));
                break;
        }

        return true;
    }
}
