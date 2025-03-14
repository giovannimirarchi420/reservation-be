package it.polito.cloudresources.be.util;

import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for standardized date and time operations
 */
@Component
public class DateTimeUtils {
    
    public static final ZoneId DEFAULT_ZONE_ID = ZoneId.of("UTC");
    public static final DateTimeFormatter ISO_DATE_TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    
    /**
     * Get the current date and time in the application's default time zone
     *
     * @return the current date and time
     */
    public ZonedDateTime getCurrentDateTime() {
        return ZonedDateTime.now(DEFAULT_ZONE_ID);
    }
    
    /**
     * Ensure a date has time zone info, applying the default if missing
     *
     * @param dateTime the date time to check
     * @return the date time with time zone info
     */
    public ZonedDateTime ensureTimeZone(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return getCurrentDateTime();
        }
        
        // A ZonedDateTime always has a zone, so we just normalize to the application's default time zone
        return dateTime.withZoneSameInstant(DEFAULT_ZONE_ID);
    }
    
    /**
     * Format a date time using the ISO formatter
     *
     * @param dateTime the date time to format
     * @return the formatted date time string
     */
    public String formatDateTime(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(ISO_DATE_TIME_FORMATTER);
    }
    
    /**
     * Parse a string to a date time using the ISO formatter
     *
     * @param dateTimeString the date time string to parse
     * @return the parsed date time
     */
    public ZonedDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return null;
        }
        ZonedDateTime dateTime = ZonedDateTime.parse(dateTimeString, ISO_DATE_TIME_FORMATTER);
        return ensureTimeZone(dateTime);
    }
}