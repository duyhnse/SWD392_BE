package swd392.group6.AIVES.interview.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.interview.internal.InterviewDtos.AttemptState;
import swd392.group6.AIVES.interview.internal.InterviewDtos.ClientEventRequest;
import swd392.group6.AIVES.interview.internal.InterviewDtos.LiveAttempt;
import swd392.group6.AIVES.interview.internal.InterviewDtos.NoAnswerRequest;
import swd392.group6.AIVES.interview.internal.InterviewDtos.ReasonRequest;
import swd392.group6.AIVES.user.User;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/** WF3 runtime endpoints (05 §11): the student's exam loop and the examiner's live control. */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
class InterviewController {

    private final InterviewService service;

    // ---- student ------------------------------------------------------------------------------------------

    /** State of the attempt; polled every 3–5 s, it is also the heartbeat (05 §6). */
    @GetMapping("/attempts/{id}/state")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptState state(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.state(id, user);
    }

    @PostMapping("/attempts/{id}/turns/{turnId}/answer")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptState answer(@PathVariable UUID id, @PathVariable UUID turnId, @RequestPart("audio") MultipartFile audio,
                               @RequestPart(value = "tail", required = false) MultipartFile tail,
                               @RequestParam(required = false) Integer durationSec,
                               @RequestParam(required = false) Integer parts,
                               @AuthenticationPrincipal User user) throws IOException {
        return service.submitAnswer(id, turnId, audio.getBytes(), audio.getContentType(), durationSec,
                tail == null ? null : tail.getBytes(), tail == null ? null : tail.getContentType(), parts, user);
    }

    /** D74: a ~25 s part of the answer being recorded, transcribed in the background. */
    @PostMapping("/attempts/{id}/turns/{turnId}/answer-parts/{index}")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void answerPart(@PathVariable UUID id, @PathVariable UUID turnId, @PathVariable int index,
                           @RequestPart("audio") MultipartFile audio, @AuthenticationPrincipal User user) throws IOException {
        service.answerPart(id, turnId, index, audio.getBytes(), audio.getContentType(), user);
    }

    @PostMapping("/attempts/{id}/turns/{turnId}/no-answer")
    @PreAuthorize("hasRole('STUDENT')")
    public AttemptState noAnswer(@PathVariable UUID id, @PathVariable UUID turnId,
                                 @RequestBody(required = false) NoAnswerRequest body, @AuthenticationPrincipal User user) {
        return service.noAnswer(id, turnId, body == null ? null : body.reason(), user);
    }

    /** Browser-side observations for the audit trail (silence warning, tab hidden, camera lost …). */
    @PostMapping("/attempts/{id}/client-events")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clientEvent(@PathVariable UUID id, @RequestBody ClientEventRequest body, @AuthenticationPrincipal User user) {
        service.clientEvent(id, body.type(), body.details(), user);
    }

    // ---- lecturer -----------------------------------------------------------------------------------------

    @GetMapping("/viva-exams/{id}/live")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public List<LiveAttempt> live(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.live(id, user);
    }

    @GetMapping("/attempts/{id}/live")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public LiveAttempt liveAttempt(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.liveAttempt(id, user);
    }

    @PostMapping("/attempts/{id}/skip-current")
    @PreAuthorize("hasRole('LECTURER')")
    public LiveAttempt skip(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest body,
                            @AuthenticationPrincipal User user) {
        service.skip(id, body == null ? null : body.reason(), user);
        return service.liveAttempt(id, user);
    }

    @PostMapping("/attempts/{id}/end")
    @PreAuthorize("hasRole('LECTURER')")
    public LiveAttempt end(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest body,
                           @AuthenticationPrincipal User user) {
        service.end(id, body == null ? null : body.reason(), user);
        return service.liveAttempt(id, user);
    }

    /** Void the attempt (cheating …): reason required, never graded (D65). */
    @PostMapping("/attempts/{id}/void")
    @PreAuthorize("hasRole('LECTURER')")
    public LiveAttempt voidAttempt(@PathVariable UUID id, @RequestBody(required = false) ReasonRequest body,
                                   @AuthenticationPrincipal User user) {
        service.voidAttempt(id, body == null ? null : body.reason(), user);
        return service.liveAttempt(id, user);
    }

    /** Student browser: latest camera / screen still for the live proctoring view (every ~5 s). */
    @PutMapping("/attempts/{id}/live-frame/{kind}")
    @PreAuthorize("hasRole('STUDENT')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void putFrame(@PathVariable UUID id, @PathVariable String kind, @RequestPart("file") MultipartFile file,
                         @AuthenticationPrincipal User user) throws IOException {
        service.putFrame(id, kind, file.getBytes(), file.getContentType(), user);
    }

    @GetMapping("/attempts/{id}/live-frame/{kind}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
    public ResponseEntity<byte[]> frame(@PathVariable UUID id, @PathVariable String kind, @AuthenticationPrincipal User user) {
        LiveFrames.Frame f = service.frame(id, kind, user);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).cacheControl(CacheControl.noStore())
                .lastModified(f.at().toEpochMilli()).body(f.data());
    }

    @PostMapping("/attempts/{id}/resume")
    @PreAuthorize("hasRole('LECTURER')")
    public LiveAttempt resume(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        service.resume(id, user);
        return service.liveAttempt(id, user);
    }
}
