package swd392.group6.AIVES.exam;

import swd392.group6.AIVES.questionbank.BloomLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * {@code RANDOM_BALANCED} question selection with an optional blueprint (07 §2, 15 §3). Pure and deterministic:
 * the same inputs and seed always give the same assignment.
 * <p>
 * For every student (in examination order) and every blueprint row, each slot is filled from the best non-empty
 * candidate tier of the row's pool, so overlap is only accepted when the pool really cannot avoid it:
 * <ol>
 *   <li>not given to the previous examinee and not already received by this student in an earlier buổi thi</li>
 *   <li>only avoids the retake exclusion (warning {@code OVERLAP_UNAVOIDABLE})</li>
 *   <li>only avoids the previous examinee (warning {@code RETAKE_OVERLAP_UNAVOIDABLE})</li>
 *   <li>anything left in the row (both warnings)</li>
 * </ol>
 * Inside a tier: rows without a Bloom level spread over distinct levels first, then least-used question, then a
 * seeded random tie-break. The chosen questions are finally ordered by Bloom level ascending.
 */
final class QuestionSelector {

    static final String POOL_TOO_SMALL = "POOL_TOO_SMALL";
    static final String POOL_SMALL = "POOL_SMALL";
    static final String OVERLAP_UNAVOIDABLE = "OVERLAP_UNAVOIDABLE";
    static final String RETAKE_OVERLAP_UNAVOIDABLE = "RETAKE_OVERLAP_UNAVOIDABLE";

    record Candidate(UUID questionId, UUID topicId, BloomLevel bloomLevel) {
    }

    /** One blueprint row; {@code null} topic / Bloom means "any". */
    record Row(UUID topicId, BloomLevel bloomLevel, int count) {

        boolean matches(Candidate c) {
            return (topicId == null || topicId.equals(c.topicId())) && (bloomLevel == null || bloomLevel == c.bloomLevel());
        }
    }

    /** @param excluded questions this student already received in the original buổi thi (retake, D30) */
    record Examinee(UUID studentId, Set<UUID> excluded) {
    }

    record RowShortage(int rowIndex, UUID topicId, BloomLevel bloomLevel, int required, int available) {
    }

    record Warning(String code, UUID studentId, Integer rowIndex, String message) {
    }

    /** Either {@code assignments} (studentId → ordered question ids) or {@code shortages} is filled. */
    record Result(Map<UUID, List<UUID>> assignments, List<Warning> warnings, List<RowShortage> shortages) {

        boolean failed() {
            return !shortages.isEmpty();
        }
    }

    private QuestionSelector() {
    }

    static Result select(Collection<Candidate> pool, List<Row> rows, List<Examinee> examinees, long seed) {
        List<Candidate> sortedPool = pool.stream()
                .sorted(Comparator.comparing(Candidate::questionId)).distinct().toList();

        List<RowShortage> shortages = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int available = (int) sortedPool.stream().filter(row::matches).count();
            if (available < row.count()) {
                shortages.add(new RowShortage(i, row.topicId(), row.bloomLevel(), row.count(), available));
            } else if (available < 2 * row.count()) {
                warnings.add(new Warning(POOL_SMALL, null, i, "Row " + (i + 1) + " has " + available
                        + " questions for " + row.count() + " per student; consecutive students may share questions"));
            }
        }
        if (!shortages.isEmpty()) {
            return new Result(Map.of(), List.of(), shortages);
        }

        // Most specific rows first so a broad row cannot use up the questions a narrow row needs.
        List<Integer> rowOrder = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            rowOrder.add(i);
        }
        rowOrder.sort(Comparator.comparingInt((Integer i) -> -specificity(rows.get(i))).thenComparingInt(i -> i));

        Random random = new Random(seed);
        Map<UUID, Integer> usage = new HashMap<>();
        Set<UUID> previous = Set.of();
        Map<UUID, List<UUID>> assignments = new LinkedHashMap<>();

        for (Examinee examinee : examinees) {
            Set<UUID> excluded = examinee.excluded() == null ? Set.of() : examinee.excluded();
            Set<UUID> prev = previous;
            List<Candidate> picked = new ArrayList<>();
            Set<UUID> pickedIds = new HashSet<>();
            boolean overlap = false;
            boolean retakeOverlap = false;
            for (int rowIndex : rowOrder) {
                Row row = rows.get(rowIndex);
                Map<UUID, Double> tieBreak = new HashMap<>();
                List<Candidate> rowPool = new ArrayList<>();
                for (Candidate c : sortedPool) {
                    if (row.matches(c)) {
                        rowPool.add(c);
                        tieBreak.put(c.questionId(), random.nextDouble());
                    }
                }
                Set<BloomLevel> coveredInRow = new HashSet<>();
                for (int slot = 0; slot < row.count(); slot++) {
                    Candidate best = null;
                    int bestTier = -1;
                    for (int tier = 0; tier < 4 && best == null; tier++) {
                        final int t = tier;
                        best = rowPool.stream()
                                .filter(c -> !pickedIds.contains(c.questionId()))
                                .filter(c -> tierOf(c, prev, excluded) == t)
                                .min(Comparator
                                        .comparingInt((Candidate c) -> row.bloomLevel() == null
                                                && coveredInRow.contains(c.bloomLevel()) ? 1 : 0)
                                        .thenComparingInt(c -> usage.getOrDefault(c.questionId(), 0))
                                        .thenComparingDouble(c -> tieBreak.get(c.questionId())))
                                .orElse(null);
                        bestTier = tier;
                    }
                    if (best == null) {
                        // Overlapping rows used up this row's questions for this student.
                        int available = (int) rowPool.stream().filter(c -> !pickedIds.contains(c.questionId())).count();
                        return new Result(Map.of(), List.of(), List.of(new RowShortage(rowIndex, row.topicId(),
                                row.bloomLevel(), row.count(), available + slot)));
                    }
                    overlap |= bestTier == 1 || bestTier == 3;
                    retakeOverlap |= bestTier == 2 || bestTier == 3;
                    picked.add(best);
                    pickedIds.add(best.questionId());
                    coveredInRow.add(best.bloomLevel());
                }
            }
            if (overlap) {
                warnings.add(new Warning(OVERLAP_UNAVOIDABLE, examinee.studentId(), null,
                        "Shares a question with the previous examinee: the pool is too small"));
            }
            if (retakeOverlap) {
                warnings.add(new Warning(RETAKE_OVERLAP_UNAVOIDABLE, examinee.studentId(), null,
                        "Gets a question already received in the original exam: the pool is too small"));
            }
            // Bloom ascending; the current order (random within the tiers) breaks ties.
            List<Candidate> ordered = new ArrayList<>(picked);
            ordered.sort(Comparator.comparingInt(c -> c.bloomLevel() == null ? Integer.MAX_VALUE : c.bloomLevel().ordinal()));
            List<UUID> ids = ordered.stream().map(Candidate::questionId).toList();
            ids.forEach(id -> usage.merge(id, 1, Integer::sum));
            assignments.put(examinee.studentId(), ids);
            previous = new LinkedHashSet<>(ids);
        }
        return new Result(assignments, warnings, List.of());
    }

    private static int tierOf(Candidate c, Set<UUID> previous, Set<UUID> excluded) {
        boolean prev = previous.contains(c.questionId());
        boolean ex = excluded.contains(c.questionId());
        if (!prev && !ex) {
            return 0;
        }
        if (prev && !ex) {
            return 1;
        }
        return !prev ? 2 : 3;
    }

    private static int specificity(Row row) {
        return (row.topicId() != null ? 1 : 0) + (row.bloomLevel() != null ? 1 : 0);
    }
}
