package swd392.group6.AIVES.exam;

import swd392.group6.AIVES.questionbank.BloomLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * {@code RANDOM_BALANCED} question draw for one student at check-in (D48, 07 §2, 15 §3). Pure and deterministic:
 * the same inputs and seed always give the same questions.
 * <p>
 * For every template row, each slot is filled from the best non-empty candidate tier of the row's pool, so overlap
 * is only accepted when the pool really cannot avoid it:
 * <ol>
 *   <li>not given to the previous examinee and not already received by this student in an earlier buổi thi</li>
 *   <li>only avoids the retake exclusion (warning {@code OVERLAP_UNAVOIDABLE})</li>
 *   <li>only avoids the previous examinee (warning {@code RETAKE_OVERLAP_UNAVOIDABLE})</li>
 *   <li>anything left in the row (both warnings)</li>
 * </ol>
 * Inside a tier: rows without a Bloom level spread over distinct levels first, then the question used least in this
 * buổi thi, then a seeded random tie-break. The chosen questions are finally ordered by Bloom level ascending.
 */
final class QuestionSelector {

    static final String POOL_TOO_SMALL = "POOL_TOO_SMALL";
    static final String POOL_SMALL = "POOL_SMALL";
    static final String OVERLAP_UNAVOIDABLE = "OVERLAP_UNAVOIDABLE";
    static final String RETAKE_OVERLAP_UNAVOIDABLE = "RETAKE_OVERLAP_UNAVOIDABLE";

    record Candidate(UUID questionId, UUID chapterId, BloomLevel bloomLevel) {
    }

    /** One template row; {@code null} chapter / Bloom means "any". */
    record Row(UUID chapterId, BloomLevel bloomLevel, int count) {

        boolean matches(Candidate c) {
            return (chapterId == null || chapterId.equals(c.chapterId())) && (bloomLevel == null || bloomLevel == c.bloomLevel());
        }
    }

    record RowShortage(int rowIndex, UUID chapterId, BloomLevel bloomLevel, int required, int available) {
    }

    record Warning(String code, UUID studentId, Integer rowIndex, String message) {
    }

    /** A drawn question and the template row it serves. */
    record Pick(UUID questionId, int rowIndex, BloomLevel bloomLevel) {
    }

    /** Either {@code picks} (asking order) or {@code shortages} is filled. */
    record Draw(List<Pick> picks, List<Warning> warnings, List<RowShortage> shortages) {

        boolean failed() {
            return !shortages.isEmpty();
        }
    }

    /** Pool coverage for publishing (D48): shortages make check-in impossible, warnings mean likely overlap. */
    record Coverage(List<RowShortage> shortages, List<Warning> warnings) {
    }

    private QuestionSelector() {
    }

    static Coverage coverage(Collection<Candidate> pool, List<Row> rows) {
        List<Candidate> sortedPool = sorted(pool);
        List<RowShortage> shortages = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int available = (int) sortedPool.stream().filter(row::matches).count();
            if (available < row.count()) {
                shortages.add(new RowShortage(i, row.chapterId(), row.bloomLevel(), row.count(), available));
            } else if (available < 2 * row.count()) {
                warnings.add(new Warning(POOL_SMALL, null, i, "Row " + (i + 1) + " has " + available
                        + " questions for " + row.count() + " per student; consecutive students may share questions"));
            }
        }
        // Rows that overlap (e.g. "chapter 1" and "chapter 1, APPLY") can still starve each other.
        if (shortages.isEmpty()) {
            Draw trial = draw(sortedPool, rows, null, Set.of(), Set.of(), Map.of(), 0L);
            shortages.addAll(trial.shortages());
        }
        return new Coverage(shortages, warnings);
    }

    /**
     * @param excluded questions this student already received in the original buổi thi (retake, D30) or earlier in
     *                 this attempt (replacement after a disconnect, D51)
     * @param previous questions of the attempt that checked in just before this one
     * @param usage    how often each question was already drawn in this buổi thi
     */
    static Draw draw(Collection<Candidate> pool, List<Row> rows, UUID studentId, Set<UUID> excluded, Set<UUID> previous,
                     Map<UUID, Integer> usage, long seed) {
        List<Candidate> sortedPool = sorted(pool);
        // Most specific rows first so a broad row cannot use up the questions a narrow row needs.
        List<Integer> rowOrder = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            rowOrder.add(i);
        }
        rowOrder.sort(Comparator.comparingInt((Integer i) -> -specificity(rows.get(i))).thenComparingInt(i -> i));

        Random random = new Random(seed);
        Set<UUID> ex = excluded == null ? Set.of() : excluded;
        Set<UUID> prev = previous == null ? Set.of() : previous;
        List<Pick> picked = new ArrayList<>();
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
                            .filter(c -> tierOf(c, prev, ex) == t)
                            .min(Comparator
                                    .comparingInt((Candidate c) -> row.bloomLevel() == null
                                            && coveredInRow.contains(c.bloomLevel()) ? 1 : 0)
                                    .thenComparingInt(c -> usage.getOrDefault(c.questionId(), 0))
                                    .thenComparingDouble(c -> tieBreak.get(c.questionId())))
                            .orElse(null);
                    bestTier = tier;
                }
                if (best == null) {
                    int available = (int) rowPool.stream().filter(c -> !pickedIds.contains(c.questionId())).count();
                    return new Draw(List.of(), List.of(), List.of(new RowShortage(rowIndex, row.chapterId(),
                            row.bloomLevel(), row.count(), available + slot)));
                }
                overlap |= bestTier == 1 || bestTier == 3;
                retakeOverlap |= bestTier == 2 || bestTier == 3;
                picked.add(new Pick(best.questionId(), rowIndex, best.bloomLevel()));
                pickedIds.add(best.questionId());
                coveredInRow.add(best.bloomLevel());
            }
        }
        List<Warning> warnings = new ArrayList<>();
        if (overlap) {
            warnings.add(new Warning(OVERLAP_UNAVOIDABLE, studentId, null,
                    "Shares a question with the previous examinee: the pool is too small"));
        }
        if (retakeOverlap) {
            warnings.add(new Warning(RETAKE_OVERLAP_UNAVOIDABLE, studentId, null,
                    "Gets a question already received before: the pool is too small"));
        }
        // Bloom ascending; the current order (random within the tiers) breaks ties.
        List<Pick> ordered = new ArrayList<>(picked);
        ordered.sort(Comparator.comparingInt(p -> p.bloomLevel() == null ? Integer.MAX_VALUE : p.bloomLevel().ordinal()));
        return new Draw(List.copyOf(ordered), warnings, List.of());
    }

    private static List<Candidate> sorted(Collection<Candidate> pool) {
        return pool.stream().sorted(Comparator.comparing(Candidate::questionId)).distinct().toList();
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
        return (row.chapterId() != null ? 1 : 0) + (row.bloomLevel() != null ? 1 : 0);
    }
}
