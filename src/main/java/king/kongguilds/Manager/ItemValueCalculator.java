package king.kongguilds.Manager;

import io.lumine.mythic.lib.api.item.NBTItem;
import net.Indyuce.mmoitems.MMOItems;
import net.Indyuce.mmoitems.api.Type;
import net.Indyuce.mmoitems.api.item.mmoitem.LiveMMOItem;
import net.Indyuce.mmoitems.stat.type.ItemStat;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public class ItemValueCalculator {

    /*
     * Trọng số định giá stat MMOItems.
     */
    private static final Map<String, Double> STAT_WEIGHTS = new HashMap<>();

    static {
        // Offensive
        STAT_WEIGHTS.put("ATTACK_DAMAGE", 0.20);
        STAT_WEIGHTS.put("ATTACK_SPEED", 0.40);
        STAT_WEIGHTS.put("CRITICAL_STRIKE_CHANCE", 0.30);
        STAT_WEIGHTS.put("CRITICAL_STRIKE_POWER", 0.30);

        // Defensive
        STAT_WEIGHTS.put("DEFENSE", 0.10);
        STAT_WEIGHTS.put("MAX_HEALTH", 0.10);
        STAT_WEIGHTS.put("MAX_MANA", 0.20);

        // Attributes
        STAT_WEIGHTS.put("STRENGTH", 0.10);
        STAT_WEIGHTS.put("DEXTERITY", 0.40);
        STAT_WEIGHTS.put("INTELLIGENCE", 0.30);
        STAT_WEIGHTS.put("VITALITY", 0.10);

        // Speed
        STAT_WEIGHTS.put("MOVEMENT_SPEED", 0.30);

        // Regeneration
        STAT_WEIGHTS.put("HEALTH_REGENERATION", 0.10);
        STAT_WEIGHTS.put("MANA_REGENERATION", 0.20);

        // Skill
        STAT_WEIGHTS.put("SKILL_COOLDOWN_REDUCTION", 0.30);

        // Block
        STAT_WEIGHTS.put("BLOCK_RATING", 0.20);
        STAT_WEIGHTS.put("BLOCK_POWER", 0.20);

        // Other
        STAT_WEIGHTS.put("PENETRATION", 0.30);
        STAT_WEIGHTS.put("WEAPON_DAMAGE", 0.20);
    }

    /**
     * Tính giá trị của MMOItem.
     */
    public static double calculateItemValue(ItemStack itemStack) {

        if (itemStack == null || itemStack.getType().isAir()) {
            return 0.0;
        }

        // Lấy MMOItems NBT
        NBTItem nbt = NBTItem.get(itemStack);

        if (nbt == null || !nbt.hasType()) {
            return 0.0;
        }

        // Lấy Type
        Type type = MMOItems.getType(nbt);

        if (type == null) {
            return 0.0;
        }

        LiveMMOItem mmoItem;

        try {
            mmoItem = new LiveMMOItem(nbt);
        } catch (Exception exception) {
            return 0.0;
        }

        BigDecimal value = BigDecimal.ZERO;

        /*
         * Duyệt toàn bộ stat mà Type của item hỗ trợ.
         */
        for (ItemStat<?, ?> stat : type.getAvailableStats()) {

            if (stat == null) {
                continue;
            }

            String statId = stat.getId();

            /*
             * Lấy trọng số của stat.
             */
            Double weight = STAT_WEIGHTS.get(statId);

            /*
             * Stat không nằm trong bảng định giá
             * thì bỏ qua.
             */
            if (weight == null || weight == 0.0) {
                continue;
            }

            double statValue;

            try {
                statValue = nbt.getStat(statId);
            } catch (Exception exception) {
                continue;
            }

            /*
             * Không tính stat = 0.
             */
            if (statValue == 0.0) {
                continue;
            }

            /*
             * Value += Stat × Weight
             */
            value = value.add(
                    BigDecimal.valueOf(statValue)
                            .multiply(BigDecimal.valueOf(weight))
            );
        }

        /*
         * Làm tròn 2 chữ số.
         */
        return Math.round(value.doubleValue() * 100.0) / 100.0;
    }

    /**
     * Lấy toàn bộ stat thực tế của MMOItem.
     *
     * Dùng để debug hoặc kiểm tra item.
     */
    public static Map<String, Double> getItemStats(ItemStack itemStack) {

        Map<String, Double> result = new HashMap<>();

        if (itemStack == null || itemStack.getType().isAir()) {
            return result;
        }

        NBTItem nbt = NBTItem.get(itemStack);

        if (nbt == null || !nbt.hasType()) {
            return result;
        }

        Type type = MMOItems.getType(nbt);

        if (type == null) {
            return result;
        }

        LiveMMOItem mmoItem;

        try {
            mmoItem = new LiveMMOItem(nbt);
        } catch (Exception exception) {
            return result;
        }

        /*
         * Duyệt tất cả stat MMOItems hỗ trợ.
         */
        for (ItemStat<?, ?> stat : type.getAvailableStats()) {

            if (stat == null) {
                continue;
            }

            String statId = stat.getId();

            try {

                double value = nbt.getStat(statId);

                if (value != 0.0) {
                    result.put(statId, value);
                }

            } catch (Exception ignored) {
                // Bỏ qua stat đặc biệt không phải số.
            }
        }

        return result;
    }

    /**
     * In toàn bộ thông tin stat của item ra Console.
     */
    public static void debugItem(ItemStack itemStack) {

        if (itemStack == null || itemStack.getType().isAir()) {
            System.out.println("[ItemValue] Item null/air.");
            return;
        }

        NBTItem nbt = NBTItem.get(itemStack);

        if (nbt == null || !nbt.hasType()) {
            System.out.println("[ItemValue] Không phải MMOItem.");
            return;
        }

        Type type = MMOItems.getType(nbt);

        if (type == null) {
            System.out.println("[ItemValue] Không tìm thấy MMOItems Type.");
            return;
        }

        LiveMMOItem mmoItem;

        try {
            mmoItem = new LiveMMOItem(nbt);
        } catch (Exception exception) {
            System.out.println(
                    "[ItemValue] Không thể đọc MMOItem."
            );
            return;
        }

        System.out.println("========== MMOITEM ==========");
        System.out.println("Type: " + type.getId());

        Map<String, Double> stats = getItemStats(itemStack);

        if (stats.isEmpty()) {

            System.out.println("Stats: Không có stat số.");

        } else {

            System.out.println("Stats:");

            for (Map.Entry<String, Double> entry : stats.entrySet()) {

                System.out.println(
                        "  "
                                + entry.getKey()
                                + " = "
                                + entry.getValue()
                );
            }
        }

        System.out.println(
                "Value: "
                        + calculateItemValue(itemStack)
        );

        System.out.println("==============================");
    }
}