package com.coursepick.route;

import com.coursepick.assemble.CourseAssembler.Pick;
import com.coursepick.config.CoursepickSettings;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RouteVerifier {

    public record Result(List<Pick> picks, Map<Integer, String> modes) {
    }

    private final RouteClient client;
    private final CoursepickSettings settings;

    public RouteVerifier(RouteClient client, CoursepickSettings settings) {
        this.client = client;
        this.settings = settings;
    }

    public Result verify(
            List<Pick> picks, double startLat, double startLng, int startMin, int endMin, int reserveMin,
            boolean transit) {
        JsonNode config = settings.root().path("routeVerify");
        int maxLegs = config.path("maxLegs").asInt(3);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < picks.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt((Integer i) -> picks.get(i).travelMin()).reversed());

        int[] travel = new int[picks.size()];
        for (int i = 0; i < picks.size(); i++) {
            travel[i] = picks.get(i).travelMin();
        }
        Map<Integer, String> modes = new HashMap<>();
        for (int n = 0; n < Math.min(maxLegs, order.size()); n++) {
            int i = order.get(n);
            double fromLat = i == 0 ? startLat : picks.get(i - 1).place().lat();
            double fromLng = i == 0 ? startLng : picks.get(i - 1).place().lng();
            double toLat = picks.get(i).place().lat();
            double toLng = picks.get(i).place().lng();
            if (transit) {
                try {
                    travel[i] = client.transitMinutes(fromLat, fromLng, toLat, toLng);
                    modes.put(i, "transit");
                    continue;
                } catch (RouteException e) {
                    if (!e.getMessage().startsWith("transit:NO_RESULTS")
                            && !e.getMessage().startsWith("transit:STARTNODES_NULL")
                            && !e.getMessage().startsWith("transit:ENDNODES_NULL")) {
                        throw e;
                    }
                }
            }
            travel[i] = client.walkMinutes(fromLat, fromLng, toLat, toLng);
            modes.put(i, "walk");
        }
        return new Result(rebuild(picks, travel, startMin, endMin, reserveMin), modes);
    }

    private List<Pick> rebuild(List<Pick> picks, int[] travel, int startMin, int endMin, int reserveMin) {
        List<Pick> result = new ArrayList<>();
        int clock = startMin;
        for (int i = 0; i < picks.size(); i++) {
            Pick p = picks.get(i);
            int arrive = clock + travel[i];
            Pick moved = new Pick(p.place(), p.theme(), travel[i], arrive, p.stayMin(), p.stay(), p.openStatus());
            if (moved.departMin() + reserveMin > endMin) {
                int stay = Math.max(p.stay().minMin(), endMin - reserveMin - arrive);
                moved = new Pick(p.place(), p.theme(), travel[i], arrive, stay, p.stay(), p.openStatus());
                if (moved.departMin() + reserveMin > endMin) {
                    break;
                }
                result.add(moved);
                break;
            }
            result.add(moved);
            clock = moved.departMin();
        }
        return result;
    }
}
