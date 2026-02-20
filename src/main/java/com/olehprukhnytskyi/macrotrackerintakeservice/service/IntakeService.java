package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.exception.error.FoodErrorCode;
import com.olehprukhnytskyi.exception.error.IntakeErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateIntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.producer.UserEventProducer;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientCalculationStrategy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientStrategyFactory;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.CacheConstants;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.NutrientUtils;
import com.olehprukhnytskyi.util.UnitType;
import feign.FeignException;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntakeService {
    private static final int DELETE_BATCH_SIZE = 1000;
    private final NutrientStrategyFactory strategyFactory;
    private final IntakeRepository intakeRepository;
    private final CacheManager cacheManager;
    private final IntakeMapper intakeMapper;
    private final NutrimentsMapper nutrimentsMapper;
    private final FoodClientService foodClientService;
    private final UserEventProducer userEventProducer;

    @CacheEvict(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #intakeRequest.date")
    @Transactional
    public IntakeResponseDto save(IntakeRequestDto intakeRequest, Long userId) {
        log.info("Saving intake for userId={}", userId);
        FoodDto food = fetchFoodSafe(intakeRequest.getFoodId(), userId);
        UnitType unitType = resolveUnitType(intakeRequest.getUnitType());
        NutrientUtils.validateUnitSupported(food, unitType);
        Intake intake = createIntakeEntity(intakeRequest, userId, food, unitType);
        calculateAndSetNutriments(intake, food.getNutriments(), intakeRequest.getAmount());
        Intake saved = intakeRepository.save(intake);
        log.debug("Intake saved successfully userId={} intakeId={}", userId, saved.getId());
        return intakeMapper.toDto(saved);
    }

    @Cacheable(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #date")
    public List<IntakeResponseDto> findByDate(LocalDate date, Long userId) {
        log.debug("Fetching intake list for userId={} date={}", userId, date);
        List<Intake> intakes = (date != null)
                ? intakeRepository.findByUserIdAndDate(userId, date)
                : intakeRepository.findByUserId(userId);
        log.debug("Fetched {} intake records for userId={}", intakes.size(), userId);
        return intakes.stream()
                .map(intakeMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public IntakeResponseDto update(Long id, UpdateIntakeRequestDto request,
                                    Long userId) {
        log.info("Updating intake id={} for userId={}", id, userId);
        Intake intake = intakeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Intake not found"));
        manualEvict(userId, intake.getDate());
        Integer oldAmount = intake.getAmount();
        UnitType oldUnit = intake.getUnitType();
        intakeMapper.updateFromDto(intake, request);
        recalculateIfNecessary(intake, oldAmount, oldUnit);
        if (request.getDate() != null && !request.getDate().equals(intake.getDate())) {
            manualEvict(userId, intake.getDate());
        }
        Intake saved = intakeRepository.save(intake);
        log.debug("Intake updated successfully id={} userId={}", id, userId);
        return intakeMapper.toDto(saved);
    }

    @Transactional
    public void deleteById(Long id, Long userId) {
        log.info("Deleting intake id={} for userId={}", id, userId);
        intakeRepository.findByIdAndUserId(id, userId).ifPresent(intake -> {
            manualEvict(userId, intake.getDate());
            intakeRepository.delete(intake);
        });
    }

    @Transactional
    public void deleteUserIntakesRecursively(Long userId) {
        log.info("Processing batch deletion for user: {}", userId);
        int deletedCount = intakeRepository.deleteBatchByUserId(userId, DELETE_BATCH_SIZE);
        log.info("Deleted {} intake records for user {}", deletedCount, userId);
        if (deletedCount >= DELETE_BATCH_SIZE) {
            log.info("User {} still has data. Republishing event to continue deletion.",
                    userId);
            userEventProducer.sendUserDeletedEvent(new UserDeletedEvent(userId));
        } else {
            log.info("Data cleanup completed for user {}", userId);
        }
    }

    @Transactional
    public void undoIntakeGroup(String mealGroupId, Long userId) {
        log.info("Reverting intake group {} for user {}", mealGroupId, userId);
        intakeRepository.findFirstByMealGroupIdAndUserId(mealGroupId, userId)
                .ifPresent(intake -> manualEvictUserIntakes(userId, intake.getDate()));
        intakeRepository.deleteByMealGroupIdAndUserId(mealGroupId, userId);
    }

    private void manualEvictUserIntakes(Long userId, LocalDate date) {
        String key = userId + ":" + date;
        try {
            Cache cache = cacheManager.getCache(CacheConstants.USER_INTAKES);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception e) {
            log.error("Failed to evict cache for key {}", key, e);
        }
    }

    private Intake createIntakeEntity(IntakeRequestDto dto, Long userId,
                                      FoodDto food, UnitType type) {
        Intake intake = intakeMapper.toModel(dto);
        intake.setUserId(userId);
        intake.setFoodId(dto.getFoodId());
        intake.setUnitType(type);
        intakeMapper.updateIntakeFromFoodDto(intake, food);
        return intake;
    }

    private void calculateAndSetNutriments(Intake intake, NutrimentsDto sourceNutriments,
                                           Integer amount) {
        Nutriments calculated = nutrimentsMapper.fromFoodNutriments(sourceNutriments);
        NutrientCalculationStrategy strategy = strategyFactory.getStrategy(intake.getUnitType());
        strategy.calculate(calculated, amount);
        intake.setNutriments(calculated);
    }

    private UnitType resolveUnitType(UnitType requested) {
        return requested != null ? requested : UnitType.GRAMS;
    }

    private FoodDto fetchFoodSafe(String foodId, Long userId) {
        try {
            return foodClientService.getFoodById(foodId);
        } catch (FeignException.NotFound ex) {
            log.warn("Food not found for foodId={} userId={}", foodId, userId);
            throw new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND, "Food not found");
        } catch (FeignException ex) {
            log.error("Food service unavailable userId={} foodId={}", userId, foodId);
            throw new ExternalServiceException(CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                    "Food service is unavailable");
        }
    }

    private void recalculateIfNecessary(Intake intake, Integer oldAmount, UnitType oldUnit) {
        Integer newAmount = intake.getAmount();
        UnitType newUnit = intake.getUnitType();
        boolean amountChanged = !Objects.equals(oldAmount, newAmount);
        boolean unitChanged = !newUnit.equals(oldUnit);
        if (amountChanged || unitChanged) {
            if (unitChanged) {
                NutrientUtils.validateUnitType(newUnit, intake.getNutriments());
            }
            NutrientCalculationStrategy strategy = strategyFactory.getStrategy(newUnit);
            strategy.calculate(intake.getNutriments(), newAmount);
        }
    }

    private void manualEvict(Long userId, LocalDate date) {
        String key = userId + ":" + date;
        try {
            Cache cache = cacheManager.getCache(CacheConstants.USER_INTAKES);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception e) {
            log.error("Failed to evict cache", e);
        }
    }
}
