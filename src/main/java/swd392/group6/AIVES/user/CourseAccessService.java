package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class CourseAccessService implements CourseAccessApi {

    private final JdbcTemplate jdbc;

    @Override
    public boolean isLecturerOf(UUID courseId, UUID userId) {
        Integer n = jdbc.queryForObject(
                "select count(*) from course_lecturers where course_id = ? and lecturer_id = ?", Integer.class, courseId, userId);
        return n != null && n > 0;
    }

    @Override
    public Set<UUID> assignedCourseIds(UUID userId) {
        return new HashSet<>(jdbc.queryForList("select course_id from course_lecturers where lecturer_id = ?", UUID.class, userId));
    }

    @Override
    public void requireRead(UUID courseId, User user) {
        if (!courseExists(courseId)) {
            throw notFound();
        }
        if (user.getRole() == Role.ADMIN) {
            return;
        }
        if (user.getRole() != Role.LECTURER || !isLecturerOf(courseId, user.getUserId())) {
            throw notFound();
        }
    }

    @Override
    public void requireWrite(UUID courseId, User user) {
        if (user.getRole() == Role.ADMIN && courseExists(courseId)) {
            throw ApiException.forbidden("ADMIN_READ_ONLY", "Administrators can read but not change course content");
        }
        requireRead(courseId, user);
    }

    @Override
    public boolean courseExists(UUID courseId) {
        Integer n = jdbc.queryForObject("select count(*) from courses where course_id = ?", Integer.class, courseId);
        return n != null && n > 0;
    }

    private static ApiException notFound() {
        return ApiException.notFound("COURSE_NOT_FOUND", "Course not found");
    }
}
