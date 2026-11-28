package kr.coursepick.places;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Plain Java test runner: no JUnit download needed. Run with -ea. */
public final class PlacesTest {
    public static void main(String[] args) throws Exception {
        AtomicInteger code = new AtomicInteger(200);
        AtomicReference<String> body = new AtomicReference<>("""
            {"id":"test-id","businessStatus":"OPERATIONAL","rating":4.5,"userRatingCount":23,
             "currentOpeningHours":{"periods":[]},"regularOpeningHours":{"periods":[{"open":{"day":0,"hour":0,"minute":0}}]}}
            """);
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<String> mask = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/places/", exchange -> {
            key.set(exchange.getRequestHeaders().getFirst("X-Goog-Api-Key"));
            mask.set(exchange.getRequestHeaders().getFirst("X-Goog-FieldMask"));
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var client = new GooglePlacesClient("test-secret", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/places/"),
                Clock.fixed(Instant.parse("2026-11-19T01:00:00Z"), ZoneId.of("Asia/Seoul")));
            var data = client.fetch("test-id");
            check(data.state() == GooglePlacesClient.State.OK && data.rating() == 4.5, "rating decode");
            check(key.get().equals("test-secret") && mask.get().contains("userRatingCount"), "headers");
            check(!mask.get().contains("reviews"), "no reviews requested");
            check(data.hoursBasis(LocalDate.parse("2026-11-25")) == GooglePlacesClient.HoursBasis.CURRENT, "day 6 current");
            check(data.hoursBasis(LocalDate.parse("2026-11-26")) == GooglePlacesClient.HoursBasis.REGULAR, "day 7 estimate");
            check(data.hoursBasis(LocalDate.parse("2026-11-18")) == GooglePlacesClient.HoursBasis.UNKNOWN, "past unknown");
            check(data.currentOpeningHours().get("periods").isEmpty(), "empty hours not missing");
            body.set("{\"id\":\"test-id\"}");
            data = client.fetch("test-id");
            check(data.rating() == null && data.userRatingCount() == null, "missing rating not zero");
            check(data.hoursBasis(LocalDate.parse("2026-11-19")) == GooglePlacesClient.HoursBasis.UNKNOWN, "missing hours");
            body.set("invalid-json-with-test-secret");
            check(client.fetch("test-id").state() == GooglePlacesClient.State.API_ERROR, "invalid JSON");
            for (int status : new int[]{403,429,500}) {
                code.set(status);
                check(client.fetch("test-id").state() == GooglePlacesClient.State.API_ERROR, "API error distinct " + status);
            }
            code.set(404);
            check(client.fetch("test-id").state() == GooglePlacesClient.State.NOT_FOUND, "404 distinct");
            boolean rejected = false;
            try { client.fetch("../bad?key=secret"); } catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "ID injection rejected");
            var dir = Files.createTempDirectory("coursepick-test");
            var mappings = dir.resolve("mappings.json");
            var official = dir.resolve("official.json");
            Files.writeString(mappings, """
                {"schemaVersion":1,"mappings":[
                 {"kakaoId":"1","status":"matched","googlePlaceId":"test-id"},
                 {"kakaoId":"2","status":"unmatched"},
                 {"kakaoId":"3","status":"api_error"},
                 {"kakaoId":"4","status":"review"}]}
                """);
            Files.writeString(official, "{\"schemaVersion\":1,\"facilities\":[]}");
            var service = new PlaceLookupService(client, mappings, official);
            LocalDate date = LocalDate.parse("2026-11-19");
            check(service.lookup("2", "food", date).decision().equals("EXCLUDE"), "unmatched food excluded");
            check(service.lookup("2", "culture", date).decision().equals("KEEP_UNKNOWN"), "unmatched culture retained");
            check(service.lookup("3", "food", date).decision().equals("KEEP_UNKNOWN"), "API outage retains food");
            check(service.lookup("4", "food", date).decision().equals("NEEDS_REVIEW"), "review not unmatched");
            check(service.lookup("5", "food", date).reason().equals("MAPPING_NOT_BUILT"), "unbuilt not unmatched");
            code.set(403);
            check(service.lookup("1", "food", date).decision().equals("KEEP_UNKNOWN"), "details outage fallback");
            code.set(200); body.set("{\"id\":\"test-id\",\"businessStatus\":\"CLOSED_PERMANENTLY\"}");
            check(service.lookup("1", "food", date).decision().equals("EXCLUDE"), "closed excluded");
            Files.writeString(official, """
                {"schemaVersion":1,"facilities":[
                  {"kakaoId":"1","verificationStatus":"unconfirmed","sources":[],"recommendation":{"exclude":true,"reason":"USER_REQUESTED_EXCLUSION"}},
                  {"kakaoId":"2","verificationStatus":"user_reported","sources":[],"hours":{"alwaysOpen":true}}
                ]}
                """);
            service = new PlaceLookupService(client, mappings, official);
            check(service.lookup("1", "culture", date).reason().equals("USER_REQUESTED_EXCLUSION"), "team exclusion precedes Google mapping");
            check(service.lookup("2", "walk", date).reason().equals("USER_HOURS_REQUIRE_CONFIRMATION"), "user hours not official verification");
            check(service.lookup("2", "walk", date).official().path("hours").path("alwaysOpen").asBoolean(), "user hours retained");
            Files.delete(mappings); Files.delete(official); Files.delete(dir);
            System.out.println("Java checks passed");
        } finally { server.stop(0); }
    }
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
