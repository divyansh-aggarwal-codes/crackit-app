package com.crackit.discovery.controller;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.repository.DiscoveredJobRepository;
import com.crackit.discovery.service.NotificationService;
import com.crackit.tracker.dto.JobApplicationResponse;
import com.crackit.tracker.service.JobApplicationService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicJobTrackingControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DiscoveredJobRepository discoveredJobRepository;

    @Mock
    private JobApplicationService jobApplicationService;

    @Mock
    private NotificationService notificationService;

    private PublicJobTrackingController controller;

    @BeforeEach
    void setUp() {
        controller = new PublicJobTrackingController(
                userRepository,
                discoveredJobRepository,
                jobApplicationService,
                notificationService
        );
    }

    @Test
    @DisplayName("trackAppliedFromEmail with valid token and jobId tracks application and renders success page")
    void testTrackAppliedSuccess() {
        String token = "valid-token-123";
        String jobId = "job-disc-456";

        User user = User.builder()
                .id("u1")
                .email("candidate@example.com")
                .unsubscribeToken(token)
                .build();

        DiscoveredJob dj = DiscoveredJob.builder()
                .id(jobId)
                .title("Staff Java Engineer")
                .company("Google")
                .url("https://careers.google.com/job/123")
                .build();

        when(userRepository.findByUnsubscribeToken(token)).thenReturn(Optional.of(user));
        when(discoveredJobRepository.findById(jobId)).thenReturn(Optional.of(dj));
        when(jobApplicationService.trackApplyForUser(eq(user), any())).thenReturn(
                JobApplicationResponse.builder().id("app-1").companyName("Google").jobTitle("Staff Java Engineer").build()
        );

        ResponseEntity<String> response = controller.trackAppliedFromEmail(jobId, token);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Application Added to Pipeline!"));
        assertTrue(response.getBody().contains("Staff Java Engineer"));
        assertTrue(response.getBody().contains("Google"));
        assertTrue(response.getBody().contains("/tracker?tracked=applied"));

        verify(jobApplicationService, times(1)).trackApplyForUser(eq(user), any());
    }

    @Test
    @DisplayName("trackAppliedFromEmail with invalid token returns 400 Bad Request")
    void testTrackAppliedInvalidToken() {
        String token = "invalid-token";
        when(userRepository.findByUnsubscribeToken(token)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.trackAppliedFromEmail("job-123", token);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Link Expired"));
        verify(jobApplicationService, never()).trackApplyForUser(any(), any());
    }

    @Test
    @DisplayName("trackAppliedFromEmail with missing token returns 400 Bad Request")
    void testTrackAppliedMissingToken() {
        ResponseEntity<String> response = controller.trackAppliedFromEmail("job-123", null);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("Invalid Request"));
        verify(jobApplicationService, never()).trackApplyForUser(any(), any());
    }

    @Test
    @DisplayName("dispatchAlertsWebhook runs batch alerts when no email specified")
    void testDispatchAlertsWebhookBatch() {
        when(notificationService.sendDailyStrongMatchNotifications()).thenReturn(3);

        ResponseEntity<Map<String, Object>> response = controller.dispatchAlertsWebhook(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("success"));
        assertEquals(3, response.getBody().get("dispatchedCount"));
        verify(notificationService, times(1)).sendDailyStrongMatchNotifications();
    }

    @Test
    @DisplayName("dispatchAlertsWebhook dispatches single user alert when email specified")
    void testDispatchAlertsWebhookSingleUser() {
        User user = User.builder().id("u1").email("divyanshu5981.iimt@gmail.com").build();
        when(userRepository.findByEmail("divyanshu5981.iimt@gmail.com")).thenReturn(Optional.of(user));
        when(notificationService.sendDailyStrongMatchNotificationForUser(user, true)).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.dispatchAlertsWebhook("divyanshu5981.iimt@gmail.com");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(true, response.getBody().get("success"));
        assertEquals("divyanshu5981.iimt@gmail.com", response.getBody().get("target"));
        verify(notificationService, times(1)).sendDailyStrongMatchNotificationForUser(user, true);
    }
}
