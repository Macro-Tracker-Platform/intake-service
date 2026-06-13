package com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface IntakeRepository extends JpaRepository<Intake, Long> {
    List<Intake> findByUserIdAndDate(Long userId, LocalDate date);

    List<Intake> findByUserId(Long userId);

    Optional<Intake> findByIdAndUserId(Long id, Long userId);

    Optional<Intake> findByUserIdAndRequestId(Long userId, UUID requestId);

    List<Intake> findByMealGroupIdAndUserIdOrderByMealItemPositionAsc(
            String mealGroupId, Long userId);

    void deleteByIdAndUserId(Long id, Long userId);

    @Transactional
    @Modifying
    @Query(value = """
        DELETE FROM intake\s
        WHERE id IN (
            SELECT id FROM intake\s
            WHERE user_id = :userId\s
            LIMIT :batchSize
        )
            """, nativeQuery = true)
    int deleteBatchByUserId(
            @Param("userId") Long userId,
            @Param("batchSize") int batchSize
    );

    @Modifying
    @Query("delete from Intake i where i.mealGroupId = :groupId and i.userId = :userId")
    void deleteByMealGroupIdAndUserId(
            @Param("groupId") String groupId,
            @Param("userId") Long userId
    );

    Optional<Intake> findFirstByMealGroupIdAndUserId(String mealGroupId, Long userId);
}
