package king.kongguilds;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GuildMemberEditGUI {

    public static void openEditGUI(Player editor, Guild guild, UUID targetMemberId) {
        Guild.MemberInfo targetMember = guild.getMembers().get(targetMemberId);
        if (targetMember == null) {
            editor.sendMessage(ChatColor.RED + "Thành viên không tồn tại trong guild.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 27,
                ChatColor.DARK_GREEN + "EditMember:" + targetMemberId.toString());

        // Tạo item đại diện cho member (player head)
        ItemStack memberHead = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = memberHead.getItemMeta();
        if (meta != null) {
            String playerName = Bukkit.getOfflinePlayer(targetMemberId).getName();
            meta.setDisplayName(ChatColor.YELLOW + (playerName != null ? playerName : targetMember.getName()));
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Rank: " + targetMember.getRankName());
            lore.add(ChatColor.GRAY + "Role: " + targetMember.getMemberType());
            meta.setLore(lore);
            memberHead.setItemMeta(meta);
        }
        gui.setItem(4, memberHead);

        // Tạo item tăng rank
        ItemStack increaseRank = new ItemStack(Material.LIME_CONCRETE);
        meta = increaseRank.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "Tăng rank");
            increaseRank.setItemMeta(meta);
        }
        gui.setItem(11, increaseRank);

        // Tạo item giảm rank
        ItemStack decreaseRank = new ItemStack(Material.RED_CONCRETE);
        meta = decreaseRank.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Giảm rank");
            decreaseRank.setItemMeta(meta);
        }
        gui.setItem(15, decreaseRank);

        // Tạo item kick member
        ItemStack kickMember = new ItemStack(Material.BARRIER);
        meta = kickMember.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_RED + "Kick khỏi guild");
            kickMember.setItemMeta(meta);
        }
        gui.setItem(22, kickMember);

        editor.openInventory(gui);
    }
}
