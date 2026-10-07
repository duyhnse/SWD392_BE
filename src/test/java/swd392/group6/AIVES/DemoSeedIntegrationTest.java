package swd392.group6.AIVES;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.IntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The "demo" profile seeds working accounts for every role (11_IMPLEMENTATION_PLAN.md §5). */
@IntegrationTest
@ActiveProfiles({"test", "demo"})
class DemoSeedIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void demoAccountsCanLogInWithTheirRole() throws Exception {
        String[][] accounts = {{"admin", "ADMIN"}, {"lecturer1", "LECTURER"}, {"student1", "STUDENT"}};
        for (String[] account : accounts) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + account[0] + "\",\"password\":\"Aives@123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.user.role").value(account[1]));
        }
    }

    @Test
    void teamSampleAccountLogsInWithItsOwnPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"vinhdqse190180\",\"password\":\"VinhAives@2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.studentCode").value("SE190180"))
                .andExpect(jsonPath("$.user.googleLinked").value(false));
    }
}
