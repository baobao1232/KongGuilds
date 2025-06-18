package king.kongguilds.WorldGuard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import king.kongguilds.Base.BaseData;
import king.kongguilds.Model.Guild;
import king.kongguilds.Model.Member;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static king.kongguilds.Base.BaseData.loadBases;

public class BaseRegionManager {

    /**
     * Tạo WorldGuard region cho guild base
     * @param baseId ID của base
     * @param pos1 Vị trí góc 1
     * @param pos2 Vị trí góc 2
     * @param guild Guild sở hữu base
     * @param world World chứa base
     * @return true nếu tạo thành công
     */
    public static boolean createBaseRegion(String baseId, Location pos1, Location pos2, Guild guild, World world) {
        try {
            // Lấy RegionManager của world
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                Bukkit.getLogger().info("RegionManager is null for world: " + world.getName());
                return false;
            }

            // Tạo region name - đảm bảo tên hợp lệ
            String regionName = "guild_base_" + baseId.toLowerCase().replaceAll("[^a-z0-9_]", "_");

            // Kiểm tra region đã tồn tại chưa
            if (regionManager.hasRegion(regionName)) {
                Bukkit.getLogger().info("Region " + regionName + " already exists!");
                return false;
            }

            // Tạo BlockVector3 từ Location
            BlockVector3 min = BlockVector3.at(
                    Math.min(pos1.getBlockX(), pos2.getBlockX()),
                    Math.min(pos1.getBlockY(), pos2.getBlockY()),
                    Math.min(pos1.getBlockZ(), pos2.getBlockZ())
            );

            BlockVector3 max = BlockVector3.at(
                    Math.max(pos1.getBlockX(), pos2.getBlockX()),
                    Math.max(pos1.getBlockY(), pos2.getBlockY()),
                    Math.max(pos1.getBlockZ(), pos2.getBlockZ())
            );

            // Tạo ProtectedCuboidRegion
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionName, min, max);

            // Thiết lập owners - KIỂM TRA NULL
            DefaultDomain owners = new DefaultDomain();
            UUID leaderUUID = guild.getLeader();
            if (leaderUUID != null) {
                owners.addPlayer(leaderUUID);
                Bukkit.getLogger().info("Added leader UUID: " + leaderUUID + " to owners");
            } else {
                Bukkit.getLogger().warning("Guild leader UUID is null! Cannot add owner to region.");
                // Có thể thêm owner bằng tên thay vì UUID
                // owners.addPlayer(leaderName); // nếu có tên leader
            }
            region.setOwners(owners);

            // Thiết lập members - KIỂM TRA NULL CHO TỪNG MEMBER
            DefaultDomain members = new DefaultDomain();
            if (guild.getMembers() != null) {
                for (Member member : guild.getMembers().values()) {
                    if (member != null) {
                        UUID memberUUID = member.getUUID();
                        if (memberUUID != null && !Guild.RankType.LEADER.name().equalsIgnoreCase(member.getRank())) {
                            members.addPlayer(memberUUID);
                            Bukkit.getLogger().info("Added member UUID: " + memberUUID + " to members");
                        } else if (memberUUID == null) {
                            Bukkit.getLogger().warning("Member UUID is null for member: " + member.getName() +
                                    ". Trying to add by name instead.");
                            // Thêm member bằng tên nếu UUID null
                            if (member.getName() != null && !member.getName().isEmpty()) {
                                members.addPlayer(member.getName());
                                Bukkit.getLogger().info("Added member by name: " + member.getName());
                            }
                        }
                    }
                }
            }
            region.setMembers(members);

            // Thiết lập flags
            try {
                region.setFlag(Flags.PVP, StateFlag.State.DENY);
                region.setFlag(Flags.INTERACT, StateFlag.State.ALLOW);
                region.setFlag(Flags.USE, StateFlag.State.ALLOW);
                region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.ALLOW);
                region.setFlag(Flags.ENTRY, StateFlag.State.DENY); // ✅ CHẶN NGƯỜI LẠ
                region.setFlag(Flags.EXIT, StateFlag.State.ALLOW);
            } catch (Exception flagEx) {
                Bukkit.getLogger().warning("Error setting flags: " + flagEx.getMessage());
                flagEx.printStackTrace();
            }

            // Thêm region vào RegionManager
            regionManager.addRegion(region);

            // Lưu dữ liệu (quan trọng!)
            try {
                regionManager.save();
                Bukkit.getLogger().info("Successfully created and saved region: " + regionName);
            } catch (Exception saveEx) {
                Bukkit.getLogger().warning("Error saving region data: " + saveEx.getMessage());
                saveEx.printStackTrace();
                return false;
            }
            return true;

        } catch (Exception e) {
            Bukkit.getLogger().severe("Error creating region for baseId: " + baseId);
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Cập nhật members trong region khi có thay đổi guild
     * @param baseId ID của base
     * @param guild Guild được cập nhật
     * @param world World chứa base
     * @return true nếu cập nhật thành công
     */
    public static boolean updateBaseRegionMembers(String baseId, Guild guild, World world) {
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                return false;
            }

            String regionName = "guild_base_" + baseId.toLowerCase();
            ProtectedRegion region = regionManager.getRegion(regionName);

            if (region == null) {
                return false;
            }

            DefaultDomain owners = new DefaultDomain();
            UUID leaderUuid = guild.getLeader();
            if (leaderUuid != null) {
                owners.addPlayer(leaderUuid);
            }
            region.setOwners(owners);

            DefaultDomain members = new DefaultDomain();

            for (Member member : guild.getMembers().values()) {
                if (!member.getRank().equalsIgnoreCase(Guild.RankType.LEADER.name())) {
                    members.addPlayer(member.getUUID());
                }
            }

            region.setMembers(members);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Thêm member mới vào region
     * @param baseId ID của base
     * @param playerUUID UUID của player
     * @param world World chứa base
     * @return true nếu thêm thành công
     */
    public static boolean addMemberToBaseRegion(String baseId, UUID playerUUID, World world) {
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                return false;
            }

            String regionName = "guild_base_" + baseId.toLowerCase();
            ProtectedRegion region = regionManager.getRegion(regionName);

            if (region == null) {
                return false;
            }

            // Thêm player vào members
            DefaultDomain members = region.getMembers();
            members.addPlayer(playerUUID);
            region.setMembers(members);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Xóa member khỏi region
     * @param baseId ID của base
     * @param playerUUID UUID của player
     * @param world World chứa base
     * @return true nếu xóa thành công
     */
    public static boolean removeMemberFromBaseRegion(String baseId, UUID playerUUID, World world) {
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                return false;
            }

            String regionName = "guild_base_" + baseId.toLowerCase();
            ProtectedRegion region = regionManager.getRegion(regionName);

            if (region == null) {
                return false;
            }

            // Xóa player khỏi members
            DefaultDomain members = region.getMembers();
            members.removePlayer(playerUUID);
            region.setMembers(members);

            // Xóa khỏi owners nếu có
            DefaultDomain owners = region.getOwners();
            owners.removePlayer(playerUUID);
            region.setOwners(owners);

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Xóa region khi guild bỏ base
     * @param baseId ID của base
     * @param world World chứa base
     * @return true nếu xóa thành công
     */
    public static boolean removeBaseRegion(String baseId, World world) {
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                return false;
            }

            String regionName = "guild_base_" + baseId.toLowerCase();

            // Nếu removeRegion trả về Set<ProtectedRegion>
            Set<ProtectedRegion> removedRegions = regionManager.removeRegion(regionName);

            return removedRegions != null && !removedRegions.isEmpty();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


    /**
     * Kiểm tra xem region có tồn tại không
     * @param baseId ID của base
     * @param world World chứa base
     * @return true nếu region tồn tại
     */
    public static boolean baseRegionExists(String baseId, World world) {
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) {
                return false;
            }

            String regionName = "guild_base_" + baseId.toLowerCase();
            return regionManager.hasRegion(regionName);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean addMemberToRegion(String regionId, String playerName, World world) {
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) return false;

            ProtectedRegion region = regionManager.getRegion(regionId.toLowerCase());
            if (region == null) return false;

            region.getMembers().addPlayer(playerName);
            region.setMembers(region.getMembers());

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public static boolean removeMemberFromRegion(String regionId, String playerName, World world) {
        try {
            RegionManager regionManager = WorldGuard.getInstance()
                    .getPlatform()
                    .getRegionContainer()
                    .get(BukkitAdapter.adapt(world));

            if (regionManager == null) return false;

            ProtectedRegion region = regionManager.getRegion(regionId.toLowerCase());
            if (region == null) return false;

            region.getMembers().removePlayer(playerName);
            region.setMembers(region.getMembers());

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}