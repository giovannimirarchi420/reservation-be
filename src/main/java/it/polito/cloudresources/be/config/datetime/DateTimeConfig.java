package it.polito.cloudresources.be.config.datetime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Centralized configuration for managing dates and times in the application.
 * This class replaces DateTimeConfig and includes the functionalities of DateTimeUtils.
 */
@Configuration
public class DateTimeConfig {

    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("UTC");
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Configures Jackson ObjectMapper with appropriate date and time handling.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        ObjectMapper objectMapper = builder.build();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return objectMapper;
    }
    
    /**
     * Gets the current date and time in the application's default time zone.
     *
     * @return current date and time
     */
    @Bean
    public DateTimeService dateTimeService() {
        return new DateTimeService();
    }
    
    /**
     * Service for standardized date and time operations.
     */
    public static class DateTimeService {
        /**
         * Gets the current date and time in the application's default time zone.
         *
         * @return current date and time
         */
        public ZonedDateTime getCurrentDateTime() {
            return ZonedDateTime.now(DEFAULT_ZONE_ID);
        }
        
        /**
         * Ensures that a date has time zone information, applying the default if missing.
         *
         * @param dateTime the date to verify
         * @return the date with time zone information
         */
        public ZonedDateTime ensureTimeZone(ZonedDateTime dateTime) {
            if (dateTime == null) {
                return getCurrentDateTime();
            }
            
            return dateTime.withZoneSameInstant(DEFAULT_ZONE_ID);
        }
        
        /**
         * Formats a date using the ISO formatter.
         *
         * @param dateTime the date to format
         * @return the formatted date string
         */
        public String formatDateTime(ZonedDateTime dateTime) {
            if (dateTime == null) {
                return null;
            }
            return dateTime.format(ISO_DATE_TIME_FORMATTER);
        }
        
        /**
         * Parses a string into a date using the ISO formatter.
         *
         * @param dateTimeString the date string to parse
         * @return the parsed date
         */
        public ZonedDateTime parseDateTime(String dateTimeString) {
            if (dateTimeString == null || dateTimeString.isEmpty()) {
                return null;
            }
            ZonedDateTime dateTime = ZonedDateTime.parse(dateTimeString, ISO_DATE_TIME_FORMATTER);
            return ensureTimeZone(dateTime);
        }
    }
}