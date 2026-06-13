package com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateApplication;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MealTemplateApplicationRepository
        extends JpaRepository<MealTemplateApplication, Long> {
    Optional<MealTemplateApplication> findByUserIdAndRequestId(Long userId, UUID requestId);

    Optional<MealTemplateApplication> findByUserIdAndMealGroupId(Long userId, UUID mealGroupId);

    void deleteByUserIdAndMealGroupId(Long userId, UUID mealGroupId);
}
