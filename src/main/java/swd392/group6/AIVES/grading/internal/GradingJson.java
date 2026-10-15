package swd392.group6.AIVES.grading.internal;

import swd392.group6.AIVES.questionbank.QuestionBankApi.RubricSnapshot;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** (De)serialises the jsonb columns of the grading tables, which the entities keep as raw JSON text. */
final class GradingJson {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GradingJson() {
    }

    /** Shape of {@code question_grades.rubric_snapshot}. */
    record Snapshot(UUID rubricId, String name, List<SnapshotCriterion> criteria) {

        static Snapshot of(RubricSnapshot rubric) {
            return new Snapshot(rubric.rubricId(), rubric.name(), rubric.criteria().stream()
                    .map(c -> new SnapshotCriterion(c.criterionId(), c.name(), c.description(), c.maxScore(),
                            c.weightPercent(), c.sortOrder()))
                    .toList());
        }
    }

    record SnapshotCriterion(UUID criterionId, String name, String description, BigDecimal maxScore,
                             BigDecimal weightPercent, int sortOrder) {
    }

    /** Display-only answer signals of a thread (06 §4 step 2, BR-G5). */
    record Signals(int totalAnswerSec, int words, Integer wordsPerMin, int followupsUsed, int noAnswerTurns) {
    }

    static String write(Object value) {
        return value == null ? null : JSON.writeValueAsString(value);
    }

    static Snapshot snapshot(String json) {
        return JSON.readValue(json, Snapshot.class);
    }

    /** {@code attempt_questions.rubric_snapshot} (same shape as {@link Snapshot}); null for missing / empty JSON. */
    static RubricSnapshot readRubric(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Snapshot s = snapshot(json);
        if (s.rubricId() == null || s.criteria() == null) {
            return null;
        }
        return new RubricSnapshot(s.rubricId(), s.name(), s.criteria().stream()
                .map(c -> new RubricSnapshot.Criterion(c.criterionId(), c.name(), c.description(), c.maxScore(),
                        c.weightPercent(), c.sortOrder()))
                .toList());
    }

    static Signals signals(String json) {
        return json == null ? null : JSON.readValue(json, Signals.class);
    }

    static List<String> strings(String json) {
        return json == null ? List.of() : JSON.readValue(json, new TypeReference<List<String>>() { });
    }

    static List<UUID> uuids(String json) {
        return json == null ? List.of() : JSON.readValue(json, new TypeReference<List<UUID>>() { });
    }
}
