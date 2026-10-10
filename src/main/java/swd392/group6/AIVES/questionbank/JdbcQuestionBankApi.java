package swd392.group6.AIVES.questionbank;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.Language;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        List<UUID> rubricId = jdbc.queryForList("select rubric_id from questions where question_id = :id and rubric_id is not null",
                new MapSqlParameterSource("id", questionId), UUID.class);
        return rubricId.isEmpty() ? Optional.empty() : getRubric(rubricId.getFirst());
    }

    @Override
    public Optional<RubricSnapshot> getRubric(UUID rubricId) {
        Map<UUID, RubricSnapshot> rubrics = rubrics(List.of(rubricId));
        return Optional.ofNullable(rubrics.get(rubricId));
    }

    @Override
    public boolean rubricUsableInCourse(UUID rubricId, UUID courseId) {
        Boolean ok = jdbc.queryForObject("""
                select exists(select 1 from rubrics r where r.rubric_id = :r and r.course_id = :c
                  and abs(coalesce((select sum(weight_percent) from rubric_criteria rc where rc.rubric_id = r.rubric_id), 0) - 100) <= 0.01)""",
                new MapSqlParameterSource("r", rubricId).addValue("c", courseId), Boolean.class);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public Map<UUID, TopicInfo> topics(UUID courseId) {
        Map<UUID, TopicInfo> result = new LinkedHashMap<>();
        jdbc.query("select topic_id, name, sort_order from topics where course_id = :c order by sort_order, name",
                new MapSqlParameterSource("c", courseId), rs -> {
                    UUID id = rs.getObject(1, UUID.class);
                    result.put(id, new TopicInfo(id, rs.getString(2), rs.getInt(3)));
                });
        return result;
    }

    @Override
    public Map<UUID, QuestionSnapshot> snapshot(Collection<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        record Row(UUID id, UUID topicId, String topicName, String content, String reference,
                   BloomLevel bloom, Language language, int version, UUID rubricId) {
        }
        List<Row> rows = jdbc.query("""
                        select q.question_id, q.topic_id, c.name, q.content, q.reference_answer,
                               q.bloom_level, q.language, q.version, q.rubric_id
                        from questions q join topics c on c.topic_id = q.topic_id
                        where q.question_id in (:ids)""", new MapSqlParameterSource("ids", questionIds),
                (rs, i) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5),
                        rs.getString(6) == null ? null : BloomLevel.valueOf(rs.getString(6)),
                        Language.valueOf(rs.getString(7)), rs.getInt(8), rs.getObject(9, UUID.class)));
        Map<UUID, RubricSnapshot> rubrics = rubrics(rows.stream().map(Row::rubricId).filter(Objects::nonNull).toList());
        Map<UUID, QuestionSnapshot> result = new HashMap<>();
        for (Row r : rows) {
            result.put(r.id(), new QuestionSnapshot(r.id(), r.topicId(), r.topicName(), r.content(),
                    r.reference(), r.bloom(), r.language(), r.version(), r.rubricId() == null ? null : rubrics.get(r.rubricId())));
        }
        return result;
    }

    @Override
    @Transactional
    public void lockForExam(Collection<UUID> questionIds, Collection<UUID> rubricIds) {
        if (!questionIds.isEmpty()) {
            jdbc.update("""
                    update questions set is_locked = true, version = version + 1
                    where question_id in (:ids) and not is_locked""", new MapSqlParameterSource("ids", questionIds));
        }
        if (!rubricIds.isEmpty()) {
            jdbc.update("update rubrics set is_locked = true where rubric_id in (:ids) and not is_locked",
                    new MapSqlParameterSource("ids", rubricIds));
        }
    }

    private Map<UUID, RubricSnapshot> rubrics(Collection<UUID> rubricIds) {
        if (rubricIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        jdbc.query("select rubric_id, name from rubrics where rubric_id in (:ids)", new MapSqlParameterSource("ids", rubricIds),
                rs -> {
                    names.put(rs.getObject(1, UUID.class), rs.getString(2));
                });
        Map<UUID, List<RubricSnapshot.Criterion>> criteria = new HashMap<>();
        jdbc.query("""
                        select rubric_id, criterion_id, name, description, max_score, weight_percent, sort_order
                        from rubric_criteria where rubric_id in (:ids) order by sort_order, name""",
                new MapSqlParameterSource("ids", rubricIds), rs -> {
                    criteria.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>())
                            .add(new RubricSnapshot.Criterion(rs.getObject(2, UUID.class), rs.getString(3),
                                    rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7)));
                });
        Map<UUID, RubricSnapshot> result = new HashMap<>();
        names.forEach((id, name) -> result.put(id, new RubricSnapshot(id, name, List.copyOf(criteria.getOrDefault(id, List.of())))));
        return result;
    }

    @Override
    public boolean topicBelongsToCourse(UUID topicId, UUID courseId) {
        Integer n = jdbc.queryForObject("select count(*) from topics where topic_id = :t and course_id = :c",
                new MapSqlParameterSource("t", topicId).addValue("c", courseId), Integer.class);
        return n != null && n > 0;
    }
}
