package it.polito.cloudresources.be.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * JPA converter to store ZonedDateTime as UTC timestamp in the database
 * and convert it back to ZonedDateTime with the original timezone
 */
@Converter(autoApply = true)
public class ZonedDateTimeConverter implements AttributeConverter<ZonedDateTime, Timestamp> {

    @Override
    public Timestamp convertToDatabaseColumn(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        // Store in UTC time zone
        return Timestamp.valueOf(zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime());
    }

    @Override
    public ZonedDateTime convertToEntityAttribute(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        // Retrieve as UTC, then convert to application default time zone
        return ZonedDateTime.of(timestamp.toLocalDateTime(), ZoneId.of("UTC"))
                .withZoneSameInstant(DateTimeConfig.DEFAULT_ZONE_ID);
    }
}