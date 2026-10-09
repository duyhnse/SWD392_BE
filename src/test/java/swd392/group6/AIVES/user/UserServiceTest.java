package swd392.group6.AIVES.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.security.JwtService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Spy private Clock clock = Clock.fixed(Instant.parse("2026-10-08T03:00:00Z"), ZoneOffset.UTC);

    @InjectMocks private UserService userService;

    private static User user() {
        return User.builder().userId(UUID.randomUUID()).username("vinhdq").fullName("Vinh").email("vinh@fpt.edu.vn")
                .hashedPassword("hashed").roleId(Role.LECTURER.getId()).build();
    }

    @Test
    void loginNormalizesUsernameAndReturnsToken() {
        User user = user();
        when(authenticationManager.authenticate(argThat(a -> "vinhdq".equals(a.getPrincipal()))))
                .thenReturn(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(jwtService.generateToken(eq("vinhdq"), anyMap())).thenReturn("jwt-token");

        AuthResponseDTO response = userService.login(LoginRequestDTO.builder().username(" VinhDQ ").password("pw").build());

        assertEquals("jwt-token", response.getToken());
        assertEquals("vinhdq", response.getUser().getUsername());
    }

    @Test
    void loginWithBadCredentialsIsUnauthorized() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        ApiException ex = assertThrows(ApiException.class,
                () -> userService.login(LoginRequestDTO.builder().username("a").password("pw").build()));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        assertEquals("INVALID_CREDENTIALS", ex.getCode());
    }

    @Test
    void changePasswordStampsPasswordChangedAt() {
        User user = user();
        when(userRepository.findById(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword1", "hashed")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword1")).thenReturn("new-hash");
        when(jwtService.generateToken(eq("vinhdq"), anyMap())).thenReturn("fresh");

        AuthResponseDTO response = userService.changePassword(user.getUserId(), new ChangePasswordRequest("old", "NewPassword1"));

        assertEquals("new-hash", user.getHashedPassword());
        assertEquals(Instant.parse("2026-10-08T03:00:00Z"), user.getPasswordChangedAt());
        assertEquals("fresh", response.getToken());
    }

    @Test
    void getUserProfileNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> userService.getUserProfile(id));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
