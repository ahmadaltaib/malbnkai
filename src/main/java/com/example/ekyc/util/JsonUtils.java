package com.example.ekyc.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;

/**
 * Utility class for JSON serialization and deserialization using Gson.
 */
public final class JsonUtils {
    
    private static final Gson GSON = new GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss")
            .create();
    
    private JsonUtils() {
        // Utility class, no instantiation
    }

    /**
     * Serializes an object to JSON string.
     * @param object The object to serialize
     * @return JSON string representation
     */
    public static String toJson(Object object) {
        return GSON.toJson(object);
    }

    /**
     * Deserializes a JSON string to an object.
     * @param json The JSON string
     * @param clazz The target class
     * @param <T> The type parameter
     * @return The deserialized object
     * @throws JsonSyntaxException if the JSON is invalid
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        return GSON.fromJson(json, clazz);
    }

    /**
     * Deserializes a JSON string to an object with a specific type.
     * @param json The JSON string
     * @param type The target type
     * @param <T> The type parameter
     * @return The deserialized object
     * @throws JsonSyntaxException if the JSON is invalid
     */
    public static <T> T fromJson(String json, Type type) {
        return GSON.fromJson(json, type);
    }
}
