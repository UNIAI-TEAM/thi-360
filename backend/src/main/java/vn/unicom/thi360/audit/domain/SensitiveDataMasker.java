package vn.unicom.thi360.audit.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SensitiveDataMasker {

    private static final List<String> SENSITIVE_KEY_TOKENS = List.of("password", "secret", "token", "otp");

    private SensitiveDataMasker() {
    }

    public static Map<String, Object> mask(Map<String, Object> source) {
        if (source == null) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            Object rawKey = entry.getKey();
            if (rawKey == null) {
                continue;
            }
            String key = String.valueOf(rawKey);
            if (isSensitiveKey(key)) {
                continue;
            }
            sanitized.put(key, maskValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(sanitized);
    }

    public static boolean isSensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        for (String token : SENSITIVE_KEY_TOKENS) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static Object maskValue(Object value) {
        if (value instanceof Map<?, ?> nestedMap) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : nestedMap.entrySet()) {
                Object rawKey = entry.getKey();
                if (rawKey == null) {
                    continue;
                }
                String key = String.valueOf(rawKey);
                if (isSensitiveKey(key)) {
                    continue;
                }
                copy.put(key, maskValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> list = new ArrayList<>(collection.size());
            for (Object item : collection) {
                list.add(maskValue(item));
            }
            return Collections.unmodifiableList(list);
        }
        return value;
    }
}
