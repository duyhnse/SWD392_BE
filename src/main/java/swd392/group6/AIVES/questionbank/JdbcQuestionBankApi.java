package swd392.group6.AIVES.questionbank;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.Language;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only implementation of {@link QuestionBankApi} over SQL, so other modules can be built in parallel.
 * The question bank owner may replace it with a JPA-based implementation (keep exactly one bean).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class JdbcQuestionBankApi implements QuestionBankApi {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<PublishedQuestion> findPublished(UUID courseId, Collection<UUID> topicIds, Collection<BloomLevel> bloomLevels,
                                                 Collection<UUID> restrictToIds) {
        StringBuilder sql = new StringBuilder("""
                select question_id, course_id, topic_id, bloom_level, language, content
                from questions where course_id = :courseId and status = 'PUBLISHED'""");
        MapSqlParameterSource p = new MapSqlParameterSource("courseId", courseId);
        if (topicIds != null && !topicIds.isEmpty()) {
            sql.append(" and topic_id in (:topicIds)");
            p.addValue("topicIds", topicIds);
        }
        if (bloomLevels != null && !bloomLevels.isEmpty()) {
            sql.append(" and bloom_level in (:blooms)");
            p.addValue("blooms", bloomLevels.stream().map(Enum::name).toList());
        }
        if (restrictToIds != null && !restrictToIds.isEmpty()) {
            sql.append(" and question_id in (:ids)");
            p.addValue("ids", restrictToIds);
        }
        sql.append(" order by question_id");
        return jdbc.query(sql.toString(), p, (rs, i) -> new PublishedQuestion(
                rs.getObject("question_id", UUID.class), rs.getObject("course_id", UUID.class),
                rs.getObject("topic_id", UUID.class), BloomLevel.valueOf(rs.getString("bloom_level")),
                Language.valueOf(rs.getString("language")), rs.getString("content")));
    }

    @Override
    public Optional<QuestionInfo> getQuestion(UUID questionId) {
        return jdbc.query("""
                        select question_id, course_id, topic_id, content, reference_answer, bloom_level, language, status,
                               rubric_id, is_locked from questions where question_id = :id""",
                new MapSqlParameterSource("id", questionId),
                (rs, i) -> new QuestionInfo(rs.getObject("question_id", UUID.class), rs.getObject("course_id", UUID.class),
                        rs.getObject("topic_id", UUID.class), rs.getString("content"), rs.getString("reference_answer"),
                        rs.getString("bloom_level") == null ? null : BloomLevel.valueOf(rs.getString("bloom_level")),
                        Language.valueOf(rs.getString("language")), rs.getString("status"),
                        rs.getObject("rubric_id", UUID.class), rs.getBoolean("is_locked")))
                .stream().findFirst();
    }

    @Override
    public Optional<RubricSnapshot> getRubricSnapshot(UUID questionId) {
        List<RubricSnapshot> rubric = jdbc.query("""
                        select r.rubric_id, r.name from questions q join rubrics r on r.rubric_id = q.rubric_id
                        where q.question_id = :id""", new MapSqlParameterSource("id", questionId),
                (rs, i) -> new RubricSnapshot(rs.getObject("rubric_id", UUID.class), rs.getString("name"), List.of()));
        if (rubric.isEmpty()) {
            return Optional.empty();
        }
        UUID rubricId = rubric.getFirst().rubricId();
        List<RubricSnapshot.Criterion> criteria = jdbc.query("""
                        select criterion_id, name, description, max_score, weight_percent, sort_order
                        from rubric_criteria where rubric_id = :id order by sort_order, name""",
                new MapSqlParameterSource("id", rubricId),
                (rs, i) -> new RubricSnapshot.Criterion(rs.getObject("criterion_id", UUID.class), rs.getString("name"),
                        rs.getString("description"), rs.getBigDecimal("max_score"), rs.getBigDecimal("weight_percent"),
                        rs.getInt("sort_order")));
        return Optional.of(new RubricSnapshot(rubricId, rubric.getFirst().name(), criteria));
    }

    @Override
    public boolean topicBelongsToCourse(UUID topicId, UUID courseId) {
        Integer n = jdbc.queryForObject("select count(*) from topics where topic_id = :t and course_id = :c",
                new MapSqlParameterSource("t", topicId).addValue("c", courseId), Integer.class);
        return n != null && n > 0;
    }
}
