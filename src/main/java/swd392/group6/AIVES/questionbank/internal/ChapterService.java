package swd392.group6.AIVES.questionbank.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.CreateChapterRequest;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.TermsDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.ChapterDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.UpdateChapterRequest;
import swd392.group6.AIVES.user.User;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Chương CRUD (D42) and course terms (STT hotwords) — 15 §5.2. */
@Service
@RequiredArgsConstructor
@Transactional
public class ChapterService {

    static final int MAX_TERMS = 200;
    static final int MAX_TERM_LENGTH = 100;
    private static final UUID NO_ID = new UUID(0, 0);

    private final ChapterRepository chapters;
    private final QuestionRepository questions;
    private final CourseTermRepository terms;
    private final ContentAccess access;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ChapterDto> list(UUID courseId, User user) {
        access.readCourse(courseId, user);
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : questions.countByChapter(courseId)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return chapters.findByCourseIdOrderByChapterNoAsc(courseId).stream()
                .map(t -> toDto(t, counts.getOrDefault(t.getId(), 0L))).toList();
    }

    public ChapterDto create(UUID courseId, CreateChapterRequest request, User user) {
        access.writeCourse(courseId, user);
        String title = request.title().trim();
        if (chapters.titleTaken(courseId, title, NO_ID)) {
            throw titleExists(title);
        }
        int number = request.chapterNo() != null ? request.chapterNo() : chapters.maxNumber(courseId) + 1;
        requireFreeNumber(courseId, number, NO_ID);
        Instant now = Instant.now(clock);
        Chapter chapter = new Chapter();
        chapter.setCourseId(courseId);
        chapter.setChapterNo(number);
        chapter.setTitle(title);
        chapter.setDescription(blankToNull(request.description()));
        chapter.setCreatedBy(user.getUserId());
        chapter.setCreatedAt(now);
        chapter.setUpdatedAt(now);
        return toDto(chapters.save(chapter), 0);
    }

    public ChapterDto update(UUID chapterId, UpdateChapterRequest request, User user) {
        Chapter chapter = load(chapterId);
        access.write(chapter.getCourseId(), user, "CHAPTER_NOT_FOUND", "Chapter not found");
        if (request.title() != null) {
            String title = request.title().trim();
            if (title.isEmpty()) {
                throw ApiException.unprocessable("CHAPTER_TITLE_REQUIRED", "Chapter title must not be blank");
            }
            if (chapters.titleTaken(chapter.getCourseId(), title, chapter.getId())) {
                throw titleExists(title);
            }
            chapter.setTitle(title);
        }
        if (request.chapterNo() != null) {
            requireFreeNumber(chapter.getCourseId(), request.chapterNo(), chapter.getId());
            chapter.setChapterNo(request.chapterNo());
        }
        if (request.description() != null) {
            chapter.setDescription(blankToNull(request.description()));
        }
        chapter.setUpdatedAt(Instant.now(clock));
        return toDto(chapter, questionCount(chapter.getId()));
    }

    public void delete(UUID chapterId, User user) {
        Chapter chapter = load(chapterId);
        access.write(chapter.getCourseId(), user, "CHAPTER_NOT_FOUND", "Chapter not found");
        Boolean usedElsewhere = jdbc.queryForObject("""
                select exists(select 1 from exam_template_items where chapter_id = ?)
                    or exists(select 1 from ai_generation_requests where chapter_id = ?)
                    or exists(select 1 from attempt_questions where chapter_id = ?)""",
                Boolean.class, chapterId, chapterId, chapterId);
        if (questions.existsByChapterId(chapterId) || Boolean.TRUE.equals(usedElsewhere)) {
            throw ApiException.conflict("CHAPTER_IN_USE", "The chapter still has questions or is used by an exam template");
        }
        chapters.delete(chapter);
    }

    @Transactional(readOnly = true)
    public TermsDto getTerms(UUID courseId, User user) {
        access.readCourse(courseId, user);
        return new TermsDto(terms.findByCourse(courseId).stream().map(CourseTerm::getTerm).toList());
    }

    /** Replaces the list: trimmed, blanks dropped, de-duplicated case-insensitively (first spelling wins). */
    public TermsDto replaceTerms(UUID courseId, TermsDto request, User user) {
        access.writeCourse(courseId, user);
        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : request.terms()) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String term = raw.trim();
            if (term.length() > MAX_TERM_LENGTH) {
                throw ApiException.unprocessable("TERM_TOO_LONG",
                        "Each term must be at most " + MAX_TERM_LENGTH + " characters: " + term.substring(0, 20) + "…");
            }
            if (seen.add(term.toLowerCase(Locale.ROOT))) {
                cleaned.add(term);
            }
        }
        if (cleaned.size() > MAX_TERMS) {
            throw ApiException.unprocessable("TOO_MANY_TERMS", "A course can have at most " + MAX_TERMS + " terms");
        }
        terms.deleteByCourse(courseId);
        terms.saveAll(cleaned.stream().map(t -> new CourseTerm(courseId, t)).toList());
        terms.flush();
        return new TermsDto(terms.findByCourse(courseId).stream().map(CourseTerm::getTerm).toList());
    }

    private Chapter load(UUID chapterId) {
        return chapters.findById(chapterId).orElseThrow(() -> ApiException.notFound("CHAPTER_NOT_FOUND", "Chapter not found"));
    }

    private long questionCount(UUID chapterId) {
        Long n = jdbc.queryForObject("select count(*) from questions where chapter_id = ?", Long.class, chapterId);
        return n == null ? 0 : n;
    }

    private void requireFreeNumber(UUID courseId, int number, UUID excludeId) {
        if (number < 1 || number > 99) {
            throw ApiException.unprocessable("INVALID_CHAPTER_NO", "chapterNo must be 1–99");
        }
        if (chapters.numberTaken(courseId, number, excludeId)) {
            throw ApiException.conflict("CHAPTER_NO_EXISTS", "Chapter " + number + " already exists in this course");
        }
    }

    private static ApiException titleExists(String title) {
        return ApiException.conflict("CHAPTER_TITLE_EXISTS", "A chapter titled \"" + title + "\" already exists in this course");
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ChapterDto toDto(Chapter t, long questionCount) {
        return new ChapterDto(t.getId(), t.getCourseId(), t.getChapterNo(), t.getTitle(), t.getDescription(),
                questionCount, t.getCreatedBy(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
