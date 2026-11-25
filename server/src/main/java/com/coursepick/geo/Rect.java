package com.coursepick.geo;

import java.util.List;

public record Rect(double west, double south, double east, double north) {

    public static Rect around(double lat, double lng, double halfM) {
        double dLat = Geo.latDegrees(halfM);
        double dLng = Geo.lngDegrees(halfM, lat);
        return new Rect(lng - dLng, lat - dLat, lng + dLng, lat + dLat);
    }

    public List<Rect> quarters() {
        double midLng = (west + east) / 2;
        double midLat = (south + north) / 2;
        return List.of(
                new Rect(west, south, midLng, midLat),
                new Rect(midLng, south, east, midLat),
                new Rect(west, midLat, midLng, north),
                new Rect(midLng, midLat, east, north));
    }

    public static List<Rect> ring(double lat, double lng, double innerHalfM, double outerHalfM) {
        Rect outer = around(lat, lng, outerHalfM);
        if (innerHalfM <= 0) {
            return List.of(outer);
        }
        Rect inner = around(lat, lng, innerHalfM);
        return List.of(
                new Rect(outer.west, inner.north, outer.east, outer.north),
                new Rect(outer.west, outer.south, outer.east, inner.south),
                new Rect(outer.west, inner.south, inner.west, inner.north),
                new Rect(inner.east, inner.south, outer.east, inner.north));
    }
}
