package io.github.exec.rwp;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Self-reported software identity, not a credential, account identity or capability grant. */
public record ImplementationIdentity(String id, String version) {
    public ImplementationIdentity {
        if (id == null || id.length() > 128 || !id.matches("[a-z0-9]+(?:[.-][a-z0-9]+)*"))
            throw new IllegalArgumentException("Invalid implementation ID");
        if (version == null || version.length() > 64 || !version.matches("[A-Za-z0-9][A-Za-z0-9._+-]*"))
            throw new IllegalArgumentException("Invalid implementation version");
    }

    public JsonObject toJson() {
        JsonObject result = new JsonObject();
        result.addProperty("id", id); result.addProperty("version", version);
        return result;
    }

    public static ImplementationIdentity parse(JsonObject value) {
        if (value == null) throw new IllegalArgumentException("Missing implementation identity");
        return new ImplementationIdentity(string(value, "id"), string(value, "version"));
    }

    private static String string(JsonObject value, String field) {
        JsonElement item = value.get(field);
        if (item == null || !item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("Expected implementation " + field);
        return item.getAsString();
    }
}
