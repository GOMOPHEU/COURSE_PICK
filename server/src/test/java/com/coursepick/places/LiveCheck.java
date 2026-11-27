package com.coursepick.places;

/** One real request; prints field presence only, never the API key or Google content. */
public final class LiveCheck {
    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Pass one Google Place ID");
        var details = new GooglePlacesClient(System.getenv("GOOGLE_PLACES_API_KEY")).fetch(args[0]);
        System.out.println("state=" + details.state() + ", ratingPresent=" + (details.rating() != null)
            + ", hoursPresent=" + (details.currentOpeningHours() != null || details.regularOpeningHours() != null)
            + ", errorCode=" + details.errorCode());
        if (details.state() != GooglePlacesClient.State.OK) System.exit(2);
    }
}
