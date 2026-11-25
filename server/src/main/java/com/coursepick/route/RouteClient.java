package com.coursepick.route;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RouteClient {

    private final RestClient client;

    public RouteClient(@Qualifier("kakaoRestClient") RestClient client) {
        this.client = client;
    }

    public int walkMinutes(double fromLat, double fromLng, double toLat, double toLng) {
        JsonNode body = get("/v2/routing/walk", fromLat, fromLng, toLat, toLng);
        String status = body.path("status").asText();
        if ("SAME_POINT".equals(status)) {
            return 0;
        }
        if (!"OK".equals(status)) {
            throw new RouteException("walk:" + status);
        }
        return toMinutes(body.path("route").path("properties").path("totalTime").asInt(-1));
    }

    public int transitMinutes(double fromLat, double fromLng, double toLat, double toLng) {
        JsonNode body = get("/v2/routing/publictraffic", fromLat, fromLng, toLat, toLng);
        String status = body.path("status").asText();
        if ("EQUAL_POINTS".equals(status)) {
            return 0;
        }
        if (!"OK".equals(status)) {
            throw new RouteException("transit:" + status);
        }
        int best = -1;
        for (JsonNode route : body.path("routes")) {
            int seconds = route.path("properties").path("totalTime").asInt(-1);
            if (seconds >= 0 && (best < 0 || seconds < best)) {
                best = seconds;
            }
        }
        return toMinutes(best);
    }

    private JsonNode get(String path, double fromLat, double fromLng, double toLat, double toLng) {
        try {
            JsonNode body = client.get()
                    .uri(uri -> uri.path(path)
                            .queryParam("start_x", fromLng)
                            .queryParam("start_y", fromLat)
                            .queryParam("end_x", toLng)
                            .queryParam("end_y", toLat)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new RouteException("empty response");
            }
            return body;
        } catch (RestClientException e) {
            throw new RouteException("request failed");
        }
    }

    private int toMinutes(int seconds) {
        if (seconds < 0) {
            throw new RouteException("no duration");
        }
        return (int) Math.ceil(seconds / 60.0);
    }
}
