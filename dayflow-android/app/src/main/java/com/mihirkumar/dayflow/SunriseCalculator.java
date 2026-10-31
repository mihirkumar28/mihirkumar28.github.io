package com.mihirkumar.dayflow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;

/** Offline sunrise calculation for a coordinate and a local calendar date. */
public final class SunriseCalculator {
    private static final double OFFICIAL_ZENITH_DEGREES = 90.833d;

    private SunriseCalculator() {
    }

    /**
     * Returns the sunrise instant for the supplied date, or null when the date or
     * coordinates are invalid or the sun does not rise at this latitude on that date.
     *
     * The NOAA sunrise equation is used; the returned Date is an absolute instant
     * and should be formatted in the device's local time zone by the caller.
     */
    public static Date calculateSunrise(double latitude, double longitude, String dateKey) {
        if (Double.isNaN(latitude) || Double.isInfinite(latitude)
                || latitude < -90d || latitude > 90d
                || Double.isNaN(longitude) || Double.isInfinite(longitude)
                || longitude < -180d || longitude > 180d
                || dateKey == null) {
            return null;
        }

        final LocalDate date;
        try {
            date = LocalDate.parse(dateKey);
        } catch (DateTimeParseException e) {
            return null;
        }

        int dayOfYear = date.getDayOfYear();
        double longitudeHour = longitude / 15d;
        double approximateTime = dayOfYear + ((6d - longitudeHour) / 24d);

        double meanAnomaly = (0.9856d * approximateTime) - 3.289d;
        double trueLongitude = normalizeDegrees(meanAnomaly
                + (1.916d * Math.sin(Math.toRadians(meanAnomaly)))
                + (0.020d * Math.sin(2d * Math.toRadians(meanAnomaly)))
                + 282.634d);

        double rightAscension = normalizeDegrees(Math.toDegrees(Math.atan(
                0.91764d * Math.tan(Math.toRadians(trueLongitude)))));
        double longitudeQuadrant = Math.floor(trueLongitude / 90d) * 90d;
        double rightAscensionQuadrant = Math.floor(rightAscension / 90d) * 90d;
        rightAscension = (rightAscension + longitudeQuadrant - rightAscensionQuadrant) / 15d;

        double sinDeclination = 0.39782d * Math.sin(Math.toRadians(trueLongitude));
        double cosDeclination = Math.cos(Math.asin(sinDeclination));
        double denominator = cosDeclination * Math.cos(Math.toRadians(latitude));
        if (Math.abs(denominator) < 1.0e-12d) return null;

        double cosHourAngle = (Math.cos(Math.toRadians(OFFICIAL_ZENITH_DEGREES))
                - (sinDeclination * Math.sin(Math.toRadians(latitude)))) / denominator;
        if (cosHourAngle > 1d || cosHourAngle < -1d) return null;

        // Sunrise uses the morning hour-angle branch.
        double hourAngle = 360d - Math.toDegrees(Math.acos(cosHourAngle));
        hourAngle /= 15d;

        double localMeanTime = hourAngle + rightAscension
                - (0.06571d * approximateTime) - 6.622d;
        // Do not wrap into 0..24: a sunrise at some longitudes falls on the
        // previous UTC date even though it belongs to the requested local date.
        double utcHoursFromDateStart = localMeanTime - longitudeHour;

        Instant utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        long sunriseNanos = Math.round(utcHoursFromDateStart * 3_600_000_000_000d);
        return Date.from(utcMidnight.plusNanos(sunriseNanos));
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360d;
        return normalized < 0d ? normalized + 360d : normalized;
    }
}
