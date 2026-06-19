package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.exception.error.FoodErrorCode;
import com.olehprukhnytskyi.exception.error.IntakeErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeSyncItemDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeSyncPushRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeSyncResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateIntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.producer.CacheInvalidationProducer;
import com.olehprukhnytskyi.macrotrackerintakeservice.producer.UserEventProducer;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateApplicationRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientCalculationStrategy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientStrategyFactory;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.CacheConstants;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.NutrientUtils;
import com.olehprukhnytskyi.util.UnitType;
import feign.FeignException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntakeService {
    private static final String INTAKE_DOMAIN = "INTAKE";
    private static final int DELETE_BATCH_SIZE = 1000;
    private final NutrientStrategyFactory strategyFactory;
    private final IntakeRepository intakeRepository;
    private final MealTemplateApplicationRepository applicationRepository;
    private final CacheManager cacheManager;
    private final IntakeMapper intakeMapper;
    private final NutrimentsMapper nutrimentsMapper;
    private final FoodClientService foodClientService;
    private final CacheInvalidationProducer cacheInvalidationProducer;
    private final UserEventProducer userEventProducer;

    @CacheEvict(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #intakeRequest.date")
    public IntakeResponseDto save(IntakeRequestDto intakeRequest, Long userId, UUID requestId) {
        return save(intakeRequest, userId, requestId, null);
    }

    @CacheEvict(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #intakeRequest.date")
    public IntakeResponseDto save(IntakeRequestDto intakeRequest, Long userId, UUID requestId,
                                  String originDeviceId) {
        log.info("Saving intake for userId={}", userId);
        Intake existing = intakeRepository.findByUserIdAndRequestId(userId, requestId)
                .orElse(null);
        if (existing != null) {
            return intakeMapper.toDto(existing);
        }
        FoodDto food = fetchFoodSafe(intakeRequest.getFoodId(),
                intakeRequest.getOriginalFoodId(), userId);
        UnitType unitType = resolveUnitType(intakeRequest.getUnitType());
        NutrientUtils.validateUnitSupported(food, unitType);
        Intake intake = createIntakeEntity(intakeRequest, userId, food, unitType);
        intake.setRequestId(requestId);
        intake.setUpdatedAt(now());
        calculateAndSetNutriments(intake, food.getNutriments(), intakeRequest.getAmount());
        try {
            Intake saved = intakeRepository.saveAndFlush(intake);
            log.debug("Intake saved successfully userId={} intakeId={}", userId, saved.getId());
            cacheInvalidationProducer.send(userId, INTAKE_DOMAIN, originDeviceId);
            return intakeMapper.toDto(saved);
        } catch (DataIntegrityViolationException exception) {
            return intakeRepository.findByUserIdAndRequestId(userId, requestId)
                    .map(intakeMapper::toDto)
                    .orElseThrow(() -> exception);
        }
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

    @Transactional(readOnly = true)
    public List<IntakeResponseDto> findByDateRange(LocalDate startDate, LocalDate endDate,
                                                   Long userId) {
        log.debug("Fetching intake list for userId={} range={}..{}", userId, startDate, endDate);
        return intakeRepository
                .findByUserIdAndDateBetweenOrderByDateAscIntakePeriodAscIdAsc(
                        userId, startDate, endDate)
                .stream()
                .map(intakeMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public IntakeSyncResponseDto pullSync(Long userId, Instant since, int limit) {
        Instant snapshotTime = now();
        int boundedLimit = Math.clamp(limit, 1, 500);
        List<Intake> fetched = intakeRepository.findAllChangedAfter(
                userId,
                since,
                PageRequest.of(0, boundedLimit + 1)
        );
        boolean hasMore = fetched.size() > boundedLimit;
        List<Intake> page = hasMore
                ? new ArrayList<>(fetched.subList(0, boundedLimit))
                : fetched;
        Instant nextSyncTime = hasMore && !page.isEmpty()
                ? page.getLast().getUpdatedAt().minusNanos(1)
                : snapshotTime;
        return IntakeSyncResponseDto.builder()
                .data(page.stream().map(intakeMapper::toSyncDto).toList())
                .nextSyncTime(nextSyncTime)
                .hasMore(hasMore)
                .build();
    }

    @Transactional
    public IntakeSyncResponseDto pushSync(Long userId, IntakeSyncPushRequestDto requestDto) {
        return pushSync(userId, requestDto, null);
    }

    @Transactional
    public IntakeSyncResponseDto pushSync(Long userId, IntakeSyncPushRequestDto requestDto,
                                          String originDeviceId) {
        List<IntakeSyncItemDto> applied = new ArrayList<>();
        for (IntakeSyncItemDto change : requestDto.getChanges()) {
            applySyncChange(userId, change).ifPresent(applied::add);
        }
        if (!applied.isEmpty()) {
            cacheInvalidationProducer.send(userId, INTAKE_DOMAIN, originDeviceId);
        }
        return IntakeSyncResponseDto.builder()
                .data(applied)
                .nextSyncTime(now())
                .hasMore(false)
                .build();
    }

    @Transactional
    public IntakeResponseDto update(Long id, UpdateIntakeRequestDto request, Long userId) {
        return update(id, request, userId, null);
    }

    @Transactional
    public IntakeResponseDto update(Long id, UpdateIntakeRequestDto request,
                                    Long userId, String originDeviceId) {
        log.info("Updating intake id={} for userId={}", id, userId);
        Intake intake = intakeRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Intake not found"));
        ensureVersionMatches(request.getVersion(), intake);
        LocalDate oldDate = intake.getDate();
        manualEvict(userId, oldDate);
        Integer oldAmount = intake.getAmount();
        UnitType oldUnit = intake.getUnitType();
        intakeMapper.updateFromDto(intake, request);
        recalculateIfNecessary(intake, oldAmount, oldUnit);
        intake.setUpdatedAt(now());
        if (!Objects.equals(oldDate, intake.getDate())) {
            manualEvict(userId, intake.getDate());
        }
        Intake saved = intakeRepository.save(intake);
        log.debug("Intake updated successfully id={} userId={}", id, userId);
        cacheInvalidationProducer.send(userId, INTAKE_DOMAIN, originDeviceId);
        return intakeMapper.toDto(saved);
    }

    @Transactional
    public void deleteById(Long id, Long userId) {
        deleteById(id, userId, null);
    }

    @Transactional
    public void deleteById(Long id, Long userId, String originDeviceId) {
        log.info("Deleting intake id={} for userId={}", id, userId);
        intakeRepository.findByIdAndUserId(id, userId).ifPresent(intake -> {
            manualEvict(userId, intake.getDate());
            intake.setDeleted(true);
            intake.setUpdatedAt(now());
            intakeRepository.saveAndFlush(intake);
            cacheInvalidationProducer.send(userId, INTAKE_DOMAIN, originDeviceId);
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
    public void undoIntakeGroup(UUID mealGroupId, Long userId) {
        undoIntakeGroup(mealGroupId, userId, null);
    }

    @Transactional
    public void undoIntakeGroup(UUID mealGroupId, Long userId, String originDeviceId) {
        log.info("Reverting intake group {} for user {}", mealGroupId, userId);
        intakeRepository
                .findFirstByMealGroupIdAndUserIdAndDeletedFalse(mealGroupId.toString(), userId)
                .ifPresent(intake -> manualEvictUserIntakes(userId, intake.getDate()));
        int deleted = intakeRepository.softDeleteByMealGroupIdAndUserId(
                mealGroupId.toString(), userId, now());
        if (deleted > 0) {
            cacheInvalidationProducer.send(userId, INTAKE_DOMAIN, originDeviceId);
        }
        applicationRepository.deleteByUserIdAndMealGroupId(userId, mealGroupId);
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

    private FoodDto fetchFoodSafe(String foodId, String originalFoodId, Long userId) {
        try {
            return foodClientService.getFoodById(foodId);
        } catch (FeignException.NotFound ex) {
            if (originalFoodId != null) {
                try {
                    return foodClientService.getFoodById(originalFoodId);
                } catch (FeignException.NotFound fallbackEx) {
                    log.warn("Neither foodId nor originalFoodId found for userId={}", userId);
                }
            }
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

    private Optional<IntakeSyncItemDto> applySyncChange(Long userId, IntakeSyncItemDto change) {
        if (change.getUpdatedAt() == null) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Intake sync changes must include updatedAt");
        }
        Optional<Intake> existing = findExistingSyncTarget(userId, change);
        if (existing.isPresent()) {
            Intake intake = existing.get();
            LocalDate oldDate = intake.getDate();
            if (change.isDeleted()) {
                intake.setDeleted(true);
                intake.setUpdatedAt(now());
                Intake saved = intakeRepository.saveAndFlush(intake);
                manualEvict(userId, oldDate);
                return Optional.of(intakeMapper.toSyncDto(saved));
            }
            applySyncState(intake, change);
            intake.setUpdatedAt(now());
            Intake saved = intakeRepository.saveAndFlush(intake);
            manualEvict(userId, oldDate);
            manualEvict(userId, saved.getDate());
            return Optional.of(intakeMapper.toSyncDto(saved));
        }

        if (change.isDeleted()) {
            return Optional.empty();
        }
        validateActiveSyncChange(change);
        Intake intake = new Intake();
        intake.setUserId(userId);
        applySyncState(intake, change);
        intake.setUpdatedAt(now());
        Intake saved = intakeRepository.saveAndFlush(intake);
        manualEvict(userId, saved.getDate());
        return Optional.of(intakeMapper.toSyncDto(saved));
    }

    private Optional<Intake> findExistingSyncTarget(Long userId, IntakeSyncItemDto change) {
        if (change.getId() != null) {
            Optional<Intake> byId = intakeRepository.findAnyByIdAndUserId(change.getId(), userId);
            if (byId.isPresent()) {
                return byId;
            }
        }
        if (change.getRequestId() != null) {
            return intakeRepository.findAnyByUserIdAndRequestId(userId, change.getRequestId());
        }
        return Optional.empty();
    }

    private void applySyncState(Intake intake, IntakeSyncItemDto change) {
        if (change.isDeleted()) {
            intake.setDeleted(true);
            return;
        }
        validateActiveSyncChange(change);
        intakeMapper.updateEntityFromSyncDto(change, intake);
        intake.setDeleted(false);
    }

    private void validateActiveSyncChange(IntakeSyncItemDto change) {
        if (change.getFoodId() == null || change.getDate() == null
                || change.getAmount() == null || change.getNutriments() == null) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Active intake sync changes must include foodId, date, amount and nutriments");
        }
    }

    private void ensureVersionMatches(Long clientVersion, Intake intake) {
        if (clientVersion != null && !clientVersion.equals(intake.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Intake version is stale; pull latest data and retry");
        }
    }

    private void manualEvict(Long userId, LocalDate date) {
        if (date == null) {
            return;
        }
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

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
