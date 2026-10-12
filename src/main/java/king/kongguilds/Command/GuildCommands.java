package king.kongguilds.Command;

import king.kongguilds.Base.BaseData;
import king.kongguilds.Config.GuildIconConfig;
import king.kongguilds.Gui.GuildCreateGUI;
import king.kongguilds.Gui.GuildGUI;
import king.kongguilds.Manager.GuildManager;
import king.kongguilds.Manager.GuildSkillManager;
import king.kongguilds.Manager.MessageManager;
import king.kongguilds.Model.Guild;
import king.kongguilds.Model.Member;
import king.kongguilds.Main;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public class GuildCommands implements CommandExecutor {

    private final Main plugin;
    private final GuildManager guildManager;
    private final MessageManager messageManager;

    public GuildCommands(
            Main plugin,
            GuildManager guildManager,
            MessageManager messageManager
    ) {
        this.plugin = plugin;
        this.guildManager = guildManager;
        this.messageManager = messageManager;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(
                    messageManager.getMessage(
                            "member",
                            "only_players"
                    )
            );
            return true;
        }

        boolean isAdmin = player.hasPermission("guild.admin");

        if (args.length == 0) {
            sendHelp(player, isAdmin);
            return true;
        }

        String sub = args[0].toLowerCase();

        /* =========================================================
           CREATE
           /g create <name>
           ========================================================= */

        if (sub.equals("create")) {

//            if (args.length < 2) {
//                player.sendMessage(messageManager.getMessage("member", "create_usage"));
//                return true;
//            }
//
//            String name = String.join(
//                    " ",
//                    Arrays.copyOfRange(args, 1, args.length)
//            );
//
//            switch (guildManager.createGuild(player, name)) {
//
//                case SUCCESS:
//                    break;
//
//                case ALREADY_IN_GUILD:
//                    player.sendMessage(messageManager.getMessage("member", "already_in_guild"));
//                    break;
//
//                case NAME_EXISTS:
//                    player.sendMessage(messageManager.getMessage("member", "guild_already_exists"));
//                    break;
//
//                case INVALID_NAME:
//                    player.sendMessage(messageManager.getMessage("member", "invalid_guild_name"));
//                    break;
//            }
            GuildCreateGUI.start(player, args.length >= 2 ? args[1] : null);
            return true;
        }

        /* =========================================================
           INFO
           /g info
           ========================================================= */

        if (sub.equals("info")) {

            Guild guild = guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "not_in_guild"
                        )
                );
                return true;
            }

            GuildGUI.openGuildInfoGUI(
                    player,
                    guild,
                    0
            );

            return true;
        }

        /* =========================================================
           SKILL
           /g skill
           ========================================================= */

        if (sub.equals("skill") || sub.equals("skills")) {

            Guild guild = guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "not_in_guild"
                        )
                );
                return true;
            }

            GuildGUI.openGuildSkillTree(
                    player,
                    guild
            );

            return true;
        }

        /* =========================================================
           AUCTION
           /g auction
           ========================================================= */

        if (sub.equals("auction") || sub.equals("ah")) {

            Guild guild = guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "not_in_guild"
                        )
                );
                return true;
            }

            GuildGUI.openAuctionGUI(
                    player,
                    guild,
                    plugin.getAuctionManager()
                            .getGuildAuctions(
                                    guild.getName()
                            )
            );

            return true;
        }

        /* =========================================================
           INVITE
           /g invite <player>
           ========================================================= */

        if (sub.equals("invite")) {

            Guild guild = guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "not_in_guild"
                        )
                );
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(messageManager.getMessage("member", "invite_usage"));
                return true;
            }

            Member inviter =
                    guild.getMembers()
                            .get(player.getUniqueId());

            if (inviter == null) {
                player.sendMessage(messageManager.getMessage("member", "member_data_not_found"));
                return true;
            }

            if (!hasManagementRank(inviter)) {
                player.sendMessage(messageManager.getMessage("member", "no_invite_permission"));
                return true;
            }

            Player target =
                    Bukkit.getPlayerExact(args[1]);

            if (target == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "player_not_found"
                        )
                );
                return true;
            }

            if (guildManager.getPlayerGuild(target) != null) {
                player.sendMessage(messageManager.getMessage("member", "target_already_in_guild"));
                return true;
            }

            guildManager.inviteMember(
                    target.getUniqueId(),
                    guild.getName()
            );

            player.sendMessage(messageManager.getMessage("member", "invite_success")
                    .replace("{player}", target.getName()));

            target.sendMessage(messageManager.getMessage("member", "invite_received")
                    .replace("{guild}", guild.getName()));

            target.sendMessage(messageManager.getMessage("member", "invite_accept_hint"));

            return true;
        }

        /* =========================================================
           KICK
           /g kick <player>
           ========================================================= */

        if (sub.equals("kick")) {

            Guild guild =
                    guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "not_in_guild"
                        )
                );
                return true;
            }

            if (args.length < 2) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "kick_usage"
                        )
                );
                return true;
            }

            Member kicker =
                    guild.getMembers()
                            .get(player.getUniqueId());

            if (kicker == null ||
                    !hasManagementRank(kicker)) {

                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "no_kick_permission"
                        )
                );
                return true;
            }

            Member target =
                    findMember(
                            guild,
                            args[1]
                    );

            if (target == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "member_not_found"
                        ).replace(
                                "{player}",
                                args[1]
                        )
                );
                return true;
            }

            if (target.getUUID()
                    .equals(guild.getLeader())) {

                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "cannot_kick_leader"
                        )
                );
                return true;
            }

            if (target.getUUID()
                    .equals(player.getUniqueId())) {

                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "cannot_kick_self"
                        )
                );
                return true;
            }

            guildManager.kickMember(
                    player,
                    guild.getName(),
                    target.getUUID()
            );

            guildManager.saveGuildSync(
                    guild.getName()
            );

            return true;
        }

        /* =========================================================
           LEAVE
           /g leave
           ========================================================= */

        if (sub.equals("leave")) {
            guildManager.leaveGuild(player);
            return true;
        }

        /* =========================================================
           DISBAND
           /g disband
           ========================================================= */

        if (sub.equals("disband")) {
            guildManager.disbandGuild(player);
            return true;
        }

        /* =========================================================
           DONATE
           /g donate
           ========================================================= */

        if (sub.equals("donate")) {

            Guild guild =
                    guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(messageManager.getMessage("member", "not_in_guild"));
                return true;
            }

//            if (guild.getBaseID() == null ||
//                    BaseData.getBaseClaim(
//                            guild.getBaseID()
//                    ) == null) {
//
//                player.sendMessage(messageManager.getMessage("member", "no_base"));
//                return true;
//            }

//            if (!guildManager.isPlayerInTheirOwnBase(player)) {
//
//                player.sendMessage(messageManager.getMessage("member", "must_be_in_base"));
//
//                return true;
//            }

            GuildGUI.openDonateGUI(
                    player,
                    guild
            );

            return true;
        }

        /* =========================================================
           STORAGE
           /g storage
           ========================================================= */

        if (sub.equals("storage") ||
                sub.equals("store")) {

            Guild guild =
                    guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "not_in_guild"
                        )
                );
                return true;
            }

            GuildGUI.openStorageGUI(
                    player,
                    guild
            );

            player.sendMessage(
                    messageManager.getMessage(
                            "member",
                            "storage_opened"
                    )
            );

            return true;
        }

        /* =========================================================
           ACCEPT
           /g accept
           ========================================================= */

        if (sub.equals("accept")) {

            guildManager.acceptInvite(player);

            player.sendMessage(
                    messageManager.getMessage(
                            "member",
                            "accept_success"
                    )
            );

            return true;
        }

        /* =========================================================
           REJECT
           /g reject
           ========================================================= */

        if (sub.equals("reject")) {

            guildManager.rejectInvite(
                    player.getUniqueId()
            );

            player.sendMessage(
                    messageManager.getMessage(
                            "member",
                            "reject_success"
                    )
            );

            return true;
        }

        /* =========================================================
           CHAT
           /g chat
           /g chat <message>
           ========================================================= */

        if (sub.equals("chat")) {

            Guild guild =
                    guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(messageManager.getMessage("member", "not_in_guild"));
                return true;
            }

            if (args.length == 1) {

                guildManager.toggleGuildChat(player);

                return true;
            }

            String message =
                    String.join(
                            " ",
                            Arrays.copyOfRange(
                                    args,
                                    1,
                                    args.length
                            )
                    );

            guildManager.sendGuildMessage(
                    plugin,
                    guild,
                    player,
                    message
            );

            return true;
        }

        /* =========================================================
           BASE

           /g base
           /g base info
           /g base requirements
           /g base upgrade
           ========================================================= */

        if (sub.equals("base")) {

            Guild guild =
                    guildManager.getPlayerGuild(player);

            if (guild == null) {
                player.sendMessage(
                        messageManager.getMessage(
                                "member",
                                "not_in_guild"
                        )
                );
                return true;
            }

            if (guild.getBaseID() == null) {

                player.sendMessage(messageManager.getMessage("member", "no_base"));

                return true;
            }

            BaseData.BaseClaim base =
                    BaseData.getBaseClaim(
                            guild.getBaseID()
                    );

            if (base == null) {

                player.sendMessage(messageManager.getMessage("member", "base_data_not_found"));

                return true;
            }

            /* -------------------------
               /g base
               ------------------------- */

            if (args.length == 1 ||
                    args[1].equalsIgnoreCase("info")) {

                player.sendMessage(messageManager.getMessage("member", "guild_base_header"));

                player.sendMessage(messageManager.getMessage("member", "guild_base_name")
                        .replace("{guild}", guild.getName()));

                player.sendMessage(messageManager.getMessage("member", "guild_base_id")
                        .replace("{base_id}", guild.getBaseID()));

                player.sendMessage(messageManager.getMessage("member", "guild_base_level")
                        .replace("{level}", String.valueOf(base.level)));

                player.sendMessage(messageManager.getMessage("member", "guild_level")
                        .replace("{level}", String.valueOf(guild.getLevel())));

                player.sendMessage(messageManager.getMessage("member", "guild_members")
                        .replace("{members}", String.valueOf(guild.getMembers().size()))
                        .replace("{max_members}", String.valueOf(guild.getMaxMembers())));

                return true;
            }

            /* -------------------------
               /g base requirements
               ------------------------- */

            if (args[1].equalsIgnoreCase("requirements") ||
                    args[1].equalsIgnoreCase("req")) {

                int nextLevel =
                        base.level + 1;

                if (nextLevel >
                        GuildManager.getMaxBaseLevel()) {

                    player.sendMessage(messageManager.getMessage("member", "base_max_level"));

                    return true;
                }

                GuildManager.showUpgradeRequirements(
                        player,
                        nextLevel
                );

                return true;
            }

            /* -------------------------
               /g base upgrade
               ------------------------- */

            if (args[1].equalsIgnoreCase("upgrade")) {

                if (!guildManager.isGuildLeader(player) &&
                        !guildManager.isCoLeader(player)) {

                    player.sendMessage(messageManager.getMessage("member", "base_upgrade_permission"));

                    return true;
                }

                int currentLevel =
                        base.level;

                int nextLevel =
                        currentLevel + 1;

                if (nextLevel >
                        GuildManager.getMaxBaseLevel()) {

                    player.sendMessage(messageManager.getMessage("member", "base_upgrade_max_level"));

                    return true;
                }

                if (!GuildManager.canUpgradeBase(
                        guild,
                        currentLevel,
                        nextLevel
                )) {

                    GuildManager.showUpgradeRequirements(
                            player,
                            nextLevel
                    );

                    player.sendMessage(messageManager.getMessage("member", "base_upgrade_not_ready"));

                    return true;
                }

                player.sendMessage(messageManager.getMessage("member", "base_upgrade_start")
                        .replace("{current}", String.valueOf(currentLevel))
                        .replace("{next}", String.valueOf(nextLevel)));

                guildManager.upgradeBaseAsync(
                        player,
                        guild,
                        guild.getBaseID(),
                        nextLevel
                );

                return true;
            }

            player.sendMessage(messageManager.getMessage("member", "base_usage"));

            return true;
        }

        /* =========================================================
           ADMIN
           /g admin ...
           ========================================================= */

        if (sub.equals("admin")) {

            if (!isAdmin) {
                player.sendMessage(
                        messageManager.getMessage(
                                "admin",
                                "no_permission"
                        )
                );

                return true;
            }

            return handleAdmin(
                    player,
                    args
            );
        }

        /* =========================================================
           UNKNOWN COMMAND
           ========================================================= */

        sendHelp(player, isAdmin);

        return true;
    }

    /* =============================================================
       ADMIN COMMANDS
       ============================================================= */

    private boolean handleAdmin(Player player, String[] args) {

        if (args.length < 2) {
            sendAdminHelp(player);
            return true;
        }

        String sub = args[1].toLowerCase();

        switch (sub) {

            /* =====================================================
            RELOAD
            /g admin reload
            ===================================================== */

            case "reload":

                try {

                    guildManager.asyncLoadGuilds(
                            () -> player.sendMessage(
                                    messageManager.getMessage("admin", "reload_guild_success")
                            )
                    );

                    messageManager.load(
                            new File(
                                    plugin.getDataFolder(),
                                    "messages.yml"
                            )
                    );

                    GuildSkillManager.reload(
                            plugin
                    );
                    plugin.reloadConfig();
                    GuildIconConfig.load(plugin);

                    player.sendMessage(
                            "§aReload messages thành công."
                    );

                    player.sendMessage(
                            "§aReload skill tree thành công."
                    );

                } catch (IOException e) {

                    player.sendMessage(
                            "§cReload thất bại."
                    );

                    e.printStackTrace();
                }

                return true;

            /* =====================================================
               LIST
               /g admin list
               ===================================================== */

            case "list":

                if (guildManager.getAllGuilds().isEmpty()) {

                    player.sendMessage(messageManager.getMessage("admin", "no_guilds"));

                    return true;
                }

                player.sendMessage(messageManager.getMessage("admin", "guild_list_header"));

                for (Guild guild :
                        guildManager.getAllGuilds()) {

                    player.sendMessage(messageManager.getMessage("admin", "guild_list_entry")
                            .replace("{guild}", guild.getName())
                            .replace("{level}", String.valueOf(guild.getLevel()))
                            .replace("{members}", String.valueOf(guild.getMembers().size()))
                            .replace("{max_members}", String.valueOf(guild.getMaxMembers())));
                }

                return true;

            /* =====================================================
               SAVE
               /g admin save <guild>
               ===================================================== */

            case "save":

                if (args.length < 3) {

                    player.sendMessage(messageManager.getMessage("admin", "save_usage"));

                    return true;
                }

                Guild saveGuild =
                        findGuild(args[2]);

                if (saveGuild == null) {

                    player.sendMessage(messageManager.getMessage("admin", "guild_not_found")
                            .replace("{guild}", args[2]));

                    return true;
                }

                guildManager.saveGuildSync(
                        saveGuild.getName()
                );

                player.sendMessage(messageManager.getMessage("admin", "guild_saved")
                        .replace("{guild}", saveGuild.getName()));

                return true;

            /* =====================================================
               ADD MEMBER
               /g admin add <player> <guild>
               ===================================================== */

            case "add":

                if (args.length < 4) {
                    player.sendMessage(messageManager.getMessage("admin", "add_usage"));
                    return true;
                }

                Player target = Bukkit.getPlayerExact(args[2]);

                if (target == null) {
                    player.sendMessage(messageManager.getMessage("admin", "player_must_be_online"));
                    return true;
                }

                if (guildManager.getPlayerGuild(target) != null) {
                    player.sendMessage(messageManager.getMessage("admin", "player_already_in_guild"));
                    return true;
                }

                Guild targetGuild = findGuild(args[3]);

                if (targetGuild == null) {
                    player.sendMessage(messageManager.getMessage("admin", "guild_not_found")
                            .replace("{guild}", args[3]));
                    return true;
                }

                if (targetGuild.getMembers().size()
                        >= targetGuild.getMaxMembers()) {

                    player.sendMessage(messageManager.getMessage("admin", "guild_full"));
                    return true;
                }

                guildManager.addMember(
                        targetGuild.getName(),
                        target.getName(),
                        target.getUniqueId(),
                        "MEMBER",
                        "Member",
                        0
                );

                guildManager.saveGuildSync(
                        targetGuild.getName()
                );

                player.sendMessage(messageManager.getMessage("admin", "member_added")
                        .replace("{player}", target.getName())
                        .replace("{guild}", targetGuild.getName()));

                target.sendMessage(messageManager.getMessage("admin", "member_added_target")
                        .replace("{guild}", targetGuild.getName()));

                return true;

            /* =====================================================
               ADMIN KICK
               /g admin kick <guild> <player>
               ===================================================== */

            case "kick":

                if (args.length < 4) {

                    player.sendMessage(messageManager.getMessage("admin", "admin_kick_usage"));

                    return true;
                }

                Guild kickGuild =
                        findGuild(args[2]);

                if (kickGuild == null) {

                    player.sendMessage(messageManager.getMessage("admin", "guild_not_found")
                            .replace("{guild}", args[2]));

                    return true;
                }

                Member kickTarget =
                        findMember(
                                kickGuild,
                                args[3]
                        );

                if (kickTarget == null) {

                    player.sendMessage(messageManager.getMessage("admin", "admin_member_not_found")
                            .replace("{player}", args[3]));

                    return true;
                }

                if (kickTarget.getUUID()
                        .equals(kickGuild.getLeader())) {

                    player.sendMessage(messageManager.getMessage("member", "cannot_kick_leader"));

                    return true;
                }

                if (!guildManager.kickMemberAsAdmin(
                        kickGuild.getName(),
                        kickTarget.getUUID()
                )) {

                    player.sendMessage(messageManager.getMessage("admin", "admin_kick_failed"));

                    return true;
                }

                guildManager.saveGuildSync(
                        kickGuild.getName()
                );

                player.sendMessage(messageManager.getMessage("admin", "admin_kick_success")
                        .replace("{player}", kickTarget.getName())
                        .replace("{guild}", kickGuild.getName()));

                Player onlineTarget =
                        Bukkit.getPlayer(
                                kickTarget.getUUID()
                        );

                if (onlineTarget != null) {

                    onlineTarget.sendMessage(messageManager.getMessage("admin", "admin_kick_target")
                            .replace("{guild}", kickGuild.getName()));
                }

                return true;

            /* =====================================================
               ADMIN DISBAND
               /g admin disband <guild>
               ===================================================== */

            case "disband":

                if (args.length < 3) {

                    player.sendMessage(messageManager.getMessage("admin", "disband_usage"));

                    return true;
                }

                Guild disbandGuild =
                        findGuild(args[2]);

                if (disbandGuild == null) {

                    player.sendMessage(messageManager.getMessage("admin", "guild_not_found")
                            .replace("{guild}", args[2]));

                    return true;
                }

                guildManager.removeGuild(
                        disbandGuild.getName()
                );

                player.sendMessage(messageManager.getMessage("admin", "disband_success")
                        .replace("{guild}", disbandGuild.getName()));

                return true;

            /* =====================================================
               ADD EXP
               /g admin addexp <guild> <amount>
               ===================================================== */

            case "addexp":

                if (args.length < 4) {

                    player.sendMessage(messageManager.getMessage("admin", "addexp_usage"));

                    return true;
                }

                Guild expGuild =
                        findGuild(args[2]);

                if (expGuild == null) {

                    player.sendMessage(messageManager.getMessage("admin", "guild_not_found")
                            .replace("{guild}", args[2]));

                    return true;
                }

                Integer exp =
                        parseInt(
                                player,
                                args[3],
                                "EXP"
                        );

                if (exp == null ||
                        exp <= 0) {

                    return true;
                }

                expGuild.addExp(exp);

                guildManager.saveGuildSync(
                        expGuild.getName()
                );

                player.sendMessage(messageManager.getMessage("admin", "addexp_success")
                        .replace("{amount}", String.valueOf(exp))
                        .replace("{guild}", expGuild.getName()));

                return true;

            /* =====================================================
               ADD LEVEL
               /g admin addlevel <guild> <amount>
               ===================================================== */

            case "addlevel":

                if (args.length < 4) {

                    player.sendMessage(messageManager.getMessage("admin", "addlevel_usage"));

                    return true;
                }

                Guild levelGuild =
                        findGuild(args[2]);

                if (levelGuild == null) {

                    player.sendMessage(messageManager.getMessage("admin", "guild_not_found")
                            .replace("{guild}", args[2]));

                    return true;
                }

                Integer level =
                        parseInt(
                                player,
                                args[3],
                                "Level"
                        );

                if (level == null ||
                        level <= 0) {

                    return true;
                }

                levelGuild.addLevel(level);

                guildManager.saveGuildSync(
                        levelGuild.getName()
                );

                player.sendMessage(messageManager.getMessage("admin", "addlevel_success")
                        .replace("{amount}", String.valueOf(level))
                        .replace("{guild}", levelGuild.getName()));

                return true;

            /* =====================================================
               ADD POINTS
               /g admin addpoints <guild> <player> <amount>
               ===================================================== */

            case "addpoints":

                if (args.length < 5) {

                    player.sendMessage(messageManager.getMessage("admin", "addpoints_usage"));

                    return true;
                }

                Guild pointsGuild =
                        findGuild(args[2]);

                if (pointsGuild == null) {

                    player.sendMessage(messageManager.getMessage("admin", "guild_not_found")
                            .replace("{guild}", args[2]));

                    return true;
                }

                Member pointsTarget =
                        findMember(
                                pointsGuild,
                                args[3]
                        );

                if (pointsTarget == null) {

                    player.sendMessage(messageManager.getMessage("member", "member_not_found")
                            .replace("{player}", args[3]));

                    return true;
                }

                Integer points =
                        parseInt(
                                player,
                                args[4],
                                "Điểm"
                        );

                if (points == null) {
                    return true;
                }

                guildManager.updateMemberPoints(
                        pointsGuild.getName(),
                        pointsTarget.getUUID(),
                        points
                );

                guildManager.saveGuildSync(
                        pointsGuild.getName()
                );

                player.sendMessage(messageManager.getMessage(
                                "admin",
                                points >= 0 ? "addpoints_success_add" : "addpoints_success_remove"
                        )
                        .replace("{amount}", String.valueOf(Math.abs(points)))
                        .replace("{player}", pointsTarget.getName()));

                return true;

               /* =====================================================
               ADD CONTRIBUTION
               /g admin addcontribution <guild> <amount>
               ===================================================== */

            case "addcontribution":

                if (args.length < 4) {

                    player.sendMessage(
                            "§c/g admin addcontribution <guild> <amount>"
                    );

                    return true;
                }

                Guild contributionGuild =
                        findGuild(args[2]);

                if (contributionGuild == null) {

                    player.sendMessage(
                            messageManager.getMessage("admin", "guild_not_found")
                                    .replace("{guild}", args[2])
                    );

                    return true;
                }

                Long contribution =
                        parseLong(
                                player,
                                args[3],
                                "Điểm cống hiến"
                        );

                if (contribution == null ||
                        contribution <= 0) {

                    return true;
                }

                contributionGuild.addContribution(contribution);

                guildManager.saveGuildSync(
                        contributionGuild.getName()
                );

                player.sendMessage(
                        "§aĐã thêm §e"
                                + contribution
                                + " §ađiểm cống hiến cho Guild §f"
                                + contributionGuild.getName()
                                + "§a."
                );

                return true;

                /* =====================================================
               REMOVE CONTRIBUTION
               /g admin removecontribution <guild> <amount>
               ===================================================== */

            case "removecontribution":

                if (args.length < 4) {

                    player.sendMessage(
                            "§c/g admin removecontribution <guild> <amount>"
                    );

                    return true;
                }

                Guild removeContributionGuild =
                        findGuild(args[2]);

                if (removeContributionGuild == null) {

                    player.sendMessage(
                            messageManager.getMessage("admin", "guild_not_found")
                                    .replace("{guild}", args[2])
                    );

                    return true;
                }

                Long removeAmount =
                        parseLong(
                                player,
                                args[3],
                                "Điểm cống hiến"
                        );

                if (removeAmount == null ||
                        removeAmount <= 0) {

                    return true;
                }

                if (!removeContributionGuild.removeContribution(removeAmount)) {

                    player.sendMessage(
                            "§cGuild chỉ còn §e"
                                    + removeContributionGuild.getAvailableContribution()
                                    + " §cđiểm cống hiến có thể sử dụng."
                    );

                    return true;
                }

                guildManager.saveGuildSync(
                        removeContributionGuild.getName()
                );

                player.sendMessage(
                        "§aĐã trừ §e"
                                + removeAmount
                                + " §ađiểm cống hiến của Guild §f"
                                + removeContributionGuild.getName()
                                + "§a."
                );

                return true;

            default:

                sendAdminHelp(player);

                return true;
        }
    }

    /* =============================================================
    HELP
    ============================================================= */

    private void sendHelp(Player player, boolean isAdmin) {

        for (String line :
                messageManager.getMessageList("help", "member")) {

            player.sendMessage(line);
        }

        if (isAdmin) {

            player.sendMessage("");

            for (String line :
                    messageManager.getMessageList("help", "admin")) {

                player.sendMessage(line);
            }
        }
    }

    private void sendAdminHelp(Player player) {

        for (String line :
                messageManager.getMessageList("help", "admin")) {

            player.sendMessage(line);
        }
    }

    /* =============================================================
       HELPERS
       ============================================================= */

    private boolean hasManagementRank(Member member) {

        String rank =
                member.getRank();

        return rank.equalsIgnoreCase("Leader")
                || rank.equalsIgnoreCase("Co-Leader")
                || rank.equalsIgnoreCase("Officer");
    }

    private Member findMember(
            Guild guild,
            String name
    ) {

        for (Map.Entry<UUID, Member> entry :
                guild.getMembers().entrySet()) {

            Member member =
                    entry.getValue();

            if (member.getName()
                    .equalsIgnoreCase(name)) {

                return member;
            }
        }

        return null;
    }

    private Guild findGuild(String name) {

        for (Guild guild :
                guildManager.getAllGuilds()) {

            if (guild.getName()
                    .equalsIgnoreCase(name)) {

                return guild;
            }
        }

        return null;
    }

    private Integer parseInt(
            Player player,
            String value,
            String type
    ) {

        try {

            return Integer.parseInt(value);

        } catch (NumberFormatException e) {

            player.sendMessage(messageManager.getMessage("system", "invalid_number")
                    .replace("{type}", type));

            return null;
        }
    }

    private Long parseLong(
            Player player,
            String value,
            String type
    ) {

        try {

            return Long.parseLong(value);

        } catch (NumberFormatException e) {

            player.sendMessage(
                    messageManager.getMessage(
                            "system",
                            "invalid_number"
                    ).replace("{type}", type)
            );

            return null;
        }
    }


}