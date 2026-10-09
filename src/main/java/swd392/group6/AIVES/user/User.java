package swd392.group6.AIVES.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import swd392.group6.AIVES.common.Language;
import swd392.group6.AIVES.security.TokenRevocation;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails, TokenRevocation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Builder.Default
    @NotNull(message = "Role ID is required")
    @Column(name = "role_id", nullable = false)
    private Short roleId = Role.STUDENT.getId();

    /** University Wi-Fi username, lowercase — the login id (D22). */
    @NotBlank(message = "Username is required")
    @Size(max = 50)
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email format is invalid")
    @Size(max = 100, message = "Email cannot exceed 100 characters")
    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @NotBlank(message = "Password hash is required")
    @Size(max = 255, message = "Password hash cannot exceed 255 characters")
    @Column(name = "hashed_password", nullable = false, length = 255)
    private String hashedPassword;

    /** e.g. SE190180 — students only. */
    @Size(max = 20)
    @Column(name = "student_code", unique = true, length = 20)
    private String studentCode;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_language", nullable = false, length = 30)
    private Language preferredLanguage = Language.VI;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Tokens issued before this instant are rejected (D24). */
    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    /** P1: linked Google account (OIDC subject) — D25. */
    @Column(name = "google_subject", unique = true)
    private String googleSubject;

    /** Email of the linked Google account (D34). */
    @Column(name = "google_email", length = 150)
    private String googleEmail;

    @Column(name = "google_linked_at")
    private Instant googleLinkedAt;

    /** StoragePort key of the profile picture, e.g. avatars/{userId}/{random}.png (D33). */
    @Column(name = "avatar_key", length = 500)
    private String avatarKey;

    @Column(name = "avatar_updated_at")
    private Instant avatarUpdatedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Role getRole() {
        return Role.fromId(roleId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(getRole().authority()));
    }

    @Override
    public String getPassword() {
        return this.hashedPassword;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public Instant tokensValidFrom() {
        return this.passwordChangedAt;
    }

    /** Deactivated users cannot log in, and their existing tokens stop working. */
    @Override
    public boolean isEnabled() {
        return this.active;
    }
}
