package swd392.group6.AIVES.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.user.CourseDtos.AssignLecturersRequest;
import swd392.group6.AIVES.user.CourseDtos.CourseDetail;
import swd392.group6.AIVES.user.CourseDtos.CreateCourseRequest;
import swd392.group6.AIVES.user.CourseDtos.UpdateCourseRequest;

import java.util.UUID;

/** Course administration, ADMIN only (15 §5.1). */
@RestController
@RequestMapping("/api/v1/admin/courses")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
class AdminCourseController {

    private final CourseService courseService;

    @PostMapping
    public ResponseEntity<CourseDetail> create(@Valid @RequestBody CreateCourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(request));
    }

    @PatchMapping("/{id}")
    public CourseDetail update(@PathVariable UUID id, @Valid @RequestBody UpdateCourseRequest request) {
        return courseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        courseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/lecturers")
    public CourseDetail assignLecturers(@PathVariable UUID id, @Valid @RequestBody AssignLecturersRequest request) {
        return courseService.assignLecturers(id, request);
    }
}
