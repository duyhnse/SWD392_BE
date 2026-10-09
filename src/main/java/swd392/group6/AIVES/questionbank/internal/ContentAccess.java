package swd392.group6.AIVES.questionbank.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;

import java.util.UUID;

/**
 * Course-level authorization for question bank resources (15 §6). Resources reached by their own id answer
 * "not visible" with the resource's own 404 code so existence never leaks across courses.
 */
@Component
@RequiredArgsConstructor
public class ContentAccess {

    private final CourseAccessApi courseAccess;

    public void readCourse(UUID courseId, User user) {
        requireStaff(user);
        courseAccess.requireRead(courseId, user);
    }

    public void writeCourse(UUID courseId, User user) {
        requireStaff(user);
        courseAccess.requireWrite(courseId, user);
    }

    public void read(UUID courseId, User user, String notFoundCode, String notFoundMessage) {
        try {
            readCourse(courseId, user);
        } catch (ApiException e) {
            throw remap(e, notFoundCode, notFoundMessage);
        }
    }

    public void write(UUID courseId, User user, String notFoundCode, String notFoundMessage) {
        try {
            writeCourse(courseId, user);
        } catch (ApiException e) {
            throw remap(e, notFoundCode, notFoundMessage);
        }
    }

    public boolean canWrite(UUID courseId, User user) {
        return user.getRole() == Role.LECTURER && courseAccess.isLecturerOf(courseId, user.getUserId());
    }

    public boolean canRead(UUID courseId, User user) {
        return user.getRole() == Role.ADMIN || canWrite(courseId, user);
    }

    /** Students never reach course content (also enforced by @PreAuthorize on the controllers). */
    public static void requireStaff(User user) {
        if (user.getRole() == Role.STUDENT) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have permission to perform this action");
        }
    }

    private static ApiException remap(ApiException e, String code, String message) {
        return e.getStatus() == HttpStatus.NOT_FOUND ? ApiException.notFound(code, message) : e;
    }
}
