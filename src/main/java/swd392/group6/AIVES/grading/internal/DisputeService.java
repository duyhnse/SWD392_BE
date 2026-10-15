package swd392.group6.AIVES.grading.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.exam.ExamApi;
import swd392.group6.AIVES.exam.ExamApi.ExamInfo;
import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.grading.internal.GradingDtos.CreateDisputeRequest;
import swd392.group6.AIVES.grading.internal.GradingDtos.DisputeDto;
import swd392.group6.AIVES.grading.internal.GradingDtos.StudentRef;
import swd392.group6.AIVES.user.User;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * FG5 phúc khảo. A student disputes a CONFIRMED, released evaluation within 7 days of the release; one OPEN dispute
 * per evaluation. <b>Resolve</b> closes the dispute as RESOLVED immediately and re-opens the evaluation as DISPUTED
 * (editable) so the lecturer can adjust and confirm again; <b>reject</b> closes it and leaves the evaluation CONFIRMED.
 */
@Service
@RequiredArgsConstructor
@Transactional
class DisputeService {

    private final GradingSupport support;
    private final StudentResultService results;
    private final ExamApi examApi;
    private final GradeEvaluationRepository evaluations;
    private final QuestionGradeRepository grades;
    private final GradeDisputeRepository disputes;
    private final GradeChangeLogRepository changeLog;
    private final Clock clock;

    public DisputeDto create(UUID evaluationId, CreateDisputeRequest request, User student) {
        StudentResultService.Visible visible = results.requireVisible(evaluationId, student);
        Instant now = clock.instant();
        Instant deadline = StudentResultService.disputeDeadline(visible.exam());
        if (deadline == null || now.isAfter(deadline)) {
            throw ApiException.conflict("DISPUTE_WINDOW_CLOSED", "Disputes can only be filed within 7 days of the results release");
        }
        if (disputes.existsByEvaluationIdAndStatus(evaluationId, DisputeStatus.OPEN)) {
            throw ApiException.conflict("DISPUTE_ALREADY_OPEN", "A dispute for this result is already open");
        }
        List<UUID> gradeIds = request.questionGradeIds() == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(request.questionGradeIds()));
        if (!gradeIds.isEmpty()) {
            Set<UUID> own = grades.findByEvaluationId(evaluationId).stream().map(QuestionGrade::getQuestionGradeId)
                    .collect(Collectors.toSet());
            if (!own.containsAll(gradeIds)) {
                throw ApiException.unprocessable("INVALID_QUESTION_GRADE", "questionGradeIds must belong to this result");
            }
        }
        GradeDispute dispute = new GradeDispute();
        dispute.setDisputeId(UUID.randomUUID());
        dispute.setEvaluationId(evaluationId);
        dispute.setStudentId(student.getUserId());
        dispute.setReason(request.reason().strip());
        dispute.setQuestionGradeIds(gradeIds.isEmpty() ? null : GradingJson.write(gradeIds));
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setCreatedAt(now);
        try {
            disputes.saveAndFlush(dispute);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("DISPUTE_ALREADY_OPEN", "A dispute for this result is already open");
        }
        return toDtos(List.of(dispute)).getFirst();
    }

    @Transactional(readOnly = true)
    public List<DisputeDto> mine(User student) {
        return toDtos(disputes.findByStudentIdOrderByCreatedAtDesc(student.getUserId()));
    }

    @Transactional(readOnly = true)
    public List<DisputeDto> forExam(UUID vivaExamId, User user) {
        support.requireExamRead(vivaExamId, user);
        List<UUID> attemptIds = support.attemptsOfExam(vivaExamId).stream().map(GradingSupport.AttemptRow::attemptId).toList();
        List<UUID> evaluationIds = evaluations.findByAttemptIdIn(attemptIds).stream()
                .map(GradeEvaluation::getEvaluationId).toList();
        return evaluationIds.isEmpty() ? List.of()
                : toDtos(disputes.findByEvaluationIdInOrderByCreatedAtDesc(evaluationIds));
    }

    @Transactional(readOnly = true)
    public DisputeDto get(UUID disputeId, User user) {
        GradeDispute dispute = requireDispute(disputeId);
        support.evaluationForRead(dispute.getEvaluationId(), user);
        return toDtos(List.of(dispute)).getFirst();
    }

    public DisputeDto resolve(UUID disputeId, String resolution, User user) {
        GradeDispute dispute = requireOpen(disputeId, user);
        GradeEvaluation evaluation = support.requireEvaluation(dispute.getEvaluationId());
        Instant now = clock.instant();
        if (evaluation.getStatus() == EvaluationStatus.CONFIRMED) {
            GradeChangeLog row = new GradeChangeLog();
            row.setChangeId(UUID.randomUUID());
            row.setEvaluationId(evaluation.getEvaluationId());
            row.setField("status");
            row.setOldValue(EvaluationStatus.CONFIRMED.name());
            row.setNewValue(EvaluationStatus.DISPUTED.name());
            row.setChangedBy(user.getUserId());
            row.setChangedAt(now);
            changeLog.save(row);
            evaluation.setStatus(EvaluationStatus.DISPUTED);
            EvaluationService.touch(evaluation, now);
            try {
                evaluations.saveAndFlush(evaluation);
            } catch (ObjectOptimisticLockingFailureException e) {
                throw ApiException.conflict("VERSION_CONFLICT", "The evaluation was changed by someone else; retry");
            }
        }
        close(dispute, DisputeStatus.RESOLVED, resolution, user, now);
        return toDtos(List.of(dispute)).getFirst();
    }

    public DisputeDto reject(UUID disputeId, String resolution, User user) {
        GradeDispute dispute = requireOpen(disputeId, user);
        close(dispute, DisputeStatus.REJECTED, resolution, user, clock.instant());
        return toDtos(List.of(dispute)).getFirst();
    }

    private GradeDispute requireOpen(UUID disputeId, User user) {
        GradeDispute dispute = requireDispute(disputeId);
        support.evaluationForWrite(dispute.getEvaluationId(), user);
        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw ApiException.conflict("DISPUTE_NOT_OPEN", "The dispute has already been " + dispute.getStatus().name().toLowerCase());
        }
        return dispute;
    }

    private void close(GradeDispute dispute, DisputeStatus status, String resolution, User user, Instant now) {
        dispute.setStatus(status);
        dispute.setResolution(resolution.strip());
        dispute.setResolvedBy(user.getUserId());
        dispute.setResolvedAt(now);
        disputes.saveAndFlush(dispute);
    }

    private GradeDispute requireDispute(UUID disputeId) {
        return disputes.findById(disputeId)
                .orElseThrow(() -> ApiException.notFound("DISPUTE_NOT_FOUND", "Dispute not found"));
    }

    private List<DisputeDto> toDtos(List<GradeDispute> rows) {
        Map<UUID, GradeEvaluation> evaluationById = evaluations
                .findAllById(rows.stream().map(GradeDispute::getEvaluationId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(GradeEvaluation::getEvaluationId, Function.identity()));
        Map<UUID, GradingSupport.Student> students = support.students(
                rows.stream().map(GradeDispute::getStudentId).collect(Collectors.toSet()));
        Map<UUID, AttemptInfo> attempts = new HashMap<>();
        Map<UUID, ExamInfo> exams = new HashMap<>();
        return rows.stream().map(d -> {
            GradeEvaluation evaluation = evaluationById.get(d.getEvaluationId());
            AttemptInfo attempt = attempts.computeIfAbsent(evaluation.getAttemptId(),
                    id -> examApi.getAttempt(id).orElse(null));
            ExamInfo exam = attempt == null ? null
                    : exams.computeIfAbsent(attempt.vivaExamId(), id -> examApi.getExam(id).orElse(null));
            GradingSupport.Student student = students.getOrDefault(d.getStudentId(),
                    new GradingSupport.Student(d.getStudentId(), null, null));
            return new DisputeDto(d.getDisputeId(), d.getEvaluationId(), evaluation.getAttemptId(),
                    exam == null ? null : exam.vivaExamId(), exam == null ? null : exam.title(),
                    new StudentRef(student.id(), student.fullName(), student.studentCode()), d.getReason(),
                    GradingJson.uuids(d.getQuestionGradeIds()), d.getStatus().name(), d.getResolution(),
                    d.getResolvedBy(), d.getCreatedAt(), d.getResolvedAt(), evaluation.getStatus().name());
        }).toList();
    }
}
