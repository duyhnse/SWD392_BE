package swd392.group6.AIVES.questionbank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Actor;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Bank;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.completeQuestion;

/** Topics CRUD and course terms (15 §5.2). */
@IntegrationTest
class TopicsAndTermsIntegrationTest {

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
    void topicCrudHappyPath() throws Exception {
        UUID b = QuestionBankFixture.id(fx.json(lecturer, post("/api/v1/courses/" + courseId + "/topics"),
                        "{\"name\":\"  Testing  \",\"description\":\"unit tests\",\"sortOrder\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Testing"))
                .andExpect(jsonPath("$.sortOrder").value(2))
                .andExpect(jsonPath("$.createdBy").value(lecturer.id().toString())));
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/topics"), "{\"name\":\"Architecture\",\"sortOrder\":1}")
                .andExpect(status().isCreated());

        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/topics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("Architecture", "Testing")))
                .andExpect(jsonPath("$[1].questionCount").value(0));

        fx.json(lecturer, patch("/api/v1/topics/" + b), "{\"name\":\"Software testing\",\"sortOrder\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Software testing"))
                .andExpect(jsonPath("$.description").value("unit tests"))
                .andExpect(jsonPath("$.sortOrder").value(0));

        fx.perform(lecturer, delete("/api/v1/topics/" + b)).andExpect(status().isNoContent());
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/topics"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void topicNameIsUniquePerCourseIgnoringCase() throws Exception {
        UUID a = fx.topic(lecturer, courseId, "Patterns");
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/topics"), "{\"name\":\"patterns\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_NAME_EXISTS"));
        UUID b = fx.topic(lecturer, courseId, "Other");
        fx.json(lecturer, patch("/api/v1/topics/" + b), "{\"name\":\"PATTERNS\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_NAME_EXISTS"));
        // renaming a topic to its own name is fine
        fx.json(lecturer, patch("/api/v1/topics/" + a), "{\"name\":\"Patterns\"}").andExpect(status().isOk());

        // same name in another course is allowed
        UUID other = fx.course("EN");
        fx.assign(other, lecturer);
        fx.topic(lecturer, other, "Patterns");
    }

    @Test
    void blankTopicNameIsRejected() throws Exception {
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/topics"), "{\"name\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deletingTopicWithQuestionsIsConflict_AC_C11() throws Exception {
        Bank bank = new Bank(courseId, fx.topic(lecturer, courseId, "Used"), fx.rubric(lecturer, courseId, "R", 100));
        fx.question(lecturer, courseId, completeQuestion(bank.topicId(), bank.rubricId(), "What is SOLID?"));
        fx.perform(lecturer, delete("/api/v1/topics/" + bank.topicId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TOPIC_IN_USE"));
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/topics"))
                .andExpect(jsonPath("$[0].questionCount").value(1));
    }

    @Test
    void movingQuestionsEmptiesATopicSoItCanBeDeleted_D54() throws Exception {
        UUID from = fx.topic(lecturer, courseId, "Old");
        UUID to = fx.topic(lecturer, courseId, "New");
        UUID rubric = fx.rubric(lecturer, courseId, "R", 100);
        UUID q1 = fx.question(lecturer, courseId, completeQuestion(from, rubric, "What is coupling?"));
        fx.question(lecturer, courseId, completeQuestion(from, rubric, "What is cohesion?"));
        UUID otherCourse = fx.course("VI");
        fx.assign(otherCourse, lecturer);
        UUID foreign = fx.topic(lecturer, otherCourse, "Foreign");

        fx.json(lecturer, post("/api/v1/topics/" + from + "/move-questions"), "{\"targetTopicId\":\"" + foreign + "\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_IN_COURSE"));
        fx.json(lecturer, post("/api/v1/topics/" + from + "/move-questions"), "{\"targetTopicId\":\"" + to + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(2));
        fx.perform(lecturer, get("/api/v1/questions/" + q1)).andExpect(jsonPath("$.topicName").value("New"));
        fx.perform(lecturer, delete("/api/v1/topics/" + from)).andExpect(status().isNoContent());
    }

    @Test
    void archivedCourseIsReadOnly_D54() throws Exception {
        UUID topic = fx.topic(lecturer, courseId, "Kept");
        jdbc.update("update courses set is_active = false where course_id = ?", courseId);
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/topics")).andExpect(status().isOk());
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/topics"), "{\"name\":\"X\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_ARCHIVED"));
        fx.perform(lecturer, delete("/api/v1/topics/" + topic)).andExpect(status().isConflict());
    }

    @Test
    void unknownTopicIs404() throws Exception {
        fx.perform(lecturer, delete("/api/v1/topics/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TOPIC_NOT_FOUND"));
    }

    @Test
    void termsAreReplacedTrimmedAndDeduplicatedCaseInsensitively() throws Exception {
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/terms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms", hasSize(0)));

        fx.json(lecturer, put("/api/v1/courses/" + courseId + "/terms"),
                        "{\"terms\":[\" microservice \",\"SOLID\",\"Microservice\",\"\",\"  \",\"solid\",\"Kafka\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms", contains("Kafka", "microservice", "SOLID")));

        // replacing keeps no old terms, and re-adding a term in another case does not collide
        fx.json(lecturer, put("/api/v1/courses/" + courseId + "/terms"), "{\"terms\":[\"KAFKA\",\"REST\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terms", contains("KAFKA", "REST")));
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/terms"))
                .andExpect(jsonPath("$.terms", contains("KAFKA", "REST")));
    }

    @Test
    void termsLimits() throws Exception {
        String tooLong = "x".repeat(101);
        fx.json(lecturer, put("/api/v1/courses/" + courseId + "/terms"), "{\"terms\":[\"" + tooLong + "\"]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TERM_TOO_LONG"));

        StringBuilder many = new StringBuilder("{\"terms\":[");
        for (int i = 0; i < 201; i++) {
            many.append(i == 0 ? "" : ",").append("\"term").append(i).append('"');
        }
        fx.json(lecturer, put("/api/v1/courses/" + courseId + "/terms"), many + "]}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TOO_MANY_TERMS"));

        fx.json(lecturer, put("/api/v1/courses/" + courseId + "/terms"), "{\"terms\":[\"" + "y".repeat(100) + "\"]}")
                .andExpect(status().isOk());
    }
}
