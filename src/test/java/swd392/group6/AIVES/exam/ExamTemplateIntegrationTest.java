package swd392.group6.AIVES.exam;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import swd392.group6.AIVES.support.IntegrationTest;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.BloomLevel.APPLY;
import static swd392.group6.AIVES.questionbank.BloomLevel.UNDERSTAND;

/** Đề thi CRUD: rows, time budgets, pool, lock and duplicate (D45, D46, D49). */
@IntegrationTest
@Import(ExamTestConfiguration.class)
class ExamTemplateIntegrationTest extends ExamTestBase {

    @BeforeEach
    void resetClock() {
        clock.set(T0);
    }

    @Test
    void rowsGiveTheQuestionCountAndTheDuration_D46() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 3);
        questions(c, c.topicB(), APPLY, 1);
        String body = json(call(post("/api/v1/courses/" + c.id() + "/exam-templates"), c.token(), """
                {"title":"Giữa kỳ","passScore":5,"maxFollowupsPerQuestion":3,"items":[
                  {"topicId":"%s","bloomLevel":"UNDERSTAND","count":2},
                  {"topicId":"%s","bloomLevel":"APPLY","count":1,"secondsPerQuestion":400}]}"""
                .formatted(c.topicA(), c.topicB()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("VI"))
                .andExpect(jsonPath("$.passScore").value(5.0))
                .andExpect(jsonPath("$.maxFollowupsPerQuestion").value(3))
                .andExpect(jsonPath("$.items", hasSize(2)))
                // UNDERSTAND defaults to the exam.seconds.UNDERSTAND setting (180 s)
                .andExpect(jsonPath("$.items[0].secondsPerQuestion").value(180))
                .andExpect(jsonPath("$.items[0].topicName").exists())
                .andExpect(jsonPath("$.items[0].available").value(3))
                .andExpect(jsonPath("$.items[1].available").value(1))
                .andExpect(jsonPath("$.mainQuestionCount").value(3))
                .andExpect(jsonPath("$.totalDurationSec").value(2 * 180 + 400))
                .andExpect(jsonPath("$.poolSufficient").value(true))
                .andExpect(jsonPath("$.locked").value(false)));
        String id = JsonPath.read(body, "$.id");

        call(put("/api/v1/exam-templates/" + id + "/items"), c.token(),
                "{\"items\":[{\"topicId\":\"" + c.topicB() + "\",\"bloomLevel\":\"APPLY\",\"count\":2}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].secondsPerQuestion").value(240))
                .andExpect(jsonPath("$.poolSufficient").value(false));
        call(get("/api/v1/courses/" + c.id() + "/exam-templates"), c.token())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].mainQuestionCount").value(2))
                .andExpect(jsonPath("$[0].totalDurationSec").value(480));
    }

    @Test
    void rowsAreValidated() throws Exception {
        Course c = course();
        Course other = course();
        UUID id = template(c, 1);
        String url = "/api/v1/exam-templates/" + id + "/items";
        call(put(url), c.token(), "{\"items\":[{\"topicId\":\"" + other.topicA() + "\",\"count\":1}]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("TOPIC_NOT_IN_COURSE"));
        call(put(url), c.token(), "{\"items\":[{\"count\":6},{\"count\":5}]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_QUESTION_COUNT"));
        call(put(url), c.token(), "{\"items\":[{\"count\":1,\"secondsPerQuestion\":10}]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("INVALID_SECONDS_PER_QUESTION"));
        call(put(url), c.token(), "{\"items\":[{\"count\":1,\"rubricId\":\"" + other.rubric() + "\"}]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("RUBRIC_NOT_USABLE"));
        call(put(url), c.token(), "{\"items\":[]}")
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("TEMPLATE_ITEMS_REQUIRED"));
        call(put(url), c.token(), "{\"items\":[{\"count\":11}]}").andExpect(status().isBadRequest());
    }

    @Test
    void selectedPoolOnlyTakesPublishedQuestionsOfTheCourse() throws Exception {
        Course c = course();
        UUID published = questions(c, c.topicA(), UNDERSTAND, 1).getFirst();
        UUID draft = data.question(c.id(), c.topicA(), UNDERSTAND, c.rubric(), c.lecturer(), "DRAFT");
        UUID id = template(c, 1);
        String url = "/api/v1/exam-templates/" + id + "/question-pool";
        call(put(url), c.token(), "{\"mode\":\"SELECTED\",\"questionIds\":[\"" + published + "\",\"" + draft + "\"]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_PUBLISHED"));
        call(put(url), c.token(), "{\"mode\":\"SELECTED\",\"questionIds\":[]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUESTION_POOL_EMPTY"));
        call(put(url), c.token(), "{\"mode\":\"SELECTED\",\"questionIds\":[\"" + published + "\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionPoolMode").value("SELECTED"))
                .andExpect(jsonPath("$.selectedQuestionIds[0]").value(published.toString()))
                .andExpect(jsonPath("$.items[0].available").value(1));
    }

    @Test
    void publishedExamLocksItsTemplate_duplicateToChange_D49() throws Exception {
        Course c = course();
        questions(c, c.topicA(), UNDERSTAND, 3);
        UUID template = template(c, 2);
        UUID exam = createExam(c, template, T0.plusSeconds(3600), T0.plusSeconds(7200), null);
        addStudents(c, exam, data.student());
        publish(c, exam);

        String body = json(call(get("/api/v1/exam-templates/" + template), c.token())
                .andExpect(jsonPath("$.locked").value(true))
                .andExpect(jsonPath("$.usedByExamCount").value(1)));
        int version = JsonPath.read(body, "$.version");
        call(put("/api/v1/exam-templates/" + template + "/items"), c.token(), "{\"items\":[{\"count\":1}]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_LOCKED"));
        call(put("/api/v1/exam-templates/" + template), c.token(), """
                {"version":%d,"title":"X","language":"VI","maxFollowupsPerQuestion":1,"maxAnswerSec":60,
                 "silenceWarningSec":10,"showQuestionText":true}""".formatted(version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_LOCKED"));
        call(delete("/api/v1/exam-templates/" + template), c.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_IN_USE"));

        String copy = JsonPath.read(json(call(post("/api/v1/exam-templates/" + template + "/duplicate"), c.token())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.mainQuestionCount").value(2))
                .andExpect(jsonPath("$.title").value("Đề (bản sao)"))), "$.id");
        call(put("/api/v1/exam-templates/" + copy + "/items"), c.token(), "{\"items\":[{\"count\":1}]}")
                .andExpect(status().isOk());
        call(post("/api/v1/exam-templates/" + copy + "/archive"), c.token(), "{\"archived\":true}")
                .andExpect(jsonPath("$.archived").value(true));
        call(get("/api/v1/courses/" + c.id() + "/exam-templates"), c.token()).andExpect(jsonPath("$", hasSize(1)));
        call(get("/api/v1/courses/" + c.id() + "/exam-templates?includeArchived=true"), c.token())
                .andExpect(jsonPath("$", hasSize(2)));
        call(delete("/api/v1/exam-templates/" + copy), c.token()).andExpect(status().isNoContent());
    }

    @Test
    void assignedLecturersManage_adminReads_othersSeeNothing_AC_C1() throws Exception {
        Course c = course();
        UUID id = template(c, 1);
        call(get("/api/v1/exam-templates/" + id), token(data.lecturer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EXAM_TEMPLATE_NOT_FOUND"));
        call(get("/api/v1/exam-templates/" + id), token(data.admin())).andExpect(status().isOk());
        call(post("/api/v1/exam-templates/" + id + "/duplicate"), token(data.admin()))
                .andExpect(status().isForbidden());
        call(get("/api/v1/exam-templates/" + id), token(data.student())).andExpect(status().isForbidden());
    }
}
