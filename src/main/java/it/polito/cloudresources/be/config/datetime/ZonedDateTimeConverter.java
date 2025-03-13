package it.polito.cloudresources.be.config.datetime;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Convertitore JPA per memorizzare ZonedDateTime come timestamp UTC nel database
 * e convertirlo nuovamente in ZonedDateTime con il fuso orario originale
 */
@Converter(autoApply = true)
public class ZonedDateTimeConverter implements AttributeConverter<ZonedDateTime, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        // Memorizza nel fuso orario UTC
        return Timestamp.valueOf(zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime());
    }

    @Override
    public ZonedDateTime convertToEntityAttribute(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        // Recupera come UTC, poi converte nel fuso orario predefinito dell'applicazione
        return ZonedDateTime.of(timestamp.toLocalDateTime(), ZoneId.of("UTC"))
                .withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
    }
}