package swd392.group6.AIVES.questionbank.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.CriterionDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.CriterionRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.DuplicateRubricRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.RubricDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.RubricRequest;
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
    private static final BigDecimal MAX_NUMERIC_5_2 = new BigDecimal("999.99");
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
        rubric.setDescription(ChapterService.blankToNull(request.description()));
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
        rubric.setDescription(ChapterService.blankToNull(request.description()));
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

    /** INV-01 + BR-Q3: 1–10 criteria, 0 < maxScore, 0 < weight ≤ 100, Σ weight = 100 ± 0.01. */
    static void validateCriteria(List<CriterionRequest> criteria) {
        if (criteria.isEmpty() || criteria.size() > MAX_CRITERIA) {
            throw ApiException.unprocessable("RUBRIC_CRITERIA_COUNT",
                    "A rubric needs between 1 and " + MAX_CRITERIA + " criteria");
        }
        BigDecimal total = BigDecimal.ZERO;
        for (CriterionRequest c : criteria) {
            if (c.maxScore().signum() <= 0 || c.maxScore().compareTo(MAX_NUMERIC_5_2) > 0) {
                throw ApiException.unprocessable("RUBRIC_MAX_SCORE_INVALID",
                        "Criterion \"" + c.name() + "\": max score must be greater than 0 (and at most 999.99)");
            }
            if (c.weightPercent().signum() <= 0 || c.weightPercent().compareTo(Rubric.HUNDRED) > 0) {
                throw ApiException.unprocessable("RUBRIC_WEIGHT_INVALID",
                        "Criterion \"" + c.name() + "\": weight must be greater than 0 and at most 100");
            }
            total = total.add(c.weightPercent());
        }
        if (total.subtract(Rubric.HUNDRED).abs().compareTo(Rubric.WEIGHT_TOLERANCE) > 0) {
            throw ApiException.unprocessable("RUBRIC_WEIGHTS_NOT_100",
                    "Weights total " + total.setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
    }

    private static void addCriteria(Rubric rubric, List<CriterionRequest> criteria) {
        for (int i = 0; i < criteria.size(); i++) {
            CriterionRequest c = criteria.get(i);
            rubric.addCriterion(new RubricCriterion(c.name().trim(), c.description() == null ? "" : c.description().trim(),
                    c.maxScore().setScale(2, RoundingMode.HALF_UP), c.weightPercent().setScale(2, RoundingMode.HALF_UP),
                    c.sortOrder() == null ? i : c.sortOrder()));
        }
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
        return new RubricDto(r.getId(), r.getCourseId(), r.getName(), r.getDescription(), r.isLocked(), r.totalWeight(),
                questionCount, criteria(r), r.getCreatedBy(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
