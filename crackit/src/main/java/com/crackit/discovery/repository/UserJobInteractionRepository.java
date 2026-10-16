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

    List<UserJobInteraction> findByUserId(String userId);

    List<UserJobInteraction> findByUserIdAndStatus(String userId, String status);

    @Query("SELECT uji FROM UserJobInteraction uji WHERE uji.user.id = :userId AND uji.status != 'DISMISSED' ORDER BY uji.matchScore DESC")
    List<UserJobInteraction> findActiveRecommendationsForUser(@Param("userId") String userId);

    @Query("SELECT uji FROM UserJobInteraction uji WHERE uji.user.id = :userId AND uji.matchScore >= :minScore AND uji.status != 'DISMISSED' ORDER BY uji.matchScore DESC")
    List<UserJobInteraction> findByUserIdAndMinMatchScore(@Param("userId") String userId, @Param("minScore") int minScore);

    @Query("""
        SELECT uji FROM UserJobInteraction uji 
        WHERE uji.matchScore >= :minScore 
          AND (uji.notified IS NULL OR uji.notified = false)
          AND (uji.status IS NULL OR uji.status != 'DISMISSED')
          AND (uji.discoveredJob.isActive IS NULL OR uji.discoveredJob.isActive = true)
        ORDER BY uji.matchScore DESC
    """)
    List<UserJobInteraction> findUnnotifiedStrongMatches(@Param("minScore") int minScore);

    default List<UserJobInteraction> findUnnotifiedStrongMatches(int minScore, LocalDateTime since) {
        return findUnnotifiedStrongMatches(minScore);
    }

    @Query("""
        SELECT uji FROM UserJobInteraction uji 
        WHERE uji.user.id = :userId 
          AND uji.matchScore >= :minScore 
          AND (uji.notified IS NULL OR uji.notified = false)
          AND (uji.status IS NULL OR uji.status != 'DISMISSED')
          AND (uji.discoveredJob.isActive IS NULL OR uji.discoveredJob.isActive = true)
        ORDER BY uji.matchScore DESC
    """)
    List<UserJobInteraction> findUnnotifiedStrongMatchesForUser(@Param("userId") String userId, @Param("minScore") int minScore);

    @Query("SELECT uji.discoveredJob.id FROM UserJobInteraction uji WHERE uji.user.id = :userId AND uji.status = 'DISMISSED'")
    List<String> findDismissedJobIdsByUserId(@Param("userId") String userId);
}
