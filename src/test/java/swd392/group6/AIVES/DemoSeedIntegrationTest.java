package swd392.group6.AIVES;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    /** The CRUD-milestone scenarios in the seed are consistent across the exam, grading and user modules. */
    @Test
    void seededScenariosAreVisibleThroughTheApis() throws Exception {
        String vinh = TestUsers.login(mockMvc, "vinhdqse190180", "VinhAives@2026");
        mockMvc.perform(get("/api/v1/me/sessions").header("Authorization", "Bearer " + vinh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].stage", hasItems("UPCOMING", "COMPLETED")));
        mockMvc.perform(get("/api/v1/me/results").header("Authorization", "Bearer " + vinh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..finalTotalScore", hasItems(7.53)));

        String lecturer1 = TestUsers.login(mockMvc, "lecturer1", "Aives@123");
        mockMvc.perform(get("/api/v1/courses").header("Authorization", "Bearer " + lecturer1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1)); // PRN232 belongs to lecturer2 only
        mockMvc.perform(get("/api/v1/viva-exams/50000000-0000-4000-8000-000000000003/report")
                        .header("Authorization", "Bearer " + lecturer1))
                .andExpect(status().isOk());
    }
}
