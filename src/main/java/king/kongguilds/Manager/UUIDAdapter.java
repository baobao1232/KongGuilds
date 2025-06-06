package king.kongguilds.Manager;

import com.google.gson.JsonElement;

import java.util.UUID;
import com.google.gson.*;
import java.lang.reflect.Type;
public class UUIDAdapter implements JsonSerializer<UUID>, JsonDeserializer<UUID> {

    @Override
    public JsonElement serialize(UUID src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

    @Override
    public UUID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        try {
            return UUID.fromString(json.getAsString());
        } catch (Exception e) {
            // Trả về null nếu không parse được UUID
            return null;
        }
    }
}