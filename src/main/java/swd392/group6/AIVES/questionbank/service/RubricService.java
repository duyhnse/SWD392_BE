package swd392.group6.AIVES.questionbank.service;

import java.util.ArrayList;

import swd392.group6.AIVES.questionbank.dto.PublishFailure;

import swd392.group6.AIVES.questionbank.dto.MoveRubricQuestionsResult;

import swd392.group6.AIVES.questionbank.dto.MoveRubricQuestionsRequest;

import swd392.group6.AIVES.questionbank.dto.RubricAssignResult;

import swd392.group6.AIVES.questionbank.dto.RubricAssignRequest;

import swd392.group6.AIVES.questionbank.domain.Rubric;
import swd392.group6.AIVES.questionbank.domain.RubricCriterion;
import swd392.group6.AIVES.questionbank.repository.QuestionRepository;
import swd392.group6.AIVES.questionbank.repository.RubricRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.questionbank.dto.CriterionDto;
import swd392.group6.AIVES.questionbank.dto.CriterionRequest;
import swd392.group6.AIVES.questionbank.dto.DuplicateRubricRequest;
import swd392.group6.AIVES.questionbank.dto.RubricDto;
import swd392.group6.AIVES.questionbank.dto.RubricRequest;
import swd392.group6.AIVES.user.User;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Course rubrics with weighted criteria (BR-Q3, BR-Q10, 15 §5.2). */
@Service
@RequiredArgsConstructor
@Transactional
public class RubricService {

    static final int MAX_CRITERIA = 10;
    static final BigDecimal MAX_SCORE = new BigDecimal("100");
    private static final UUID NO_ID = new UUID(0, 0);

    private final RubricRepository rubrics;
    private final QuestionRepository questions;
    private final ContentAccess access;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<RubricDto> list(UUID courseId, User user) {
        access.readCourse(courseId, user);
        List<Rubric> list = rubrics.findByCourseIdOrderByNameAsc(courseId);
        Map<UUID, Long> counts = new HashMap<>();
        if (!list.isEmpty()) {
            for (Object[] row : questions.countByRubric(list.stream().map(Rubric::getId).toList())) {
                counts.put((UUID) row[0], (Long) row[1]);
            }
        }
        return list.stream().map(r -> toDto(r, counts.getOrDefault(r.getId(), 0L))).toList();
    }

    @Transactional(readOnly = true)
    public RubricDto get(UUID rubricId, User user) {
        Rubric rubric = load(rubricId);
        access.read(rubric.getCourseId(), user, "RUBRIC_NOT_FOUND", "Rubric not found");
        return toDto(rubric, questions.countByRubricId(rubricId));
    }

    public RubricDto create(UUID courseId, RubricRequest request, User user) {
        access.writeCourse(courseId, user);
        validateCriteria(request.criteria());
        String name = request.name().trim();
        requireFreeName(courseId, name, NO_ID);
        Instant now = Instant.now(clock);
        Rubric rubric = new Rubric();
        rubric.setCourseId(courseId);
        rubric.setName(name);
        rubric.setDescription(TopicService.blankToNull(request.description()));
        rubric.setCreatedBy(user.getUserId());
        rubric.setCreatedAt(now);
        rubric.setUpdatedAt(now);
        addCriteria(rubric, request.criteria());
        return toDto(rubrics.save(rubric), 0);
    }

    /** Replaces name, description and the whole criteria list. */
    public RubricDto update(UUID rubricId, RubricRequest request, User user) {
        Rubric rubric = load(rubricId);
        access.write(rubric.getCourseId(), user, "RUBRIC_NOT_FOUND", "Rubric not found");
        requireUnlocked(rubric);
        validateCriteria(request.criteria());
        String name = request.name().trim();
        requireFreeName(rubric.getCourseId(), name, rubric.getId());
        rubric.setName(name);
        rubric.setDescription(TopicService.blankToNull(request.description()));
        rubric.setUpdatedAt(Instant.now(clock));
        rubric.getCriteria().clear();
        rubrics.flush();
        addCriteria(rubric, request.criteria());
        rubrics.flush();
        return toDto(rubric, questions.countByRubricId(rubricId));
    }

    public void delete(UUID rubricId, User user) {
        Rubric rubric = load(rubricId);
        access.write(rubric.getCourseId(), user, "RUBRIC_NOT_FOUND", "Rubric not found");
        Boolean usedByTemplate = jdbc.queryForObject("""
                select exists(select 1 from exam_templates where rubric_id = ?)
                    or exists(select 1 from exam_template_items where rubric_id = ?)""", Boolean.class, rubricId, rubricId);
        if (questions.existsByRubricId(rubricId) || criteriaScored(rubricId) || Boolean.TRUE.equals(usedByTemplate)) {
            throw ApiException.conflict("RUBRIC_IN_USE", "The rubric is attached to questions or exam templates; detach it first");
        }
        rubrics.delete(rubric);
    }

    /** Copy (also of a locked rubric) under a new name; the copy is unlocked and unused. */
    public RubricDto duplicate(UUID rubricId, DuplicateRubricRequest request, User user) {
        Rubric source = load(rubricId);
        access.write(source.getCourseId(), user, "RUBRIC_NOT_FOUND", "Rubric not found");
        String name = request == null || request.name() == null || request.name().isBlank()
                ? copyName(source) : request.name().trim();
        requireFreeName(source.getCourseId(), name, NO_ID);
        Instant now = Instant.now(clock);
        Rubric copy = new Rubric();
        copy.setCourseId(source.getCourseId());
        copy.setName(name);
        copy.setDescription(source.getDescription());
        copy.setCreatedBy(user.getUserId());
        copy.setCreatedAt(now);
        copy.setUpdatedAt(now);
        for (RubricCriterion c : source.getCriteria()) {
            copy.addCriterion(new RubricCriterion(c.getName(), c.getDescription(), c.getMaxScore(), c.getWeightPercent(),
                    c.getSortOrder()));
        }
        return toDto(rubrics.save(copy), 0);
    }

    private String copyName(Rubric source) {
        String base = source.getName().length() > 140 ? source.getName().substring(0, 140) : source.getName();
        String candidate = base + " (copy)";
        for (int i = 2; rubrics.nameTaken(source.getCourseId(), candidate, NO_ID); i++) {
            candidate = base + " (copy " + i + ")";
        }
        return candidate;
    }

    private boolean criteriaScored(UUID rubricId) {
        Boolean used = jdbc.queryForObject("""
                select exists(select 1 from criterion_scores cs join rubric_criteria rc on rc.criterion_id = cs.criterion_id
                              where rc.rubric_id = ?)""", Boolean.class, rubricId);
        return Boolean.TRUE.equals(used);
    }

    private void requireUnlocked(Rubric rubric) {
        if (rubric.isLocked() || criteriaScored(rubric.getId())) {
            throw ApiException.conflict("RUBRIC_LOCKED",
                    "The rubric was used in an exam attempt and cannot change; duplicate it instead");
        }
    }

    private void requireFreeName(UUID courseId, String name, UUID excludeId) {
        if (rubrics.nameTaken(courseId, name, excludeId)) {
            throw ApiException.conflict("RUBRIC_NAME_EXISTS", "A rubric named \"" + name + "\" already exists in this course");
        }
    }

    /** INV-01 + BR-Q3: 1–10 criteria, 0 < maxScore ≤ 100, 0 < weight ≤ 100, both with at most 2 decimals, Σ weight = 100 ± 0.01. */
    static void validateCriteria(List<CriterionRequest> criteria) {
        if (criteria.isEmpty() || criteria.size() > MAX_CRITERIA) {
            throw ApiException.unprocessable("RUBRIC_CRITERIA_COUNT",
                    "A rubric needs between 1 and " + MAX_CRITERIA + " criteria");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (CriterionRequest c : criteria) {
            if (c.maxScore().signum() <= 0 || c.maxScore().compareTo(MAX_SCORE) > 0 || decimals(c.maxScore()) > 2) {
                throw ApiException.unprocessable("RUBRIC_MAX_SCORE_INVALID",
                        "Criterion \"" + c.name() + "\": max score must be greater than 0 and at most 100, with at most 2 decimals");
            }
            if (c.weightPercent().signum() <= 0 || c.weightPercent().compareTo(Rubric.HUNDRED) > 0 || decimals(c.weightPercent()) > 2) {
                throw ApiException.unprocessable("RUBRIC_WEIGHT_INVALID",
                        "Criterion \"" + c.name() + "\": weight must be greater than 0 and at most 100, with at most 2 decimals");
            }
            total = total.add(c.weightPercent());
        }
        if (total.subtract(Rubric.HUNDRED).abs().compareTo(Rubric.WEIGHT_TOLERANCE) > 0) {
            throw ApiException.unprocessable("RUBRIC_WEIGHTS_NOT_100",
                    "Weights must total exactly 100%; they total " + total.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%");
        }
    }

    private static int decimals(BigDecimal v) {
        return Math.max(0, v.stripTrailingZeros().scale());
    }

    private static void addCriteria(Rubric rubric, List<CriterionRequest> criteria) {
        for (int i = 0; i < criteria.size(); i++) {
            CriterionRequest c = criteria.get(i);
            rubric.addCriterion(new RubricCriterion(c.name().trim(), c.description() == null ? "" : c.description().trim(),
                    c.maxScore().setScale(2, RoundingMode.HALF_UP), c.weightPercent().setScale(2, RoundingMode.HALF_UP),
                    c.sortOrder() == null ? i : c.sortOrder()));
        }
    }

    /**
     * Attach a rubric to many questions of its course (rubric by question type). A drawn (locked) question keeps its
     * rubric — the attempt graded with it is evidence; a discarded or retired one is not touched.
     */
    @Transactional
    public RubricAssignResult assign(UUID courseId, RubricAssignRequest request, User user) {
        access.writeCourse(courseId, user);
        Rubric rubric = load(request.rubricId());
        if (!rubric.getCourseId().equals(courseId)) {
            throw ApiException.unprocessable("RUBRIC_NOT_IN_COURSE", "The rubric does not belong to this course");
        }
        List<UUID> updated = new ArrayList<>();
        List<PublishFailure> failed = new ArrayList<>();
        java.sql.Timestamp now = java.sql.Timestamp.from(Instant.now(clock));
        for (UUID id : new java.util.LinkedHashSet<>(request.questionIds())) {
            List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "select status, is_locked from questions where question_id = ? and course_id = ?", id, courseId);
            if (rows.isEmpty()) {
                failed.add(new PublishFailure(id, List.of("QUESTION_NOT_FOUND")));
                continue;
            }
            String status = (String) rows.getFirst().get("status");
            if (Boolean.TRUE.equals(rows.getFirst().get("is_locked"))) {
                failed.add(new PublishFailure(id, List.of("QUESTION_LOCKED")));
                continue;
            }
            if (!"DRAFT".equals(status) && !"PUBLISHED".equals(status)) {
                failed.add(new PublishFailure(id, List.of("INVALID_QUESTION_STATE")));
                continue;
            }
            jdbc.update("update questions set rubric_id = ?, updated_at = ?, version = version + 1 where question_id = ?",
                    rubric.getId(), now, id);
            updated.add(id);
        }
        return new RubricAssignResult(updated, failed);
    }

    /**
     * After "duplicate" of a locked rubric: every draft / published question that is not locked moves to the new
     * version; locked ones keep the rubric they were graded with (successor to change them).
     */
    @Transactional
    public MoveRubricQuestionsResult moveQuestions(UUID rubricId, MoveRubricQuestionsRequest request, User user) {
        Rubric source = load(rubricId);
        access.write(source.getCourseId(), user, "RUBRIC_NOT_FOUND", "Rubric not found");
        Rubric target = load(request.targetRubricId());
        if (!target.getCourseId().equals(source.getCourseId())) {
            throw ApiException.unprocessable("RUBRIC_NOT_IN_COURSE", "The target rubric does not belong to this course");
        }
        if (target.getId().equals(source.getId())) {
            throw ApiException.unprocessable("SAME_RUBRIC", "Choose another rubric");
        }
        int moved = jdbc.update("""
                update questions set rubric_id = ?, updated_at = ?, version = version + 1
                where rubric_id = ? and not is_locked and status in ('DRAFT', 'PUBLISHED')""",
                target.getId(), java.sql.Timestamp.from(Instant.now(clock)), source.getId());
        Integer kept = jdbc.queryForObject("select count(*) from questions where rubric_id = ? and is_locked", Integer.class,
                source.getId());
        return new MoveRubricQuestionsResult(moved, kept == null ? 0 : kept);
    }

    private Rubric load(UUID rubricId) {
        return rubrics.findById(rubricId).orElseThrow(() -> ApiException.notFound("RUBRIC_NOT_FOUND", "Rubric not found"));
    }

    static List<CriterionDto> criteria(Rubric r) {
        return r.getCriteria().stream()
                .sorted(java.util.Comparator.comparingInt(RubricCriterion::getSortOrder).thenComparing(RubricCriterion::getName))
                .map(c -> new CriterionDto(c.getId(), c.getName(), c.getDescription(), c.getMaxScore(),
                        c.getWeightPercent(), c.getSortOrder()))
                .toList();
    }

    static RubricDto toDto(Rubric r, long questionCount) {
        return new RubricDto(r.getId(), no(r.getDisplayNo()), r.getCourseId(), r.getName(), r.getDescription(), r.isLocked(), r.totalWeight(),
                questionCount, criteria(r), r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedAt());
    }

    static long no(Long displayNo) {
        return displayNo == null ? 0 : displayNo;
    }
}
