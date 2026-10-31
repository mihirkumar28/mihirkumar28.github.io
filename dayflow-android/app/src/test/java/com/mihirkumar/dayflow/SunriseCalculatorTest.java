package com.mihirkumar.dayflow;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SunriseCalculatorTest {
    @Test
    public void calculatesPuneSunriseForSelectedDate() {
        Date sunrise = SunriseCalculator.calculateSunrise(18.5204d, 73.8567d, "2026-10-31");
        assertNotNull(sunrise);

        SimpleDateFormat localTime = new SimpleDateFormat("HH:mm", Locale.US);
        localTime.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
        String[] parts = localTime.format(sunrise).split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        assertTrue("Pune sunrise should be in the early morning", hour >= 6 && hour <= 7);
        assertTrue("Sunrise minute should be plausible", minute >= 0 && minute < 60);
    }

    @Test
    public void rejectsInvalidCoordinatesAndDates() {
        assertNull(SunriseCalculator.calculateSunrise(91d, 73d, "2026-10-31"));
        assertNull(SunriseCalculator.calculateSunrise(18d, 73d, "not-a-date"));
    }
}
