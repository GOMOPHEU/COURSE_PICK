package com.coursepick.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coursepick.assemble.CourseAssembler.Pick;
import com.coursepick.config.CoursepickSettings;
import com.coursepick.kakao.KakaoPlace;
import com.coursepick.plan.Stay;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RouteVerifierTest {

    private RouteClient client;
    private RouteVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        client = mock(RouteClient.class);
        CoursepickSettings settings = new CoursepickSettings(
                new ObjectMapper().readTree("{\"routeVerify\": {\"enabled\": true, \"maxLegs\": 1}}"));
        verifier = new RouteVerifier(client, settings);
    }

    private Pick pick(String id, int travel, int arrive, int stay) {
        KakaoPlace place = new KakaoPlace(id, id, "", "", "", "", 37.57, 126.98, 0, "");
        return new Pick(place, "walk", travel, arrive, stay, new Stay(stay, 20, 90), "confirmed");
    }

    @Test
    void replacesOnlyTheLongestLegsAndShiftsTimes() {
        List<Pick> picks = List.of(pick("a", 5, 605, 60), pick("b", 20, 685, 60));
        when(client.walkMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(30);

        RouteVerifier.Result result = verifier.verify(picks, 37.5, 126.9, 600, 900, 15, false);

        assertThat(result.picks()).extracting(Pick::travelMin).containsExactly(5, 30);
        assertThat(result.picks().get(1).arriveMin()).isEqualTo(695);
        assertThat(result.modes()).containsOnlyKeys(1);
    }

    @Test
    void dropsPlacesThatNoLongerFitBeforeEnd() {
        List<Pick> picks = List.of(pick("a", 5, 605, 60), pick("b", 20, 685, 60));
        when(client.walkMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(300);

        RouteVerifier.Result result = verifier.verify(picks, 37.5, 126.9, 600, 780, 15, false);

        assertThat(result.picks()).extracting(p -> p.place().id()).containsExactly("a");
    }

    @Test
    void shrinksEarlierStaysBeforeDroppingTheTail() {
        List<Pick> picks = List.of(pick("a", 5, 605, 60), pick("b", 20, 685, 60));
        when(client.walkMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(50);

        RouteVerifier.Result result = verifier.verify(picks, 37.5, 126.9, 600, 780, 15, false);

        assertThat(result.picks()).extracting(p -> p.place().id()).containsExactly("a", "b");
        assertThat(result.picks().get(1).departMin() + 15).isLessThanOrEqualTo(780);
    }

    @Test
    void propagatesRouteFailure() {
        List<Pick> picks = List.of(pick("a", 5, 605, 60));
        when(client.walkMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RouteException("walk:TOO_FAR_AWAY"));

        assertThatThrownBy(() -> verifier.verify(picks, 37.5, 126.9, 600, 900, 15, false))
                .isInstanceOf(RouteException.class);
    }
}
