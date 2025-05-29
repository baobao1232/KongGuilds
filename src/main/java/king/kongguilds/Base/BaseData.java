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

    static {
        gson = new GsonBuilder()
                .registerTypeAdapter(Location.class, new LocationAdapter())
                .setPrettyPrinting()
                .create();
    }

    // Phải set BASE_FILE khi plugin khởi động, ví dụ trong onEnable()
    public static void setBaseFile(File dataFolder) {
        BASE_FILE = new File(dataFolder, "base.json");
        if (!BASE_FILE.exists()) {
            try {
                BASE_FILE.createNewFile();
                // Khởi tạo file rỗng dạng map rỗng {}
                try (Writer writer = new FileWriter(BASE_FILE)) {
                    writer.write("{}");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
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
    }

    public static Map<String, BaseClaim> loadBases() {
        try (Reader reader = new FileReader(BASE_FILE)) {
            Type type = new TypeToken<Map<String, BaseClaim>>(){}.getType();
            Map<String, BaseClaim> map = gson.fromJson(reader, type);
            if (map == null) return new HashMap<>();
            return map;
        } catch (IOException e) {
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    public static void saveBaseToFile(String guildName, String adminCreator, Location pos1, Location pos2) {
        Map<String, BaseClaim> bases = loadBases();

        BaseClaim newBase = new BaseClaim(guildName, adminCreator, pos1, pos2);
        bases.put(guildName, newBase);

        try (Writer writer = new FileWriter(BASE_FILE)) {
            gson.toJson(bases, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveBaseToFileWithId(String id, String guildName, String adminCreator, Location pos1, Location pos2) {
        Map<String, BaseClaim> bases = loadBases();

        BaseClaim newBase = new BaseClaim(guildName, adminCreator, pos1, pos2);
        bases.put(id, newBase);

        try (Writer writer = new FileWriter(BASE_FILE)) {
            gson.toJson(bases, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
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

    public static boolean canClaimBase(Location newPos1, Location newPos2) {
        Map<String, BaseClaim> bases = loadBases();
        for (BaseClaim base : bases.values()) {
            if (base.pos1.getWorld().equals(newPos1.getWorld())) {
                if (isOverlap(base.pos1, base.pos2, newPos1, newPos2)) {
                    return false;
                }
            }
        }
        return true;
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

    public static boolean removeBaseById(String id) {
        Map<String, BaseClaim> bases = loadBases();
        BaseClaim base = bases.get(id);
        if (base == null) return false;

        // Bỏ kiểm tra owner => xóa thẳng
        bases.remove(id);

        try (Writer writer = new FileWriter(BASE_FILE)) {
            gson.toJson(bases, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    public static File getBaseFile() {
        return BASE_FILE;
    }

}
