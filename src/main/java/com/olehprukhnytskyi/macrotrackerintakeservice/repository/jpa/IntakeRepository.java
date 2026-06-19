package com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface IntakeRepository extends JpaRepository<Intake, Long> {
    @Query("select i from Intake i where i.userId = :userId and i.date = :date "
            + "and i.deleted = false")
    List<Intake> findByUserIdAndDate(
            @Param("userId") Long userId,
            @Param("date") LocalDate date
    );

    @Query("""
            select i from Intake i
            where i.userId = :userId
              and i.date between :startDate and :endDate
              and i.deleted = false
            order by i.date asc, i.intakePeriod asc, i.id asc
            """)
    List<Intake> findByUserIdAndDateBetweenOrderByDateAscIntakePeriodAscIdAsc(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("select i from Intake i where i.userId = :userId and i.deleted = false")
    List<Intake> findByUserId(@Param("userId") Long userId);

    @Query("select i from Intake i where i.id = :id and i.userId = :userId "
            + "and i.deleted = false")
    Optional<Intake> findByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    @Query("select i from Intake i where i.userId = :userId and i.requestId = :requestId "
            + "and i.deleted = false")
    Optional<Intake> findByUserIdAndRequestId(
            @Param("userId") Long userId,
            @Param("requestId") UUID requestId
    );

    @Query("select i from Intake i where i.userId = :userId and i.requestId = :requestId")
    Optional<Intake> findAnyByUserIdAndRequestId(
            @Param("userId") Long userId,
            @Param("requestId") UUID requestId
    );

    @Query("""
            select i from Intake i
            where i.mealGroupId = :mealGroupId
              and i.userId = :userId
              and i.deleted = false
            order by i.mealItemPosition asc
            """)
    List<Intake> findByMealGroupIdAndUserIdOrderByMealItemPositionAsc(
            @Param("mealGroupId") String mealGroupId,
            @Param("userId") Long userId
    );

    @Query("select i from Intake i where i.id = :id and i.userId = :userId")
    Optional<Intake> findAnyByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    @Query("""
            select i from Intake i
            where i.userId = :userId
              and i.updatedAt > :updatedAt
            order by i.updatedAt asc, i.id asc
            """)
    List<Intake> findAllChangedAfter(
            @Param("userId") Long userId,
            @Param("updatedAt") Instant updatedAt,
            Pageable pageable
    );

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
    @Query("""
            update Intake i
            set i.deleted = true,
                i.updatedAt = :updatedAt,
                i.version = i.version + 1
            where i.mealGroupId = :groupId
              and i.userId = :userId
              and i.deleted = false
            """)
    int softDeleteByMealGroupIdAndUserId(
            @Param("groupId") String groupId,
            @Param("userId") Long userId,
            @Param("updatedAt") Instant updatedAt
    );

    Optional<Intake> findFirstByMealGroupIdAndUserIdAndDeletedFalse(
            String mealGroupId,
            Long userId
    );
}
