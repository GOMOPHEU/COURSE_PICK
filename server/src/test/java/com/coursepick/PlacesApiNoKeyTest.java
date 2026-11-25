package com.coursepick;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "coursepick.keys.google-places=")
@AutoConfigureMockMvc
class PlacesApiNoKeyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingKeyReturnsServiceUnavailable() throws Exception {
        mockMvc.perform(get("/places/0000000000").param("theme", "food"))
                .andExpect(status().isServiceUnavailable());
    }
}
