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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.completeQuestion;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.ids;
import static swd392.group6.AIVES.questionbank.QuestionBankFixture.read;

/** Questions: state machine 03 §2.1, business rules BR-Q1..Q12, 15 §5.2. */
@IntegrationTest
class QuestionsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TestUsers users;

    private QuestionBankFixture fx;
    private Actor owner;
    private Bank bank;

    @BeforeEach
    void setUp() throws Exception {
        fx = new QuestionBankFixture(mockMvc, jdbc, users);
        owner = fx.lecturer();
        bank = fx.bank(owner);
    }

    private String updateBody(int version, String content, String extra) {
        return "{\"version\":" + version + ",\"chapterId\":\"" + bank.chapterId() + "\",\"content\":\"" + content + "\","
                + "\"referenceAnswer\":\"- key point\",\"bloomLevel\":\"APPLY\",\"rubricId\":\"" + bank.rubricId() + "\""
                + extra + "}";
    }

    private int version(UUID id) throws Exception {
        return read(fx.perform(owner, get("/api/v1/questions/" + id)), "$.version");
    }

    @Test
    void manualQuestionIsDraftOwnedByCaller_AC_Q1() throws Exception {
        fx.json(owner, post("/api/v1/courses/" + bank.courseId() + "/questions"),
                        "{\"chapterId\":\"" + bank.chapterId() + "\",\"content\":\"Explain CQRS\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.origin").value("MANUAL"))
                .andExpect(jsonPath("$.ownerId").value(owner.id().toString()))
                .andExpect(jsonPath("$.chapterTitle").value("Architecture"))
                .andExpect(jsonPath("$.chapterNo").value(1))
                .andExpect(jsonPath("$.language").value("VI"))
                .andExpect(jsonPath("$.isLocked").value(false))
                .andExpect(jsonPath("$.rubric").value(nullValue()))
                .andExpect(jsonPath("$.sources", hasSize(0)))
                .andExpect(jsonPath("$.version").isNumber());
    }

    @Test
    void languageDefaultsToCourseLanguage_BR_Q9() throws Exception {
        UUID en = fx.course("EN");
        fx.assign(en, owner);
        UUID chapter = fx.chapter(owner, en, "T");
        fx.json(owner, post("/api/v1/courses/" + en + "/questions"), "{\"chapterId\":\"" + chapter + "\",\"content\":\"Q\"}")
                .andExpect(jsonPath("$.language").value("EN"));
        fx.json(owner, post("/api/v1/courses/" + en + "/questions"),
                        "{\"chapterId\":\"" + chapter + "\",\"content\":\"Q\",\"language\":\"VI\"}")
                .andExpect(jsonPath("$.language").value("VI"));
    }

    @Test
    void chapterAndRubricMustBelongToTheCourse() throws Exception {
        Bank other = fx.bank(owner);
        fx.json(owner, post("/api/v1/courses/" + bank.courseId() + "/questions"),
                        "{\"chapterId\":\"" + other.chapterId() + "\",\"content\":\"Q\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CHAPTER_NOT_IN_COURSE"));
        fx.json(owner, post("/api/v1/courses/" + bank.courseId() + "/questions"),
                        completeQuestion(bank.chapterId(), other.rubricId(), "Q"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("RUBRIC_NOT_IN_COURSE"));
        fx.json(owner, post("/api/v1/courses/" + bank.courseId() + "/questions"), "{\"content\":\"no chapter\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsFullDtoWithRubricAndAiFields() throws Exception {
        UUID id = fx.question(owner, bank.courseId(), completeQuestion(bank.chapterId(), bank.rubricId(), "Explain DI"));
        UUID material = UUID.randomUUID();
        jdbc.update("""
                        insert into course_materials (material_id, course_id, file_name, content_type, size_bytes, storage_key, uploaded_by)
                        values (?, ?, 'slides.pdf', 'application/pdf', 10, 'materials/x/slides.pdf', ?)""",
                material, bank.courseId(), owner.id());
        jdbc.update("""
                        update questions set origin = 'AI_GENERATED', ai_original_content = 'AI text', ai_suggested_bloom = 'APPLY',
                            ai_suggested_rubric = '{"name":"Suggested","criteria":[]}'::jsonb where question_id = ?""", id);
        jdbc.update("""
                        insert into question_sources (question_source_id, question_id, material_id, location_label, excerpt)
                        values (?, ?, ?, 'Page 3', 'excerpt')""", UUID.randomUUID(), id, material);

        fx.perform(owner, get("/api/v1/questions/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rubric.name").value("Default rubric"))
                .andExpect(jsonPath("$.rubric.criteria", hasSize(2)))
                .andExpect(jsonPath("$.rubric.totalWeight").value(100.0))
                .andExpect(jsonPath("$.ai.originalContent").value("AI text"))
                .andExpect(jsonPath("$.ai.suggestedBloom").value("APPLY"))
                .andExpect(jsonPath("$.ai.suggestedRubric.name").value("Suggested"))
                .andExpect(jsonPath("$.sources[0].materialName").value("slides.pdf"))
                .andExpect(jsonPath("$.sources[0].locationLabel").value("Page 3"));
    }

    @Test
    void listFiltersAndPaging() throws Exception {
        UUID chapter2 = fx.chapter(owner, bank.courseId(), "Testing");
        UUID a = fx.question(owner, bank.courseId(), completeQuestion(bank.chapterId(), bank.rubricId(), "Explain microservices"));
        UUID b = fx.question(owner, bank.courseId(), "{\"chapterId\":\"" + chapter2 + "\",\"content\":\"What is a mock?\","
                + "\"bloomLevel\":\"REMEMBER\"}");
        UUID c = fx.question(owner, bank.courseId(), completeQuestion(chapter2, bank.rubricId(), "Design a MICROSERVICE test"));
        fx.json(owner, post("/api/v1/questions/publish"), ids(List.of(a))).andExpect(status().isOk());
        jdbc.update("update questions set origin = 'IMPORTED' where question_id = ?", c);
        String base = "/api/v1/courses/" + bank.courseId() + "/questions";

        fx.perform(owner, get(base)).andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items", hasSize(3)));
        fx.perform(owner, get(base).param("status", "PUBLISHED"))
                .andExpect(jsonPath("$.items[*].id", contains(a.toString())))
                .andExpect(jsonPath("$.items[0].rubricName").value("Default rubric"))
                .andExpect(jsonPath("$.items[0].chapterTitle").value("Architecture"));
        fx.perform(owner, get(base).param("status", "PUBLISHED", "DRAFT")).andExpect(jsonPath("$.total").value(3));
        fx.perform(owner, get(base).param("chapterId", chapter2.toString()))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(b.toString(), c.toString())));
        fx.perform(owner, get(base).param("bloomLevel", "REMEMBER"))
                .andExpect(jsonPath("$.items[*].id", contains(b.toString())));
        fx.perform(owner, get(base).param("origin", "IMPORTED"))
                .andExpect(jsonPath("$.items[*].id", contains(c.toString())));
        fx.perform(owner, get(base).param("q", "microservice"))
                .andExpect(jsonPath("$.items[*].id", containsInAnyOrder(a.toString(), c.toString())));
        fx.perform(owner, get(base).param("q", "100%")).andExpect(jsonPath("$.total").value(0));
        fx.perform(owner, get(base).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void publishWithoutBloomRubricReferenceReturnsCodes_AC_C2_AC_Q2() throws Exception {
        UUID bare = fx.question(owner, bank.courseId(), "{\"chapterId\":\"" + bank.chapterId() + "\",\"content\":\"   \"}");
        UUID badRubric = fx.rubric(owner, bank.courseId(), "Ninety", 100);
        jdbc.update("update rubric_criteria set weight_percent = 90 where rubric_id = ?", badRubric);
        UUID withBadRubric = fx.question(owner, bank.courseId(), completeQuestion(bank.chapterId(), badRubric, "Q?"));
        UUID good = fx.question(owner, bank.courseId(), completeQuestion(bank.chapterId(), bank.rubricId(), "Good?"));
        UUID missing = UUID.randomUUID();

        fx.json(owner, post("/api/v1/questions/publish"), ids(List.of(bare, withBadRubric, good, missing)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published", contains(good.toString())))
                .andExpect(jsonPath("$.failed", hasSize(3)))
                .andExpect(jsonPath("$.failed[0].id").value(bare.toString()))
                .andExpect(jsonPath("$.failed[0].errors", containsInAnyOrder(
                        "CONTENT_EMPTY", "REFERENCE_ANSWER_MISSING", "BLOOM_MISSING", "RUBRIC_MISSING")))
                .andExpect(jsonPath("$.failed[1].errors", contains("RUBRIC_WEIGHTS_NOT_100")))
                .andExpect(jsonPath("$.failed[2].errors", contains("QUESTION_NOT_FOUND")));

        fx.perform(owner, get("/api/v1/questions/" + bare)).andExpect(jsonPath("$.status").value("DRAFT"));
        fx.perform(owner, get("/api/v1/questions/" + good))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedBy").value(owner.id().toString()))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        // already published → not a draft any more
        fx.json(owner, post("/api/v1/questions/publish"), ids(List.of(good)))
                .andExpect(jsonPath("$.failed[0].errors", contains("INVALID_QUESTION_STATE")));
        fx.json(owner, post("/api/v1/questions/publish"), "{\"questionIds\":[]}").andExpect(status().isBadRequest());
    }

    @Test
    void ownerEditsWithOptimisticLocking_E7() throws Exception {
        UUID id = fx.question(owner, bank.courseId(), completeQuestion(bank.chapterId(), bank.rubricId(), "Old"));
        int v = version(id);
        fx.json(owner, put("/api/v1/questions/" + id), updateBody(v, "New wording", ",\"language\":\"EN\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("New wording"))
                .andExpect(jsonPath("$.bloomLevel").value("APPLY"))
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.version").value(v + 1));

        // a second tab still holding the old version
        fx.json(owner, put("/api/v1/questions/" + id), updateBody(v, "Stale", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
        fx.json(owner, put("/api/v1/questions/" + id), "{\"chapterId\":\"" + bank.chapterId() + "\",\"content\":\"x\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonOwnerCannotEditButCanRead_BR_Q6_AC_Q8() throws Exception {
        UUID id = fx.question(owner, bank.courseId(), completeQuestion(bank.chapterId(), bank.rubricId(), "Mine"));
        Actor colleague = fx.lecturer();
        fx.assign(bank.courseId(), colleague);
        int v = version(id);

        fx.perform(colleague, get("/api/v1/questions/" + id)).andExpect(status().isOk());
        fx.perform(colleague, get("/api/v1/courses/" + bank.courseId() + "/questions"))
                .andExpect(jsonPath("$.total").value(1));
        fx.json(colleague, put("/api/v1/questions/" + id), updateBody(v, "Hijack", ""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_QUESTION_OWNER"));
        for (String action : List.of("discard", "restore", "unpublish", "successor")) {
            fx.perform(colleague, post("/api/v1/questions/" + id + "/" + action))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("NOT_QUESTION_OWNER"));
        }
        fx.perform(colleague, delete("/api/v1/questions/" + id)).andExpect(status().isForbidden());
        fx.json(colleague, post("/api/v1/questions/publish"), ids(List.of(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failed[0].errors", contains("NOT_QUESTION_OWNER")));
    }

    @Test
    void editingPublishedQuestionRevalidates_BR_Q11() throws Exception {
        UUID id = fx.publishedQuestion(owner, bank, "Published one");
        int v = version(id);
        String noReference = "{\"version\":" + v + ",\"chapterId\":\"" + bank.chapterId() + "\",\"content\":\"Changed\","
                + "\"bloomLevel\":\"APPLY\",\"rubricId\":\"" + bank.rubricId() + "\"}";
        fx.json(owner, put("/api/v1/questions/" + id), noReference)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PUBLISH_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].code").value("REFERENCE_ANSWER_MISSING"));
        fx.perform(owner, get("/api/v1/questions/" + id))
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content").value("Published one"))
                .andExpect(jsonPath("$.version").value(v));

        fx.json(owner, put("/api/v1/questions/" + id), updateBody(v, "Valid change", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.content").value("Valid change"));
    }

    @Test
    void discardAndRestore_AC_Q9() throws Exception {
        UUID id = fx.question(owner, bank.courseId(), completeQuestion(bank.chapterId(), bank.rubricId(), "Throw away"));
        fx.perform(owner, post("/api/v1/questions/" + id + "/discard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISCARDED"))
                .andExpect(jsonPath("$.discardedAt").isNotEmpty());
        fx.perform(owner, get("/api/v1/courses/" + bank.courseId() + "/questions").param("status", "DISCARDED"))
                .andExpect(jsonPath("$.items[*].id", contains(id.toString())));
        fx.perform(owner, post("/api/v1/questions/" + id + "/discard"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION_STATE"));
        fx.json(owner, put("/api/v1/questions/" + id), updateBody(version(id), "x", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION_STATE"));
        fx.perform(owner, post("/api/v1/questions/" + id + "/restore"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.discardedAt").value(nullValue()));
        fx.perform(owner, post("/api/v1/questions/" + id + "/restore"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION_STATE"));

        UUID published = fx.publishedQuestion(owner, bank, "Published");
        fx.perform(owner, post("/api/v1/questions/" + published + "/discard"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION_STATE"));
    }

    @Test
    void unpublishBackToDraft() throws Exception {
        UUID id = fx.publishedQuestion(owner, bank, "Q");
        fx.perform(owner, post("/api/v1/questions/" + id + "/unpublish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.publishedAt").value(nullValue()));
        fx.perform(owner, post("/api/v1/questions/" + id + "/unpublish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_QUESTION_STATE"));
    }

    @Test
    void unpublishRefusedWhileInTheSelectedPoolOfAPublishedExam() throws Exception {
        UUID selected = fx.publishedQuestion(owner, bank, "Selected");
        fx.selectInPublishedExam(bank.courseId(), selected, owner);
        fx.perform(owner, post("/api/v1/questions/" + selected + "/unpublish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_IN_USE"));

        UUID free = fx.publishedQuestion(owner, bank, "Free");
        fx.perform(owner, post("/api/v1/questions/" + free + "/unpublish")).andExpect(status().isOk());
    }

    @Test
    void lockedQuestionChangesOnlyThroughSuccessor_AC_Q10_BR_Q8() throws Exception {
        UUID id = fx.publishedQuestion(owner, bank, "Locked");
        fx.assignToSession(bank.courseId(), id, owner, "COMPLETED");
        fx.lock(id);

        fx.json(owner, put("/api/v1/questions/" + id), updateBody(version(id), "Change", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_LOCKED"));
        fx.perform(owner, post("/api/v1/questions/" + id + "/unpublish"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_LOCKED"));

        UUID successor = QuestionBankFixture.id(fx.perform(owner, post("/api/v1/questions/" + id + "/successor"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.supersedesQuestionId").value(id.toString()))
                .andExpect(jsonPath("$.content").value("Locked"))
                .andExpect(jsonPath("$.isLocked").value(false)));
        fx.perform(owner, post("/api/v1/questions/" + id + "/successor"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUCCESSOR_EXISTS"));

        // the copied rubric is locked: switch to an unlocked duplicate and publish
        UUID newRubric = QuestionBankFixture.id(fx.json(owner, post("/api/v1/rubrics/" + bank.rubricId() + "/duplicate"),
                "{\"name\":\"v2\"}"));
        String body = "{\"version\":" + version(successor) + ",\"chapterId\":\"" + bank.chapterId() + "\",\"content\":\"Improved\","
                + "\"referenceAnswer\":\"- a\",\"bloomLevel\":\"ANALYZE\",\"rubricId\":\"" + newRubric + "\"}";
        fx.json(owner, put("/api/v1/questions/" + successor), body).andExpect(status().isOk());
        fx.json(owner, post("/api/v1/questions/publish"), ids(List.of(successor)))
                .andExpect(jsonPath("$.published", contains(successor.toString())));

        fx.perform(owner, get("/api/v1/questions/" + id)).andExpect(jsonPath("$.status").value("RETIRED"));
        fx.perform(owner, get("/api/v1/questions/" + successor)).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void successorOnlyForLockedPublishedQuestion() throws Exception {
        UUID id = fx.publishedQuestion(owner, bank, "Not locked");
        fx.perform(owner, post("/api/v1/questions/" + id + "/successor"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_LOCKED"));
    }

    @Test
    void deleteOnlyUnusedDrafts_AC_C11() throws Exception {
        UUID draft = fx.question(owner, bank.courseId(), completeQuestion(bank.chapterId(), bank.rubricId(), "Draft"));
        fx.perform(owner, delete("/api/v1/questions/" + draft)).andExpect(status().isNoContent());
        fx.perform(owner, get("/api/v1/questions/" + draft))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUESTION_NOT_FOUND"));

        UUID published = fx.publishedQuestion(owner, bank, "Published");
        fx.perform(owner, delete("/api/v1/questions/" + published))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_IN_USE"));

        // used in a session, then unpublished back to DRAFT: still not deletable
        UUID used = fx.publishedQuestion(owner, bank, "Used");
        fx.assignToSession(bank.courseId(), used, owner, "CANCELLED");
        fx.perform(owner, post("/api/v1/questions/" + used + "/unpublish")).andExpect(status().isOk());
        fx.perform(owner, delete("/api/v1/questions/" + used))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUESTION_IN_USE"));
        Integer n = jdbc.queryForObject("select count(*) from questions where question_id = ?", Integer.class, used);
        assertThat(n).isEqualTo(1);
    }
}
