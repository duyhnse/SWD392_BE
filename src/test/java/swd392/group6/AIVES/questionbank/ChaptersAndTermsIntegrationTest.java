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

/** Chương CRUD (D42) and course terms (15 §5.2). */
@IntegrationTest
class ChaptersAndTermsIntegrationTest {

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
    void chapterCrudHappyPath_D42() throws Exception {
        UUID b = QuestionBankFixture.id(fx.json(lecturer, post("/api/v1/courses/" + courseId + "/chapters"),
                        "{\"title\":\"  Testing  \",\"description\":\"unit tests\",\"chapterNo\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Testing"))
                .andExpect(jsonPath("$.chapterNo").value(2))
                .andExpect(jsonPath("$.createdBy").value(lecturer.id().toString())));
        // no number → next free one
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/chapters"), "{\"title\":\"Architecture\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.chapterNo").value(3));

        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/chapters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Testing", "Architecture")))
                .andExpect(jsonPath("$[1].questionCount").value(0));

        fx.json(lecturer, patch("/api/v1/chapters/" + b), "{\"title\":\"Software testing\",\"chapterNo\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Software testing"))
                .andExpect(jsonPath("$.description").value("unit tests"))
                .andExpect(jsonPath("$.chapterNo").value(1));

        fx.perform(lecturer, delete("/api/v1/chapters/" + b)).andExpect(status().isNoContent());
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/chapters"))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void chapterNumberIsUniqueAndInRange() throws Exception {
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/chapters"), "{\"title\":\"One\",\"chapterNo\":1}")
                .andExpect(status().isCreated());
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/chapters"), "{\"title\":\"Two\",\"chapterNo\":1}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAPTER_NO_EXISTS"));
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/chapters"), "{\"title\":\"Two\",\"chapterNo\":100}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("INVALID_CHAPTER_NO"));
    }

    @Test
    void chapterTitleIsUniquePerCourseIgnoringCase() throws Exception {
        UUID a = fx.chapter(lecturer, courseId, "Patterns");
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/chapters"), "{\"title\":\"patterns\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAPTER_TITLE_EXISTS"));
        UUID b = fx.chapter(lecturer, courseId, "Other");
        fx.json(lecturer, patch("/api/v1/chapters/" + b), "{\"title\":\"PATTERNS\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAPTER_TITLE_EXISTS"));
        // renaming a chapter to its own title is fine
        fx.json(lecturer, patch("/api/v1/chapters/" + a), "{\"title\":\"Patterns\"}").andExpect(status().isOk());

        // same title in another course is allowed
        UUID other = fx.course("EN");
        fx.assign(other, lecturer);
        fx.chapter(lecturer, other, "Patterns");
    }

    @Test
    void blankChapterNameIsRejected() throws Exception {
        fx.json(lecturer, post("/api/v1/courses/" + courseId + "/chapters"), "{\"title\":\"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void deletingChapterWithQuestionsIsConflict_AC_C11() throws Exception {
        Bank bank = new Bank(courseId, fx.chapter(lecturer, courseId, "Used"), fx.rubric(lecturer, courseId, "R", 100));
        fx.question(lecturer, courseId, completeQuestion(bank.chapterId(), bank.rubricId(), "What is SOLID?"));
        fx.perform(lecturer, delete("/api/v1/chapters/" + bank.chapterId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAPTER_IN_USE"));
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/chapters"))
                .andExpect(jsonPath("$[0].questionCount").value(1));
    }

    @Test
    void unknownChapterIs404() throws Exception {
        fx.perform(lecturer, delete("/api/v1/chapters/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAPTER_NOT_FOUND"));
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
