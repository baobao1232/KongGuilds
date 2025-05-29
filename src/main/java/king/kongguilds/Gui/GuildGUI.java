package king.kongguilds.Gui;

import king.kongguilds.Model.Guild;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GuildGUI {

    public static void displayGuildInfo(Player player, Guild guild) {
        // Check if player has a GUI; if not, display an error message
        if (guild == null) {
            player.sendMessage(ChatColor.RED + "Guild không tồn tại hoặc chưa có GUI.");
            return;
        }

        // Create the inventory GUI with a custom size
        Inventory gui = Bukkit.createInventory(
                null,
                27,
                ChatColor.GREEN + "Guild: " + guild.getName() + ChatColor.YELLOW + " | Cấp: " + guild.getLevel()
        );


        // Get the sorted list of members
        List<Map.Entry<UUID, Guild.MemberInfo>> sortedMembers = guild.getSortedMembers();

        // Loop through each member and add an item for them in the GUI
        for (int i = 0; i < sortedMembers.size(); i++) {
            Map.Entry<UUID, Guild.MemberInfo> memberEntry = sortedMembers.get(i);
            UUID memberId = memberEntry.getKey();
            Guild.MemberInfo info = memberEntry.getValue();

            // Create an ItemStack to represent each member (e.g., player head or a specific item)
            ItemStack item = new ItemStack(Material.PLAYER_HEAD); // Change to any material you like
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.YELLOW + Bukkit.getOfflinePlayer(memberId).getName());

                // Set item lore to display rank and points
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Rank: " + info.getRankName());
                lore.add(ChatColor.GRAY + "Role: " + info.getMemberType());
                lore.add(ChatColor.GRAY + "Points: " + info.getPoints());
                meta.setLore(lore);

                item.setItemMeta(meta);
            }

            // Place the item in the inventory slot
            gui.setItem(i, item);
        }

        // Open the GUI for the player
        player.openInventory(gui);
    }
}
