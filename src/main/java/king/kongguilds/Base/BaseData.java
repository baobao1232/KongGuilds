package king.kongguilds.Base;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.*;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class BaseData {

    private static File BASE_FILE;
    private static final Gson gson;
    private static Map<String, BaseClaim> cachedBases = new HashMap<>();

    static {
        gson = new GsonBuilder()
                .registerTypeAdapter(Location.class, new LocationAdapter())
                .setPrettyPrinting()
                .create();
    }

    // Phải gọi setBaseFile trong onEnable
    public static void setBaseFile(File dataFolder) {
        BASE_FILE = new File(dataFolder, "base.json");
        if (!BASE_FILE.exists()) {
            try {
                BASE_FILE.createNewFile();
                try (Writer writer = new FileWriter(BASE_FILE)) {
                    writer.write("{}");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        loadCache();  // load cache ngay khi set file
    }

    // Load cache từ file
    public static void loadCache() {
        cachedBases = loadBasesFromFile();
    }

    // Trả về bản sao hoặc readonly để tránh sửa ngoài cache trực tiếp
    public static Map<String, BaseClaim> getCachedBases() {
        return new HashMap<>(cachedBases);
    }

    public static BaseClaim getBaseClaim(String baseID) {
        return cachedBases.get(baseID);
    }

    public static World getWorldByBaseID(String baseID) {
        BaseClaim base = getBaseClaim(baseID);
        if (base == null || base.pos1 == null) return null;
        return base.pos1.getWorld();
    }

    // Nội bộ load từ file thật sự, không cập nhật cache
    private static Map<String, BaseClaim> loadBasesFromFile() {
        try (Reader reader = new FileReader(BASE_FILE)) {
            Type type = new TypeToken<Map<String, BaseClaim>>() {}.getType();
            Map<String, BaseClaim> map = gson.fromJson(reader, type);
            if (map == null) return new HashMap<>();
            return map;
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    // Lấy bản cached bases hiện tại
    public static Map<String, BaseClaim> loadBases() {
        return getCachedBases();
    }

    // Lưu và cập nhật cache
    public static boolean saveBaseToFileWithId(String id, String guildName, String adminCreator, String owner, int level, Location pos1, Location pos2) {
        if (cachedBases.containsKey(id)) return false; // Đã có base với ID này
        BaseClaim claim = new BaseClaim(guildName, adminCreator, pos1, pos2);
        claim.owner = owner;
        claim.level = level;
        cachedBases.put(id, claim);
        saveCacheToFile();
        return true;
    }


    private static void saveCacheToFile() {
        try (Writer writer = new FileWriter(BASE_FILE)) {
            gson.toJson(cachedBases, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static boolean removeBaseById(String id) {
        if (!cachedBases.containsKey(id)) return false;
        BaseHologramManager.removeHologram(id);
        cachedBases.remove(id);
        saveCacheToFile();
        return true;
    }

    // Các hàm kiểm tra trùng lặp vùng nên lấy từ cache
    public static boolean canClaimBase(Location newPos1, Location newPos2) {
        for (BaseClaim base : cachedBases.values()) {
            if (base.pos1 == null || base.pos2 == null) continue;

            if (base.pos1.getWorld().equals(newPos1.getWorld())) {
                if (isOverlap(base.pos1, base.pos2, newPos1, newPos2)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isOverlap(Location pos1a, Location pos2a, Location pos1b, Location pos2b) {
        int ax1 = Math.min(pos1a.getBlockX(), pos2a.getBlockX());
        int ax2 = Math.max(pos1a.getBlockX(), pos2a.getBlockX());
        int az1 = Math.min(pos1a.getBlockZ(), pos2a.getBlockZ());
        int az2 = Math.max(pos1a.getBlockZ(), pos2a.getBlockZ());

        int bx1 = Math.min(pos1b.getBlockX(), pos2b.getBlockX());
        int bx2 = Math.max(pos1b.getBlockX(), pos2b.getBlockX());
        int bz1 = Math.min(pos1b.getBlockZ(), pos2b.getBlockZ());
        int bz2 = Math.max(pos1b.getBlockZ(), pos2b.getBlockZ());

        boolean xOverlap = ax1 <= bx2 && ax2 >= bx1;
        boolean zOverlap = az1 <= bz2 && az2 >= bz1;

        return xOverlap && zOverlap;
    }

    // Gson adapter cho Location Bukkit vì không thể serialize thẳng
    public static class LocationAdapter implements JsonSerializer<Location>, JsonDeserializer<Location> {
        @Override
        public JsonElement serialize(Location loc, java.lang.reflect.Type type, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("world", loc.getWorld().getName());
            obj.addProperty("x", loc.getX());
            obj.addProperty("y", loc.getY());
            obj.addProperty("z", loc.getZ());
            obj.addProperty("yaw", loc.getYaw());
            obj.addProperty("pitch", loc.getPitch());
            return obj;
        }

        @Override
        public Location deserialize(JsonElement json, java.lang.reflect.Type type, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String worldName = obj.get("world").getAsString();
            World world = org.bukkit.Bukkit.getWorld(worldName);
            double x = obj.get("x").getAsDouble();
            double y = obj.get("y").getAsDouble();
            double z = obj.get("z").getAsDouble();
            float yaw = obj.get("yaw").getAsFloat();
            float pitch = obj.get("pitch").getAsFloat();
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    public static class BaseClaim {
        public String guildName;
        public String adminCreator;
        public Location pos1;
        public Location pos2;
        public String owner = "";
        public int level = 0;

        public BaseClaim() {}

        public BaseClaim(String guildName, String adminCreator, Location pos1, Location pos2) {
            this.guildName = guildName;
            this.adminCreator = adminCreator;
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        public boolean isClaimed() {
            return owner != null && !owner.isEmpty();
        }

        public Location getLocation() {
            if (pos1 == null || pos2 == null) return null;

            World world = pos1.getWorld();
            if (world == null || !world.equals(pos2.getWorld())) return null;

            // Lấy min/max cho từng trục
            double minX = Math.min(pos1.getX(), pos2.getX());
            double maxX = Math.max(pos1.getX(), pos2.getX());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxZ = Math.max(pos1.getZ(), pos2.getZ());

            // Trung điểm chính xác
            double centerX = (minX + maxX) / 2.0;
            double centerZ = (minZ + maxZ) / 2.0;

            // Lấy block cao nhất tại floor(centerX), floor(centerZ)
            int blockX = (int) Math.floor(centerX);
            int blockZ = (int) Math.floor(centerZ);
            int highestY = world.getHighestBlockYAt(blockX, blockZ);

            // Trả về tọa độ giữa, Y nằm trên mặt đất
            return new Location(world, centerX, highestY + 4, centerZ);
        }


        public String getGuildName() {
            return guildName;
        }

        public int getLevel() {
            return level;
        }
    }

    public static File getBaseFile() {
        return BASE_FILE;
    }
}
