package swd392.group6.AIVES.questionbank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Actor;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.completeQuestion;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.rubricBody;

/** Rubrics: BR-Q3, BR-Q10, 15 §5.2. */
@IntegrationTest
class RubricsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestUsers users;

    private QuestionBankFixture fx;
    private Actor lecturer;
    private UUID courseId;

    @BeforeEach
    void setUp() throws Exception {
        fx = new QuestionBankFixture(mockMvc, jdbc, users);
        lecturer = fx.lecturer();
        courseId = fx.course("VI");
        fx.assign(courseId, lecturer);
    }

    @Test
    void createListGetRubric() throws Exception {
        UUID id = QuestionBankFixture.id(fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"), """
                        {"name":"Architecture rubric","description":"for ANALYZE questions","criteria":[
                          {"name":"Accuracy","description":"Correct concepts","maxScore":10,"weightPercent":50.5,"sortOrder":1},
                          {"name":"Depth","maxScore":5,"weightPercent":49.5,"sortOrder":0}]}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isLocked").value(false))
                .andExpect(jsonPath("$.totalWeight").value(100.0))
                .andExpect(jsonPath("$.criteria[*].name", contains("Depth", "Accuracy")))
                .andExpect(jsonPath("$.criteria[0].description").value("")));

        fx.perform(lecturer, get("/api/v1/rubrics/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Architecture rubric"))
                .andExpect(jsonPath("$.criteria", hasSize(2)))
                .andExpect(jsonPath("$.questionCount").value(0));
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/rubrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id.toString()));
    }

    @Test
    void weightsMustTotal100_AC_C2() throws Exception {
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"), rubricBody("Bad", 60, 30))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RUBRIC_WEIGHTS_NOT_100"))
                .andExpect(jsonPath("$.detail").value("Weights total 90.00"));
    }

    @Test
    void weightsWithinTolerancePass_BR_Q3() throws Exception {
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"), """
                        {"name":"Thirds","criteria":[{"name":"a","maxScore":1,"weightPercent":33.33},
                          {"name":"b","maxScore":1,"weightPercent":33.33},{"name":"c","maxScore":1,"weightPercent":33.33}]}""")
                .andExpect(status().isCreated());
    }

    @Test
    void criteriaRules() throws Exception {
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"), "{\"name\":\"Empty\",\"criteria\":[]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RUBRIC_CRITERIA_COUNT"));
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"),
                        rubricBody("Eleven", 10, 10, 10, 10, 10, 10, 10, 10, 10, 5, 5))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RUBRIC_CRITERIA_COUNT"));
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"),
                        "{\"name\":\"Zero\",\"criteria\":[{\"name\":\"a\",\"maxScore\":0,\"weightPercent\":100}]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RUBRIC_MAX_SCORE_INVALID"));
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"),
                        "{\"name\":\"Neg\",\"criteria\":[{\"name\":\"a\",\"maxScore\":10,\"weightPercent\":-10},"
                                + "{\"name\":\"b\",\"maxScore\":10,\"weightPercent\":110}]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RUBRIC_WEIGHT_INVALID"));
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"),
                        "{\"name\":\"NoName\",\"criteria\":[{\"maxScore\":10,\"weightPercent\":100}]}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void rubricNameUniquePerCourse_BR_Q10() throws Exception {
        fx.rubric(lecturer, courseId, "Standard", 100);
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/rubrics"), rubricBody("STANDARD", 100))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUBRIC_NAME_EXISTS"));
    }

    @Test
    void putReplacesCriteria() throws Exception {
        UUID id = fx.rubric(lecturer, courseId, "Old", 50, 50);
        fx.json(lecturer, put("/api/v1/rubrics/" + id), """
                        {"name":"New","criteria":[{"name":"Only","description":"all","maxScore":4,"weightPercent":100}]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New"))
                .andExpect(jsonPath("$.criteria", hasSize(1)))
                .andExpect(jsonPath("$.criteria[0].name").value("Only"))
                .andExpect(jsonPath("$.criteria[0].maxScore").value(4.0));
        Integer rows = jdbc.queryForObject("select count(*) from rubric_criteria where rubric_id = ?", Integer.class, id);
        org.assertj.core.api.Assertions.assertThat(rows).isEqualTo(1);

        fx.json(lecturer, put("/api/v1/rubrics/" + id), rubricBody("New", 70, 20))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RUBRIC_WEIGHTS_NOT_100"));
    }

    @Test
    void lockedRubricCannotChangeButCanBeDuplicated() throws Exception {
        UUID id = fx.rubric(lecturer, courseId, "Locked one", 100);
        jdbc.update("update rubrics set is_locked = true where rubric_id = ?", id);

        fx.json(lecturer, put("/api/v1/rubrics/" + id), rubricBody("Locked one", 100))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUBRIC_LOCKED"));

        fx.json(lecturer, post("/api/v1/rubrics/" + id + "/duplicate"), "{\"name\":\"Unlocked copy\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Unlocked copy"))
                .andExpect(jsonPath("$.isLocked").value(false))
                .andExpect(jsonPath("$.criteria", hasSize(1)));
        // default name when none given
        fx.json(lecturer, post("/api/v1/rubrics/" + id + "/duplicate"), "{}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Locked one (copy)"));
        fx.json(lecturer, post("/api/v1/rubrics/" + id + "/duplicate"), "{\"name\":\"unlocked COPY\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUBRIC_NAME_EXISTS"));
    }

    @Test
    void deleteUnusedRubricButNotUsedOne_AC_C11() throws Exception {
        UUID unused = fx.rubric(lecturer, courseId, "Unused", 100);
        fx.perform(lecturer, delete("/api/v1/rubrics/" + unused)).andExpect(status().isNoContent());
        fx.perform(lecturer, get("/api/v1/rubrics/" + unused))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUBRIC_NOT_FOUND"));

        UUID used = fx.rubric(lecturer, courseId, "Used", 100);
        UUID chapter = fx.chapter(lecturer, courseId, "T");
        fx.question(lecturer, courseId, completeQuestion(chapter, used, "Explain DI"));
        fx.perform(lecturer, delete("/api/v1/rubrics/" + used))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUBRIC_IN_USE"));
        fx.perform(lecturer, get("/api/v1/rubrics/" + used)).andExpect(jsonPath("$.questionCount").value(1));
    }
}
