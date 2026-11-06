package swd392.group6.AIVES.exam;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import swd392.group6.AIVES.support.IntegrationTest;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.BloomLevel.APPLY;
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** A published / open buổi thi keeps enough published questions to draw at check-in (15 §5.6, PublishedPoolGuard). */
@IntegrationTest
@Import(ExamTestConfiguration.class)
class ExamPoolGuardIntegrationTest extends ExamTestBase {

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    @Test
    void publishedExamProtectsTheQuestionsItNeeds() throws Exception {
        Course c = course();
        List<UUID> needed = questions(c, c.topicA(), UNDERSTAND, 2);
        UUID spare = questions(c, c.topicA(), APPLY, 1).getFirst();
        UUID template = template(c, "[{\"topicId\":\"" + c.topicA() + "\",\"bloomLevel\":\"UNDERSTAND\",\"count\":2}]", null);
        UUID exam = createExam(c, template, T0.plusSeconds(3600), T0.plusSeconds(7200), null);
        addStudents(c, exam, data.student());
        publish(c, exam);
        UUID q = needed.getFirst();

        for (String action : List.of("unpublish", "discard")) {
            call(post("/api/v1/questions/" + q + "/" + action), c.token())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("QUESTION_NEEDED_BY_EXAM"))
                    .andExpect(jsonPath("$.detail", containsString("B-")));
        }
        int version = com.jayway.jsonpath.JsonPath.read(json(call(get("/api/v1/questions/" + q), c.token())), "$.version");
        call(put("/api/v1/questions/" + q), c.token(), """
                {"version":%d,"topicId":"%s","content":"Changed level","referenceAnswer":"- a","bloomLevel":"APPLY","rubricId":"%s"}"""
                .formatted(version, c.topicA(), c.rubric()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUESTION_NEEDED_BY_EXAM"));
        call(post("/api/v1/topics/" + c.topicA() + "/move-questions"), c.token(), "{\"targetTopicId\":\"" + c.topicB() + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("QUESTION_NEEDED_BY_EXAM"));

        // a question the exam does not need is free; with a replacement published, the needed one is free too
        call(post("/api/v1/questions/" + spare + "/unpublish"), c.token()).andExpect(status().isOk());
        questions(c, c.topicA(), UNDERSTAND, 1);
        call(post("/api/v1/questions/" + q + "/unpublish"), c.token()).andExpect(status().isOk());

        // once the buổi thi is back to draft nothing is protected any more
        call(post("/api/v1/viva-exams/" + exam + "/unpublish"), c.token()).andExpect(status().isOk());
        call(post("/api/v1/topics/" + c.topicA() + "/move-questions"), c.token(), "{\"targetTopicId\":\"" + c.topicB() + "\"}")
                .andExpect(status().isOk());
    }
}
