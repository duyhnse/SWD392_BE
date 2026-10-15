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
 * Random question draw for one student at check-in (D48, 15 §3). Students may get the same questions — pools are
 * usually too small to avoid it — so the draw only <em>spreads</em> use: inside each template row the questions used
 * least in this buổi thi come first, ties are broken by a seeded random (stored on the attempt, reproducible), and rows
 * without a Bloom level spread over distinct levels. The result is ordered by Bloom level ascending.
 */
final class QuestionSelector {

    static final String POOL_TOO_SMALL = "POOL_TOO_SMALL";

    record Candidate(UUID questionId, UUID topicId, BloomLevel bloomLevel) {
    }

    /** One template row; {@code null} topic / Bloom means "any". */
    record Row(UUID topicId, BloomLevel bloomLevel, int count) {

        boolean matches(Candidate c) {
            return (topicId == null || topicId.equals(c.topicId())) && (bloomLevel == null || bloomLevel == c.bloomLevel());
        }
    }

    record RowShortage(int rowIndex, UUID topicId, BloomLevel bloomLevel, int required, int available) {
    }

    /** A drawn question and the template row it serves. */
    record Pick(UUID questionId, int rowIndex, BloomLevel bloomLevel) {
    }

    /** Either {@code picks} (asking order) or {@code shortages} is filled. */
    record Draw(List<Pick> picks, List<RowShortage> shortages) {

        boolean failed() {
            return !shortages.isEmpty();
        }
    }

    private QuestionSelector() {
    }

    /** Rows the pool cannot serve (publishing and check-in refuse them with {@code POOL_TOO_SMALL}). */
    static List<RowShortage> shortages(Collection<Candidate> pool, List<Row> rows) {
        List<RowShortage> shortages = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int available = (int) pool.stream().distinct().filter(row::matches).count();
            if (available < row.count()) {
                shortages.add(new RowShortage(i, row.topicId(), row.bloomLevel(), row.count(), available));
            }
        }
        // Overlapping rows (e.g. "topic A" and "topic A, APPLY") can still starve each other.
        return shortages.isEmpty() ? draw(pool, rows, Set.of(), Map.of(), 0L).shortages() : shortages;
    }

    /**
     * @param excluded questions that must not be drawn (e.g. already in this attempt when one is replaced, D51)
     * @param usage    how often each question was already drawn in this buổi thi
     */
    static Draw draw(Collection<Candidate> pool, List<Row> rows, Set<UUID> excluded, Map<UUID, Integer> usage, long seed) {
        List<Candidate> sortedPool = pool.stream().filter(c -> !excluded.contains(c.questionId()))
                .sorted(Comparator.comparing(Candidate::questionId)).distinct().toList();
        // Most specific rows first so a broad row cannot use up the questions a narrow row needs.
        List<Integer> rowOrder = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            rowOrder.add(i);
        }
        rowOrder.sort(Comparator.comparingInt((Integer i) -> -specificity(rows.get(i))).thenComparingInt(i -> i));

        Random random = new Random(seed);
        List<Pick> picked = new ArrayList<>();
        Set<UUID> pickedIds = new HashSet<>();
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
                Candidate best = rowPool.stream()
                        .filter(c -> !pickedIds.contains(c.questionId()))
                        .min(Comparator
                                .comparingInt((Candidate c) -> row.bloomLevel() == null
                                        && coveredInRow.contains(c.bloomLevel()) ? 1 : 0)
                                .thenComparingInt(c -> usage.getOrDefault(c.questionId(), 0))
                                .thenComparingDouble(c -> tieBreak.get(c.questionId())))
                        .orElse(null);
                if (best == null) {
                    int available = (int) rowPool.stream().filter(c -> !pickedIds.contains(c.questionId())).count();
                    return new Draw(List.of(), List.of(new RowShortage(rowIndex, row.topicId(), row.bloomLevel(),
                            row.count(), available + slot)));
                }
                picked.add(new Pick(best.questionId(), rowIndex, best.bloomLevel()));
                pickedIds.add(best.questionId());
                coveredInRow.add(best.bloomLevel());
            }
        }
        List<Pick> ordered = new ArrayList<>(picked);
        ordered.sort(Comparator.comparingInt(p -> p.bloomLevel() == null ? Integer.MAX_VALUE : p.bloomLevel().ordinal()));
        return new Draw(List.copyOf(ordered), List.of());
    }

    private static int specificity(Row row) {
        return (row.topicId() != null ? 1 : 0) + (row.bloomLevel() != null ? 1 : 0);
    }
}
