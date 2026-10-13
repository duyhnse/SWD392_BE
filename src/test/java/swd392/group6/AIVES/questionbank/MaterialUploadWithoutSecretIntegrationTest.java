package swd392.group6.AIVES.questionbank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Actor;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Regression: uploads failed with 500 "Empty key" when AI_NODE_CALLBACK_SECRET was not set (mock mode, production). */
@IntegrationTest
@TestPropertySource(properties = "application.ai-node.callback-secret=")
class MaterialUploadWithoutSecretIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestUsers users;

    @Test
    void uploadAndIndexWorkWithoutAConfiguredSecret() throws Exception {
        QuestionBankFixture fx = new QuestionBankFixture(mockMvc, jdbc, users);
        Actor lecturer = fx.lecturer();
        UUID courseId = fx.course("VI");
        fx.assign(courseId, lecturer);
        UUID id = QuestionBankFixture.id(fx.perform(lecturer, multipart("/api/v1/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", "slides.pdf", "application/pdf",
                                QuestionInputsIntegrationTest.pdf("Derivatives. The derivative of x squared is 2x."))))
                .andExpect(status().isCreated()));
        fx.perform(lecturer, get("/api/v1/materials/" + id)).andExpect(jsonPath("$.status").value("INDEXED"));
    }
}
