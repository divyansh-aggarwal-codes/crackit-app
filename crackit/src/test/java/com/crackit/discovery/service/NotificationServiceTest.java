package com.crackit.discovery.service;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.entity.Notification;
import com.crackit.discovery.entity.UserJobInteraction;
import com.crackit.discovery.repository.NotificationRepository;
import com.crackit.discovery.repository.UserJobInteractionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserJobInteractionRepository userJobInteractionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private JobDiscoveryService jobDiscoveryService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userJobInteractionRepository, userRepository, emailService);
    }

    @Test
    @DisplayName("Should dispatch consolidated daily notification for users with >=75% strong matches")
    void testSendDailyStrongMatchNotifications() {
        User user = User.builder()
                .id("user-999")
                .email("candidate@crackit.com")
                .targetRole("Senior Java Developer")
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-888")
                .title("Senior Java Engineer")
                .company("Stripe")
                .build();

        UserJobInteraction interaction = UserJobInteraction.builder()
                .id("int-777")
                .user(user)
                .discoveredJob(job)
                .matchScore(92)
                .notified(false)
                .build();

        when(userJobInteractionRepository.findUnnotifiedStrongMatches(75))
                .thenReturn(List.of(interaction));

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(emailService.sendStrongJobMatchAlert(eq(user), anyList())).thenReturn(true);

        int count = notificationService.sendDailyStrongMatchNotifications();

        assertEquals(1, count);
        ArgumentCaptor<Notification> notifCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(notifCaptor.capture());

        Notification captured = notifCaptor.getValue();
        assertNotNull(captured);
        assertEquals("user-999", captured.getUser().getId());
        assertTrue(captured.getTitle().contains("1 New Strong Job Matches"));
        assertTrue(captured.getMessage().contains("Stripe"));
        assertTrue(captured.getMessage().contains("92% fit"));
        assertTrue(interaction.getNotified(), "Interaction must be marked as notified when email succeeds");
        verify(emailService, times(1)).sendStrongJobMatchAlert(eq(user), anyList());
    }

    @Test
    @DisplayName("Should NOT mark interaction as notified if email dispatch fails")
    void testFailedEmailDoesNotMarkInteractionsAsNotified() {
        User user = User.builder()
                .id("user-998")
                .email("candidate2@crackit.com")
                .targetRole("Senior Java Developer")
                .jobAlertsEnabled(true)
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-887")
                .title("Senior Java Engineer")
                .company("Google")
                .build();

        UserJobInteraction interaction = UserJobInteraction.builder()
                .id("int-776")
                .user(user)
                .discoveredJob(job)
                .matchScore(90)
                .notified(false)
                .build();

        when(userJobInteractionRepository.findUnnotifiedStrongMatches(75))
                .thenReturn(List.of(interaction));

        when(emailService.sendStrongJobMatchAlert(eq(user), anyList())).thenReturn(false);

        int count = notificationService.sendDailyStrongMatchNotifications();

        assertEquals(0, count);
        assertFalse(interaction.getNotified(), "Interaction must remain unnotified if email dispatch fails");
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should skip sending notifications to users who disabled job alerts")
    void testDisabledJobAlertsUserSkipped() {
        User user = User.builder()
                .id("user-997")
                .email("unsubscribed@crackit.com")
                .targetRole("Senior Java Developer")
                .jobAlertsEnabled(false)
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-886")
                .title("Senior Java Engineer")
                .company("Amazon")
                .build();

        UserJobInteraction interaction = UserJobInteraction.builder()
                .id("int-775")
                .user(user)
                .discoveredJob(job)
                .matchScore(95)
                .notified(false)
                .build();

        when(userJobInteractionRepository.findUnnotifiedStrongMatches(75))
                .thenReturn(List.of(interaction));

        int count = notificationService.sendDailyStrongMatchNotifications();

        assertEquals(0, count);
        verify(emailService, never()).sendStrongJobMatchAlert(any(), any());
        assertFalse(interaction.getNotified());
    }

    @Test
    @DisplayName("Should dispatch on-demand daily match alert for a single user")
    void testSendDailyStrongMatchNotificationForUser() {
        User user = User.builder()
                .id("user-div")
                .email("divyanshu5981.iimt@gmail.com")
                .jobAlertsEnabled(true)
                .unsubscribeToken("test-token-123")
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-1")
                .title("Backend Engineer")
                .company("Crackit Tech")
                .build();

        UserJobInteraction uji = UserJobInteraction.builder()
                .id("uji-1")
                .user(user)
                .discoveredJob(job)
                .matchScore(85)
                .notified(false)
                .build();

        when(userJobInteractionRepository.findUnnotifiedStrongMatchesForUser("user-div", 70))
                .thenReturn(List.of(uji));
        when(emailService.sendStrongJobMatchAlert(eq(user), anyList())).thenReturn(true);

        boolean success = notificationService.sendDailyStrongMatchNotificationForUser(user);

        assertTrue(success);
        assertTrue(uji.getNotified());
        verify(emailService, times(1)).sendStrongJobMatchAlert(eq(user), anyList());
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    @DisplayName("Should mark notification as read for the correct owner")
    void testMarkAsRead() {
        User user = User.builder().id("user-1").build();
        Notification notif = Notification.builder()
                .id("notif-1")
                .user(user)
                .isRead(false)
                .build();

        when(notificationRepository.findById("notif-1")).thenReturn(Optional.of(notif));

        notificationService.markAsRead("notif-1", "user-1");

        assertTrue(notif.getIsRead());
        verify(notificationRepository, times(1)).save(notif);
    }

    @Test
    @DisplayName("Should skip daily notification run when distributed lock is held by another instance")
    void testDistributedLockPreventsDuplicateExecution() {
        com.crackit.common.scheduler.DistributedSchedulerLockService mockLockService =
                mock(com.crackit.common.scheduler.DistributedSchedulerLockService.class);
        notificationService.setDistributedSchedulerLockService(mockLockService);

        when(mockLockService.acquireLock("daily_job_alerts", 30)).thenReturn(false);

        int count = notificationService.sendDailyStrongMatchNotifications();

        assertEquals(0, count);
        verify(userJobInteractionRepository, never()).findUnnotifiedStrongMatches(anyInt());
        verify(emailService, never()).sendStrongJobMatchAlert(any(), any());
    }

    @Test
    @DisplayName("Should release distributed lock when daily notification run completes")
    void testDistributedLockReleasedOnCompletion() {
        com.crackit.common.scheduler.DistributedSchedulerLockService mockLockService =
                mock(com.crackit.common.scheduler.DistributedSchedulerLockService.class);
        notificationService.setDistributedSchedulerLockService(mockLockService);

        when(mockLockService.acquireLock("daily_job_alerts", 30)).thenReturn(true);
        when(userJobInteractionRepository.findUnnotifiedStrongMatches(75))
                .thenReturn(List.of());

        int count = notificationService.sendDailyStrongMatchNotifications();

        assertEquals(0, count);
        verify(mockLockService, times(1)).releaseLock("daily_job_alerts");
    }
}
