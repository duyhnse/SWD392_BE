package swd392.group6.AIVES.interview.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import swd392.group6.AIVES.ai.AiUnavailableException;
import swd392.group6.AIVES.ai.InterviewAiPort;
import swd392.group6.AIVES.ai.InterviewAiPort.Analysis;
import swd392.group6.AIVES.ai.InterviewAiPort.AnswerResult;
import swd392.group6.AIVES.ai.SpeechToTextPort;
import swd392.group6.AIVES.ai.SpeechToTextRequest;
import swd392.group6.AIVES.ai.SynthesizedAudio;
import swd392.group6.AIVES.ai.Transcript;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.exam.AttemptControlApi;
import swd392.group6.AIVES.exam.ExamApi;
import swd392.group6.AIVES.exam.ExamApi.AttemptInfo;
import swd392.group6.AIVES.exam.ExamApi.AttemptQuestionInfo;
import swd392.group6.AIVES.exam.ExamApi.ExamInfo;
import swd392.group6.AIVES.interview.AttemptCompletedEvent;
import swd392.group6.AIVES.interview.internal.InterviewDtos.AttemptState;
import swd392.group6.AIVES.interview.internal.InterviewDtos.CurrentTurn;
import swd392.group6.AIVES.interview.internal.InterviewDtos.LiveAttempt;
import swd392.group6.AIVES.interview.internal.InterviewDtos.Notice;
import swd392.group6.AIVES.interview.internal.InterviewDtos.Progress;
import swd392.group6.AIVES.interview.internal.TurnStore.Turn;
import swd392.group6.AIVES.questionbank.QuestionBankApi;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.user.CourseAccessApi;
import swd392.group6.AIVES.user.NotificationApi;
import swd392.group6.AIVES.user.Role;
import swd392.group6.AIVES.user.User;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The adaptive viva loop of one attempt (WF3, 05 §4–§8): state + heartbeat, answers through the AI node, the
 * follow-up decision, time accounting (D46), disconnects (D51), the examiner's skip / end / resume and the sweeper.
 * <p>
 * Every change happens in a short transaction that first locks the attempt row; AI calls and TTS run between
 * transactions, so no lock is held while the AI node works (05 §4 sequence). Each change writes an
 * {@code attempt_events} row (BR-I5).
 */
@Slf4j
@Service
class InterviewService {

    static final Duration OFFLINE_AFTER = Duration.ofSeconds(10);
    static final Duration PROCESSING_STUCK_AFTER = Duration.ofSeconds(45);
    /** Upload / network allowance after the answer limit (05 §6). */
    static final int ANSWER_GRACE_SEC = 15;
    static final int MIN_ANSWER_LIMIT_SEC = 10;
    static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;
    private static final Map<String, String> AUDIO_TYPES = Map.of("audio/webm", "webm", "video/webm", "webm",
            "audio/mp4", "m4a", "video/mp4", "m4a", "audio/ogg", "ogg", "audio/mpeg", "mp3", "audio/wav", "wav",
            "audio/x-wav", "wav");
    /** What the browser may add to the audit trail (05 §10, FG5). */
    static final Set<String> CLIENT_EVENTS = Set.of("SILENCE_WARNING", "QUESTION_REPLAYED", "TAB_HIDDEN", "TAB_VISIBLE",
            "FULLSCREEN_EXIT", "FULLSCREEN_ENTER", "MIC_LOST", "CAMERA_LOST", "RECORDING_STARTED", "RECORDING_STOPPED",
            "RECORDING_UPLOAD_FAILED", "TTS_FALLBACK", "NETWORK_LOST", "NETWORK_BACK");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ExamApi exams;
    private final AttemptControlApi control;
    private final TurnStore turns;
    private final AttemptEventLog events;
    private final QuestionAudio questionAudio;
    private final InterviewAiPort ai;
    private final SpeechToTextPort stt;
    private final QuestionBankApi questionBank;
    private final StoragePort storage;
    private final CourseAccessApi courseAccess;
    private final NotificationApi notifications;
    private final ApplicationEventPublisher publisher;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;

    InterviewService(ExamApi exams, AttemptControlApi control, TurnStore turns, AttemptEventLog events,
                     QuestionAudio questionAudio, InterviewAiPort ai, SpeechToTextPort stt, QuestionBankApi questionBank,
                     StoragePort storage, CourseAccessApi courseAccess, NotificationApi notifications,
                     ApplicationEventPublisher publisher, JdbcTemplate jdbc, PlatformTransactionManager txManager,
                     Clock clock) {
        this.exams = exams;
        this.control = control;
        this.turns = turns;
        this.events = events;
        this.questionAudio = questionAudio;
        this.ai = ai;
        this.stt = stt;
        this.questionBank = questionBank;
        this.storage = storage;
        this.courseAccess = courseAccess;
        this.notifications = notifications;
        this.publisher = publisher;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
    }

    // ================================================================================================ student

    /** {@code GET /attempts/{id}/state}: heartbeat, reconnect handling, next question if due, then the state. */
    AttemptState state(UUID attemptId, User student) {
        own(attemptId, student);
        inTx(() -> heartbeat(attemptId));
        advance(attemptId);
        return view(attemptId);
    }

    AttemptState submitAnswer(UUID attemptId, UUID turnId, byte[] audio, String contentType, Integer durationSec,
                              User student) {
        own(attemptId, student);
        if (audio == null || audio.length == 0) {
            throw ApiException.unprocessable("AUDIO_EMPTY", "The answer recording is empty");
        }
        if (audio.length > MAX_AUDIO_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "AUDIO_TOO_LARGE", "An answer may be at most 10 MB");
        }
        String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase(Locale.ROOT);
        if (!AUDIO_TYPES.containsKey(type)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AUDIO_UNSUPPORTED_TYPE",
                    "Upload audio/webm, audio/mp4, audio/ogg, audio/mpeg or audio/wav");
        }
        Pending pending = inTx(() -> receive(attemptId, turnId, audio, type, durationSec));
        if (pending != null) {
            Outcome outcome = analyse(pending);
            inTx(() -> conclude(pending, outcome));
        }
        advance(attemptId);
        return view(attemptId);
    }

    /** The client heard no voice for the whole answer limit (EX-1) — next main question, no follow-up. */
    AttemptState noAnswer(UUID attemptId, UUID turnId, String reason, User student) {
        own(attemptId, student);
        inTx(() -> {
            AttemptInfo a = lockRunning(attemptId);
            Turn t = turnOf(a, turnId);
            if ("ASKED".equals(t.status())) {
                recordNoAnswer(a, t, reason == null ? "SILENCE" : reason, student.getUserId(), clock.instant());
            } else if (!t.answered() && !"PROCESSING".equals(t.status())) {
                throw turnNotCurrent();
            }
            return null;
        });
        advance(attemptId);
        return view(attemptId);
    }

    void clientEvent(UUID attemptId, String type, Map<String, Object> details, User student) {
        AttemptInfo a = own(attemptId, student);
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (!CLIENT_EVENTS.contains(t)) {
            throw ApiException.unprocessable("EVENT_TYPE_INVALID", "Unknown client event " + type);
        }
        boolean recent = a.endedAt() == null || clock.instant().isBefore(a.endedAt().plus(Duration.ofMinutes(10)));
        if (!recent) {
            throw ApiException.conflict("ATTEMPT_NOT_IN_PROGRESS", "This attempt is over");
        }
        Map<String, Object> payload = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
        payload.entrySet().removeIf(e -> e.getKey() == null || e.getKey().length() > 40);
        if (payload.size() > 20) {
            throw ApiException.unprocessable("EVENT_TOO_LARGE", "At most 20 details per event");
        }
        Turn open = turns.open(attemptId).orElse(null);
        events.log(attemptId, open == null ? null : open.turnId(), t, student.getUserId(), payload, clock.instant());
    }

    // ================================================================================================ lecturer

    /** "Bỏ qua câu này" (05 §7): the open turn is skipped and the next main question follows. */
    AttemptState skip(UUID attemptId, String reason, User lecturer) {
        requireWrite(attemptId, lecturer);
        inTx(() -> {
            AttemptInfo a = locked(attemptId);
            if (!"IN_PROGRESS".equals(a.status())) {
                throw notInProgress();
            }
            Instant now = clock.instant();
            Optional<AttemptQuestionInfo> current = currentQuestion(a.attemptId());
            Turn open = turns.open(a.attemptId()).orElse(null);
            if (open != null) {
                turns.close(open.turnId(), "SKIPPED_BY_LECTURER");
            }
            current.ifPresent(q -> control.finishQuestion(q.attemptQuestionId(), threadEnd(q, "SKIPPED"), now));
            events.log(a.attemptId(), open == null ? null : open.turnId(), "LECTURER_SKIPPED", lecturer.getUserId(),
                    payload("reason", blankToNull(reason), "orderNo", current.map(AttemptQuestionInfo::orderNo).orElse(null)), now);
            return null;
        });
        advance(attemptId);
        return view(attemptId);
    }

    /** "Kết thúc thi sớm": reason required; remaining questions are skipped. */
    AttemptState end(UUID attemptId, String reason, User lecturer) {
        requireWrite(attemptId, lecturer);
        if (reason == null || reason.isBlank()) {
            throw ApiException.unprocessable("REASON_REQUIRED", "Give a reason for ending the exam");
        }
        inTx(() -> {
            AttemptInfo a = locked(attemptId);
            if (!"IN_PROGRESS".equals(a.status()) && !"INTERRUPTED".equals(a.status())) {
                throw notInProgress();
            }
            Instant now = clock.instant();
            turns.open(a.attemptId()).ifPresent(t -> turns.close(t.turnId(), "CANCELLED"));
            for (AttemptQuestionInfo q : questions(a.attemptId())) {
                if ("IN_PROGRESS".equals(q.status())) {
                    control.finishQuestion(q.attemptQuestionId(), threadEnd(q, "SKIPPED"), now);
                } else if ("PENDING".equals(q.status())) {
                    control.finishQuestion(q.attemptQuestionId(), "SKIPPED", now);
                }
            }
            events.log(a.attemptId(), null, "LECTURER_ENDED", lecturer.getUserId(), payload("reason", reason.strip()), now);
            complete(a, "ENDED_BY_LECTURER", now);
            return null;
        });
        return view(attemptId);
    }

    /** Resume an INTERRUPTED attempt (D51): frozen time is given back and the open question is asked again. */
    AttemptState resume(UUID attemptId, User lecturer) {
        requireWrite(attemptId, lecturer);
        inTx(() -> {
            AttemptInfo a = locked(attemptId);
            if (!"INTERRUPTED".equals(a.status())) {
                throw ApiException.conflict("ATTEMPT_NOT_INTERRUPTED", "Only an interrupted attempt can be resumed");
            }
            Instant now = clock.instant();
            int frozen = a.disconnectedSince() == null ? 0 : seconds(a.disconnectedSince(), now);
            control.resume(a.attemptId(), now);
            turns.open(a.attemptId()).ifPresent(t -> turns.reask(t.turnId(), now));
            events.log(a.attemptId(), null, "LECTURER_RESUMED", lecturer.getUserId(), payload("frozenSec", frozen), now);
            return null;
        });
        advance(attemptId);
        return view(attemptId);
    }

    /** Live monitor of a buổi thi (05 §7): one row per checked-in student. */
    List<LiveAttempt> live(UUID vivaExamId, User lecturer) {
        ExamInfo exam = exams.getExam(vivaExamId)
                .orElseThrow(() -> ApiException.notFound("VIVA_EXAM_NOT_FOUND", "Viva exam not found"));
        courseAccess.requireRead(exam.courseId(), lecturer);
        Instant now = clock.instant();
        return exams.listAttempts(vivaExamId).stream().map(a -> liveRow(a, exam, now)).toList();
    }

    LiveAttempt liveAttempt(UUID attemptId, User lecturer) {
        AttemptInfo a = attempt(attemptId);
        courseAccess.requireRead(a.courseId(), lecturer);
        return liveRow(a, exam(a), clock.instant());
    }

    // ================================================================================================ sweeper

    /** Time up, offline detection, stuck processing, overdue answers (05 §6, §8 EX-1, EX-9). */
    void sweep() {
        for (UUID id : exams.runningAttemptIds()) {
            try {
                inTx(() -> sweepOne(id));
                advance(id);
            } catch (RuntimeException e) {
                log.warn("Interview sweep failed for attempt {}: {}", id, e.toString());
            }
        }
    }

    private Void sweepOne(UUID attemptId) {
        AttemptInfo a = locked(attemptId);
        if (!"IN_PROGRESS".equals(a.status())) {
            return null;
        }
        Instant now = clock.instant();
        if (a.deadlineAt() != null && now.isAfter(a.deadlineAt())) {
            timeUp(a, now);
            return null;
        }
        ExamInfo exam = exam(a);
        Instant since = a.disconnectedSince();
        if (since == null && lastSeen(a).plus(OFFLINE_AFTER).isBefore(now)) {
            since = lastSeen(a);
            control.markDisconnected(a.attemptId(), since);
            events.log(a.attemptId(), null, "DISCONNECTED", null, payload("lastSeenAt", since.toString()), now);
        }
        if (since != null && seconds(since, now) > exam.reconnectGraceSec()) {
            interrupt(a, exam, now, payload("offlineSec", seconds(since, now), "rule", "RECONNECT_GRACE"));
            return null;
        }
        Turn open = turns.open(a.attemptId()).orElse(null);
        if (open == null) {
            return null;
        }
        if ("PROCESSING".equals(open.status()) && open.answerSubmittedAt() != null
                && open.answerSubmittedAt().plus(PROCESSING_STUCK_AFTER).isBefore(now)) {
            turns.reask(open.turnId(), now);
            events.log(a.attemptId(), open.turnId(), "PROCESSING_RESET", null, null, now);
        } else if ("ASKED".equals(open.status()) && since == null) {
            AttemptQuestionInfo q = question(a.attemptId(), open.attemptQuestionId());
            Instant overdue = open.askedAt().plusMillis(audioMs(open))
                    .plusSeconds(answerLimit(exam, q) + ANSWER_GRACE_SEC);
            if (now.isAfter(overdue)) {
                recordNoAnswer(a, open, "TIMEOUT", null, now);
            }
        }
        return null;
    }

    // ================================================================================================ answer pipeline

    /** Facts of one submitted answer, gathered in tx 1 and used by the AI phase and tx 2. */
    private record Pending(UUID attemptId, UUID courseId, Turn turn, AttemptQuestionInfo question, List<Turn> priorTurns,
                           int maxFollowups, int threadRemainingSec, int attemptRemainingSec, byte[] audio,
                           String contentType, Instant submittedAt, String nextMainText, String nextMainLanguage) {
    }

    private record Outcome(String status, String transcript, String analysisJson, String decision, String followUpText,
                           QuestionAudio.Stored followUpAudio, List<Map.Entry<String, Map<String, Object>>> events,
                           int processingMs) {
    }

    /** Tx 1: check the turn is the current one, store the clip, charge the answering time (D46). */
    private Pending receive(UUID attemptId, UUID turnId, byte[] audio, String type, Integer durationSec) {
        AttemptInfo a = lockRunning(attemptId);
        Instant now = clock.instant();
        control.touch(attemptId, now);
        Turn t = turnOf(a, turnId);
        if (!"ASKED".equals(t.status())) {
            if ("PROCESSING".equals(t.status()) || t.answered()) {
                return null; // retry of an upload that already arrived (EX-10)
            }
            throw turnNotCurrent();
        }
        ExamInfo exam = exam(a);
        AttemptQuestionInfo q = question(attemptId, t.attemptQuestionId());
        int limit = answerLimit(exam, q);
        long counted = Duration.between(t.askedAt().plusMillis(audioMs(t)), now).toMillis();
        int countedMs = (int) Math.max(0, Math.min(counted, limit * 1000L));
        String key = "attempts/%s/turns/%03d-%s.%s".formatted(attemptId, t.turnOrder(), turnId, AUDIO_TYPES.get(type));
        storage.put(key, audio, type);
        turns.markProcessing(turnId, now, durationSec, countedMs, key);
        int countedSec = Math.round(countedMs / 1000f);
        control.chargeTime(q.attemptQuestionId(), countedSec);
        events.log(attemptId, turnId, "ANSWER_SUBMITTED", a.studentId(),
                payload("sizeBytes", audio.length, "durationSec", durationSec, "countedSec", countedSec), now);
        List<Turn> prior = turns.thread(q.attemptQuestionId()).stream()
                .filter(x -> x.followupIndex() < t.followupIndex()).toList();
        Optional<AttemptQuestionInfo> next = questions(attemptId).stream()
                .filter(x -> "PENDING".equals(x.status())).findFirst();
        return new Pending(attemptId, a.courseId(), t, q, prior, exam.maxFollowupsPerQuestion(),
                q.timeBudgetSec() - q.timeUsedSec() - countedSec, a.deadlineAt() == null ? Integer.MAX_VALUE : seconds(now, a.deadlineAt()),
                audio, type, now, next.map(AttemptQuestionInfo::content).orElse(null),
                next.map(AttemptQuestionInfo::language).orElse(null));
    }

    /** AI phase, no transaction: transcript + analysis (or only a transcript when NEXT is already forced). */
    private Outcome analyse(Pending p) {
        Turn t = p.turn();
        Language language = Language.valueOf(p.question().language());
        List<Map.Entry<String, Map<String, Object>>> notes = new ArrayList<>();
        String forced = FollowupDecider.forced(t.followupIndex(), p.maxFollowups(), p.threadRemainingSec(),
                p.attemptRemainingSec());
        List<String> hotwords = questionBank.courseTerms(p.courseId()).stream().limit(50).toList();
        Transcript transcript = null;
        Analysis analysis = null;
        String analysisError = null;
        SynthesizedAudio followUpAudio = null;
        if (forced == null) {
            try {
                AnswerResult r = ai.processAnswer(new InterviewAiPort.AnswerRequest(t.turnId().toString(), language,
                        p.audio(), p.contentType(), hotwords, p.question().content(), p.question().referenceAnswer(),
                        p.priorTurns().stream().map(x -> new InterviewAiPort.PriorTurn(x.type(), x.questionText(),
                                x.transcript() == null ? "" : x.transcript())).toList(),
                        t.questionText(), t.followupIndex(), p.maxFollowups(), true));
                transcript = r.transcript();
                analysis = r.analysis();
                analysisError = r.analysisError();
                followUpAudio = r.followUpAudio();
            } catch (AiUnavailableException e) {
                analysisError = "AI_UNAVAILABLE";
                log.warn("process-answer failed for turn {}: {}", t.turnId(), e.getMessage());
            }
        }
        if (transcript == null) {
            transcript = transcribe(p, language, hotwords);
        }
        String status;
        String text;
        String decision;
        if (transcript == null) {
            status = "TRANSCRIPTION_FAILED";
            text = null;
            decision = forced != null ? forced : "NEXT_AI_ERROR";
            notes.add(Map.entry("STT_FAILED", payload("turnType", t.type())));
        } else if (FollowupDecider.isNoAnswer(transcript.text())) {
            status = "NO_ANSWER";
            text = TurnStore.NO_ANSWER_TEXT;
            decision = "NEXT_NO_ANSWER";
        } else {
            status = "ANSWERED";
            text = transcript.text();
            decision = forced != null ? forced : FollowupDecider.decide(analysis);
            if (forced == null && analysis == null) {
                notes.add(Map.entry("LLM_FAILED", payload("error", analysisError == null ? "ANALYSIS_FAILED" : analysisError)));
            }
        }
        String followUpText = null;
        QuestionAudio.Stored followUpStored = null;
        if ("FOLLOW_UP".equals(decision)) {
            followUpText = analysis.followUpQuestion().strip();
            followUpStored = questionAudio.store(followUpText, language, followUpAudio).orElse(null);
            if (followUpStored == null) {
                notes.add(Map.entry("TTS_FAILED", payload("turnType", "FOLLOW_UP")));
            }
        } else if (p.nextMainText() != null) {
            questionAudio.forText(p.nextMainText(), Language.valueOf(p.nextMainLanguage())); // warm the cache
        }
        Map<String, Object> stored = new LinkedHashMap<>();
        if (analysis != null) {
            stored.put("analysis", analysis);
        }
        if (analysisError != null) {
            stored.put("analysisError", analysisError);
        }
        if (forced != null) {
            stored.put("skippedBecause", forced);
        }
        if (transcript != null) {
            stored.put("transcriptModel", transcript.model());
            stored.put("sttLatencyMs", transcript.latencyMs());
        }
        int processingMs = (int) Duration.between(p.submittedAt(), clock.instant()).toMillis();
        return new Outcome(status, text, stored.isEmpty() ? null : JSON.writeValueAsString(stored), decision,
                followUpText, followUpStored, notes, Math.max(0, processingMs));
    }

    /** STT alone, one retry (EX-4). Null when both tries fail. */
    private Transcript transcribe(Pending p, Language language, List<String> hotwords) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return stt.transcribe(new SpeechToTextRequest(p.audio(), p.contentType(), language, String.join(", ", hotwords)));
            } catch (AiUnavailableException e) {
                log.warn("STT failed for turn {} (try {}): {}", p.turn().turnId(), attempt + 1, e.getMessage());
            }
        }
        return null;
    }

    /** Tx 2: store the outcome; ask the follow-up or close the thread — unless the examiner acted meanwhile. */
    private Void conclude(Pending p, Outcome o) {
        AttemptInfo a = locked(p.attemptId());
        Instant now = clock.instant();
        Turn t = turns.get(p.turn().turnId()).orElseThrow();
        boolean stillProcessing = "PROCESSING".equals(t.status());
        turns.saveResult(t.turnId(), o.status(), o.transcript(), o.analysisJson(), o.decision(), o.processingMs());
        for (Map.Entry<String, Map<String, Object>> note : o.events()) {
            events.log(a.attemptId(), t.turnId(), note.getKey(), null, note.getValue(), now);
        }
        boolean running = "IN_PROGRESS".equals(a.status()) || "INTERRUPTED".equals(a.status());
        if (!stillProcessing || !running) {
            return null;
        }
        control.extendDeadline(a.attemptId(), Math.round(o.processingMs() / 1000f)); // AI time is never charged (D46)
        events.log(a.attemptId(), t.turnId(), "ANSWER_PROCESSED", null,
                payload("decision", o.decision(), "status", o.status(), "processingMs", o.processingMs()), now);
        if ("FOLLOW_UP".equals(o.decision())) {
            UUID id = turns.ask(a.attemptId(), t.attemptQuestionId(), t.questionId(), t.turnId(), t.followupIndex() + 1,
                    o.followUpText(), t.language(), o.followUpAudio(), now);
            events.log(a.attemptId(), id, "QUESTION_ASKED", null,
                    payload("turnType", "FOLLOW_UP", "followupIndex", t.followupIndex() + 1), now);
        } else {
            control.finishQuestion(t.attemptQuestionId(), "DONE", now);
        }
        return null;
    }

    private void recordNoAnswer(AttemptInfo a, Turn t, String reason, UUID actor, Instant now) {
        ExamInfo exam = exam(a);
        AttemptQuestionInfo q = question(a.attemptId(), t.attemptQuestionId());
        int limit = answerLimit(exam, q);
        turns.noAnswer(t.turnId(), now, limit * 1000);
        control.chargeTime(q.attemptQuestionId(), limit);
        events.log(a.attemptId(), t.turnId(), "NO_ANSWER", actor, payload("reason", reason), now);
        control.finishQuestion(q.attemptQuestionId(), "DONE", now);
    }

    // ================================================================================================ progression

    private record NextQuestion(UUID attemptQuestionId, UUID questionId, String text, String language) {
    }

    /** Creates the next MAIN turn when none is open, or completes the attempt. TTS runs between transactions. */
    void advance(UUID attemptId) {
        for (int i = 0; i < 4; i++) {
            NextQuestion next = inTx(() -> plan(attemptId));
            if (next == null) {
                return;
            }
            QuestionAudio.Stored audio = questionAudio.forText(next.text(), Language.valueOf(next.language())).orElse(null);
            if (Boolean.TRUE.equals(inTx(() -> askMain(attemptId, next, audio)))) {
                return;
            }
        }
    }

    private NextQuestion plan(UUID attemptId) {
        AttemptInfo a = locked(attemptId);
        if (!"IN_PROGRESS".equals(a.status())) {
            return null;
        }
        Instant now = clock.instant();
        if (a.deadlineAt() != null && now.isAfter(a.deadlineAt())) {
            timeUp(a, now);
            return null;
        }
        if (turns.open(attemptId).isPresent()) {
            return null;
        }
        List<AttemptQuestionInfo> qs = questions(attemptId);
        for (AttemptQuestionInfo q : qs) {
            if ("IN_PROGRESS".equals(q.status())) { // left without an open turn (crash between steps)
                control.finishQuestion(q.attemptQuestionId(), threadEnd(q, "SKIPPED"), now);
            }
        }
        Optional<AttemptQuestionInfo> next = qs.stream().filter(q -> "PENDING".equals(q.status())).findFirst();
        if (next.isEmpty()) {
            complete(a, "ALL_QUESTIONS_DONE", now);
            return null;
        }
        AttemptQuestionInfo q = next.get();
        return new NextQuestion(q.attemptQuestionId(), q.questionId(), q.content(), q.language());
    }

    private Boolean askMain(UUID attemptId, NextQuestion next, QuestionAudio.Stored audio) {
        AttemptInfo a = locked(attemptId);
        if (!"IN_PROGRESS".equals(a.status()) || turns.open(attemptId).isPresent()) {
            return true; // someone else moved on; nothing to do
        }
        AttemptQuestionInfo q = question(attemptId, next.attemptQuestionId());
        if (!"PENDING".equals(q.status())) {
            return false;
        }
        Instant now = clock.instant();
        control.startQuestion(q.attemptQuestionId(), now);
        UUID id = turns.ask(attemptId, q.attemptQuestionId(), q.questionId(), null, 0, q.content(), q.language(), audio, now);
        events.log(attemptId, id, "QUESTION_ASKED", null, payload("turnType", "MAIN", "orderNo", q.orderNo()), now);
        if (audio == null) {
            events.log(attemptId, id, "TTS_FAILED", null, payload("turnType", "MAIN"), now);
        }
        return true;
    }

    private void timeUp(AttemptInfo a, Instant now) {
        turns.open(a.attemptId()).filter(t -> "ASKED".equals(t.status())).ifPresent(t -> turns.close(t.turnId(), "CANCELLED"));
        for (AttemptQuestionInfo q : questions(a.attemptId())) {
            if ("IN_PROGRESS".equals(q.status())) {
                control.finishQuestion(q.attemptQuestionId(), threadEnd(q, "NOT_REACHED"), now);
            } else if ("PENDING".equals(q.status())) {
                control.finishQuestion(q.attemptQuestionId(), "NOT_REACHED", now);
            }
        }
        events.log(a.attemptId(), null, "TIME_UP", null, null, now);
        complete(a, "TIME_UP", now);
    }

    /** BR-I9: exactly once, in the transaction that completes the attempt. */
    private void complete(AttemptInfo a, String reason, Instant now) {
        control.complete(a.attemptId(), reason, now);
        events.log(a.attemptId(), null, "ATTEMPT_COMPLETED", null, payload("reason", reason), now);
        List<UUID> asked = turns.all(a.attemptId()).stream().map(Turn::questionId).distinct().toList();
        publisher.publishEvent(new AttemptCompletedEvent(a.attemptId(), a.courseId(), asked, reason));
    }

    // ================================================================================================ connection

    private Void heartbeat(UUID attemptId) {
        AttemptInfo a = locked(attemptId);
        Instant now = clock.instant();
        if ("IN_PROGRESS".equals(a.status())) {
            Instant since = a.disconnectedSince() != null ? a.disconnectedSince()
                    : lastSeen(a).plus(OFFLINE_AFTER).isBefore(now) ? lastSeen(a) : null;
            if (since != null) {
                reconnected(a, since, now);
            } else {
                control.touch(attemptId, now);
            }
        } else if ("INTERRUPTED".equals(a.status())) {
            control.touch(attemptId, now);
        }
        return null;
    }

    /** Back online (D51): freeze the outage, then re-ask, replace the main question, or interrupt. */
    private void reconnected(AttemptInfo a, Instant since, Instant now) {
        ExamInfo exam = exam(a);
        int offline = seconds(since, now);
        control.reconnect(a.attemptId(), offline, now);
        int count = a.disconnectCount() + 1;
        int frozen = a.frozenSecTotal() + offline;
        if (count > exam.maxDisconnects() || frozen > exam.maxFrozenSec() || offline > exam.reconnectGraceSec()) {
            events.log(a.attemptId(), null, "RECONNECTED", a.studentId(),
                    payload("offlineSec", offline, "disconnectCount", count, "frozenSecTotal", frozen, "action", "INTERRUPTED"), now);
            String rule = count > exam.maxDisconnects() ? "MAX_DISCONNECTS"
                    : frozen > exam.maxFrozenSec() ? "MAX_FROZEN" : "RECONNECT_GRACE";
            interrupt(a, exam, now, payload("rule", rule, "disconnectCount", count, "frozenSecTotal", frozen));
            return;
        }
        String action = "NONE";
        Turn open = turns.open(a.attemptId()).orElse(null);
        if (open != null && "ASKED".equals(open.status())) {
            action = "REASKED";
            if (open.followupIndex() == 0 && offline > exam.replaceMainAfterSec()) {
                Optional<AttemptQuestionInfo> replacement = control.replaceQuestion(open.attemptQuestionId(),
                        "REPLACED_AFTER_DISCONNECT", now);
                if (replacement.isPresent()) {
                    turns.close(open.turnId(), "CANCELLED");
                    action = "REPLACED";
                    events.log(a.attemptId(), open.turnId(), "QUESTION_REPLACED", null,
                            payload("voidedAttemptQuestionId", open.attemptQuestionId().toString(),
                                    "newAttemptQuestionId", replacement.get().attemptQuestionId().toString(),
                                    "offlineSec", offline), now);
                } else {
                    action = "REASKED_NO_REPLACEMENT";
                }
            }
            if (!"REPLACED".equals(action)) {
                turns.reask(open.turnId(), now);
            }
        }
        events.log(a.attemptId(), open == null ? null : open.turnId(), "RECONNECTED", a.studentId(),
                payload("offlineSec", offline, "disconnectCount", count, "frozenSecTotal", frozen, "action", action), now);
    }

    private void interrupt(AttemptInfo a, ExamInfo exam, Instant now, Map<String, Object> details) {
        control.interrupt(a.attemptId(), now);
        events.log(a.attemptId(), null, "INTERRUPTED", null, details, now);
        UUID examiner = a.examinerId() != null ? a.examinerId() : exam.examinerId();
        if (examiner != null) {
            notifications.notify(examiner, "ATTEMPT_INTERRUPTED", "Lượt thi bị gián đoạn — " + exam.title(),
                    "Sinh viên mất kết nối quá giới hạn. Mở giám sát trực tiếp để tiếp tục hoặc kết thúc lượt thi.",
                    Map.of("attemptId", a.attemptId().toString(), "vivaExamId", a.vivaExamId().toString()));
        }
    }

    // ================================================================================================ views

    AttemptState view(UUID attemptId) {
        AttemptInfo a = attempt(attemptId);
        ExamInfo exam = exam(a);
        Instant now = clock.instant();
        List<AttemptQuestionInfo> qs = questions(attemptId);
        Turn open = turns.open(attemptId).orElse(null);
        AttemptQuestionInfo current = open == null ? null
                : qs.stream().filter(q -> q.attemptQuestionId().equals(open.attemptQuestionId())).findFirst().orElse(null);
        Progress progress = progress(qs, current, open, exam);
        CurrentTurn turn = null;
        if (open != null && current != null && !"COMPLETED".equals(a.status())) {
            String audioUrl = open.questionAudioKey() == null ? null : "/api/v1/turns/" + open.turnId() + "/question-audio";
            turn = new CurrentTurn(open.turnId(), open.type(), open.language(), open.questionText(), audioUrl, open.questionAudioMs(),
                    open.askedAt(), answerLimit(exam, current), open.status());
        }
        AttemptEventLog.Notice n = events.notice(attemptId, now);
        return new AttemptState(attemptId, exam.title(), a.status(), a.endReason(), events.version(attemptId), now,
                a.deadlineAt(), remaining(a, now), progress, turn, n == null ? null : new Notice(n.type(), n.at()),
                exam.showQuestionText(), exam.silenceWarningSec());
    }

    private LiveAttempt liveRow(AttemptInfo a, ExamInfo exam, Instant now) {
        List<AttemptQuestionInfo> qs = questions(a.attemptId());
        Turn open = turns.open(a.attemptId()).orElse(null);
        AttemptQuestionInfo current = open == null ? null
                : qs.stream().filter(q -> q.attemptQuestionId().equals(open.attemptQuestionId())).findFirst().orElse(null);
        Progress p = progress(qs, current, open, exam);
        Optional<Turn> last = turns.all(a.attemptId()).stream().filter(t -> t.processingMs() != null)
                .reduce((x, y) -> y);
        record Rec(int chunks, Instant last) {
        }
        Rec rec = jdbc.queryForObject("select count(*), max(uploaded_at) from attempt_recordings where attempt_id = ?",
                (rs, i) -> new Rec(rs.getInt(1), rs.getTimestamp(2) == null ? null : rs.getTimestamp(2).toInstant()),
                a.attemptId());
        Integer turnCount = jdbc.queryForObject("select count(*) from exam_turns where attempt_id = ?", Integer.class,
                a.attemptId());
        boolean online = "IN_PROGRESS".equals(a.status()) && a.disconnectedSince() == null
                && !lastSeen(a).plus(OFFLINE_AFTER).isBefore(now);
        return new LiveAttempt(a.attemptId(), a.studentId(), a.status(), a.endReason(), a.startedAt(), a.deadlineAt(),
                a.endedAt(), remaining(a, now), p.threadIndex(), p.threadCount(), p.followupIndex(), p.maxFollowups(),
                p.threadRemainingSec(), open == null ? null : open.type(), open == null ? null : open.status(),
                open == null ? null : open.questionText(), a.lastSeenAt(), online, a.disconnectedSince(),
                a.disconnectCount(), a.frozenSecTotal(), last.map(Turn::processingMs).orElse(null),
                turnCount == null ? 0 : turnCount, Objects.requireNonNull(rec).chunks(), rec.last(), events.version(a.attemptId()));
    }

    private static Progress progress(List<AttemptQuestionInfo> qs, AttemptQuestionInfo current, Turn open, ExamInfo exam) {
        int count = qs.size();
        int index = current == null ? (int) qs.stream().filter(q -> !"PENDING".equals(q.status())).count()
                : qs.indexOf(current) + 1;
        int threadRemaining = current == null ? 0 : Math.max(0, current.timeBudgetSec() - current.timeUsedSec());
        return new Progress(index, count, open == null ? 0 : open.followupIndex(), exam.maxFollowupsPerQuestion(),
                threadRemaining);
    }

    // ================================================================================================ helpers

    private int answerLimit(ExamInfo exam, AttemptQuestionInfo q) {
        int left = q.timeBudgetSec() - q.timeUsedSec();
        return Math.max(MIN_ANSWER_LIMIT_SEC, Math.min(exam.maxAnswerSec(), left));
    }

    private static int remaining(AttemptInfo a, Instant now) {
        if (a.deadlineAt() == null || "COMPLETED".equals(a.status()) || "CANCELLED".equals(a.status())) {
            return 0;
        }
        // While disconnected / interrupted the clock is frozen at the disconnect (D51).
        Instant reference = a.disconnectedSince() != null ? a.disconnectedSince() : now;
        return Math.max(0, seconds(reference, a.deadlineAt()));
    }

    private static long audioMs(Turn t) {
        return t.questionAudioMs() == null ? 0 : t.questionAudioMs();
    }

    /** DONE when the thread got at least one answer, else {@code otherwise}. */
    private String threadEnd(AttemptQuestionInfo q, String otherwise) {
        return turns.thread(q.attemptQuestionId()).stream().anyMatch(Turn::answered) ? "DONE" : otherwise;
    }

    private Optional<AttemptQuestionInfo> currentQuestion(UUID attemptId) {
        return questions(attemptId).stream().filter(q -> "IN_PROGRESS".equals(q.status())).findFirst();
    }

    /** Questions of the attempt without the voided ones, in asking order. */
    private List<AttemptQuestionInfo> questions(UUID attemptId) {
        return exams.getAttemptQuestions(attemptId).stream().filter(q -> !"VOIDED".equals(q.status())).toList();
    }

    private AttemptQuestionInfo question(UUID attemptId, UUID attemptQuestionId) {
        return exams.getAttemptQuestions(attemptId).stream()
                .filter(q -> q.attemptQuestionId().equals(attemptQuestionId)).findFirst().orElseThrow();
    }

    private static Instant lastSeen(AttemptInfo a) {
        return a.lastSeenAt() != null ? a.lastSeenAt() : a.startedAt();
    }

    private AttemptInfo attempt(UUID attemptId) {
        return exams.getAttempt(attemptId).orElseThrow(InterviewService::attemptNotFound);
    }

    private ExamInfo exam(AttemptInfo a) {
        return exams.getExam(a.vivaExamId()).orElseThrow();
    }

    private AttemptInfo locked(UUID attemptId) {
        return control.lock(attemptId).orElseThrow(InterviewService::attemptNotFound);
    }

    private AttemptInfo lockRunning(UUID attemptId) {
        AttemptInfo a = locked(attemptId);
        if (!"IN_PROGRESS".equals(a.status())) {
            throw notInProgress();
        }
        return a;
    }

    private Turn turnOf(AttemptInfo a, UUID turnId) {
        return turns.get(turnId).filter(t -> t.attemptId().equals(a.attemptId()))
                .orElseThrow(() -> ApiException.notFound("TURN_NOT_FOUND", "Turn not found"));
    }

    /** The caller must be the attempt's student (BR-I6); anyone else gets 404. */
    private AttemptInfo own(UUID attemptId, User student) {
        AttemptInfo a = attempt(attemptId);
        if (student.getRole() != Role.STUDENT || !a.studentId().equals(student.getUserId())) {
            throw attemptNotFound();
        }
        return a;
    }

    private void requireWrite(UUID attemptId, User lecturer) {
        courseAccess.requireWrite(attempt(attemptId).courseId(), lecturer);
    }

    private <T> T inTx(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    private static int seconds(Instant from, Instant to) {
        return (int) Math.max(0, Duration.between(from, to).toSeconds());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Small JSON payloads; null values are dropped. */
    private static Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) {
                map.put((String) keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }

    private static ApiException attemptNotFound() {
        return ApiException.notFound("ATTEMPT_NOT_FOUND", "Attempt not found");
    }

    private static ApiException notInProgress() {
        return ApiException.conflict("ATTEMPT_NOT_IN_PROGRESS", "This attempt is not running");
    }

    private static ApiException turnNotCurrent() {
        return ApiException.conflict("TURN_NOT_CURRENT", "This question is no longer the current one");
    }
}
