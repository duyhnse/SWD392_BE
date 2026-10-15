package swd392.group6.AIVES.grading.internal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.PageResponse;
import swd392.group6.AIVES.grading.internal.GradingDtos.ChangeDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.CreateDisputeRequest;
import swd392.group6.AIVES.grading.internal.GradingDtos.DisputeDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.EvaluationDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.EvaluationSummaryDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.ReportDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.ResolutionRequest;
import swd392.group6.AIVES.grading.internal.GradingDtos.ResultDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.ResultSummaryDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.UpdateEvaluationRequest;
import swd392.group6.AIVES.grading.internal.GradingDtos.UpdateThreadRequest;
import swd392.group6.AIVES.grading.internal.GradingDtos.VersionRequest;
import swd392.group6.AIVES.user.User;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** HTTP endpoints of 15 §5.5. Course-level checks happen in the services (404 outside assigned courses). */
final class GradingControllers {

    private GradingControllers() {
    }

    /** Lecturer review (assigned lecturers write, ADMIN reads). */
    @RestController
    @RequestMapping("/api/v1")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    @RequiredArgsConstructor
    static class EvaluationController {

        private final EvaluationService service;

        @PostMapping("/sessions/{sessionId}/evaluation")
        public ResponseEntity<EvaluationDto> create(@PathVariable UUID sessionId, @AuthenticationPrincipal User user) {
            EvaluationService.Created result = service.create(sessionId, user);
            return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.evaluation());
        }

        @GetMapping("/viva-exams/{vivaExamId}/evaluations")
        public PageResponse<EvaluationSummaryDto> list(@PathVariable UUID vivaExamId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "100") int size,
                                                @AuthenticationPrincipal User user) {
            return service.listForExam(vivaExamId, user, page, size);
        }

        @GetMapping("/evaluations/{id}")
        public EvaluationDto get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
            return service.get(id, user);
        }

        @PutMapping("/evaluations/{id}/threads/{gradeId}")
        public EvaluationDto updateThread(@PathVariable UUID id, @PathVariable UUID gradeId,
                                   @Valid @RequestBody UpdateThreadRequest request, @AuthenticationPrincipal User user) {
            return service.updateThread(id, gradeId, request, user);
        }

        @PostMapping("/evaluations/{id}/threads/{gradeId}/accept-ai")
        public EvaluationDto acceptAi(@PathVariable UUID id, @PathVariable UUID gradeId,
                               @RequestBody(required = false) VersionRequest request, @AuthenticationPrincipal User user) {
            return service.acceptAi(id, gradeId, request == null ? null : request.version(), user);
        }

        @PostMapping("/evaluations/{id}/accept-all-ai")
        public EvaluationDto acceptAllAi(@PathVariable UUID id, @RequestBody(required = false) VersionRequest request,
                                  @AuthenticationPrincipal User user) {
            return service.acceptAllAi(id, request == null ? null : request.version(), user);
        }

        @PutMapping("/evaluations/{id}")
        public EvaluationDto update(@PathVariable UUID id, @Valid @RequestBody UpdateEvaluationRequest request,
                             @AuthenticationPrincipal User user) {
            return service.updateEvaluation(id, request, user);
        }

        @PostMapping("/evaluations/{id}/confirm")
        public EvaluationDto confirm(@PathVariable UUID id, @RequestBody(required = false) VersionRequest request,
                              @AuthenticationPrincipal User user) {
            return service.confirm(id, request == null ? null : request.version(), user);
        }

        @GetMapping("/evaluations/{id}/history")
        public List<ChangeDto> history(@PathVariable UUID id, @AuthenticationPrincipal User user) {
            return service.history(id, user);
        }
    }

    /** Disputes as seen and handled by lecturers. */
    @RestController
    @RequestMapping("/api/v1")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    @RequiredArgsConstructor
    static class LecturerDisputeController {

        private final DisputeService service;

        @GetMapping("/viva-exams/{vivaExamId}/disputes")
        public List<DisputeDto> forExam(@PathVariable UUID vivaExamId, @AuthenticationPrincipal User user) {
            return service.forExam(vivaExamId, user);
        }

        @GetMapping("/disputes/{id}")
        public DisputeDto get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
            return service.get(id, user);
        }

        @PostMapping("/disputes/{id}/resolve")
        public DisputeDto resolve(@PathVariable UUID id, @Valid @RequestBody ResolutionRequest request,
                           @AuthenticationPrincipal User user) {
            return service.resolve(id, request.resolution(), user);
        }

        @PostMapping("/disputes/{id}/reject")
        public DisputeDto reject(@PathVariable UUID id, @Valid @RequestBody ResolutionRequest request,
                          @AuthenticationPrincipal User user) {
            return service.reject(id, request.resolution(), user);
        }
    }

    /** FG6 class report and score sheet. */
    @RestController
    @RequestMapping("/api/v1/viva-exams/{vivaExamId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    @RequiredArgsConstructor
    static class ReportController {

        private final ReportService service;

        @GetMapping("/report")
        public ReportDto report(@PathVariable UUID vivaExamId, @AuthenticationPrincipal User user) {
            return service.report(vivaExamId, user);
        }

        @GetMapping("/export")
        public ResponseEntity<byte[]> export(@PathVariable UUID vivaExamId, @RequestParam(defaultValue = "csv") String format,
                                      @AuthenticationPrincipal User user) {
            if (!"csv".equalsIgnoreCase(format)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_FORMAT", "Only format=csv is supported");
            }
            byte[] body = service.exportCsv(vivaExamId, user);
            return ResponseEntity.ok()
                    .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("bang-diem-" + vivaExamId + ".csv").build().toString())
                    .body(body);
        }
    }

    /** FG6 / FG5 for students: own released results and disputes. */
    @RestController
    @RequestMapping("/api/v1/me")
    @PreAuthorize("hasRole('STUDENT')")
    @RequiredArgsConstructor
    static class StudentResultController {

        private final StudentResultService results;
        private final DisputeService disputes;

        @GetMapping("/results")
        public List<ResultSummaryDto> results(@AuthenticationPrincipal User user) {
            return results.list(user);
        }

        @GetMapping("/results/{evaluationId}")
        public ResultDto result(@PathVariable UUID evaluationId, @AuthenticationPrincipal User user) {
            return results.get(evaluationId, user);
        }

        @PostMapping("/results/{evaluationId}/disputes")
        @ResponseStatus(HttpStatus.CREATED)
        public DisputeDto dispute(@PathVariable UUID evaluationId, @Valid @RequestBody CreateDisputeRequest request,
                           @AuthenticationPrincipal User user) {
            return disputes.create(evaluationId, request, user);
        }

        @GetMapping("/disputes")
        public List<DisputeDto> myDisputes(@AuthenticationPrincipal User user) {
            return disputes.mine(user);
        }
    }
}
