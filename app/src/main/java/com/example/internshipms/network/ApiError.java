package com.example.internshipms.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Response;

/** Human-readable error plus per-field validation messages (HTTP 422). */
public class ApiError {
    public final int code;
    public final String message;
    public final Map<String, String> fieldErrors;

    public ApiError(int code, String message, Map<String, String> fieldErrors) {
        this.code = code;
        this.message = message;
        this.fieldErrors = fieldErrors;
    }

    public static ApiError network() {
        return network(null);
    }

    public static ApiError network(Throwable t) {
        String msg = "Cannot reach the server. Check your connection.";
        if (t != null && t.getMessage() != null && !t.getMessage().isEmpty()) {
            msg += "\nDetails: " + t.getMessage();
        }
        return new ApiError(-1, msg, new HashMap<>());
    }

    public static ApiError from(Response<?> response) {
        int code = response.code();
        String serverMessage = null;
        Map<String, String> fields = new HashMap<>();
        try {
            if (response.errorBody() != null) {
                JsonElement root = JsonParser.parseString(response.errorBody().string());
                if (root.isJsonObject()) {
                    JsonObject obj = root.getAsJsonObject();
                    if (obj.has("message") && !obj.get("message").isJsonNull()) {
                        serverMessage = obj.get("message").getAsString();
                    }
                    if (obj.has("errors") && obj.get("errors").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> e
                                : obj.getAsJsonObject("errors").entrySet()) {
                            JsonElement v = e.getValue();
                            if (v.isJsonArray() && ((JsonArray) v).size() > 0) {
                                fields.put(e.getKey(), ((JsonArray) v).get(0).getAsString());
                            } else if (v.isJsonPrimitive()) {
                                fields.put(e.getKey(), v.getAsString());
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to default messages below.
        }

        String message;
        switch (code) {
            case 401:
                message = "Invalid email or password.";
                break;
            case 403:
                message = serverMessage != null ? serverMessage
                        : "Your account is inactive or pending approval.";
                break;
            case 422:
                message = serverMessage != null ? serverMessage : "Please check your input.";
                break;
            default:
                message = serverMessage != null ? serverMessage
                        : "Something went wrong (" + code + ").";
        }
        return new ApiError(code, message, fields);
    }
}

