package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.exception.error.FoodErrorCode;
import com.olehprukhnytskyi.exception.error.IntakeErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.MealTemplateRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.MealTemplateResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateMealTemplateDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.MealTemplateMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.producer.CacheInvalidationProducer;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateApplicationRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientCalculationStrategy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientStrategyFactory;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.CacheConstants;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.NutrientUtils;
import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealService {
    private static final String INTAKE_DOMAIN = "INTAKE";
    private final NutrientStrategyFactory strategyFactory;
    private final IntakeRepository intakeRepository;
    private final MealTemplateRepository mealTemplateRepository;
    private final MealTemplateApplicationRepository applicationRepository;
    private final MealTemplateApplicationService applicationService;
    private final CacheInvalidationProducer cacheInvalidationProducer;
    private final IntakeMapper intakeMapper;
    private final MealTemplateMapper mealTemplateMapper;
    private final NutrimentsMapper nutrimentsMapper;
    private final FoodClientService foodClientService;

    @Transactional(readOnly = true)
    @Cacheable(value = CacheConstants.MEAL_TEMPLATES, key = "#userId")
    public List<MealTemplateResponseDto> getTemplates(Long userId) {
        log.info("Fetching meal templates from DB for userId={}", userId);
        List<MealTemplate> templates = mealTemplateRepository.findAllByUserId(userId);
        return mealTemplateMapper.toDtoList(templates);
    }

    @CacheEvict(value = CacheConstants.MEAL_TEMPLATES, key = "#userId")
    public Long createTemplate(MealTemplateRequestDto request, Long userId, UUID requestId) {
        log.info("Creating meal template '{}' for userId={}", request.getName(), userId);
        MealTemplate existing = mealTemplateRepository.findByUserIdAndRequestId(userId, requestId)
                .orElse(null);
        if (existing != null) {
            return existing.getId();
        }
        RecipeYield recipeYield = resolveRecipeYield(request.isRecipe(),
                request.getTotalYieldAmount(), request.getYieldUnitType());
        List<String> foodIds = request.getItems().stream()
                .map(MealTemplateRequestDto.TemplateItemDto::getFoodId)
                .toList();
        Map<String, FoodDto> foodMap = fetchAndValidateFoods(foodIds);
        MealTemplate template = MealTemplate.builder()
                .userId(userId)
                .requestId(requestId)
                .name(request.getName())
                .recipe(request.isRecipe())
                .totalYieldAmount(recipeYield.amount())
                .yieldUnitType(recipeYield.unitType())
                .build();
        List<MealTemplateItem> items = request.getItems().stream()
                .map(dto -> buildItem(template, foodMap.get(dto.getFoodId()),
                        dto.getAmount(), dto.getUnitType()))
                .collect(Collectors.toList());
        template.setItems(items);
        try {
            return mealTemplateRepository.saveAndFlush(template).getId();
        } catch (DataIntegrityViolationException exception) {
            return mealTemplateRepository.findByUserIdAndRequestId(userId, requestId)
                    .map(MealTemplate::getId)
                    .orElseThrow(() -> exception);
        }
    }

    @CacheEvict(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #date")
    public List<IntakeResponseDto> applyTemplate(Long templateId, LocalDate date,
                                                 IntakePeriod period, UUID mealGroupId,
                                                 Long userId, UUID requestId) {
        return applyTemplate(templateId, date, period, mealGroupId, userId, requestId, null);
    }

    @CacheEvict(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #date")
    public List<IntakeResponseDto> applyTemplate(Long templateId, LocalDate date,
                                                 IntakePeriod period, UUID mealGroupId,
                                                 Long userId, UUID requestId,
                                                 String originDeviceId) {
        log.info("Applying template id={} for userId={} on date={}", templateId, userId, date);
        List<IntakeResponseDto> existing = findAppliedIntakes(userId, requestId);
        if (existing != null) {
            return existing;
        }
        IntakePeriod resolvedPeriod = period != null ? period : IntakePeriod.SNACK;
        try {
            List<IntakeResponseDto> created = applicationService.create(
                    templateId, date, resolvedPeriod, mealGroupId,
                    userId, requestId);
            cacheInvalidationProducer.send(userId, INTAKE_DOMAIN, originDeviceId);
            return created;
        } catch (DataIntegrityViolationException exception) {
            List<IntakeResponseDto> concurrentlyCreated = findAppliedIntakes(userId, requestId);
            if (concurrentlyCreated != null) {
                return concurrentlyCreated;
            }
            throw exception;
        }
    }

    @CacheEvict(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #date")
    public IntakeResponseDto applyRecipe(Long templateId, Integer consumedAmount,
                                         UnitType unitType,
                                         LocalDate date, IntakePeriod period,
                                         Long userId, UUID requestId) {
        return applyRecipe(templateId, consumedAmount, unitType, date, period, userId,
                requestId, null);
    }

    @CacheEvict(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #date")
    public IntakeResponseDto applyRecipe(Long templateId, Integer consumedAmount,
                                         UnitType unitType,
                                         LocalDate date, IntakePeriod period,
                                         Long userId, UUID requestId,
                                         String originDeviceId) {
        log.info("Applying recipe template id={} for userId={} on date={}",
                templateId, userId, date);
        List<IntakeResponseDto> existing = findAppliedIntakes(userId, requestId);
        if (existing != null && !existing.isEmpty()) {
            return existing.getFirst();
        }
        IntakePeriod resolvedPeriod = period != null ? period : IntakePeriod.SNACK;
        try {
            IntakeResponseDto created = applicationService.createRecipe(
                    templateId, consumedAmount, unitType,
                    date, resolvedPeriod, userId, requestId);
            cacheInvalidationProducer.send(userId, INTAKE_DOMAIN, originDeviceId);
            return created;
        } catch (DataIntegrityViolationException exception) {
            List<IntakeResponseDto> concurrentlyCreated = findAppliedIntakes(userId, requestId);
            if (concurrentlyCreated != null && !concurrentlyCreated.isEmpty()) {
                return concurrentlyCreated.getFirst();
            }
            throw exception;
        }
    }

    @Transactional
    @CacheEvict(value = CacheConstants.MEAL_TEMPLATES, key = "#userId")
    public void deleteTemplate(Long templateId, Long userId) {
        log.info("Deleting template id={} for userId={}", templateId, userId);
        MealTemplate template = mealTemplateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Template not found or does not belong to user"));
        mealTemplateRepository.delete(template);
    }

    @Transactional
    @CacheEvict(value = CacheConstants.MEAL_TEMPLATES, key = "#userId")
    public void updateTemplate(Long templateId, UpdateMealTemplateDto request, Long userId) {
        log.info("Updating template id={} for userId={}", templateId, userId);
        MealTemplate template = mealTemplateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Template not found"));
        if (request.getName() != null) {
            template.setName(request.getName());
        }
        if (request.getRecipe() != null) {
            template.setRecipe(request.getRecipe());
        }
        if (request.getTotalYieldAmount() != null) {
            template.setTotalYieldAmount(request.getTotalYieldAmount());
        }
        if (request.getYieldUnitType() != null) {
            template.setYieldUnitType(request.getYieldUnitType());
        }
        RecipeYield recipeYield = resolveRecipeYield(template.isRecipe(),
                template.getTotalYieldAmount(), template.getYieldUnitType());
        template.setTotalYieldAmount(recipeYield.amount());
        template.setYieldUnitType(recipeYield.unitType());
        if (request.getItems() != null) {
            Map<String, FoodDto> newFoodsMap = resolveNewFoods(request.getItems(), template);
            syncTemplateItems(template, request.getItems(), newFoodsMap);
        }
        mealTemplateRepository.save(template);
        log.debug("Meal template updated successfully id={} userId={}", templateId, userId);
    }

    private RecipeYield resolveRecipeYield(boolean recipe, Integer totalYieldAmount,
                                           UnitType yieldUnitType) {
        if (!recipe) {
            return new RecipeYield(null, null);
        }
        if (totalYieldAmount == null || totalYieldAmount < 1) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "totalYieldAmount is required for recipe templates");
        }
        if (yieldUnitType == null) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "yieldUnitType is required for recipe templates");
        }
        return new RecipeYield(totalYieldAmount, yieldUnitType);
    }

    private Map<String, FoodDto> resolveNewFoods(
            List<UpdateMealTemplateDto.TemplateItemDto> dtos, MealTemplate template) {
        Set<String> existingIds = template.getItems().stream()
                .map(MealTemplateItem::getFoodId)
                .collect(Collectors.toSet());
        List<String> newIds = dtos.stream()
                .map(UpdateMealTemplateDto.TemplateItemDto::getFoodId)
                .filter(id -> !existingIds.contains(id))
                .distinct()
                .toList();
        return fetchAndValidateFoods(newIds);
    }

    private void syncTemplateItems(MealTemplate template,
                                   List<UpdateMealTemplateDto.TemplateItemDto> dtos,
                                   Map<String, FoodDto> newFoodsMap) {
        Map<String, UpdateMealTemplateDto.TemplateItemDto> incomingMap = dtos.stream()
                .collect(Collectors.toMap(UpdateMealTemplateDto.TemplateItemDto::getFoodId,
                        item -> item, (oldV, newV) -> oldV));
        template.getItems().removeIf(item -> !incomingMap.containsKey(item.getFoodId()));
        for (UpdateMealTemplateDto.TemplateItemDto dto : dtos) {
            template.getItems().stream()
                    .filter(item -> item.getFoodId().equals(dto.getFoodId()))
                    .findFirst()
                    .ifPresentOrElse(
                            existing -> updateItemState(existing, dto),
                            () -> {
                                validateNewItemFields(dto);
                                template.getItems().add(buildItem(
                                        template, newFoodsMap.get(dto.getFoodId()),
                                        dto.getAmount(), dto.getUnitType())
                                );
                            });
        }
    }

    private void updateItemState(MealTemplateItem item,
                                 UpdateMealTemplateDto.TemplateItemDto dto) {
        boolean changed = false;
        if (dto.getAmount() != null && !dto.getAmount().equals(item.getAmount())) {
            item.setAmount(dto.getAmount());
            changed = true;
        }
        if (dto.getUnitType() != null && dto.getUnitType() != item.getUnitType()) {
            NutrientUtils.validateUnitType(dto.getUnitType(), item.getNutriments());
            item.setUnitType(dto.getUnitType());
            changed = true;
        }
        if (changed) {
            strategyFactory.getStrategy(item.getUnitType())
                    .calculate(item.getNutriments(), item.getAmount());
        }
    }

    private void validateNewItemFields(UpdateMealTemplateDto.TemplateItemDto dto) {
        if (dto.getAmount() == null) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "Amount is required for new template item: " + dto.getFoodId());
        }
        if (dto.getUnitType() == null) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "UnitType is required for new template item: " + dto.getFoodId());
        }
    }

    private List<IntakeResponseDto> findAppliedIntakes(Long userId, UUID requestId) {
        return applicationRepository.findByUserIdAndRequestId(userId, requestId)
                .map(application -> intakeRepository
                        .findByMealGroupIdAndUserIdOrderByMealItemPositionAsc(
                                application.getMealGroupId().toString(), userId)
                        .stream()
                        .map(intakeMapper::toDto)
                        .toList())
                .orElse(null);
    }

    private Map<String, FoodDto> fetchAndValidateFoods(List<String> foodIds) {
        if (foodIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> uniqueIds = foodIds.stream().distinct().toList();
        List<FoodDto> foods = foodClientService.getFoodsByIds(uniqueIds);
        if (foods.size() != uniqueIds.size()) {
            Set<String> foundIds = foods.stream()
                    .map(FoodDto::getId)
                    .collect(Collectors.toSet());
            List<String> missing = uniqueIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .toList();
            throw new NotFoundException(FoodErrorCode.FOOD_NOT_FOUND,
                    "Foods not found: " + missing);
        }
        return foods.stream().collect(Collectors.toMap(FoodDto::getId, f -> f));
    }

    private MealTemplateItem buildItem(MealTemplate template, FoodDto food,
                                       Integer amount, UnitType unitType) {
        NutrientUtils.validateUnitSupported(food, unitType);
        NutrientCalculationStrategy strategy = strategyFactory.getStrategy(unitType);
        Nutriments calculated = nutrimentsMapper.fromFoodNutriments(food.getNutriments());
        strategy.calculate(calculated, amount);
        return MealTemplateItem.builder()
                .template(template)
                .foodId(food.getId())
                .foodName(food.getProductName())
                .brand(food.getBrands())
                .amount(amount)
                .unitType(unitType)
                .nutriments(calculated)
                .originalFoodId(food.getOriginalFoodId())
                .moderationStatus(food.getModerationStatus())
                .verifiedByAdmin(food.isVerifiedByAdmin())
                .build();
    }

    private record RecipeYield(Integer amount, UnitType unitType) {
    }
}
