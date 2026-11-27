package com.coursepick.geo;

public final class Geo {

    private static final double EARTH_RADIUS_M = 6371000.0;
    private static final double METERS_PER_DEGREE_LAT = 111320.0;

    private Geo() {
    }

    public static double distanceM(double lat1, double lng1, double lat2, double lng2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    public static double latDegrees(double meters) {
        return meters / METERS_PER_DEGREE_LAT;
    }

    public static double lngDegrees(double meters, double atLat) {
        return meters / (METERS_PER_DEGREE_LAT * Math.cos(Math.toRadians(atLat)));
    }
}
