package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.user.CourseDtos.AssignLecturersRequest;
import swd392.group6.AIVES.user.CourseDtos.CourseDetail;
import swd392.group6.AIVES.user.CourseDtos.CourseResponse;
import swd392.group6.AIVES.user.CourseDtos.CreateCourseRequest;
import swd392.group6.AIVES.user.CourseDtos.LecturerRef;
import swd392.group6.AIVES.user.CourseDtos.UpdateCourseRequest;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Courses and lecturer assignments (15_CRUD_CATALOGUE.md §5.1). */
@Service
@RequiredArgsConstructor
class CourseService {

    private final CourseRepository courseRepository;
    private final UserRepository userRepository;
    private final CourseAccessApi courseAccess;
    private final SettingsService settings;
    private final JdbcClient jdbc;
    private final Clock clock;

    /** ADMIN sees every course, a LECTURER only the assigned ones; STUDENT is stopped by the controller. */
    @Transactional(readOnly = true)
    public PageResponse<CourseResponse> list(User caller, String q, Boolean active, int page, int size) {
        // "" rather than null: an untyped null parameter breaks "like" on PostgreSQL.
        String query = q == null || q.isBlank() ? "" : q.trim().toLowerCase(Locale.ROOT);
        boolean unrestricted = caller.getRole() == Role.ADMIN;
        Set<UUID> ids = unrestricted ? Set.of() : courseAccess.assignedCourseIds(caller.getUserId());
        // An empty IN list is not portable SQL; a random id matches nothing.
        List<UUID> idList = ids.isEmpty() ? List.of(UUID.randomUUID()) : List.copyOf(ids);
        Page<Course> result = courseRepository.search(query, active, unrestricted, idList,
                PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by("code")));
        Map<UUID, Integer> counts = lecturerCounts(result.getContent().stream().map(Course::getCourseId).toList());
        return PageResponse.of(result, c -> toResponse(c, counts.getOrDefault(c.getCourseId(), 0)));
    }

    @Transactional(readOnly = true)
    public CourseDetail get(User caller, UUID courseId) {
        courseAccess.requireRead(courseId, caller);
        return toDetail(load(courseId));
    }

    @Transactional
    public CourseDetail create(CreateCourseRequest request) {
        String code = normalizeCode(request.code());
        if (courseRepository.existsByCode(code)) {
            throw codeExists();
        }
        Course course = Course.builder()
                .code(code)
                .name(request.name().trim())
                .description(blankToNull(request.description()))
                .defaultLanguage(request.defaultLanguage() != null ? request.defaultLanguage() : settings.defaultLanguage())
                .build();
        return toDetail(courseRepository.saveAndFlush(course));
    }

    @Transactional
    public CourseDetail update(UUID courseId, UpdateCourseRequest request) {
        Course course = load(courseId);
        if (request.code() != null && !request.code().isBlank()) {
            String code = normalizeCode(request.code());
            if (!code.equals(course.getCode()) && courseRepository.existsByCode(code)) {
                throw codeExists();
            }
            course.setCode(code);
        }
        if (request.name() != null && !request.name().isBlank()) {
            course.setName(request.name().trim());
        }
        if (request.description() != null) {
            course.setDescription(blankToNull(request.description()));
        }
        if (request.defaultLanguage() != null) {
            course.setDefaultLanguage(request.defaultLanguage());
        }
        if (request.active() != null) {
            if (!request.active() && course.isActive()) {
                requireNothingRunning(course.getCourseId());
            }
            course.setActive(request.active());
        }
        return toDetail(courseRepository.saveAndFlush(course));
    }

    /**
     * Hard delete of an empty course. Lecturer assignments are not course content and go with it; anything
     * else that references the course (topics, terms, materials, rubrics, questions, exams, ...) blocks it.
     */
    @Transactional
    public void delete(UUID courseId) {
        load(courseId);
        try {
            jdbc.sql("delete from course_lecturers where course_id = ?").param(courseId).update();
            jdbc.sql("delete from courses where course_id = ?").param(courseId).update();
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("COURSE_IN_USE",
                    "The course has content (topics, questions, exams, ...). Deactivate it instead.");
        }
    }

    /** Replaces the assignment; every id must be an active LECTURER (422 NOT_A_LECTURER otherwise). */
    @Transactional
    public CourseDetail assignLecturers(UUID courseId, AssignLecturersRequest request) {
        Course course = load(courseId);
        Set<UUID> wanted = new LinkedHashSet<>(request.lecturerIds());
        if (wanted.contains(null)) {
            throw ApiException.unprocessable("NOT_A_LECTURER", "lecturerIds must not contain null");
        }
        List<User> found = userRepository.findAllById(wanted);
        for (UUID id : wanted) {
            boolean ok = found.stream().anyMatch(u -> u.getUserId().equals(id) && u.isActive() && u.getRole() == Role.LECTURER);
            if (!ok) {
                throw ApiException.unprocessable("NOT_A_LECTURER", "User " + id + " is not an active lecturer");
            }
        }
        Set<UUID> current = courseAccessIdsOf(courseId);
        for (UUID id : current) {
            if (!wanted.contains(id)) {
                jdbc.sql("delete from course_lecturers where course_id = ? and lecturer_id = ?").params(courseId, id).update();
            }
        }
        Timestamp now = Timestamp.from(clock.instant());
        for (UUID id : wanted) {
            if (!current.contains(id)) {
                jdbc.sql("insert into course_lecturers (course_id, lecturer_id, assigned_at) values (?, ?, ?)")
                        .params(courseId, id, now).update();
            }
        }
        return toDetail(course);
    }

    List<LecturerRef> lecturersOf(UUID courseId) {
        return jdbc.sql("""
                        select u.user_id, u.username, u.full_name, u.email, u.is_active
                        from course_lecturers cl join users u on u.user_id = cl.lecturer_id
                        where cl.course_id = ? order by u.full_name, u.username""")
                .param(courseId)
                .query((rs, i) -> new LecturerRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getBoolean(5)))
                .list();
    }

    private Set<UUID> courseAccessIdsOf(UUID courseId) {
        return new LinkedHashSet<>(jdbc.sql("select lecturer_id from course_lecturers where course_id = ?")
                .param(courseId).query(UUID.class).list());
    }

    private Map<UUID, Integer> lecturerCounts(List<UUID> courseIds) {
        Map<UUID, Integer> counts = new HashMap<>();
        if (courseIds.isEmpty()) {
            return counts;
        }
        jdbc.sql("select course_id, count(*) from course_lecturers where course_id in (:ids) group by course_id")
                .param("ids", courseIds)
                .query((rs, i) -> counts.put(rs.getObject(1, UUID.class), rs.getInt(2)))
                .list();
        return counts;
    }

    private Course load(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> ApiException.notFound("COURSE_NOT_FOUND", "Course not found"));
    }

    private CourseDetail toDetail(Course c) {
        return new CourseDetail(c.getCourseId(), c.getCode(), c.getName(), c.getDescription(), c.getDefaultLanguage(),
                c.isActive(), lecturersOf(c.getCourseId()), c.getCreatedAt(), c.getUpdatedAt());
    }

    private static CourseResponse toResponse(Course c, int lecturerCount) {
        return new CourseResponse(c.getCourseId(), c.getCode(), c.getName(), c.getDescription(), c.getDefaultLanguage(),
                c.isActive(), lecturerCount, c.getCreatedAt(), c.getUpdatedAt());
    }

    private static String normalizeCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9._-]{2,20}")) {
            throw ApiException.unprocessable("INVALID_COURSE_CODE", "Course code must be 2-20 characters: A-Z, 0-9, dot, dash, underscore");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static ApiException codeExists() {
        return ApiException.conflict("COURSE_CODE_EXISTS", "A course with this code already exists");
    }

    /**
     * Data lifecycle (D54): an inactive course is archived — readable, but its content can no longer change. It may only
     * be archived when no buổi thi is published/open, no attempt is running and no grading is left unfinished.
     */
    private void requireNothingRunning(UUID courseId) {
        Boolean busy = jdbc.sql("""
                select exists(select 1 from viva_exams where course_id = ? and status in ('READY', 'OPEN'))
                    or exists(select 1 from exam_attempts where course_id = ? and status in ('IN_PROGRESS', 'INTERRUPTED'))
                    or exists(select 1 from grade_evaluations g join exam_attempts a on a.attempt_id = g.attempt_id
                              where a.course_id = ? and g.status in ('PENDING_AI', 'AWAITING_REVIEW', 'DISPUTED'))""")
                .params(courseId, courseId, courseId).query(Boolean.class).single();
        if (Boolean.TRUE.equals(busy)) {
            throw ApiException.conflict("COURSE_HAS_ACTIVE_EXAMS",
                    "Close the course's exams and finish grading before archiving it");
        }
    }
}
