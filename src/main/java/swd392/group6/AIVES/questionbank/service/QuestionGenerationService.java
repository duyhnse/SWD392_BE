package swd392.group6.AIVES.questionbank.service;

import swd392.group6.AIVES.questionbank.domain.Question;
import swd392.group6.AIVES.questionbank.domain.QuestionOrigin;
import swd392.group6.AIVES.questionbank.domain.QuestionSource;
import swd392.group6.AIVES.questionbank.domain.QuestionStatus;
import swd392.group6.AIVES.questionbank.repository.QuestionRepository;
import swd392.group6.AIVES.questionbank.repository.QuestionSourceRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.ai.AiJobService;
import swd392.group6.AIVES.ai.AiJobType;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.questionbank.BloomLevel;
import swd392.group6.AIVES.questionbank.dto.GenerationDto;
import swd392.group6.AIVES.questionbank.dto.GenerationRequest;
import swd392.group6.AIVES.user.NotificationApi;
import swd392.group6.AIVES.user.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * AI draft generation from course materials (04 §9.2, C3). Node 1 checks the request, sends {@code GENERATE_QUESTIONS}
 * with the material ids and stores the drafts it gets back; retrieval, prompting and retries are the AI node's (D63).
 * The lecturer picks the topic (D42). Nothing generated is ever published automatically (BR-Q4).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class QuestionGenerationService {

    static final String JOB_SUBJECT = "GENERATION_REQUEST";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_EXISTING_TITLES = 60;
    static final int MAX_QUESTIONS = 20;

    private final NamedParameterJdbcTemplate jdbc;
    private final ContentAccess access;
    private final AiJobService aiJobs;
    private final QuestionRepository questions;
    private final QuestionSourceRepository sources;
    private final NotificationApi notifications;
    private final Clock clock;



    public GenerationDto start(UUID courseId, GenerationRequest r, User user) {
        access.writeCourse(courseId, user);
        Map<String, Object> topic = jdbc.queryForList("select name, description from topics where topic_id = :t and course_id = :c",
                new MapSqlParameterSource("t", r.topicId()).addValue("c", courseId)).stream().findFirst()
                .orElseThrow(() -> ApiException.unprocessable("TOPIC_NOT_IN_COURSE", "The topic does not belong to this course"));
        List<UUID> materialIds = r.materialIds().stream().distinct().toList();
        Integer indexed = jdbc.queryForObject("""
                        select count(*) from course_materials where material_id in (:m) and course_id = :c and status = 'INDEXED'""",
                new MapSqlParameterSource("m", materialIds).addValue("c", courseId), Integer.class);
        if (indexed == null || indexed != materialIds.size()) {
            throw ApiException.unprocessable("MATERIAL_NOT_INDEXED",
                    "Every selected material must belong to the course and be indexed");
        }
        Map<String, Object> course = jdbc.queryForMap("select code, name, default_language from courses where course_id = :c",
                new MapSqlParameterSource("c", courseId));
        Language language = r.language() != null ? r.language() : Language.valueOf((String) course.get("default_language"));
        // D82: a number per Bloom level, 0 = none; cognitive order kept for the AI
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BloomLevel level : BloomLevel.values()) {
            Integer n = r.countsByBloom().get(level);
            if (n != null && n > 0) {
                counts.put(level.name(), n);
            }
        }
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total < 1 || total > MAX_QUESTIONS) {
            throw ApiException.unprocessable("BLOOM_COUNTS_INVALID",
                    "Ask for 1–" + MAX_QUESTIONS + " questions in all, at least one Bloom level above 0");
        }
        String focus = r.focusText() == null || r.focusText().isBlank() ? null : r.focusText().strip();

        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("""
                insert into ai_generation_requests (generation_request_id, course_id, topic_id, requested_by, material_ids,
                    requested_count, bloom_levels, counts_by_bloom, focus_text, language, status, created_at)
                values (:id, :c, :t, :u, cast(:m as jsonb), :n, cast(:b as jsonb), cast(:cb as jsonb), :f, :lang, 'PENDING',
                        :now)""",
                new MapSqlParameterSource("id", id).addValue("c", courseId).addValue("t", r.topicId())
                        .addValue("u", user.getUserId()).addValue("m", JSON.writeValueAsString(materialIds))
                        .addValue("n", total).addValue("b", JSON.writeValueAsString(counts.keySet()))
                        .addValue("cb", JSON.writeValueAsString(counts)).addValue("f", focus)
                        .addValue("lang", language.name()).addValue("now", Timestamp.from(now)));

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("language", language.name());
        input.put("course", Map.of("code", course.get("code"), "name", course.get("name")));
        input.put("topic", mapOf("name", topic.get("name"), "description", topic.get("description")));
        input.put("counts_by_bloom", counts); // contract 1.5.0: exactly this many of each level, no rubric (D82)
        input.put("focus_text", focus);
        // drafts too: asking twice for the same topic must not repeat the first batch; other chapters of the course
        // too (a focus can reach their materials), the topic's own questions first (D76)
        input.put("existing_questions", jdbc.queryForList("""
                        select content from questions where course_id = :c and status in ('PUBLISHED', 'DRAFT')
                        order by coalesce(topic_id = :t, false) desc, updated_at desc limit :n""",
                new MapSqlParameterSource("t", r.topicId()).addValue("c", courseId).addValue("n", MAX_EXISTING_TITLES), String.class));
        input.put("material_ids", materialIds);
        // the lecturer's own material for this chapter leads; general ones (review deck, whole-course plan) support (D80)
        input.put("primary_material_ids", jdbc.queryForList("""
                        select material_id from course_materials where material_id in (:m) and topic_id = :t""",
                new MapSqlParameterSource("m", materialIds).addValue("t", r.topicId()), UUID.class));
        jdbc.update("update ai_generation_requests set status = 'RUNNING' where generation_request_id = :id",
                new MapSqlParameterSource("id", id));
        aiJobs.enqueue(AiJobType.GENERATE_QUESTIONS, JOB_SUBJECT, id, input);
        return get(id, user);
    }

    @Transactional(readOnly = true)
    public GenerationDto get(UUID id, User user) {
        GenerationDto dto = find(id).orElseThrow(() -> ApiException.notFound("GENERATION_NOT_FOUND", "Generation request not found"));
        access.read(dto.courseId(), user, "GENERATION_NOT_FOUND", "Generation request not found");
        return dto;
    }

    @Transactional(readOnly = true)
    public List<GenerationDto> list(UUID courseId, User user) {
        access.readCourse(courseId, user);
        return jdbc.query(SELECT + " where g.course_id = :c order by g.created_at desc limit 20",
                new MapSqlParameterSource("c", courseId), (rs, i) -> row(rs));
    }

    /**
     * Result of a {@code GENERATE_QUESTIONS} job (called by {@link AiResultListener}). {@code NO_CONTEXT} from the AI
     * node = the materials do not cover the topic (E1); anything else that is not a valid answer fails the request and
     * writes nothing (E4, E5 — the node already retried).
     */
    void onJobFinished(UUID requestId, String jobStatus, JsonNode result, String errorCode, String errorMessage) {
        Map<String, Object> req = jdbc.queryForList("""
                        select status, course_id, topic_id, requested_by, counts_by_bloom::text, language, material_ids::text
                        from ai_generation_requests where generation_request_id = :id for update""",
                new MapSqlParameterSource("id", requestId)).stream().findFirst().orElse(null);
        if (req == null || !"RUNNING".equals(req.get("status"))) {
            return;
        }
        UUID owner = (UUID) req.get("requested_by");
        UUID courseId = (UUID) req.get("course_id");
        if ("NO_CONTEXT".equals(errorCode)) {
            String why = errorMessage == null || errorMessage.isBlank() ? "Selected materials do not cover this topic"
                    : errorMessage.length() > 500 ? errorMessage.substring(0, 500) : errorMessage; // the AI's reason (D71)
            finish(requestId, "NO_CONTEXT", 0, why, null);
            notify(owner, requestId, courseId, "NO_CONTEXT", 0);
            return;
        }
        Set<String> materials = new HashSet<>();
        JSON.readTree((String) req.get("material_ids")).forEach(n -> materials.add(n.asString()));
        String problem = !"SUCCEEDED".equals(jobStatus) ? "AI generation failed: " + (errorMessage != null ? errorMessage : errorCode)
                : validate(result, materials, counts((String) req.get("counts_by_bloom")));
        if (problem != null) {
            finish(requestId, "FAILED", 0, problem.length() > 1000 ? problem.substring(0, 1000) : problem, null);
            notify(owner, requestId, courseId, "FAILED", 0);
            return;
        }
        Instant now = clock.instant();
        int saved = 0;
        for (JsonNode g : result.path("questions")) {
            Question q = new Question();
            q.setCourseId(courseId);
            q.setTopicId((UUID) req.get("topic_id"));
            q.setContent(g.path("content").asString().strip());
            q.setReferenceAnswer(g.path("reference_answer").asString().strip());
            BloomLevel bloom = BloomLevel.valueOf(g.path("bloom_level").asString());
            q.setBloomLevel(bloom);
            q.setAiSuggestedBloom(bloom);
            q.setAiOriginalContent(q.getContent());
            q.setAiOriginalReferenceAnswer(q.getReferenceAnswer());
            q.setLanguage(Language.valueOf((String) req.get("language")));
            q.setStatus(QuestionStatus.DRAFT);
            q.setOrigin(QuestionOrigin.AI_GENERATED);
            q.setGenerationRequestId(requestId);
            // no rubric: the lecturer reads the draft and picks one of the course's rubrics before confirming (D82)
            q.setOwnerId(owner);
            q.setCreatedAt(now);
            q.setUpdatedAt(now);
            q = questions.saveAndFlush(q);
            for (JsonNode src : g.path("sources")) {
                QuestionSource s = new QuestionSource();
                s.setQuestionId(q.getId());
                s.setMaterialId(UUID.fromString(src.path("material_id").asString()));
                s.setLocationLabel(truncate(src.path("location_label").asString("—"), 100));
                String excerpt = src.path("excerpt").asString(null);
                s.setExcerpt(excerpt == null ? null : truncate(excerpt, 1000));
                sources.save(s);
            }
            saved++;
        }
        finish(requestId, "SUCCEEDED", saved, null, result.path("model").asString(null));
        notify(owner, requestId, courseId, "SUCCEEDED", saved);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** null = valid; otherwise what is wrong (04 §9.2 step 6). */
    static String validate(JsonNode result, Set<String> materialIds, Map<String, Integer> requested) {
        JsonNode list = result == null ? null : result.path("questions");
        if (list == null || !list.isArray() || list.isEmpty()) {
            return "questions must be a non-empty list";
        }
        Map<String, Integer> seen = new HashMap<>();
        int n = 0;
        for (JsonNode q : list) {
            n++;
            String content = q.path("content").asString("");
            if (content.isBlank() || content.length() > 4000) {
                return "question " + n + ": content is empty or too long";
            }
            String reference = q.path("reference_answer").asString("");
            if (reference.isBlank() || reference.length() > 4000) {
                return "question " + n + ": reference_answer is empty or too long";
            }
            String bloom = q.path("bloom_level").asString("");
            if (!requested.containsKey(bloom)) {
                return "question " + n + ": bloom_level was not requested";
            }
            if (seen.merge(bloom, 1, Integer::sum) > requested.get(bloom)) {
                return "more " + bloom + " questions than requested";
            }
            JsonNode cites = q.path("sources");
            if (!cites.isArray() || cites.isEmpty()) {
                return "question " + n + ": no sources";
            }
            for (JsonNode c : cites) {
                if (!materialIds.contains(c.path("material_id").asString(""))
                        || c.path("location_label").asString("").isBlank()) {
                    return "question " + n + ": cites a material that was not selected";
                }
            }
        }
        return null;
    }

    private void finish(UUID id, String status, int generated, String message, String model) {
        jdbc.update("""
                update ai_generation_requests set status = :s, generated_count = :n, message = :msg, llm_model = :model,
                    finished_at = :now where generation_request_id = :id""",
                new MapSqlParameterSource("s", status).addValue("n", generated).addValue("msg", message)
                        .addValue("model", model).addValue("now", Timestamp.from(clock.instant())).addValue("id", id));
    }

    private void notify(UUID user, UUID requestId, UUID courseId, String status, int generated) {
        boolean ok = "SUCCEEDED".equals(status);
        notifications.notify(user, "QUESTIONS_GENERATED",
                ok ? "AI đã tạo " + generated + " câu hỏi nháp" : "AI chưa tạo được câu hỏi",
                ok ? "Mở từng câu nháp, đọc lại, gắn rubric rồi xác nhận vào ngân hàng." : "Xem chi tiết ở yêu cầu tạo câu hỏi.",
                Map.of("generationRequestId", requestId.toString(), "courseId", courseId.toString(), "status", status));
    }

    private static final String SELECT = """
            select g.generation_request_id, g.course_id, g.topic_id, t.name as topic_name, g.material_ids::text,
                   g.requested_count, g.counts_by_bloom::text, g.focus_text, g.language, g.status, g.generated_count,
                   g.message, g.requested_by, g.created_at, g.finished_at
            from ai_generation_requests g join topics t on t.topic_id = g.topic_id""";

    private Optional<GenerationDto> find(UUID id) {
        return jdbc.query(SELECT + " where g.generation_request_id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> row(rs)).stream().findFirst();
    }

    private static GenerationDto row(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<UUID> materials = new ArrayList<>();
        JSON.readTree(rs.getString(5)).forEach(n -> materials.add(UUID.fromString(n.asString())));
        Map<BloomLevel, Integer> blooms = new java.util.EnumMap<>(BloomLevel.class);
        counts(rs.getString(7)).forEach((k, v) -> blooms.put(BloomLevel.valueOf(k), v));
        Timestamp finished = rs.getTimestamp(15);
        return new GenerationDto(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), materials, rs.getInt(6), blooms, rs.getString(8), Language.valueOf(rs.getString(9)),
                rs.getString(10), rs.getInt(11), rs.getString(12), rs.getObject(13, UUID.class),
                rs.getTimestamp(14).toInstant(), finished == null ? null : finished.toInstant());
    }

    /** {@code counts_by_bloom} as stored: level → number asked for (empty for requests made before D82). */
    private static Map<String, Integer> counts(String json) {
        Map<String, Integer> out = new LinkedHashMap<>();
        JSON.readTree(json).properties().forEach(e -> out.put(e.getKey(), e.getValue().asInt()));
        return out;
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }
}
