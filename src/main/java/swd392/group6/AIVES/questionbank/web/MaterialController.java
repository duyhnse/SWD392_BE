package swd392.group6.AIVES.questionbank.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.questionbank.service.MaterialService;
import swd392.group6.AIVES.questionbank.dto.MaterialDto;
import swd392.group6.AIVES.questionbank.dto.MaterialFile;
import swd392.group6.AIVES.questionbank.dto.MaterialTopicRequest;
import swd392.group6.AIVES.user.User;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** Course materials (15 §5.2) and their indexing for RAG (04 §9.1). */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('ADMIN', 'LECTURER')")
@RequiredArgsConstructor
class MaterialController {

    private final MaterialService service;

    @GetMapping("/courses/{courseId}/materials")
    public List<MaterialDto> list(@PathVariable UUID courseId, @RequestParam(required = false) UUID topicId,
                                  @AuthenticationPrincipal User user) {
        return service.list(courseId, topicId, user);
    }

    @PostMapping(path = "/courses/{courseId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialDto upload(@PathVariable UUID courseId, @RequestPart(value = "file", required = false) MultipartFile file,
                              @RequestParam(required = false) UUID topicId, @AuthenticationPrincipal User user) {
        return service.upload(courseId, file, topicId, user);
    }

    @GetMapping("/materials/{id}")
    public MaterialDto get(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.get(id, user);
    }

    @GetMapping("/materials/{id}/file")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        MaterialFile file = service.download(id, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.data());
    }

    @PatchMapping("/materials/{id}")
    public MaterialDto setTopic(@PathVariable UUID id, @RequestBody MaterialTopicRequest request,
                                @AuthenticationPrincipal User user) {
        return service.setTopic(id, request.topicId(), user);
    }

    @PostMapping("/materials/{id}/reindex")
    public MaterialDto reindex(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        return service.reindex(id, user);
    }

    @DeleteMapping("/materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        service.delete(id, user);
    }
}
