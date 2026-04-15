package com.example.pipeline.converter;

import com.example.pipeline.model.DestinationConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class DestinationConfigConverter implements AttributeConverter<DestinationConfig, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public String convertToDatabaseColumn(DestinationConfig attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public DestinationConfig convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, DestinationConfig.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
