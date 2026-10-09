package swd392.group6.AIVES.questionbank.internal;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.AiDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.CreateQuestionRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.PublishFailure;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.PublishResult;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.QuestionDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.QuestionFilter;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.QuestionRubricDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.QuestionSummaryDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.SourceDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.UpdateQuestionRequest;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Questions and their state machine (03 §2.1, 04 §5–§7, 15 §5.2). */
@Service
@RequiredArgsConstructor
@Transactional
public class QuestionService {

    private static final String NOT_FOUND = "QUESTION_NOT_FOUND";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final QuestionRepository questions;
    private final TopicRepository topics;
    private final RubricRepository rubrics;
    private final QuestionSourceRepository sources;
    private final CourseMaterialRepository materials;
    private final ContentAccess access;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public PageResponse<QuestionSummaryDto> list(UUID courseId, QuestionFilter filter, int page, int size, User user) {
        access.readCourse(courseId, user);
        Specification<Question> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("courseId"), courseId));
            if (notEmpty(filter.status())) {
                p.add(root.get("status").in(filter.status()));
            }
            if (notEmpty(filter.topicId())) {
                p.add(root.get("topicId").in(filter.topicId()));
            }
            if (notEmpty(filter.bloomLevel())) {
                p.add(root.get("bloomLevel").in(filter.bloomLevel()));
            }
            if (notEmpty(filter.origin())) {
                p.add(root.get("origin").in(filter.origin()));
            }
            if (filter.q() != null && !filter.q().isBlank()) {
                String like = "%" + filter.q().trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.like(cb.lower(root.get("content")), like, '\\'));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<Question> result = questions.findAll(spec, PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))));
        Map<UUID, String> topicNames = topics.findByCourseIdOrderBySortOrderAscNameAsc(courseId).stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName));
        Map<UUID, String> rubricNames = rubrics.findAllById(result.getContent().stream().map(Question::getRubricId)
                        .filter(Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Rubric::getId, Rubric::getName));
        return PageResponse.of(result, q -> new QuestionSummaryDto(q.getId(), q.getCourseId(), q.getTopicId(),
                topicNames.get(q.getTopicId()), q.getContent(), q.getBloomLevel(), q.getLanguage(), q.getStatus(),
                q.getOrigin(), q.getRubricId(), q.getRubricId() == null ? null : rubricNames.get(q.getRubricId()),
                q.isLocked(), q.getOwnerId(), q.getVersion(), q.getCreatedAt(), q.getUpdatedAt(), q.getPublishedAt()));
    }

    @Transactional(readOnly = true)
    public QuestionDto get(UUID questionId, User user) {
        Question q = load(questionId);
        access.read(q.getCourseId(), user, NOT_FOUND, "Question not found");
        return toDto(q);
    }

    // ---------------------------------------------------------------- write

    /** AC-Q1: manual question → DRAFT, MANUAL, owner = caller; language defaults to the course language (BR-Q9). */
    public QuestionDto create(UUID courseId, CreateQuestionRequest request, User user) {
        access.writeCourse(courseId, user);
        requireTopicOfCourse(request.topicId(), courseId);
        requireRubricOfCourse(request.rubricId(), courseId);
        Instant now = Instant.now(clock);
        Question q = new Question();
        q.setCourseId(courseId);
        q.setTopicId(request.topicId());
        q.setContent(request.content().strip());
        q.setReferenceAnswer(TopicService.blankToNull(request.referenceAnswer()));
        q.setBloomLevel(request.bloomLevel());
        q.setLanguage(request.language() != null ? request.language() : courseLanguage(courseId));
        q.setRubricId(request.rubricId());
        q.setStatus(QuestionStatus.DRAFT);
        q.setOrigin(QuestionOrigin.MANUAL);
        q.setOwnerId(user.getUserId());
        q.setCreatedAt(now);
        q.setUpdatedAt(now);
        return toDto(questions.saveAndFlush(q));
    }

    /** Owner edit with optimistic locking (E6, E7, BR-Q6, BR-Q11). */
    public QuestionDto update(UUID questionId, UpdateQuestionRequest request, User user) {
        Question q = loadOwned(questionId, user);
        if (q.getStatus() != QuestionStatus.DRAFT && q.getStatus() != QuestionStatus.PUBLISHED) {
            throw invalidState(q, "edited");
        }
        if (q.isLocked()) {
            throw locked();
        }
        if (!request.version().equals(q.getVersion())) {
            throw versionConflict();
        }
        requireTopicOfCourse(request.topicId(), q.getCourseId());
        requireRubricOfCourse(request.rubricId(), q.getCourseId());
        q.setTopicId(request.topicId());
        q.setContent(request.content().strip());
        q.setReferenceAnswer(TopicService.blankToNull(request.referenceAnswer()));
        q.setBloomLevel(request.bloomLevel());
        if (request.language() != null) {
            q.setLanguage(request.language());
        }
        q.setRubricId(request.rubricId());
        q.setUpdatedAt(Instant.now(clock));
        if (q.getStatus() == QuestionStatus.PUBLISHED) {
            List<String> errors = publishErrors(q);
            if (!errors.isEmpty()) {
                throw new PublishValidationException(errors);
            }
        }
        return toDto(flush(q));
    }

    public QuestionDto discard(UUID questionId, User user) {
        Question q = loadOwned(questionId, user);
        if (q.getStatus() != QuestionStatus.DRAFT) {
            throw invalidState(q, "discarded (only drafts can be discarded; unpublish first)");
        }
        q.setStatus(QuestionStatus.DISCARDED);
        q.setDiscardedAt(Instant.now(clock));
        q.setUpdatedAt(Instant.now(clock));
        return toDto(flush(q));
    }

    public QuestionDto restore(UUID questionId, User user) {
        Question q = loadOwned(questionId, user);
        if (q.getStatus() != QuestionStatus.DISCARDED) {
            throw invalidState(q, "restored (only discarded questions can be restored)");
        }
        q.setStatus(QuestionStatus.DRAFT);
        q.setDiscardedAt(null);
        q.setUpdatedAt(Instant.now(clock));
        return toDto(flush(q));
    }

    /** PUBLISHED → DRAFT when unlocked and not assigned to a session that is about to run or running. */
    public QuestionDto unpublish(UUID questionId, User user) {
        Question q = loadOwned(questionId, user);
        if (q.getStatus() != QuestionStatus.PUBLISHED) {
            throw invalidState(q, "unpublished (it is not published)");
        }
        if (q.isLocked()) {
            throw locked();
        }
        Boolean inUse = jdbc.queryForObject("""
                select exists(select 1 from session_questions sq join exam_sessions s on s.session_id = sq.session_id
                              where sq.question_id = ? and s.status in ('SCHEDULED', 'IN_PROGRESS', 'INTERRUPTED'))""",
                Boolean.class, questionId);
        if (Boolean.TRUE.equals(inUse)) {
            throw ApiException.conflict("QUESTION_IN_USE",
                    "The question is assigned to a scheduled or running exam session");
        }
        q.setStatus(QuestionStatus.DRAFT);
        q.setPublishedAt(null);
        q.setPublishedBy(null);
        q.setUpdatedAt(Instant.now(clock));
        return toDto(flush(q));
    }

    /** New DRAFT copy of a locked PUBLISHED question; publishing it retires the predecessor (BR-Q8). */
    public QuestionDto successor(UUID questionId, User user) {
        Question source = loadOwned(questionId, user);
        if (source.getStatus() != QuestionStatus.PUBLISHED || !source.isLocked()) {
            throw ApiException.conflict("QUESTION_NOT_LOCKED",
                    "Only a locked, published question needs a successor; edit this question directly");
        }
        if (questions.hasActiveSuccessor(questionId)) {
            throw ApiException.conflict("SUCCESSOR_EXISTS", "This question already has a successor");
        }
        Instant now = Instant.now(clock);
        Question copy = new Question();
        copy.setCourseId(source.getCourseId());
        copy.setTopicId(source.getTopicId());
        copy.setContent(source.getContent());
        copy.setReferenceAnswer(source.getReferenceAnswer());
        copy.setBloomLevel(source.getBloomLevel());
        copy.setLanguage(source.getLanguage());
        copy.setRubricId(source.getRubricId());
        copy.setOrigin(source.getOrigin());
        copy.setAiOriginalContent(source.getAiOriginalContent());
        copy.setAiOriginalReferenceAnswer(source.getAiOriginalReferenceAnswer());
        copy.setAiSuggestedBloom(source.getAiSuggestedBloom());
        copy.setGenerationRequestId(source.getGenerationRequestId());
        copy.setStatus(QuestionStatus.DRAFT);
        copy.setOwnerId(user.getUserId());
        copy.setSupersedesQuestionId(source.getId());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        copy = questions.saveAndFlush(copy);
        for (QuestionSource s : sources.findByQuestionId(source.getId())) {
            QuestionSource c = new QuestionSource();
            c.setQuestionId(copy.getId());
            c.setMaterialId(s.getMaterialId());
            c.setChunkId(s.getChunkId());
            c.setLocationLabel(s.getLocationLabel());
            c.setExcerpt(s.getExcerpt());
            sources.save(c);
        }
        jdbc.update("update questions set ai_suggested_rubric = (select ai_suggested_rubric from questions where question_id = ?)"
                + " where question_id = ?", source.getId(), copy.getId());
        return toDto(copy);
    }

    /** Bulk publish with partial success (E3); validation per INV-02 / BR-Q3. */
    public PublishResult publish(List<UUID> questionIds, User user) {
        ContentAccess.requireStaff(user);
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("ADMIN_READ_ONLY", "Administrators can read but not change course content");
        }
        List<UUID> published = new ArrayList<>();
        List<PublishFailure> failed = new ArrayList<>();
        Instant now = Instant.now(clock);
        for (UUID id : new LinkedHashSet<>(questionIds)) {
            Question q = questions.findById(id).orElse(null);
            if (q == null || !access.canRead(q.getCourseId(), user)) {
                failed.add(new PublishFailure(id, List.of(NOT_FOUND)));
                continue;
            }
            if (!q.getOwnerId().equals(user.getUserId())) {
                failed.add(new PublishFailure(id, List.of("NOT_QUESTION_OWNER")));
                continue;
            }
            if (q.getStatus() != QuestionStatus.DRAFT) {
                failed.add(new PublishFailure(id, List.of("INVALID_QUESTION_STATE")));
                continue;
            }
            List<String> errors = publishErrors(q);
            if (!errors.isEmpty()) {
                failed.add(new PublishFailure(id, errors));
                continue;
            }
            q.setStatus(QuestionStatus.PUBLISHED);
            q.setPublishedAt(now);
            q.setPublishedBy(user.getUserId());
            q.setUpdatedAt(now);
            if (q.getSupersedesQuestionId() != null) {
                questions.findById(q.getSupersedesQuestionId())
                        .filter(prev -> prev.getStatus() == QuestionStatus.PUBLISHED)
                        .ifPresent(prev -> {
                            prev.setStatus(QuestionStatus.RETIRED);
                            prev.setUpdatedAt(now);
                        });
            }
            published.add(id);
        }
        questions.flush();
        return new PublishResult(published, failed);
    }

    /** Hard delete of a never-used DRAFT (15 §5.2); everything else must be discarded instead. */
    public void delete(UUID questionId, User user) {
        Question q = loadOwned(questionId, user);
        Boolean used = jdbc.queryForObject("""
                select exists(select 1 from session_questions where question_id = ?)
                    or exists(select 1 from viva_exam_questions where question_id = ?)
                    or exists(select 1 from exam_turns where question_id = ?)
                    or exists(select 1 from question_grades where question_id = ?)
                    or exists(select 1 from questions where supersedes_question_id = ?)""",
                Boolean.class, questionId, questionId, questionId, questionId, questionId);
        if (q.getStatus() != QuestionStatus.DRAFT || Boolean.TRUE.equals(used)) {
            throw ApiException.conflict("QUESTION_IN_USE",
                    "Only a draft that was never used can be deleted; discard the question instead");
        }
        sources.deleteByQuestion(questionId);
        jdbc.update("delete from question_tags where question_id = ?", questionId);
        questions.delete(q);
    }

    // ---------------------------------------------------------------- rules

    /** INV-02: per-question publish error codes (04 §5 step 7). */
    List<String> publishErrors(Question q) {
        List<String> errors = new ArrayList<>();
        if (q.getContent() == null || q.getContent().isBlank()) {
            errors.add("CONTENT_EMPTY");
        }
        if (q.getReferenceAnswer() == null || q.getReferenceAnswer().isBlank()) {
            errors.add("REFERENCE_ANSWER_MISSING");
        }
        if (q.getBloomLevel() == null) {
            errors.add("BLOOM_MISSING");
        }
        Rubric rubric = q.getRubricId() == null ? null : rubrics.findById(q.getRubricId()).orElse(null);
        if (rubric == null) {
            errors.add("RUBRIC_MISSING");
        } else if (!rubric.isValidForPublish()) {
            errors.add("RUBRIC_WEIGHTS_NOT_100");
        }
        return errors;
    }

    private Question loadOwned(UUID questionId, User user) {
        Question q = load(questionId);
        access.write(q.getCourseId(), user, NOT_FOUND, "Question not found");
        if (!q.getOwnerId().equals(user.getUserId())) {
            throw ApiException.forbidden("NOT_QUESTION_OWNER", "Only the owner of the question can change it");
        }
        return q;
    }

    private Question load(UUID questionId) {
        return questions.findById(questionId).orElseThrow(() -> ApiException.notFound(NOT_FOUND, "Question not found"));
    }

    private Question flush(Question q) {
        try {
            return questions.saveAndFlush(q);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw versionConflict();
        }
    }

    private void requireTopicOfCourse(UUID topicId, UUID courseId) {
        if (topics.findById(topicId).filter(t -> t.getCourseId().equals(courseId)).isEmpty()) {
            throw ApiException.unprocessable("TOPIC_NOT_IN_COURSE", "The topic does not belong to this course");
        }
    }

    private void requireRubricOfCourse(UUID rubricId, UUID courseId) {
        if (rubricId != null && rubrics.findById(rubricId).filter(r -> r.getCourseId().equals(courseId)).isEmpty()) {
            throw ApiException.unprocessable("RUBRIC_NOT_IN_COURSE", "The rubric does not belong to this course");
        }
    }

    private Language courseLanguage(UUID courseId) {
        String lang = jdbc.queryForObject("select default_language from courses where course_id = ?", String.class, courseId);
        return lang == null ? Language.VI : Language.valueOf(lang);
    }

    private static ApiException locked() {
        return ApiException.conflict("QUESTION_LOCKED",
                "The question was used in a completed session and cannot change; create a successor instead");
    }

    private static ApiException versionConflict() {
        return ApiException.conflict("VERSION_CONFLICT", "The question was changed by someone else; reload and retry");
    }

    private static ApiException invalidState(Question q, String action) {
        return ApiException.conflict("INVALID_QUESTION_STATE",
                "A " + q.getStatus() + " question cannot be " + action);
    }

    private static boolean notEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    // ---------------------------------------------------------------- mapping

    private QuestionDto toDto(Question q) {
        String topicName = topics.findById(q.getTopicId()).map(Topic::getName).orElse(null);
        QuestionRubricDto rubric = q.getRubricId() == null ? null : rubrics.findById(q.getRubricId())
                .map(r -> new QuestionRubricDto(r.getId(), r.getName(), r.isLocked(), r.totalWeight(),
                        RubricService.criteria(r)))
                .orElse(null);
        List<QuestionSource> rows = sources.findByQuestionId(q.getId());
        Map<UUID, String> materialNames = materials.findAllById(rows.stream().map(QuestionSource::getMaterialId)
                        .collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CourseMaterial::getId, CourseMaterial::getFileName));
        List<SourceDto> sourceDtos = rows.stream().map(s -> new SourceDto(s.getId(), s.getMaterialId(),
                materialNames.get(s.getMaterialId()), s.getChunkId(), s.getLocationLabel(), s.getExcerpt())).toList();
        AiDto ai = null;
        if (q.getOrigin() == QuestionOrigin.AI_GENERATED || q.getAiOriginalContent() != null) {
            ai = new AiDto(q.getAiOriginalContent(), q.getAiOriginalReferenceAnswer(), q.getAiSuggestedBloom(),
                    suggestedRubric(q.getId()), q.getGenerationRequestId());
        }
        return new QuestionDto(q.getId(), q.getCourseId(), q.getTopicId(), topicName, q.getContent(),
                q.getReferenceAnswer(), q.getBloomLevel(), q.getLanguage(), q.getStatus(), q.getOrigin(), rubric, ai,
                sourceDtos, q.isLocked(), q.getOwnerId(), q.getSupersedesQuestionId(), q.getVersion(), q.getCreatedAt(),
                q.getUpdatedAt(), q.getPublishedBy(), q.getPublishedAt(), q.getDiscardedAt());
    }

    private JsonNode suggestedRubric(UUID questionId) {
        List<String> raw = jdbc.queryForList(
                "select ai_suggested_rubric::text from questions where question_id = ? and ai_suggested_rubric is not null",
                String.class, questionId);
        return raw.isEmpty() ? null : JSON.readTree(raw.getFirst());
    }
}
