package swd392.group6.AIVES.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import swd392.group6.AIVES.common.ApiException;
import swd392.group6.AIVES.security.JwtService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;

    @InjectMocks private UserService userService;

    private static SignUpRequestDTO signUpRequest(String email) {
        return SignUpRequestDTO.builder()
                .fullName("  Jane Doe ")
                .email(email)
                .password("plainPassword123")
                .build();
    }

    @Test
    void signUpAlwaysCreatesStudentWithNormalizedEmailAndHashedPassword() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPassword123")).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setUserId(UUID.randomUUID());
            return u;
        });
        when(jwtService.generateToken(eq("jane@example.com"), anyMap())).thenReturn("jwt-token");
        when(jwtService.getExpirationTime()).thenReturn(1000L);

        AuthResponseDTO response = userService.signUp(signUpRequest("  Jane@Example.com "));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals(Role.STUDENT.getId(), saved.getValue().getRoleId());
        assertEquals("jane@example.com", saved.getValue().getEmail());
        assertEquals("Jane Doe", saved.getValue().getFullName());
        assertEquals("hashed", saved.getValue().getHashedPassword());
        assertEquals("jwt-token", response.getToken());
    }

    @Test
    void signUpWithExistingEmailIsConflict() {
        when(userRepository.existsByEmail("jane@example.com")).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class,
                () -> userService.signUp(signUpRequest("jane@example.com")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("EMAIL_ALREADY_REGISTERED", ex.getCode());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void signUpRaceOnUniqueConstraintIsConflict() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("dup"));

        ApiException ex = assertThrows(ApiException.class,
                () -> userService.signUp(signUpRequest("jane@example.com")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
    }

    @Test
    void loginSuccessReturnsToken() {
        User user = User.builder().userId(UUID.randomUUID()).fullName("John").email("john@example.com")
                .hashedPassword("hashed").roleId(Role.LECTURER.getId()).build();
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(jwtService.generateToken(eq("john@example.com"), anyMap())).thenReturn("jwt-token");

        AuthResponseDTO response = userService.login(
                LoginRequestDTO.builder().email(" John@Example.com").password("pw").build());

        assertEquals("jwt-token", response.getToken());
        assertEquals("john@example.com", response.getUser().getEmail());
    }

    @Test
    void loginWithBadCredentialsIsUnauthorized() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        ApiException ex = assertThrows(ApiException.class,
                () -> userService.login(LoginRequestDTO.builder().email("a@b.com").password("pw").build()));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
    }

    @Test
    void getUserProfileNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        ApiException ex = assertThrows(ApiException.class, () -> userService.getUserProfile(id));

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }
}
