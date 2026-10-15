package swd392.group6.AIVES.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    @Query("select s from UserSession s where s.userId = :userId and s.revokedAt is null and s.expiresAt > :now")
    List<UserSession> findOpen(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now, s.revokeReason = :reason where s.userId = :userId and s.revokedAt is null")
    int revokeAll(@Param("userId") UUID userId, @Param("now") Instant now, @Param("reason") String reason);

    @Modifying
    @Query("update UserSession s set s.lastSeenAt = :now where s.sessionId = :id")
    int touch(@Param("id") UUID id, @Param("now") Instant now);
}
