package swd392.group6.AIVES.grading;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Drives the lecturer grading API: create → set every criterion final → confirm. */
public class GradingClient {

    private final MockMvc mockMvc;
    private final String lecturerToken;

    public GradingClient(MockMvc mockMvc, String lecturerToken) {
        this.mockMvc = mockMvc;
        this.lecturerToken = lecturerToken;
    }

    public String create(UUID sessionId) throws Exception {
        String json = mockMvc.perform(post("/api/v1/sessions/{id}/evaluation", sessionId).header("Authorization", lecturerToken))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    public String evaluation(String evaluationId) throws Exception {
        return mockMvc.perform(get("/api/v1/evaluations/{id}", evaluationId).header("Authorization", lecturerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** Sets thread {@code index} (0-based) criteria finals in rubric order. */
    public void grade(String evaluationId, int index, double... finals) throws Exception {
        String json = evaluation(evaluationId);
        String gradeId = JsonPath.read(json, "$.threads[" + index + "].gradeId");
        int version = JsonPath.read(json, "$.version");
        List<String> criterionIds = JsonPath.read(json, "$.threads[" + index + "].criteria[*].criterionId");
        StringBuilder criteria = new StringBuilder("[");
        for (int i = 0; i < finals.length; i++) {
            criteria.append(i == 0 ? "" : ",").append("{\"criterionId\":\"").append(criterionIds.get(i))
                    .append("\",\"finalScore\":").append(finals[i]).append('}');
        }
        criteria.append(']');
        mockMvc.perform(put("/api/v1/evaluations/{id}/threads/{g}", evaluationId, gradeId)
                        .header("Authorization", lecturerToken).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + ",\"criteria\":" + criteria + "}"))
                .andExpect(status().isOk());
    }

    public void confirm(String evaluationId) throws Exception {
        mockMvc.perform(post("/api/v1/evaluations/{id}/confirm", evaluationId).header("Authorization", lecturerToken))
                .andExpect(status().isOk());
    }

    /** Creates, grades every thread with the given finals (one array per thread) and confirms. */
    public String gradeAndConfirm(UUID sessionId, double[]... threads) throws Exception {
        String id = create(sessionId);
        for (int i = 0; i < threads.length; i++) {
            grade(id, i, threads[i]);
        }
        confirm(id);
        return id;
    }
}
