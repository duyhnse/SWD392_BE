package swd392.group6.AIVES.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.storage.StoragePort;
import swd392.group6.AIVES.storage.StoredObject;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

/**
 * Profile pictures (D33, D37). Every picture — uploaded or adopted from Google — goes through
 * {@link AvatarImageProcessor} (centre-cropped square, ≤ 512 px, JPEG, no metadata) and is stored through
 * {@link StoragePort} at {@code avatars/{userId}/{random}.jpg}. The file type is recognised from its content,
 * the client's Content-Type is not trusted.
 */
@Slf4j
@Service
@RequiredArgsConstructor
class AvatarService {

    /** Raw upload limit; the stored result is far smaller. */
    static final long MAX_BYTES = 10L * 1024 * 1024;

    private final UserRepository userRepository;
    private final StoragePort storage;
    private final AvatarImageProcessor processor;
    private final Clock clock;

    /** Uploaded by the user or an admin: always replaces the current picture. Returns the new avatar URL. */
    @Transactional
    public String replace(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.unprocessable("AVATAR_EMPTY", "Choose an image file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw tooLarge();
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw ApiException.unprocessable("AVATAR_EMPTY", "The uploaded file could not be read");
        }
        if (data.length > MAX_BYTES) {
            throw tooLarge();
        }
        if (ImageType.sniff(data) == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "AVATAR_UNSUPPORTED_TYPE",
                    "Use a JPEG, PNG, WebP, GIF or BMP image");
        }
        User user = store(load(userId), processor.toAvatarJpeg(data), AvatarSource.UPLOAD);
        return UserResponseDTO.avatarUrl(user);
    }

    /**
     * Uses the Google profile photo only when the account has no picture yet (D37) — an existing picture is never
     * overwritten. Returns false when nothing was changed (already has a picture, or the image is unusable).
     */
    @Transactional
    public boolean adoptGoogleIfMissing(UUID userId, byte[] googlePhoto) {
        User user = load(userId);
        if (user.getAvatarKey() != null) {
            return false;
        }
        try {
            store(user, processor.toAvatarJpeg(googlePhoto), AvatarSource.GOOGLE);
            return true;
        } catch (ApiException e) {
            log.info("Ignoring unusable Google profile photo for {}: {}", userId, e.getCode());
            return false;
        }
    }

    @Transactional
    public void remove(UUID userId) {
        clear(load(userId));
    }

    /** On Google unlink: removes the picture only if it came from Google (D37). */
    @Transactional
    public void removeIfFromGoogle(UUID userId) {
        User user = load(userId);
        if (user.getAvatarSource() == AvatarSource.GOOGLE) {
            clear(user);
        }
    }

    private User store(User user, byte[] jpeg, AvatarSource source) {
        String previous = user.getAvatarKey();
        String key = "avatars/" + user.getUserId() + "/" + UUID.randomUUID().toString().replace("-", "") + ".jpg";
        storage.put(key, jpeg, "image/jpeg");
        user.setAvatarKey(key);
        user.setAvatarSource(source);
        user.setAvatarUpdatedAt(clock.instant());
        userRepository.saveAndFlush(user);
        deleteQuietly(previous);
        return user;
    }

    private void clear(User user) {
        String previous = user.getAvatarKey();
        user.setAvatarKey(null);
        user.setAvatarSource(null);
        user.setAvatarUpdatedAt(clock.instant());
        userRepository.saveAndFlush(user);
        deleteQuietly(previous);
    }

    @Transactional(readOnly = true)
    public StoredObject fetch(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getAvatarKey)
                .flatMap(storage::get)
                .orElseThrow(() -> ApiException.notFound("AVATAR_NOT_FOUND", "This user has no profile picture"));
    }

    void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // An orphaned file is harmless; the database no longer points to it.
            log.warn("Could not delete old avatar object {}", key, e);
        }
    }

    private User load(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
    }

    private static ApiException tooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AVATAR_TOO_LARGE", "The picture must be at most 10 MB");
    }

    enum ImageType {
        PNG("png", "image/png"),
        JPEG("jpg", "image/jpeg"),
        WEBP("webp", "image/webp"),
        GIF("gif", "image/gif"),
        BMP("bmp", "image/bmp");

        final String extension;
        final String contentType;

        ImageType(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        static ImageType sniff(byte[] d) {
            if (startsWith(d, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
                return PNG;
            }
            if (startsWith(d, 0, 0xFF, 0xD8, 0xFF)) {
                return JPEG;
            }
            if (startsWith(d, 0, 'R', 'I', 'F', 'F') && startsWith(d, 8, 'W', 'E', 'B', 'P')) {
                return WEBP;
            }
            if (startsWith(d, 0, 'G', 'I', 'F', '8')) {
                return GIF;
            }
            if (startsWith(d, 0, 'B', 'M')) {
                return BMP;
            }
            return null;
        }

        private static boolean startsWith(byte[] data, int offset, int... expected) {
            if (data.length < offset + expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if ((data[offset + i] & 0xFF) != expected[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
