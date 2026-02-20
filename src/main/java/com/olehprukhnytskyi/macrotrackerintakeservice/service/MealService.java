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
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientCalculationStrategy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientStrategyFactory;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.CacheConstants;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.NutrientUtils;
import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import java.time.LocalDate;
import java.util.ArrayList;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MealService {
    private final NutrientStrategyFactory strategyFactory;
    private final IntakeRepository intakeRepository;
    private final MealTemplateRepository mealTemplateRepository;
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

    @Transactional
    @CacheEvict(value = CacheConstants.MEAL_TEMPLATES, key = "#userId")
    public Long createTemplate(MealTemplateRequestDto request, Long userId) {
        log.info("Creating meal template '{}' for userId={}", request.getName(), userId);
        List<String> foodIds = request.getItems().stream()
                .map(MealTemplateRequestDto.TemplateItemDto::getFoodId)
                .toList();
        Map<String, FoodDto> foodMap = fetchAndValidateFoods(foodIds);
        MealTemplate template = MealTemplate.builder()
                .userId(userId)
                .name(request.getName())
                .build();
        List<MealTemplateItem> items = request.getItems().stream()
                .map(dto -> buildItem(template, foodMap.get(dto.getFoodId()),
                        dto.getAmount(), dto.getUnitType()))
                .collect(Collectors.toList());
        template.setItems(items);
        return mealTemplateRepository.save(template).getId();
    }

    @Transactional
    @CacheEvict(value = CacheConstants.USER_INTAKES, key = "#userId + ':' + #date")
    public List<IntakeResponseDto> applyTemplate(Long templateId, LocalDate date,
                                                 IntakePeriod period, Long userId) {
        log.info("Applying template id={} for userId={} on date={}", templateId, userId, date);
        MealTemplate template = mealTemplateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Template not found"));
        String batchId = UUID.randomUUID().toString();
        List<Intake> newIntakes = createIntakesFromTemplateItem(
                template.getItems(), date, period, userId, batchId);
        List<Intake> savedIntakes = intakeRepository.saveAll(newIntakes);
        log.debug("Applied template '{}', created {} intake records",
                template.getName(), savedIntakes.size());
        return savedIntakes.stream().map(intakeMapper::toDto).toList();
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
        Map<String, FoodDto> newFoodsMap = resolveNewFoods(request.getItems(), template);
        syncTemplateItems(template, request.getItems(), newFoodsMap);
        mealTemplateRepository.save(template);
        log.debug("Meal template updated successfully id={} userId={}", templateId, userId);
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

    private List<Intake> createIntakesFromTemplateItem(List<MealTemplateItem> items,
                                                       LocalDate date, IntakePeriod period,
                                                       Long userId, String batchId) {
        List<Intake> intakes = new ArrayList<>();
        for (MealTemplateItem item : items) {
            Intake intake = new Intake();
            intake.setMealGroupId(batchId);
            intake.setUserId(userId);
            intake.setFoodId(item.getFoodId());
            intake.setFoodName(item.getFoodName());
            intake.setDate(date);
            intake.setIntakePeriod(period != null ? period : IntakePeriod.SNACK);
            intake.setAmount(item.getAmount());
            intake.setNutriments(nutrimentsMapper.clone(item.getNutriments()));
            intakes.add(intake);
        }
        return intakes;
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
                .amount(amount)
                .unitType(unitType)
                .nutriments(calculated)
                .build();
    }
}
