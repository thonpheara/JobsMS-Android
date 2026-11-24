package com.example.internshipms.network;

import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Null-safe helpers for reading loosely-typed Laravel JSON responses.
 * Every getter accepts several candidate keys and returns the first one present,
 * so the app keeps working if the backend renames a field (e.g. "total" vs "total_applications").
 */
public final class Json {
    private Json() {
    }

    /** Unwraps the standard {"success":..,"data":{..}} envelope when present. */
    public static JsonObject data(@Nullable JsonObject body) {
        if (body == null) return new JsonObject();
        JsonElement d = body.get("data");
        return d != null && d.isJsonObject() ? d.getAsJsonObject() : body;
    }

    @Nullable
    public static JsonElement get(@Nullable JsonObject o, String... keys) {
        if (o == null) return null;
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && !e.isJsonNull()) return e;
        }
        return null;
    }

    @Nullable
    public static JsonObject obj(@Nullable JsonObject o, String... keys) {
        JsonElement e = get(o, keys);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    public static JsonArray arr(@Nullable JsonObject o, String... keys) {
        JsonElement e = get(o, keys);
        if (e == null) return new JsonArray();
        if (e.isJsonArray()) return e.getAsJsonArray();
        // Laravel paginator: {"data":[...], "current_page":1, ...}
        if (e.isJsonObject()) {
            JsonElement inner = e.getAsJsonObject().get("data");
            if (inner != null && inner.isJsonArray()) return inner.getAsJsonArray();
        }
        return new JsonArray();
    }

    /**
     * Extracts a list from a top-level response body regardless of whether Laravel returned:
     * - {"data": [...]}
     * - {"data": {"data": [...], "current_page": 1}}
     * - {"data": {"posts": [...]}}
     */
    public static JsonArray list(@Nullable JsonObject body, String... keys) {
        if (body == null) return new JsonArray();
        JsonElement topData = body.get("data");
        if (topData != null && topData.isJsonArray()) {
            return topData.getAsJsonArray();
        }
        JsonObject unwrapped = data(body);
        JsonArray byKey = arr(unwrapped, keys);
        if (byKey.size() > 0) return byKey;
        JsonElement innerData = unwrapped.get("data");
        if (innerData != null && innerData.isJsonArray()) {
            return innerData.getAsJsonArray();
        }
        return arr(body, keys);
    }

    public static String str(@Nullable JsonObject o, String fallback, String... keys) {
        if (o == null) return fallback;
        for (String k : keys) {
            JsonElement e = o.get(k);
            if (e != null && e.isJsonPrimitive()) {
                String s = e.getAsString();
                if (s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s.trim())) {
                    return s.trim();
                }
            }
        }
        return fallback;
    }

    public static int integer(@Nullable JsonObject o, int fallback, String... keys) {
        JsonElement e = get(o, keys);
        if (e == null || !e.isJsonPrimitive()) return fallback;
        try {
            return e.getAsInt();
        } catch (Exception ex) {
            try {
                return (int) Double.parseDouble(e.getAsString());
            } catch (Exception ignored) {
                return fallback;
            }
        }
    }
}

