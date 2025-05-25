package king.kongguilds;

import java.util.HashMap;
import java.util.Map;

public class GuildDataCache {
    private static Map<String, Guild> cache = new HashMap<>();

    public static void addToCache(String name, Guild guild) {
        cache.put(name, guild);
    }

    public static Guild getFromCache(String name) {
        return cache.get(name);
    }

    public static void removeFromCache(String name) {
        cache.remove(name);
    }
}