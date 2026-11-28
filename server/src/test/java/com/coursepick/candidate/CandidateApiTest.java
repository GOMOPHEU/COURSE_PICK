package com.coursepick.candidate;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CandidateApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean KakaoCandidateClient client;

    @Test void endpointRunsCandidatePipeline() throws Exception {
        when(client.search(any(), anyDouble(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(new KakaoCandidateClient.Page(List.of(new KakaoPlace("1","가족카페","음식점 > 카페","CE7",37.5765,126.9855,"","")), true));
        mvc.perform(post("/candidates").contentType(MediaType.APPLICATION_JSON)
                .content("{\"lat\":37.5765,\"lng\":126.9855,\"theme\":\"cafe\",\"companion\":\"family\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].kakaoId").value("1"))
                .andExpect(jsonPath("$.candidates[0].openStatus").value("unknown"))
                .andExpect(jsonPath("$.radiusMeters").value(865))
                .andExpect(jsonPath("$.insufficient").value(true));
    }
    @Test void badRequestsNeverReachProvider() throws Exception {
        for (String body : List.of("{}", "{\"lat\":91,\"lng\":126,\"theme\":\"cafe\"}",
                "{\"lat\":37,\"lng\":126,\"theme\":\"invalid\"}",
                "{\"lat\":37,\"lng\":126,\"theme\":\"cafe\",\"companion\":\"famliy\"}"))
            mvc.perform(post("/candidates").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(client);
    }
}
