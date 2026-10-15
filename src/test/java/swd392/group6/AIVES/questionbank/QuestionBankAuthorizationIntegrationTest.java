package swd392.group6.AIVES.questionbank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Actor;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Bank;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;
import swd392.group6.AIVES.user.Role;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.completeQuestion;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.ids;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.rubricBody;

/** 15 §6 / AC-C1: unassigned lecturers get 404, ADMIN reads but never writes, students get 403. */
@IntegrationTest
class QuestionBankAuthorizationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestUsers users;

    private QuestionBankFixture fx;
    private Actor owner;
    private Bank bank;
    private UUID questionId;
    private UUID materialId;

    @BeforeEach
    void setUp() throws Exception {
        fx = new QuestionBankFixture(mockMvc, jdbc, users);
        owner = fx.lecturer();
        bank = fx.bank(owner);
        questionId = fx.publishedQuestion(owner, bank, "Visible question");
        materialId = QuestionBankFixture.id(fx.perform(owner, multipart("/api/v1/courses/" + bank.courseId() + "/materials")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[] {1}))));
    }

    private List<AbstractMockHttpServletRequestBuilder<?>> reads() {
        UUID c = bank.courseId();
        return List.of(
                get("/api/v1/courses/" + c + "/topics"),
                get("/api/v1/courses/" + c + "/terms"),
                get("/api/v1/courses/" + c + "/rubrics"),
                get("/api/v1/rubrics/" + bank.rubricId()),
                get("/api/v1/courses/" + c + "/questions"),
                get("/api/v1/questions/" + questionId),
                get("/api/v1/courses/" + c + "/materials"),
                get("/api/v1/materials/" + materialId),
                get("/api/v1/materials/" + materialId + "/file"));
    }

    private List<AbstractMockHttpServletRequestBuilder<?>> writes() {
        UUID c = bank.courseId();
        String json = "application/json";
        return List.of(
                post("/api/v1/courses/" + c + "/topics").contentType(json).content("{\"name\":\"New topic\"}"),
                patch("/api/v1/topics/" + bank.topicId()).contentType(json).content("{\"name\":\"Renamed\"}"),
                delete("/api/v1/topics/" + bank.topicId()),
                put("/api/v1/courses/" + c + "/terms").contentType(json).content("{\"terms\":[\"x\"]}"),
                post("/api/v1/courses/" + c + "/rubrics").contentType(json).content(rubricBody("Another", 100)),
                put("/api/v1/rubrics/" + bank.rubricId()).contentType(json).content(rubricBody("Changed", 100)),
                delete("/api/v1/rubrics/" + bank.rubricId()),
                post("/api/v1/rubrics/" + bank.rubricId() + "/duplicate").contentType(json).content("{\"name\":\"Dup\"}"),
                post("/api/v1/courses/" + c + "/questions").contentType(json)
                        .content(completeQuestion(bank.topicId(), bank.rubricId(), "New")),
                put("/api/v1/questions/" + questionId).contentType(json).content("{\"version\":0,\"topicId\":\""
                        + bank.topicId() + "\",\"content\":\"x\"}"),
                post("/api/v1/questions/" + questionId + "/discard"),
                post("/api/v1/questions/" + questionId + "/restore"),
                post("/api/v1/questions/" + questionId + "/unpublish"),
                post("/api/v1/questions/" + questionId + "/successor"),
                delete("/api/v1/questions/" + questionId),
                multipart("/api/v1/courses/" + c + "/materials")
                        .file(new MockMultipartFile("file", "b.pdf", "application/pdf", new byte[] {1})),
                delete("/api/v1/materials/" + materialId));
    }

    @Test
    void unassignedLecturerGets404Everywhere_AC_C1() throws Exception {
        Actor stranger = fx.lecturer();
        for (AbstractMockHttpServletRequestBuilder<?> request : reads()) {
            fx.perform(stranger, request).andExpect(status().isNotFound());
        }
        for (AbstractMockHttpServletRequestBuilder<?> request : writes()) {
            fx.perform(stranger, request).andExpect(status().isNotFound());
        }
        fx.perform(stranger, get("/api/v1/questions/" + questionId))
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));
        fx.perform(stranger, get("/api/v1/courses/" + bank.courseId() + "/questions"))
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
        fx.json(stranger, post("/api/v1/questions/publish"), ids(List.of(questionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed[0].errors[0]").value("QUESTION_NOT_FOUND"));
    }

    @Test
    void adminReadsButCannotWrite_AC_C1() throws Exception {
        Actor admin = fx.actor(Role.ADMIN);
        for (AbstractMockHttpServletRequestBuilder<?> request : reads()) {
            fx.perform(admin, request).andExpect(status().isOk());
        }
        for (AbstractMockHttpServletRequestBuilder<?> request : writes()) {
            fx.perform(admin, request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ADMIN_READ_ONLY"));
        }
        fx.json(admin, post("/api/v1/questions/publish"), ids(List.of(questionId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_READ_ONLY"));
    }

    @Test
    void studentsAreForbidden() throws Exception {
        Actor student = fx.actor(Role.STUDENT);
        for (AbstractMockHttpServletRequestBuilder<?> request : reads()) {
            fx.perform(student, request).andExpect(status().isForbidden());
        }
        for (AbstractMockHttpServletRequestBuilder<?> request : writes()) {
            fx.perform(student, request).andExpect(status().isForbidden());
        }
        fx.json(student, post("/api/v1/questions/publish"), ids(List.of(questionId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousIs401() throws Exception {
        mockMvc.perform(get("/api/v1/courses/" + bank.courseId() + "/questions")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownCourseIs404ForAdminToo() throws Exception {
        Actor admin = fx.actor(Role.ADMIN);
        fx.perform(admin, get("/api/v1/courses/" + UUID.randomUUID() + "/topics"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
    }
}
