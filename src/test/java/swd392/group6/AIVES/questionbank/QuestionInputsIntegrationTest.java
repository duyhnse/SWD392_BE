package swd392.group6.AIVES.questionbank;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Actor;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Bank;
import swd392.group6.AIVES.support.AiNodeTestDouble;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two non-manual ways questions enter the bank (04 §5): import from a file (C2, §8) and AI generation from
 * indexed materials (C3, §9) — the latter on the mock AI node, which really extracts and chunks the files.
 */
@IntegrationTest
class QuestionInputsIntegrationTest {


    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestUsers users;
    @Autowired private AiNodeTestDouble aiNode;

    private QuestionBankFixture fx;
    private Actor lecturer;
    private Bank bank;

    @BeforeEach
    void setUp() throws Exception {
        fx = new QuestionBankFixture(mockMvc, jdbc, users);
        lecturer = fx.lecturer();
        bank = fx.bank(lecturer);
    }

    // ------------------------------------------------------------------ materials & indexing (AI node = AiNodeTestDouble)

    static final byte[] PDF = "%PDF-1.7 slides".getBytes(StandardCharsets.UTF_8);

    private UUID upload(String name, byte[] data) throws Exception {
        return QuestionBankFixture.id(fx.perform(lecturer, multipart("/api/v1/courses/" + bank.courseId() + "/materials")
                .file(new MockMultipartFile("file", name, PDF_TYPE, data))).andExpect(status().isCreated()));
    }

    private static final String PDF_TYPE = "application/pdf";

    @Test
    void uploadSendsTheFileToTheAiNodeAndRecordsTheResult_9_1() throws Exception {
        UUID id = upload("slides.pdf", PDF);
        fx.perform(lecturer, get("/api/v1/materials/" + id))
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.indexedAt").exists());
        String request = jdbc.queryForObject("select request::text from ai_jobs where subject_id = ? and job_type = 'INDEX_MATERIAL'",
                String.class, id);
        assertThat(request).contains("\"file_url\"").contains("/internal/ai-node/files?key=").doesNotContain("chunking");
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'MATERIAL_INDEXED'",
                Integer.class, lecturer.id())).isOne();

        fx.perform(lecturer, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/materials/" + id))
                .andExpect(status().isNoContent());
        assertThat(aiNode.deletedMaterials).contains(id);   // the AI node drops its index of the material (D63)
    }

    @Test
    void indexingErrorsFromTheAiNodeAreShown_andCanBeRetried_E4() throws Exception {
        UUID id = upload("broken.pdf", PDF);
        fx.perform(lecturer, get("/api/v1/materials/" + id))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage", startsWith("UNSUPPORTED_FILE")));
        UUID scan = upload("scan.pdf", PDF);
        fx.perform(lecturer, get("/api/v1/materials/" + scan))
                .andExpect(jsonPath("$.errorMessage", startsWith("NO_TEXT_LAYER")));

        fx.perform(lecturer, post("/api/v1/materials/" + id + "/reindex")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from ai_jobs where subject_id = ? and job_type = 'INDEX_MATERIAL'",
                Integer.class, id)).isEqualTo(2);
        fx.perform(fx.lecturer(), post("/api/v1/materials/" + id + "/reindex")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ AI generation (retrieval + LLM = AI node)

    private String generation(UUID topic, List<UUID> materials, int count, String blooms, String focus) {
        return "{\"topicId\":\"" + topic + "\",\"materialIds\":[" + String.join(",", materials.stream().map(m -> "\"" + m + "\"").toList())
                + "],\"count\":" + count + ",\"bloomLevels\":[" + blooms + "]" + (focus == null ? "" : ",\"focusText\":\"" + focus + "\"") + "}";
    }

    private UUID generate(UUID topic, List<UUID> materials, int count, String blooms, String focus) throws Exception {
        return QuestionBankFixture.id(fx.json(lecturer, post("/api/v1/courses/" + bank.courseId() + "/question-generations"),
                generation(topic, materials, count, blooms, focus)).andExpect(status().isAccepted()));
    }

    @Test
    void generationStoresCitedDrafts_andKeepsTheAiOriginal_AC_Q6() throws Exception {
        UUID material = upload("slides.pdf", PDF);
        UUID topic = fx.topic(lecturer, bank.courseId(), "Đạo hàm");
        UUID request = generate(topic, List.of(material), 2, "\"APPLY\",\"ANALYZE\"", "quy tắc lũy thừa");
        String sent = jdbc.queryForObject("select request::text from ai_jobs where subject_id = ?", String.class, request);
        assertThat(sent).contains("\"material_ids\"").contains(material.toString()).doesNotContain("\"chunks\"");

        fx.perform(lecturer, get("/api/v1/question-generations/" + request))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.generatedCount").value(2))
                .andExpect(jsonPath("$.topicName").value("Đạo hàm"));
        String list = QuestionBankFixture.body(fx.perform(lecturer, get("/api/v1/courses/" + bank.courseId() + "/questions")
                        .param("generationRequestId", request.toString()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].origin").value("AI_GENERATED")));
        UUID q = UUID.fromString(JsonPath.read(list, "$.items[0].id"));
        String original = QuestionBankFixture.read(fx.perform(lecturer, get("/api/v1/questions/" + q))
                .andExpect(jsonPath("$.sources[0].locationLabel", startsWith("Page ")))
                .andExpect(jsonPath("$.sources[0].materialName").value("slides.pdf"))
                .andExpect(jsonPath("$.sources[0].excerpt", startsWith("Đoạn trích")))
                .andExpect(jsonPath("$.ai.suggestedRubric.criteria", hasSize(2)))
                .andExpect(jsonPath("$.rubric").doesNotExist()), "$.ai.originalContent");

        int version = QuestionBankFixture.read(fx.perform(lecturer, get("/api/v1/questions/" + q)), "$.version");
        fx.json(lecturer, put("/api/v1/questions/" + q), "{\"version\":" + version + ",\"topicId\":\"" + topic
                        + "\",\"content\":\"Edited by the lecturer\",\"referenceAnswer\":\"- a\",\"bloomLevel\":\"APPLY\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ai.originalContent").value(original));

        // "Use AI-suggested rubric" (04 §5 step 5), then the lecturer publishes — never the AI (BR-Q4)
        fx.perform(lecturer, post("/api/v1/questions/" + q + "/rubric/from-suggestion"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rubric.name", containsString("Q-")))
                .andExpect(jsonPath("$.rubric.totalWeight").value(100.0));
        fx.json(lecturer, post("/api/v1/questions/publish"), "{\"questionIds\":[\"" + q + "\"]}")
                .andExpect(jsonPath("$.published", hasSize(1)));
        UUID manual = fx.question(lecturer, bank.courseId(), QuestionBankFixture.completeQuestion(bank.topicId(), bank.rubricId(), "Manual"));
        fx.perform(lecturer, post("/api/v1/questions/" + manual + "/rubric/from-suggestion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_AI_RUBRIC"));
    }

    @Test
    void theAiIsToldEveryQuestionOfTheCourse_ownChapterFirst_D76() throws Exception {
        UUID material = upload("slides.pdf", PDF);
        UUID chapterA = fx.topic(lecturer, bank.courseId(), "Chương A");
        UUID chapterB = fx.topic(lecturer, bank.courseId(), "Chương B");
        generate(chapterA, List.of(material), 2, "\"UNDERSTAND\"", null);
        fx.question(lecturer, bank.courseId(), QuestionBankFixture.completeQuestion(chapterB, bank.rubricId(), "Câu sẵn của chương B"));
        UUID second = generate(chapterB, List.of(material), 1, "\"UNDERSTAND\"", null);
        String sent = jdbc.queryForObject("select request::text from ai_jobs where subject_id = ?", String.class, second);
        List<String> existing = JsonPath.read(sent, "$.existing_questions");
        List<String> drafts = jdbc.queryForList("select content from questions where topic_id = ?", String.class, chapterA);
        assertThat(existing).hasSize(2 + 1 + jdbc.queryForObject("select count(*) from questions where course_id = ? and topic_id <> ?"
                + " and topic_id <> ? and status in ('PUBLISHED', 'DRAFT')", Integer.class, bank.courseId(), chapterA, chapterB))
                .containsAll(drafts);                                   // chapter A's drafts are there too
        assertThat(existing.getFirst()).contains("Câu sẵn của chương B");  // the chapter's own question first
    }

    @Test
    void sharedRubricChosenAtGenerationIsAttachedAndSentToTheAi_D70() throws Exception {
        UUID material = upload("slides.pdf", PDF);
        UUID topic = fx.topic(lecturer, bank.courseId(), "Chương 2");
        String body = generation(topic, List.of(material), 2, "\"UNDERSTAND\"", null)
                .replaceFirst("}$", ",\"rubricId\":\"" + bank.rubricId() + "\"}");
        UUID request = QuestionBankFixture.id(fx.json(lecturer, post("/api/v1/courses/" + bank.courseId() + "/question-generations"),
                body).andExpect(status().isAccepted()).andExpect(jsonPath("$.rubricId").value(bank.rubricId().toString())));
        String sent = jdbc.queryForObject("select request::text from ai_jobs where subject_id = ?", String.class, request);
        assertThat(sent).contains("\"rubric\"", "\"criteria\"");
        String list = QuestionBankFixture.body(fx.perform(lecturer, get("/api/v1/courses/" + bank.courseId() + "/questions")
                .param("generationRequestId", request.toString())).andExpect(jsonPath("$.total").value(2)));
        UUID q = UUID.fromString(JsonPath.read(list, "$.items[0].id"));
        fx.perform(lecturer, get("/api/v1/questions/" + q))
                .andExpect(jsonPath("$.rubric.id").value(bank.rubricId().toString()))
                .andExpect(jsonPath("$.ai.suggestedRubric.criteria", hasSize(2))); // the AI's idea stays visible

        UUID otherCourse = fx.course("VI");
        fx.assign(otherCourse, lecturer);
        UUID foreign = fx.rubric(lecturer, otherCourse, "Khác", 100);
        fx.json(lecturer, post("/api/v1/courses/" + bank.courseId() + "/question-generations"),
                        generation(topic, List.of(material), 1, "\"UNDERSTAND\"", null).replaceFirst("}$", ",\"rubricId\":\"" + foreign + "\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("RUBRIC_NOT_IN_COURSE"));
    }

    @Test
    void noContextFailuresAndBadAnswersWriteNothing_AC_Q5_Q7_Q12() throws Exception {
        UUID material = upload("slides.pdf", PDF);
        UUID topic = fx.topic(lecturer, bank.courseId(), "Kanji");
        UUID none = generate(topic, List.of(material), 3, "\"REMEMBER\"", "#no-context");
        fx.perform(lecturer, get("/api/v1/question-generations/" + none))
                .andExpect(jsonPath("$.status").value("NO_CONTEXT")).andExpect(jsonPath("$.generatedCount").value(0));
        UUID failed = generate(topic, List.of(material), 2, "\"APPLY\"", "#fail");
        fx.perform(lecturer, get("/api/v1/question-generations/" + failed))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.message", containsString("invalid twice")));
        UUID cheat = generate(topic, List.of(material), 1, "\"APPLY\"", "#bad-citation");
        fx.perform(lecturer, get("/api/v1/question-generations/" + cheat))
                .andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.message", containsString("not selected")));
        assertThat(jdbc.queryForObject("select count(*) from questions where generation_request_id in (?, ?, ?)",
                Integer.class, none, failed, cheat)).isZero();
        // with the AI unusable, manual entry still works (AC-Q12)
        fx.question(lecturer, bank.courseId(), QuestionBankFixture.completeQuestion(bank.topicId(), bank.rubricId(), "Still works"));
        fx.perform(lecturer, get("/api/v1/courses/" + bank.courseId() + "/question-generations"))
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void generationRequestIsValidated() throws Exception {
        UUID broken = upload("broken.pdf", PDF);
        String url = "/api/v1/courses/" + bank.courseId() + "/question-generations";
        fx.json(lecturer, post(url), generation(bank.topicId(), List.of(broken), 2, "\"APPLY\"", null))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("MATERIAL_NOT_INDEXED"));
        UUID good = upload("slides.pdf", PDF);
        fx.json(lecturer, post(url), generation(bank.topicId(), List.of(good), 21, "\"APPLY\"", null))
                .andExpect(status().isBadRequest());
        fx.json(lecturer, post(url), generation(bank.topicId(), List.of(good), 2, "", null))
                .andExpect(status().isBadRequest());
        fx.json(lecturer, post(url), generation(UUID.randomUUID(), List.of(good), 2, "\"APPLY\"", null))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("TOPIC_NOT_IN_COURSE"));
    }

    // ------------------------------------------------------------------ import

    private ResultHolder importFile(String name, byte[] data) throws Exception {
        return new ResultHolder(QuestionBankFixture.body(fx.perform(lecturer,
                multipart("/api/v1/courses/" + bank.courseId() + "/questions/import")
                        .file(new MockMultipartFile("file", name, "application/octet-stream", data)))
                .andExpect(status().isOk())));
    }

    record ResultHolder(String json) {
        <T> T read(String path) {
            return JsonPath.read(json, path);
        }
    }

    @Test
    void importKeepsGoodRowsAndReportsBadOnes_AC_Q4() throws Exception {
        String csv = """
                topic,content,reference_answer,bloom_level,language,rubric_name
                Architecture,Question number one about layers?,- a,REMEMBER,VI,Default rubric
                architecture,Question number two about modules?,,hiểu,,
                Architecture,Question number three about ports?,,VAN_DUNG,EN,
                Unknown topic,Question number four about nothing?,,,,
                Architecture,Too short,,,,
                Architecture,Question number six about events?,,PHAN_TICH,,
                Architecture,Question number seven about caches?,,EVALUATE,,
                Architecture,Question number eight about queues?,,,,
                Architecture,Question number nine about gateways?,,,,
                Architecture,Question number ten about sagas?,,,,
                Architecture,question  number ONE about layers?,,,,
                """;
        String check = QuestionBankFixture.body(fx.perform(lecturer,
                multipart("/api/v1/courses/" + bank.courseId() + "/questions/import").param("dryRun", "true")
                        .file(new MockMultipartFile("file", "bank.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(7))
                .andExpect(jsonPath("$.questionIds", hasSize(0))));
        assertThat(check).contains("DUPLICATE");
        assertThat(jdbc.queryForObject("select count(*) from questions where course_id = ? and origin = 'IMPORTED'",
                Integer.class, bank.courseId())).isZero();
        ResultHolder r = importFile("bank.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat((Integer) r.read("$.total")).isEqualTo(11);
        assertThat((Integer) r.read("$.imported")).isEqualTo(7);
        List<Integer> rows = r.read("$.errors[*].row");
        List<String> codes = r.read("$.errors[*].code");
        assertThat(rows).containsExactly(5, 6, 8, 12);
        assertThat(codes).containsExactly("TOPIC_NOT_FOUND", "CONTENT_LENGTH", "BLOOM_INVALID", "DUPLICATE");

        assertThat(jdbc.queryForList("""
                select bloom_level from questions where course_id = ? and origin = 'IMPORTED' and status = 'DRAFT'
                order by created_at, display_no""", String.class, bank.courseId()))
                .hasSize(7).startsWith("REMEMBER", "UNDERSTAND", "APPLY");
        assertThat(jdbc.queryForObject("select count(*) from questions where course_id = ? and rubric_id is not null",
                Integer.class, bank.courseId())).isOne();
    }

    @Test
    void importAcceptsTheCsvTemplate_andRejectsOtherFiles() throws Exception {
        byte[] template = fx.perform(lecturer, get("/api/v1/question-import-template"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("aives-question-template.csv")))
                .andReturn().getResponse().getContentAsByteArray();
        String header = new String(template, StandardCharsets.UTF_8);
        // one header row: column + help + blank default + a generic example; never a real course's data (D62)
        assertThat(header).contains("topic (bắt buộc").contains("vd. Đạo hàm").contains("trống = ngôn ngữ của môn")
                .doesNotContain("Architecture");
        assertThat(header.strip().lines()).hasSize(1);
        ResultHolder untouched = importFile("untouched.csv", template);
        assertThat((Integer) untouched.read("$.total")).isZero();
        ResultHolder filled = importFile("filled.csv", (header.stripTrailing() + "\nArchitecture,Đạo hàm của x² là gì?,,HIEU,,\n")
                .getBytes(StandardCharsets.UTF_8));
        assertThat((Integer) filled.read("$.imported")).isOne();

        String url = "/api/v1/courses/" + bank.courseId() + "/questions/import";
        fx.perform(lecturer, multipart(url).file(new MockMultipartFile("file", "x.csv", "text/csv",
                        "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("IMPORT_HEADER_INVALID"));
        fx.perform(lecturer, multipart(url).file(new MockMultipartFile("file", "x.txt", "text/plain", new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType());
        fx.perform(lecturer, multipart(url).file(new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[] {1, 2})))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.code").value("IMPORT_TYPE_UNSUPPORTED"));
        StringBuilder big = new StringBuilder("topic,content\n");
        for (int i = 0; i < 501; i++) {
            big.append("Architecture,Question number ").append(i).append(" is long enough\n");
        }
        fx.perform(lecturer, multipart(url).file(new MockMultipartFile("file", "big.csv", "text/csv",
                        big.toString().getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("IMPORT_TOO_MANY_ROWS"));
    }

    @Test
    void publishingNotifiesOnce() throws Exception {
        UUID a = fx.question(lecturer, bank.courseId(), QuestionBankFixture.completeQuestion(bank.topicId(), bank.rubricId(), "Alpha"));
        UUID b = fx.question(lecturer, bank.courseId(), QuestionBankFixture.completeQuestion(bank.topicId(), bank.rubricId(), "Beta"));
        fx.json(lecturer, post("/api/v1/questions/publish"), "{\"questionIds\":[\"" + a + "\",\"" + b + "\"]}")
                .andExpect(jsonPath("$.published", hasSize(2)));
        assertThat(jdbc.queryForList("select title from notifications where user_id = ? and type = 'QUESTIONS_PUBLISHED'",
                String.class, lecturer.id())).containsExactly("Đã công bố 2 câu hỏi");
    }
}
