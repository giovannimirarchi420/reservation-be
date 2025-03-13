package it.polito.cloudresources.be.config;

import org.modelmapper.AbstractConverter;
import org.modelmapper.Converter;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.polito.cloudresources.be.config.datetime.DateTimeConfig;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

/**
 * Configuration class for ModelMapper to handle DateTime conversions
 */
@Configuration
public class ModelMapperConfig {

    /**
     * Configure ModelMapper with custom type converters for DateTime types
     */
    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        
        // Add converter from LocalDateTime to ZonedDateTime
        Converter<LocalDateTime, ZonedDateTime> localToZoned = new AbstractConverter<>() {
            @Override
            protected ZonedDateTime convert(LocalDateTime source) {
                return source == null ? null : source.atZone(DateTimeConfig.DEFAULT_ZONE_ID);
            }
        };
        
        // Add converter from ZonedDateTime to LocalDateTime
        Converter<ZonedDateTime, LocalDateTime> zonedToLocal = new AbstractConverter<>() {
            @Override
            protected LocalDateTime convert(ZonedDateTime source) {
                return source == null ? null : source.toLocalDateTime();
            }
        };
        
        modelMapper.addConverter(localToZoned);
        modelMapper.addConverter(zonedToLocal);
        
        return modelMapper;
    }
}