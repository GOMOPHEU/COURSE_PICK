package com.coursepick.gemini;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.coursepick.assemble.CourseAssembler.Input;
import com.coursepick.config.CoursepickSettings;
import com.coursepick.dto.RecommendResponse.Course;
import com.coursepick.dto.RecommendResponse.Place;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class GeminiRankerTest {

    private GeminiClient client;
    private GeminiRanker ranker;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        CoursepickSettings settings = new CoursepickSettings(
                new ObjectMapper().readTree("{\"gemini\": {\"enabled\": true, \"candidateCourses\": 5}}"));
        client = mock(GeminiClient.class);
        ObjectProvider<GeminiClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        ranker = new GeminiRanker(provider, settings);
    }

    private Course course(String id) {
        Place place = new Place(1, "k" + id, "장소" + id, "walk", 37.57, 126.98, "14:00", 60, "confirmed");
        return new Course(id, "basic", List.of(place), List.of(), 120, "16:00", "relaxed", "기본", List.of());
    }

    private Input input() {
        return new Input(LocalDate.parse("2026-11-28"), 840, 1080, 37.57, 126.98, List.of("walk"), "family", null, null);
    }

    @Test
    void returnsChoicesInTheOrderGeminiPicked() {
        when(client.generate(any(), any())).thenReturn("""
                {"picks":[{"courseId":"c3","reason":"세 번째가 좋아요"},{"courseId":"c1","reason":"첫 번째도 좋아요"}]}
                """);

        GeminiRanker.Result result = ranker.rank(input(), List.of(course("c1"), course("c2"), course("c3")), 2);

        assertThat(result.choices()).extracting(GeminiRanker.Choice::index).containsExactly(2, 0);
        assertThat(result.choices().get(0).reason()).isEqualTo("세 번째가 좋아요");
    }

    @Test
    void ignoresUnknownAndDuplicateCourseIds() {
        when(client.generate(any(), any())).thenReturn("""
                {"picks":[{"courseId":"zzz","reason":"x"},{"courseId":"c2","reason":"둘째"},{"courseId":"c2","reason":"또"}]}
                """);

        GeminiRanker.Result result = ranker.rank(input(), List.of(course("c1"), course("c2")), 2);

        assertThat(result.choices()).extracting(GeminiRanker.Choice::index).containsExactly(1);
    }

    @Test
    void returnsNoChoicesWhenGeminiFails() {
        when(client.generate(any(), any())).thenThrow(new GeminiException("Gemini 응답 오류 503"));

        GeminiRanker.Result result = ranker.rank(input(), List.of(course("c1"), course("c2")), 2);

        assertThat(result.choices()).isEmpty();
        assertThat(result.notes()).anyMatch(n -> n.contains("503"));
    }
}
