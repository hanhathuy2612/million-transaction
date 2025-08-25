package com.hnh.example.transaction_example.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for JSON operations using a shared, properly configured
 * ObjectMapper
 */
@Slf4j
public final class JsonUtil {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();
        configureObjectMapper(OBJECT_MAPPER);
    }

    private JsonUtil() {
        // Utility class - prevent instantiation
    }

    /**
     * Get the shared ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * Convert object to JSON string
     */
    public static String toJson(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Error converting object to JSON: {}", object.getClass().getSimpleName(), e);
            throw new RuntimeException("Failed to serialize object to JSON", e);
        }
    }

    /**
     * Convert JSON string to object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to object: {}", clazz.getSimpleName(), e);
            throw new RuntimeException("Failed to deserialize JSON to object", e);
        }
    }

    /**
     * Convert object to another type (useful for Map to POJO conversions)
     */
    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        try {
            return OBJECT_MAPPER.convertValue(fromValue, toValueType);
        } catch (Exception e) {
            log.error("Error converting value from {} to {}",
                    fromValue.getClass().getSimpleName(), toValueType.getSimpleName(), e);
            throw new RuntimeException("Failed to convert value", e);
        }
    }

    /**
     * Check if a string is valid JSON
     */
    public static boolean isValidJson(String json) {
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * Configure the ObjectMapper with standard settings
     */
    private static void configureObjectMapper(ObjectMapper mapper) {
        // Handle Java 8 time types
        mapper.registerModule(new JavaTimeModule());

        // Don't write dates as timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Don't fail on unknown properties
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Include all fields for serialization
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // Enable default typing for Redis serialization compatibility
        mapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);

        // Pretty print for debugging (can be disabled in production)
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Create a copy of the shared ObjectMapper for custom configuration
     */
    public static ObjectMapper createCustomMapper() {
        ObjectMapper customMapper = new ObjectMapper();
        configureObjectMapper(customMapper);
        return customMapper;
    }
}
