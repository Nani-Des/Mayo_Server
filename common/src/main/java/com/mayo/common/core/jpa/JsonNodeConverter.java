package com.mayo.common.core.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA AttributeConverter for JsonNode to database String mapping.
 * This converter automatically handles serialization/deserialization of JsonNode
 * objects to/from PostgreSQL JSONB columns.
 * 
 * The @Converter(autoApply = true) annotation means this converter will be
 * automatically applied to all JsonNode fields across all entities.
 */
@Converter(autoApply = true)
public class JsonNodeConverter implements AttributeConverter<JsonNode, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        try {
            return attribute == null ? null : MAPPER.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert JsonNode to String", e);
        }
    }

    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        try {
            return dbData == null ? null : MAPPER.readTree(dbData);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert String to JsonNode", e);
        }
    }
}
