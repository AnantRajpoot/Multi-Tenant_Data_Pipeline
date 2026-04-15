package com.example.pipeline.converter;

import com.example.pipeline.model.SourceConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class SourceConfigConverter implements AttributeConverter<SourceConfig, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();
    @Override
    public String convertToDatabaseColumn(SourceConfig attribute) {
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public SourceConfig convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, SourceConfig.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
