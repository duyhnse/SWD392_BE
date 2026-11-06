package swd392.group6.AIVES.user;

import java.util.Set;
import java.util.UUID;

/**
 * Course-level authorization shared by every module (15_CRUD_CATALOGUE.md §6, D35).
 * Resources in a course the caller cannot see must be answered with 404, not 403.
 */
public interface CourseAccessApi {

    /** True when the user is a LECTURER assigned to the course. */
    boolean isLecturerOf(UUID courseId, UUID userId);

    /** Courses the lecturer is assigned to (empty for other roles). */
    Set<UUID> assignedCourseIds(UUID userId);

    /** ADMIN may read any course; LECTURER only assigned ones. Otherwise 404 COURSE_NOT_FOUND. */
    void requireRead(UUID courseId, User user);

    /** Only an assigned LECTURER may change course content (ADMIN is read-only). Otherwise 404 / 403. */
    void requireWrite(UUID courseId, User user);

    boolean courseExists(UUID courseId);
}
