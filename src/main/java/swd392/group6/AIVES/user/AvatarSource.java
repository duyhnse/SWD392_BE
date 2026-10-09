package swd392.group6.AIVES.user;

/** Origin of a profile picture (D37). */
public enum AvatarSource {
    /** Uploaded by the user or an admin — never replaced or removed automatically. */
    UPLOAD,
    /** Adopted from the linked Google account because there was no picture; removed when Google is unlinked. */
    GOOGLE
}
