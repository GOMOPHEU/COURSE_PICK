package com.coursepick;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "coursepick.keys.google-places=test-key")
@AutoConfigureMockMvc
class PlacesApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unknownPlaceKeepsUnknownWithoutCallingGoogle() throws Exception {
        mockMvc.perform(get("/places/0000000000").param("theme", "food").param("date", "2026-11-28"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("KEEP_UNKNOWN"))
                .andExpect(jsonPath("$.reason").value("MAPPING_NOT_BUILT"));
    }

    @Test
    void invalidThemeIsBadRequest() throws Exception {
        mockMvc.perform(get("/places/0000000000").param("theme", "unknown"))
                .andExpect(status().isBadRequest());
    }
}
