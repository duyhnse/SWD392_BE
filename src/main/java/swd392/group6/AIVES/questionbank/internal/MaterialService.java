package swd392.group6.AIVES.questionbank.internal;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.MaterialDto;
import swd392.group6.AIVES.questionbank.internal.QuestionBankDtos.MaterialFile;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.user.User;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Course materials: upload / list / download / delete (04 §9.1 step 1; indexing is the RAG milestone). */
@Service
@RequiredArgsConstructor
@Transactional
public class MaterialService {

    public static final long MAX_BYTES = 50L * 1024 * 1024;

    /** Allowed extension → canonical content type (PDF/PPTX/DOCX). */
    static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private static final String NOT_FOUND = "MATERIAL_NOT_FOUND";

    private final CourseMaterialRepository materials;
    private final QuestionSourceRepository sources;
    private final ContentAccess access;
    private final StoragePort storage;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<MaterialDto> list(UUID courseId, User user) {
        access.readCourse(courseId, user);
        return materials.findByCourseIdOrderByCreatedAtDesc(courseId).stream().map(MaterialService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public MaterialDto get(UUID materialId, User user) {
        CourseMaterial m = load(materialId);
        access.read(m.getCourseId(), user, NOT_FOUND, "Material not found");
        return toDto(m);
    }

    @Transactional(readOnly = true)
    public MaterialFile download(UUID materialId, User user) {
        CourseMaterial m = load(materialId);
        access.read(m.getCourseId(), user, NOT_FOUND, "Material not found");
        byte[] data = storage.get(m.getStorageKey())
                .orElseThrow(() -> ApiException.notFound("MATERIAL_FILE_MISSING", "The stored file is missing"))
                .data();
        return new MaterialFile(m.getFileName(), m.getContentType(), data);
    }

    public MaterialDto upload(UUID courseId, MultipartFile file, User user) {
        access.writeCourse(courseId, user);
        if (file == null || file.isEmpty()) {
            throw ApiException.unprocessable("FILE_EMPTY", "Upload a non-empty file in the 'file' field");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "MATERIAL_TOO_LARGE", "Materials are limited to 50 MB");
        }
        String fileName = safeFileName(file.getOriginalFilename());
        String contentType = acceptedContentType(fileName, file.getContentType());
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.unprocessable("FILE_UNREADABLE", "The uploaded file could not be read");
        }
        CourseMaterial m = new CourseMaterial();
        m.setCourseId(courseId);
        m.setFileName(fileName);
        m.setContentType(contentType);
        m.setSizeBytes(data.length);
        m.setStatus(MaterialStatus.UPLOADED);
        m.setUploadedBy(user.getUserId());
        m.setCreatedAt(Instant.now(clock));
        m.setStorageKey("pending");
        m = materials.save(m); // assigns the id used in the storage key
        String key = "materials/" + courseId + "/" + m.getId() + "/" + fileName;
        m.setStorageKey(key);
        materials.flush();
        storage.put(key, data, contentType);
        afterRollback(() -> storage.delete(key));
        return toDto(m);
    }

    /** BR-Q12: only when no question cites it. Chunks go with it; the stored object is removed after commit. */
    public void delete(UUID materialId, User user) {
        CourseMaterial m = load(materialId);
        access.write(m.getCourseId(), user, NOT_FOUND, "Material not found");
        if (sources.existsByMaterialId(materialId)) {
            throw ApiException.conflict("MATERIAL_CITED", "Questions cite this material; it cannot be deleted");
        }
        jdbc.update("delete from material_chunks where material_id = ?", materialId);
        materials.delete(m);
        materials.flush();
        String key = m.getStorageKey();
        afterCommit(() -> storage.delete(key));
    }

    /** Extension decides the type; the declared content type must agree (generic binary types are tolerated). */
    static String acceptedContentType(String fileName, String declared) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        String expected = TYPES.get(ext);
        String type = declared == null ? "" : declared.toLowerCase(Locale.ROOT).split(";")[0].trim();
        boolean generic = type.isEmpty() || type.equals("application/octet-stream");
        if (expected == null || !(generic || type.equals(expected))) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MATERIAL_TYPE_UNSUPPORTED",
                    "Only PDF, PPTX and DOCX files are accepted");
        }
        return expected;
    }

    /** Keeps only the last path segment and characters that are safe in a storage key. */
    static String safeFileName(String original) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        name = name.replaceAll("[\\p{Cntrl}]", "").replace("..", "_");
        if (name.isEmpty() || name.startsWith(".")) {
            name = "material" + name;
        }
        if (name.length() > 200) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            name = name.substring(0, 200 - ext.length()) + ext;
        }
        return name;
    }

    private CourseMaterial load(UUID id) {
        return materials.findById(id).orElseThrow(() -> ApiException.notFound(NOT_FOUND, "Material not found"));
    }

    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private static void afterRollback(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        action.run();
                    }
                }
            });
        }
    }

    static MaterialDto toDto(CourseMaterial m) {
        return new MaterialDto(m.getId(), m.getCourseId(), m.getFileName(), m.getContentType(), m.getSizeBytes(),
                m.getStatus(), m.getPageCount(), m.getErrorMessage(), m.getUploadedBy(), m.getCreatedAt(),
                m.getIndexedAt());
    }
}
