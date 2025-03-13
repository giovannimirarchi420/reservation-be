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
 * Configurazione centralizzata per la gestione di date e orari nell'applicazione.
 * Questa classe sostituisce DateTimeConfig e include le funzionalità di DateTimeUtils
 */
@Configuration
public class DateTimeConfig {

    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("UTC");
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Configura Jackson ObjectMapper con gestione appropriata di date e orari
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
     * Ottiene la data e ora corrente nel fuso orario predefinito dell'applicazione
     *
     * @return data e ora corrente
     */
    @Bean
    public DateTimeService dateTimeService() {
        return new DateTimeService();
    }
    
    /**
     * Servizio per operazioni standardizzate su date e orari
     */
    public static class DateTimeService {
        /**
         * Ottiene la data e ora corrente nel fuso orario predefinito dell'applicazione
         *
         * @return data e ora corrente
         */
        public ZonedDateTime getCurrentDateTime() {
            return ZonedDateTime.now(DEFAULT_ZONE_ID);
        }
        
        /**
         * Assicura che una data abbia informazioni sul fuso orario, applicando il predefinito se mancante
         *
         * @param dateTime la data da verificare
         * @return la data con informazioni sul fuso orario
         */
        public ZonedDateTime ensureTimeZone(ZonedDateTime dateTime) {
            if (dateTime == null) {
                return getCurrentDateTime();
            }
            
            return dateTime.withZoneSameInstant(DEFAULT_ZONE_ID);
        }
        
        /**
         * Formatta una data utilizzando il formattatore ISO
         *
         * @param dateTime la data da formattare
         * @return la stringa formattata della data
         */
        public String formatDateTime(ZonedDateTime dateTime) {
            if (dateTime == null) {
                return null;
            }
            return dateTime.format(ISO_DATE_TIME_FORMATTER);
        }
        
        /**
         * Analizza una stringa in una data utilizzando il formattatore ISO
         *
         * @param dateTimeString la stringa della data da analizzare
         * @return la data analizzata
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