package airhacks.zsmith.htmldb.entity;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public record Entry(String key, SortedMap<String, String> fields) {

    public Entry {
        fields = new TreeMap<>(fields);
    }

    public static Entry of(String key, Map<String, String> fields) {
        return new Entry(key, new TreeMap<>(fields));
    }

    public String field(String name) {
        return this.fields.get(name);
    }
}
