package com.crackit.discovery.controller;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnsubscribeControllerTest {

    @Mock
    private UserRepository userRepository;

    private UnsubscribeController controller;

    @BeforeEach
    void setUp() {
        controller = new UnsubscribeController(userRepository);
    }

    @Test
    @DisplayName("POST /unsubscribe should disable job alerts when valid token is provided")
    void testUnsubscribePostSuccess() {
        String token = "valid-token-abc123xyz";
        User user = User.builder()
                .id("u1")
                .email("test.candidate@example.com")
                .jobAlertsEnabled(true)
                .unsubscribeToken(token)
                .build();

        when(userRepository.findByUnsubscribeToken(token)).thenReturn(Optional.of(user));

        ResponseEntity<Map<String, Object>> response = controller.unsubscribePost(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("SUCCESS", response.getBody().get("status"));
        assertFalse(user.getJobAlertsEnabled(), "User jobAlertsEnabled must be set to false");
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("POST /unsubscribe should return 400 Bad Request when token is invalid")
    void testUnsubscribePostInvalidToken() {
        String token = "invalid-token";
        when(userRepository.findByUnsubscribeToken(token)).thenReturn(Optional.empty());

        ResponseEntity<Map<String, Object>> response = controller.unsubscribePost(token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("ERROR", response.getBody().get("status"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("GET /unsubscribe should render confirmation page with masked email")
    void testUnsubscribeGetLandingPage() {
        String token = "valid-token-abc123xyz";
        User user = User.builder()
                .id("u1")
                .email("divyanshu@example.com")
                .jobAlertsEnabled(true)
                .unsubscribeToken(token)
                .build();

        when(userRepository.findByUnsubscribeToken(token)).thenReturn(Optional.of(user));

        ResponseEntity<String> response = controller.unsubscribeLandingPage(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Unsubscribe from Daily Job Alerts?"));
        assertTrue(response.getBody().contains("d***u@example.com"));
        assertTrue(response.getBody().contains("name='token' value='valid-token-abc123xyz'"));
    }

    @Test
    @DisplayName("GET /unsubscribe should indicate already unsubscribed if job alerts are already false")
    void testUnsubscribeGetAlreadyUnsubscribed() {
        String token = "already-done-token";
        User user = User.builder()
                .id("u2")
                .email("inactive@example.com")
                .jobAlertsEnabled(false)
                .unsubscribeToken(token)
                .build();

        when(userRepository.findByUnsubscribeToken(token)).thenReturn(Optional.of(user));

        ResponseEntity<String> response = controller.unsubscribeLandingPage(token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Already Unsubscribed"));
    }
}
