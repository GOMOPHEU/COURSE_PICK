package com.coursepick.places;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.LocalDateTime;

public final class OpeningHoursChecker {

    public enum Status { OPEN, CLOSED, UNKNOWN }

    private static final int DAY = 1440;
    private static final int WEEK = 7 * DAY;

    private OpeningHoursChecker() {
    }

    public static Status checkGoogle(JsonNode hours, LocalDateTime arrive, LocalDateTime depart) {
        if (hours == null || !hours.path("periods").isArray() || !validRange(arrive, depart)) {
            return Status.UNKNOWN;
        }
        JsonNode periods = hours.get("periods");
        if (periods.isEmpty()) {
            return Status.CLOSED;
        }
        int start = googleWeekMinute(arrive);
        int end = start + minutesBetween(arrive, depart);
        boolean incomplete = false;
        for (JsonNode period : periods) {
            JsonNode open = period.path("open");
            if (!open.isObject()) {
                incomplete = true;
                continue;
            }
            int openAt = weekMinute(open);
            JsonNode close = period.path("close");
            if (!close.isObject()) {
                if (openAt == 0) {
                    return Status.OPEN;
                }
                incomplete = true;
                continue;
            }
            int closeAt = weekMinute(close);
            if (closeAt <= openAt) {
                closeAt += WEEK;
            }
            for (int shift = -WEEK; shift <= WEEK; shift += WEEK) {
                if (openAt + shift <= start && end <= closeAt + shift) {
                    return Status.OPEN;
                }
            }
        }
        return incomplete ? Status.UNKNOWN : Status.CLOSED;
    }

    public static Status checkOfficial(JsonNode facility, LocalDateTime arrive, LocalDateTime depart) {
        if (facility == null
                || !"verified".equals(facility.path("verificationStatus").asText())
                || !validRange(arrive, depart)) {
            return Status.UNKNOWN;
        }
        JsonNode weekly = facility.path("hours").path("weekly");
        if (!weekly.isArray()) {
            return Status.UNKNOWN;
        }
        int isoDay = arrive.getDayOfWeek().getValue();
        JsonNode closedDays = facility.path("closedDays");
        for (JsonNode day : closedDays.path("isoWeekdays")) {
            if (day.asInt() == isoDay) {
                return Status.CLOSED;
            }
        }
        String visitDate = arrive.toLocalDate().toString();
        for (JsonNode exception : closedDays.path("exceptions")) {
            String date = exception.isTextual() ? exception.asText() : exception.path("date").asText("");
            if (visitDate.equals(date)) {
                return Status.CLOSED;
            }
        }
        int start = arrive.getHour() * 60 + arrive.getMinute();
        int end = start + minutesBetween(arrive, depart);
        for (JsonNode entry : weekly) {
            if (!containsDay(entry.path("days"), isoDay)) {
                continue;
            }
            Integer open = minuteOfDay(entry.path("open").asText(null));
            Integer close = minuteOfDay(entry.path("close").asText(null));
            if (open == null || close == null) {
                continue;
            }
            int closeAt = close <= open ? close + DAY : close;
            if (open <= start && end <= closeAt) {
                return Status.OPEN;
            }
        }
        return Status.CLOSED;
    }

    private static boolean validRange(LocalDateTime arrive, LocalDateTime depart) {
        return arrive != null && depart != null && depart.isAfter(arrive);
    }

    private static int minutesBetween(LocalDateTime arrive, LocalDateTime depart) {
        return (int) Duration.between(arrive, depart).toMinutes();
    }

    private static int googleWeekMinute(LocalDateTime time) {
        return (time.getDayOfWeek().getValue() % 7) * DAY + time.getHour() * 60 + time.getMinute();
    }

    private static int weekMinute(JsonNode point) {
        return point.path("day").asInt(0) * DAY + point.path("hour").asInt(0) * 60 + point.path("minute").asInt(0);
    }

    private static boolean containsDay(JsonNode days, int isoDay) {
        for (JsonNode day : days) {
            if (day.asInt() == isoDay) {
                return true;
            }
        }
        return false;
    }

    private static Integer minuteOfDay(String text) {
        if (text == null) {
            return null;
        }
        String[] parts = text.split(":");
        if (parts.length != 2) {
            return null;
        }
        try {
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
