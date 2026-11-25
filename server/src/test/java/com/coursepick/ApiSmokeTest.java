package com.coursepick;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "coursepick.keys.kakao-rest=")
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsOkAndRegion() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.region").value("안국역"))
                .andExpect(jsonPath("$.radiusM").value(1300));
    }

    @Test
    void recommendReturnsStubCourse() throws Exception {
        String body = """
                {
                  "startPointId": "anguk-stn",
                  "startTime": "14:00",
                  "endTime": "19:00",
                  "themes": ["cafe", "history"]
                }
                """;
        mockMvc.perform(post("/recommend").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OK"))
                .andExpect(jsonPath("$.courses[0].places[0].name").value("경복궁"));
    }

    @Test
    void recommendRejectsMissingFields() throws Exception {
        mockMvc.perform(post("/recommend").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }
}
