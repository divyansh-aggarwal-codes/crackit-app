package com.crackit.discovery.repository;

import com.crackit.discovery.entity.UserJobInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserJobInteractionRepository extends JpaRepository<UserJobInteraction, String> {

    Optional<UserJobInteraction> findByUserIdAndDiscoveredJobId(String userId, String discoveredJobId);

    List<UserJobInteraction> findByUserIdAndStatus(String userId, String status);

    @Query("SELECT uji FROM UserJobInteraction uji WHERE uji.user.id = :userId AND uji.status != 'DISMISSED' ORDER BY uji.matchScore DESC")
    List<UserJobInteraction> findActiveRecommendationsForUser(@Param("userId") String userId);

    @Query("SELECT uji FROM UserJobInteraction uji WHERE uji.user.id = :userId AND uji.matchScore >= :minScore AND uji.status != 'DISMISSED' ORDER BY uji.matchScore DESC")
    List<UserJobInteraction> findByUserIdAndMinMatchScore(@Param("userId") String userId, @Param("minScore") int minScore);

    @Query("SELECT uji FROM UserJobInteraction uji WHERE uji.matchScore >= :minScore AND uji.notified = false AND uji.createdAt >= :since")
    List<UserJobInteraction> findUnnotifiedStrongMatches(@Param("minScore") int minScore, @Param("since") LocalDateTime since);

    @Query("SELECT uji.discoveredJob.id FROM UserJobInteraction uji WHERE uji.user.id = :userId AND uji.status = 'DISMISSED'")
    List<String> findDismissedJobIdsByUserId(@Param("userId") String userId);
}
