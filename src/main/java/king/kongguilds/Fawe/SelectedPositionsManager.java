package king.kongguilds.Fawe;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SelectedPositionsManager {

    private static final Map<UUID, Location[]> selections = new HashMap<>();

    // Set vị trí pos1 hoặc pos2 cho player
    public static void setPosition(Player player, Location loc, int posNumber) {
        if (posNumber != 1 && posNumber != 2) throw new IllegalArgumentException("posNumber phải là 1 hoặc 2");

        Location[] pos = selections.getOrDefault(player.getUniqueId(), new Location[2]);
        pos[posNumber - 1] = loc;
        selections.put(player.getUniqueId(), pos);
    }

    // Lấy vị trí pos1 và pos2 player đã chọn, có thể null nếu chưa chọn
    public static Location[] getSelectedPositions(Player player) {
        return selections.get(player.getUniqueId());
    }

    public static void clearPositions(Player player) {
        selections.remove(player.getUniqueId());
    }

}