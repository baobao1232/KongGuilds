package king.kongguilds.Manager;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ItemValueCalculator {

    private static final String NAMESPACE = "rpgtest"; // ⚠ dùng đúng với item bạn tạo
    private static final double STR_WEIGHT = 0.1;
    private static final double DEX_WEIGHT = 0.4;
    private static final double AP_WEIGHT = 0.2;
    private static final double HP_WEIGHT = 0.1;
    private static final double DEF_WEIGHT = 0.1;
    private static final double MP_WEIGHT = 0.2;
    private static final double SPD_WEIGHT = 0.3;
    private static final double ATKSPD_WEIGHT = 0.4;
    private static final double ID_WEIGHT = 0.3;
    private static final double BA_WEIGHT = 0.2;
    private static final double CDR_WEIGHT = 0.3;

    public static double calculateItemValue(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) return 0.0;

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) return 0.0;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        int str = get(container, "str");
        int dex = get(container, "dex");
        int ap = get(container, "ap");
        int hp = get(container, "hp");
        int def = get(container, "def");
        int mp = get(container, "mp");
        int spd = get(container, "spd");
        int atkspd = get(container, "atkspd");
        int id = get(container, "id");
        int ba = get(container, "ba");
        int cdr = get(container, "cdr");

        int strUp = get(container, "str_up");
        int dexUp = get(container, "dex_up");
        int apUp = get(container, "ap_up");
        int hpUp = get(container, "hp_up");
        int defUp = get(container, "def_up");
        int mpUp = get(container, "mp_up");
        int spdUp = get(container, "spd_up");
        int atkspdUp = get(container, "atkspd_up");
        int idUp = get(container, "id_up");
        int baUp = get(container, "ba_up");
        int cdrUp = get(container, "cdr_up");

        BigDecimal value = BigDecimal.ZERO;
        value = value.add(BigDecimal.valueOf(str + strUp).multiply(BigDecimal.valueOf(STR_WEIGHT)));
        value = value.add(BigDecimal.valueOf(dex + dexUp).multiply(BigDecimal.valueOf(DEX_WEIGHT)));
        value = value.add(BigDecimal.valueOf(ap + apUp).multiply(BigDecimal.valueOf(AP_WEIGHT)));
        value = value.add(BigDecimal.valueOf(hp + hpUp).multiply(BigDecimal.valueOf(HP_WEIGHT)));
        value = value.add(BigDecimal.valueOf(def + defUp).multiply(BigDecimal.valueOf(DEF_WEIGHT)));
        value = value.add(BigDecimal.valueOf(mp + mpUp).multiply(BigDecimal.valueOf(MP_WEIGHT)));
        value = value.add(BigDecimal.valueOf(spd + spdUp).multiply(BigDecimal.valueOf(SPD_WEIGHT)));
        value = value.add(BigDecimal.valueOf(atkspd + atkspdUp).multiply(BigDecimal.valueOf(ATKSPD_WEIGHT)));
        value = value.add(BigDecimal.valueOf(id + idUp).multiply(BigDecimal.valueOf(ID_WEIGHT)));
        value = value.add(BigDecimal.valueOf(ba + baUp).multiply(BigDecimal.valueOf(BA_WEIGHT)));
        value = value.add(BigDecimal.valueOf(cdr + cdrUp).multiply(BigDecimal.valueOf(CDR_WEIGHT)));

        return value.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static int get(PersistentDataContainer container, String key) {
        NamespacedKey namespacedKey = new NamespacedKey(NAMESPACE, key);
        return container.has(namespacedKey, PersistentDataType.INTEGER)
                ? container.get(namespacedKey, PersistentDataType.INTEGER)
                : 0;
    }
}