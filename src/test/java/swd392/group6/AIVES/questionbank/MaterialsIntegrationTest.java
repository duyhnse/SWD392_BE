package swd392.group6.AIVES.questionbank;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Actor;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Course materials: upload / list / download / delete (15 §5.2, BR-Q12). */
@IntegrationTest
class MaterialsIntegrationTest {

    private static final String PDF = "application/pdf";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestUsers users;
    @Autowired private StoragePort storage;

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

    private UUID upload(String name, String type, byte[] data) throws Exception {
        return QuestionBankFixture.id(fx.perform(lecturer, multipart("/api/v1/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", name, type, data)))
                .andExpect(status().isCreated()));
    }

    @Test
    void uploadListGetDownloadDelete() throws Exception {
        byte[] data = "%PDF-1.7 slides".getBytes(StandardCharsets.UTF_8);
        UUID id = upload("Topic 3 slides.pdf", PDF, data);
        String key = "materials/" + courseId + "/" + id + "/Topic 3 slides.pdf";
        assertThat(storage.exists(key)).isTrue();
        assertThat(jdbc.queryForObject("select storage_key from course_materials where material_id = ?", String.class, id))
                .isEqualTo(key);

        fx.perform(lecturer, get("/api/v1/materials/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPLOADED"))
                .andExpect(jsonPath("$.fileName").value("Topic 3 slides.pdf"))
                .andExpect(jsonPath("$.contentType").value(PDF))
                .andExpect(jsonPath("$.sizeBytes").value(data.length))
                .andExpect(jsonPath("$.uploadedBy").value(lecturer.id().toString()));
        upload("notes.docx", "application/octet-stream", new byte[] {1, 2, 3});
        fx.perform(lecturer, get("/api/v1/courses/" + courseId + "/materials"))
                .andExpect(jsonPath("$", hasSize(2)));

        fx.perform(lecturer, get("/api/v1/materials/" + id + "/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", PDF))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().bytes(data));

        fx.perform(lecturer, delete("/api/v1/materials/" + id)).andExpect(status().isNoContent());
        assertThat(storage.exists(key)).isFalse();
        fx.perform(lecturer, get("/api/v1/materials/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_FOUND"));
    }

    @Test
    void pathInFileNameIsStripped() throws Exception {
        UUID id = upload("../../etc/passwd.docx", DOCX, new byte[] {1});
        fx.perform(lecturer, get("/api/v1/materials/" + id)).andExpect(jsonPath("$.fileName").value("passwd.docx"));
    }

    @Test
    void unsupportedTypesAre415() throws Exception {
        fx.perform(lecturer, multipart("/api/v1/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MATERIAL_TYPE_UNSUPPORTED"));
        // extension and declared type must agree
        fx.perform(lecturer, multipart("/api/v1/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", "slides.pdf", DOCX, new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType());
        fx.perform(lecturer, multipart("/api/v1/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", "noext", PDF, new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void tooLargeIs413AndEmptyIsRejected() throws Exception {
        MockMultipartFile huge = new MockMultipartFile("file", "big.pdf", PDF, new byte[] {1}) {
            @Override
            public long getSize() {
                return 50L * 1024 * 1024 + 1;
            }
        };
        fx.perform(lecturer, multipart("/api/v1/courses/" + courseId + "/materials").file(huge))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("MATERIAL_TOO_LARGE"));
        fx.perform(lecturer, multipart("/api/v1/courses/" + courseId + "/materials")
                        .file(new MockMultipartFile("file", "empty.pdf", PDF, new byte[0])))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("FILE_EMPTY"));
    }

    @Test
    void citedMaterialCannotBeDeleted_BR_Q12() throws Exception {
        UUID id = upload("cited.pdf", PDF, new byte[] {1});
        UUID topic = fx.topic(lecturer, courseId, "T");
        UUID question = fx.question(lecturer, courseId, "{\"topicId\":\"" + topic + "\",\"content\":\"Q\"}");
        jdbc.update("insert into question_sources (question_source_id, question_id, material_id, location_label) values (?, ?, ?, 'Page 1')",
                UUID.randomUUID(), question, id);
        fx.perform(lecturer, delete("/api/v1/materials/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_CITED"));
        fx.perform(lecturer, get("/api/v1/materials/" + id + "/file")).andExpect(status().isOk());
    }
}
