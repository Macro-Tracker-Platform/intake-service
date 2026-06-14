package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.exception.error.IntakeErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateApplication;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateApplicationRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealTemplateApplicationService {
    private static final int NUTRIENT_SCALE = 2;
    private static final int RATIO_SCALE = 6;

    private final IntakeRepository intakeRepository;
    private final MealTemplateRepository mealTemplateRepository;
    private final MealTemplateApplicationRepository applicationRepository;
    private final IntakeMapper intakeMapper;
    private final NutrimentsMapper nutrimentsMapper;

    @Transactional
    public List<IntakeResponseDto> create(Long templateId, LocalDate date, IntakePeriod period,
                                          UUID mealGroupId, Long userId, UUID requestId) {
        MealTemplate template = mealTemplateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Template not found"));
        if (template.isRecipe()) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "Recipe templates must be applied through the recipe endpoint");
        }
        MealTemplateApplication application = MealTemplateApplication.builder()
                .userId(userId)
                .requestId(requestId)
                .mealGroupId(mealGroupId)
                .templateId(templateId)
                .date(date)
                .intakePeriod(period)
                .build();
        applicationRepository.saveAndFlush(application);

        List<Intake> intakes = createIntakes(template, date, period, mealGroupId, userId);
        List<Intake> savedIntakes = intakeRepository.saveAllAndFlush(intakes);
        return savedIntakes.stream().map(intakeMapper::toDto).toList();
    }

    @Transactional
    public IntakeResponseDto createRecipe(Long templateId, Integer consumedAmount,
                                          UnitType unitType,
                                          LocalDate date, IntakePeriod period,
                                          Long userId, UUID requestId) {
        MealTemplate template = mealTemplateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Template not found"));
        if (!template.isRecipe()) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "Template is not a recipe");
        }
        validateRecipeInput(template, consumedAmount, unitType);
        UUID mealGroupId = UUID.randomUUID();
        MealTemplateApplication application = MealTemplateApplication.builder()
                .userId(userId)
                .requestId(requestId)
                .mealGroupId(mealGroupId)
                .templateId(templateId)
                .date(date)
                .intakePeriod(period)
                .build();
        applicationRepository.saveAndFlush(application);

        Intake saved = saveRecipeIntake(template, consumedAmount, unitType, userId,
                date, period, requestId, mealGroupId);
        return intakeMapper.toDto(saved);
    }

    @Transactional
    public IntakeResponseDto logRecipeIntake(MealTemplate template, Integer consumedAmount,
                                             UnitType unitType,
                                             Long userId, LocalDate date, IntakePeriod period) {
        validateRecipeInput(template, consumedAmount, unitType);
        Intake saved = saveRecipeIntake(template, consumedAmount, unitType, userId,
                date, period, null, null);
        return intakeMapper.toDto(saved);
    }

    private List<Intake> createIntakes(MealTemplate template, LocalDate date,
                                       IntakePeriod period, UUID mealGroupId, Long userId) {
        List<Intake> intakes = new ArrayList<>();
        for (int position = 0; position < template.getItems().size(); position++) {
            MealTemplateItem item = template.getItems().get(position);
            Intake intake = new Intake();
            intake.setMealGroupId(mealGroupId.toString());
            intake.setMealTemplateName(template.getName());
            intake.setMealItemPosition(position);
            intake.setUserId(userId);
            intake.setFoodId(item.getFoodId());
            intake.setFoodName(item.getFoodName());
            intake.setBrand(item.getBrand());
            intake.setDate(date);
            intake.setUnitType(item.getUnitType());
            intake.setIntakePeriod(period);
            intake.setAmount(item.getAmount());
            intake.setNutriments(nutrimentsMapper.clone(item.getNutriments()));
            intake.setOriginalFoodId(item.getOriginalFoodId());
            intake.setModerationStatus(item.getModerationStatus());
            intake.setVerifiedByAdmin(item.isVerifiedByAdmin());
            intakes.add(intake);
        }
        return intakes;
    }

    private Intake saveRecipeIntake(MealTemplate template, Integer consumedAmount,
                                    UnitType unitType,
                                    Long userId, LocalDate date, IntakePeriod period,
                                    UUID requestId, UUID mealGroupId) {
        Intake intake = new Intake();
        intake.setRequestId(requestId);
        intake.setMealGroupId(mealGroupId != null ? mealGroupId.toString() : null);
        intake.setMealTemplateName(template.getName());
        intake.setMealItemPosition(0);
        intake.setUserId(userId);
        intake.setFoodId("RECIPE_" + template.getId());
        intake.setFoodName(template.getName());
        intake.setDate(date);
        intake.setUnitType(unitType);
        intake.setIntakePeriod(period);
        intake.setAmount(consumedAmount);
        intake.setNutriments(calculateRecipeNutriments(template, consumedAmount));
        return intakeRepository.saveAndFlush(intake);
    }

    private Nutriments calculateRecipeNutriments(MealTemplate template,
                                                 Integer consumedAmount) {
        Nutriments total = template.getItems().stream()
                .map(MealTemplateItem::getNutriments)
                .reduce(new Nutriments(), this::addNutriments);
        BigDecimal totalYield = BigDecimal.valueOf(template.getTotalYieldAmount());
        BigDecimal ratio = BigDecimal.valueOf(consumedAmount)
                .divide(totalYield, RATIO_SCALE, RoundingMode.HALF_UP);

        Nutriments result = new Nutriments();
        result.setCalories(scale(total.getCalories().multiply(ratio)));
        result.setCarbohydrates(scale(total.getCarbohydrates().multiply(ratio)));
        result.setFat(scale(total.getFat().multiply(ratio)));
        result.setProtein(scale(total.getProtein().multiply(ratio)));
        setUnitNutriments(result, total, totalYield, template.getYieldUnitType());
        return result;
    }

    private Nutriments addNutriments(Nutriments left, Nutriments right) {
        Nutriments leftValue = left != null ? left : new Nutriments();
        Nutriments rightValue = right != null ? right : new Nutriments();
        Nutriments result = new Nutriments();
        result.setCalories(safe(leftValue.getCalories()).add(safe(rightValue.getCalories())));
        result.setCarbohydrates(safe(leftValue.getCarbohydrates())
                .add(safe(rightValue.getCarbohydrates())));
        result.setFat(safe(leftValue.getFat()).add(safe(rightValue.getFat())));
        result.setProtein(safe(leftValue.getProtein()).add(safe(rightValue.getProtein())));
        return result;
    }

    private void setUnitNutriments(Nutriments result, Nutriments total, BigDecimal totalYield,
                                  UnitType unitType) {
        BigDecimal multiplier = unitType == UnitType.GRAMS ? BigDecimal.valueOf(100)
                : BigDecimal.ONE;
        BigDecimal calories = perUnit(total.getCalories(), totalYield, multiplier);
        BigDecimal carbohydrates = perUnit(total.getCarbohydrates(), totalYield, multiplier);
        BigDecimal fat = perUnit(total.getFat(), totalYield, multiplier);
        BigDecimal protein = perUnit(total.getProtein(), totalYield, multiplier);
        if (unitType == UnitType.GRAMS) {
            result.setCaloriesPer100(calories);
            result.setCarbohydratesPer100(carbohydrates);
            result.setFatPer100(fat);
            result.setProteinPer100(protein);
        } else {
            result.setCaloriesPerPiece(calories);
            result.setCarbohydratesPerPiece(carbohydrates);
            result.setFatPerPiece(fat);
            result.setProteinPerPiece(protein);
        }
    }

    private BigDecimal perUnit(BigDecimal total, BigDecimal totalYield, BigDecimal multiplier) {
        return scale(safe(total).multiply(multiplier)
                .divide(totalYield, NUTRIENT_SCALE, RoundingMode.HALF_UP));
    }

    private BigDecimal scale(BigDecimal value) {
        return safe(value).setScale(NUTRIENT_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private void validateRecipeInput(MealTemplate template, Integer consumedAmount,
                                     UnitType unitType) {
        if (template == null || !template.isRecipe()) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "Template is not a recipe");
        }
        if (template.getTotalYieldAmount() == null || template.getTotalYieldAmount() < 1
                || template.getYieldUnitType() == null) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "Recipe yield amount and unit must be configured");
        }
        if (consumedAmount == null || consumedAmount < 1) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "consumedAmount must be greater than zero");
        }
        if (unitType != template.getYieldUnitType()) {
            throw new BadRequestException(CommonErrorCode.VALIDATION_ERROR,
                    "Consumed unitType must match recipe yieldUnitType");
        }
    }
}
