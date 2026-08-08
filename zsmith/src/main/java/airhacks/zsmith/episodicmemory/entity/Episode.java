package airhacks.zsmith.episodicmemory.entity;

import java.time.Instant;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import airhacks.zsmith.json.JSONObject;

public record Episode(String content, String timestamp, MemoryType type) {

    static final String CONTENT = "content";
    static final String TIMESTAMP = "timestamp";
    static final String TYPE = "type";
    static final String LEGACY_TYPE = "category";

    public Episode {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Episode content must not be empty");
        }
        if (timestamp == null) {
            timestamp = Instant.now().toString();
        }
    }

    public static Episode of(String content) {
        return new Episode(content, null, null);
    }

    public static Episode of(String content, MemoryType type) {
        return new Episode(content, null, type);
    }

    public boolean hasType(MemoryType type) {
        return this.type != null && this.type.equals(type);
    }

    public SortedMap<String, String> toFields() {
        var fields = new TreeMap<String, String>();
        fields.put(CONTENT, this.content);
        fields.put(TIMESTAMP, this.timestamp);
        if (this.type != null) {
            fields.put(TYPE, this.type.name());
        }
        return fields;
    }

    public static Episode fromFields(Map<String, String> fields) {
        return new Episode(fields.get(CONTENT), fields.get(TIMESTAMP), MemoryType.fromString(fields.get(TYPE)));
    }

    public JSONObject toJSON() {
        return new JSONObject()
                .put(CONTENT, this.content)
                .put(TIMESTAMP, this.timestamp)
                .put(TYPE, this.type == null ? JSONObject.NULL : this.type.name());
    }

    public static Episode fromJSON(JSONObject json) {
        var content = json.getString(CONTENT);
        var timestamp = json.getString(TIMESTAMP);
        var type = MemoryType.fromString(typeOf(json));
        return new Episode(content, timestamp, type);
    }

    static String typeOf(JSONObject json) {
        if (json.has(TYPE) && !json.isNull(TYPE)) {
            return json.getString(TYPE);
        }
        if (json.has(LEGACY_TYPE) && !json.isNull(LEGACY_TYPE)) {
            return json.getString(LEGACY_TYPE);
        }
        return null;
    }
}
