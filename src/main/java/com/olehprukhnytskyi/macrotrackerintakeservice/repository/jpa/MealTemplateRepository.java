package com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealTemplateRepository extends JpaRepository<MealTemplate, Long> {
    Optional<MealTemplate> findByIdAndUserId(Long id, Long userId);

    Optional<MealTemplate> findByUserIdAndRequestId(Long userId, UUID requestId);

    List<MealTemplate> findAllByUserId(Long userId);
}
