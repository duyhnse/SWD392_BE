package swd392.group6.AIVES.questionbank;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Actor;
import swd392.group6.AIVES.questionbank.QuestionBankFixture.Bank;
import swd392.group6.AIVES.support.IntegrationTest;
import swd392.group6.AIVES.support.TestUsers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

    private static final String PDF = "application/pdf";

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestUsers users;

    private QuestionBankFixture fx;
    private Actor lecturer;
    private Bank bank;

    @BeforeEach
    void setUp() throws Exception {
        fx = new QuestionBankFixture(mockMvc, jdbc, users);
        lecturer = fx.lecturer();
        bank = fx.bank(lecturer);
    }

    // ------------------------------------------------------------------ materials & indexing

    static byte[] pdf(String... pages) throws Exception {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                    cs.newLineAtOffset(40, 740);
                    for (String line : text.split("\n")) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -14);
                    }
                    cs.endText();
                }
            }
            doc.save(out);
            return out.toByteArray();
        }
    }

    private UUID upload(String name, byte[] data) throws Exception {
        return QuestionBankFixture.id(fx.perform(lecturer, multipart("/api/v1/courses/" + bank.courseId() + "/materials")
                .file(new MockMultipartFile("file", name, PDF, data))).andExpect(status().isCreated()));
    }

    private UUID architectureSlides() throws Exception {
        return upload("architecture.pdf", pdf(
                "Architecture styles. A modular monolith is one deployable unit.\nModules talk through interfaces.",
                "Architecture trade-offs. Microservices scale each service alone.\nThey add network latency and operations work."));
    }

    @Test
    void uploadIndexesTheFilePageByPage_9_1() throws Exception {
        UUID id = architectureSlides();
        fx.perform(lecturer, get("/api/v1/materials/" + id))
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.indexedAt").exists());
        assertThat(jdbc.queryForList("select location_label from material_chunks where material_id = ? order by chunk_index",
                String.class, id)).containsExactly("Page 1", "Page 2");
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'MATERIAL_INDEXED'",
                Integer.class, lecturer.id())).isOne();
    }

    @Test
    void unreadableFileFails_andCanBeReindexed_E4() throws Exception {
        UUID id = upload("broken.pdf", "%PDF-1.7 not really".getBytes(StandardCharsets.UTF_8));
        fx.perform(lecturer, get("/api/v1/materials/" + id))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage", startsWith("UNSUPPORTED_FILE")));
        UUID blank = upload("scan.pdf", pdf(""));
        fx.perform(lecturer, get("/api/v1/materials/" + blank))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage", startsWith("NO_TEXT_LAYER")));

        fx.perform(lecturer, post("/api/v1/materials/" + id + "/reindex")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from ai_jobs where subject_id = ? and job_type = 'INDEX_MATERIAL'",
                Integer.class, id)).isEqualTo(2);
        fx.perform(fx.lecturer(), post("/api/v1/materials/" + id + "/reindex")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ AI generation

    private String generation(UUID topic, List<UUID> materials, int count, String blooms, String focus) {
        return "{\"topicId\":\"" + topic + "\",\"materialIds\":[" + String.join(",", materials.stream().map(m -> "\"" + m + "\"").toList())
                + "],\"count\":" + count + ",\"bloomLevels\":[" + blooms + "]" + (focus == null ? "" : ",\"focusText\":\"" + focus + "\"") + "}";
    }

    private UUID generate(UUID topic, List<UUID> materials, int count, String blooms, String focus) throws Exception {
        return QuestionBankFixture.id(fx.json(lecturer, post("/api/v1/courses/" + bank.courseId() + "/question-generations"),
                generation(topic, materials, count, blooms, focus)).andExpect(status().isAccepted()));
    }

    @Test
    void generationWritesDraftsWithCitations_andKeepsTheAiOriginal_AC_Q6() throws Exception {
        UUID material = architectureSlides();
        UUID topic = fx.topic(lecturer, bank.courseId(), "Microservices trade-offs");
        UUID request = generate(topic, List.of(material), 2, "\"APPLY\",\"ANALYZE\"", "latency of microservices");

        fx.perform(lecturer, get("/api/v1/question-generations/" + request))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.generatedCount").value(2))
                .andExpect(jsonPath("$.topicName").value("Microservices trade-offs"));
        String list = QuestionBankFixture.body(fx.perform(lecturer, get("/api/v1/courses/" + bank.courseId() + "/questions")
                        .param("generationRequestId", request.toString()))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].origin").value("AI_GENERATED")));
        UUID q = UUID.fromString(JsonPath.read(list, "$.items[0].id"));
        String original = QuestionBankFixture.read(fx.perform(lecturer, get("/api/v1/questions/" + q))
                .andExpect(jsonPath("$.sources[0].locationLabel", startsWith("Page ")))
                .andExpect(jsonPath("$.sources[0].materialName").value("architecture.pdf"))
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
        fx.perform(lecturer, post("/api/v1/questions/" + bank.topicId() + "/rubric/from-suggestion"))
                .andExpect(status().isNotFound());
        UUID manual = fx.question(lecturer, bank.courseId(), QuestionBankFixture.completeQuestion(bank.topicId(), bank.rubricId(), "Manual"));
        fx.perform(lecturer, post("/api/v1/questions/" + manual + "/rubric/from-suggestion"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NO_AI_RUBRIC"));
    }

    @Test
    void unrelatedMaterialsGiveNoContext_AC_Q5() throws Exception {
        UUID material = architectureSlides();
        UUID topic = fx.topic(lecturer, bank.courseId(), "Kanji radicals");
        UUID request = generate(topic, List.of(material), 3, "\"REMEMBER\"", null);
        fx.perform(lecturer, get("/api/v1/question-generations/" + request))
                .andExpect(jsonPath("$.status").value("NO_CONTEXT"))
                .andExpect(jsonPath("$.generatedCount").value(0));
        assertThat(jdbc.queryForObject("select count(*) from questions where generation_request_id = ?", Integer.class, request)).isZero();
    }

    @Test
    void invalidAiOutputTwiceFailsTheWholeBatch_AC_Q7_andLlmDownKeepsManualWorking_AC_Q12() throws Exception {
        UUID material = architectureSlides();
        UUID topic = fx.topic(lecturer, bank.courseId(), "Microservices");
        UUID invalid = generate(topic, List.of(material), 2, "\"APPLY\"", "microservices #mock-invalid");
        fx.perform(lecturer, get("/api/v1/question-generations/" + invalid))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message", containsString("invalid twice")));
        assertThat(jdbc.queryForObject("select count(*) from ai_jobs where subject_id = ?", Integer.class, invalid)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from questions where generation_request_id = ?", Integer.class, invalid)).isZero();

        UUID down = generate(topic, List.of(material), 1, "\"APPLY\"", "microservices #mock-fail");
        fx.perform(lecturer, get("/api/v1/question-generations/" + down)).andExpect(jsonPath("$.status").value("FAILED"));
        fx.question(lecturer, bank.courseId(), QuestionBankFixture.completeQuestion(bank.topicId(), bank.rubricId(), "Still works"));
        fx.perform(lecturer, get("/api/v1/courses/" + bank.courseId() + "/question-generations"))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void generationRequestIsValidated() throws Exception {
        UUID broken = upload("broken.pdf", "nope".getBytes(StandardCharsets.UTF_8));
        String url = "/api/v1/courses/" + bank.courseId() + "/question-generations";
        fx.json(lecturer, post(url), generation(bank.topicId(), List.of(broken), 2, "\"APPLY\"", null))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("MATERIAL_NOT_INDEXED"));
        UUID good = architectureSlides();
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
    void importAcceptsTheXlsxTemplate_andRejectsUnreadableFiles() throws Exception {
        byte[] template = fx.perform(lecturer, get("/api/v1/question-import-template")
                        .param("format", "xlsx").param("courseId", bank.courseId().toString()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("aives-question-template.xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            // one header row that explains each column (D62) and names this course's topics; no example rows
            assertThat(book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue())
                    .startsWith("topic (bắt buộc").contains("Architecture");
            assertThat(book.getSheetAt(0).getRow(0).getCell(4).getStringCellValue()).contains("trống = ngôn ngữ của môn");
            assertThat((Object) book.getSheetAt(0).getRow(1)).isNull();
            assertThat(book.getNumberOfSheets()).isOne();
        }
        ResultHolder r = importFile("untouched.xlsx", template);
        assertThat((Integer) r.read("$.total")).isZero();
        assertThat((Integer) r.read("$.errors.length()")).isZero();

        fx.perform(lecturer, get("/api/v1/question-import-template").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")));
        String url = "/api/v1/courses/" + bank.courseId() + "/questions/import";
        fx.perform(lecturer, multipart(url).file(new MockMultipartFile("file", "x.csv", "text/csv",
                        "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("IMPORT_HEADER_INVALID"));
        fx.perform(lecturer, multipart(url).file(new MockMultipartFile("file", "x.txt", "text/plain", new byte[] {1})))
                .andExpect(status().isUnsupportedMediaType());
        fx.perform(lecturer, multipart(url).file(new MockMultipartFile("file", "x.xlsx", "application/octet-stream", new byte[] {1, 2})))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("IMPORT_UNREADABLE"));
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
