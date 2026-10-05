package swd392.group6.AIVES.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import swd392.group6.AIVES.security.JwtService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .userId(UUID.randomUUID())
                .fullName("John Doe")
                .email("john.doe@example.com")
                .hashedPassword("encodedSecretPassword")
                .roleId(User.ROLE_STUDENT)
                .build();
    }

    @Test
    void testSignUpWithDefaultRoleIdStudent() {
        SignUpRequestDTO request = SignUpRequestDTO.builder()
                .fullName("Jane Doe")
                .email("jane.doe@example.com")
                .password("plainPassword123")
                // roleId is null, should default to 3 (STUDENT)
                .build();

        when(userRepository.existsByEmail("jane.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPassword123")).thenReturn("encodedSecretPassword");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any(User.class))).thenReturn("mocked.jwt.token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        AuthResponseDTO response = userService.signUp(request);

        assertNotNull(response);
        assertEquals((short) 3, response.getUser().getRoleId());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals((short) 3, userCaptor.getValue().getRoleId());
    }

    @Test
    void testSignUpSuccessWithCustomRoleId() {
        SignUpRequestDTO request = SignUpRequestDTO.builder()
                .fullName("John Doe")
                .email("john.doe@example.com")
                .password("plainPassword123")
                .roleId((short) 1)
                .build();

        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPassword123")).thenReturn("encodedSecretPassword");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);
        when(jwtService.generateToken(sampleUser)).thenReturn("mocked.jwt.token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        AuthResponseDTO response = userService.signUp(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals("john.doe@example.com", response.getUser().getEmail());
        assertEquals("John Doe", response.getUser().getFullName());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testSignUpEmailAlreadyExists() {
        SignUpRequestDTO request = SignUpRequestDTO.builder()
                .fullName("John Doe")
                .email("john.doe@example.com")
                .password("plainPassword123")
                .build();

        when(userRepository.existsByEmail("john.doe@example.com")).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> userService.signUp(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testLoginSuccess() {
        LoginRequestDTO request = LoginRequestDTO.builder()
                .email("john.doe@example.com")
                .password("plainPassword123")
                .build();

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(null);
        when(userRepository.findByEmail("john.doe@example.com")).thenReturn(Optional.of(sampleUser));
        when(jwtService.generateToken(sampleUser)).thenReturn("mocked.jwt.token");
        when(jwtService.getExpirationTime()).thenReturn(86400000L);

        AuthResponseDTO response = userService.login(request);

        assertNotNull(response);
        assertEquals("mocked.jwt.token", response.getToken());
        assertEquals("john.doe@example.com", response.getUser().getEmail());
        verify(authenticationManager, times(1)).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }
}
