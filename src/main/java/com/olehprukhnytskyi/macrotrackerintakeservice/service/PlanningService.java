package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.ShoppingListItemDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.IntakeStatus;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.util.UnitType;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlanningService {
    private static final String RECIPE_PREFIX = "RECIPE_";
    private final IntakeRepository intakeRepository;
    private final MealTemplateRepository templateRepository;
    private final PlanningEntitlementService entitlementService;

    @Transactional(readOnly = true)
    public List<ShoppingListItemDto> shoppingList(Long userId, LocalDate from, LocalDate to) {
        entitlementService.requireFuturePlanning(userId);
        LocalDate today = LocalDate.now();
        LocalDate safeFrom = from == null || from.isBefore(today) ? today : from;
        LocalDate safeTo = to == null ? today.plusDays(14) : to;
        if (safeTo.isBefore(safeFrom) || safeTo.isAfter(today.plusDays(14))) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Shopping list range must be within 14 days");
        }
        Map<String, ShoppingListItemDto> generalItems = new LinkedHashMap<>();
        Map<Long, RecipeGroup> recipeGroups = new LinkedHashMap<>();
        for (Intake intake : intakeRepository.findByUserIdAndDateBetweenAndStatus(
                userId, safeFrom, safeTo, IntakeStatus.PLANNED)) {
            if (!expandRecipe(userId, intake, recipeGroups)) {
                addGeneral(generalItems, intake.getFoodId(), intake.getFoodName(),
                        intake.getAmount(), intake.getUnitType());
            }
        }
        List<ShoppingListItemDto> result = new ArrayList<>(generalItems.values());
        recipeGroups.values().forEach(group -> group.items.values().forEach(item ->
                result.add(ShoppingListItemDto.builder()
                        .foodId(item.foodId)
                        .foodName(item.foodName)
                        .amount(Math.max(1, (int) Math.ceil(item.amount)))
                        .unitType(item.unitType)
                        .recipeId(group.recipeId)
                        .recipeName(group.recipeName)
                        .build())));
        return result;
    }

    private boolean expandRecipe(Long userId, Intake intake,
                                 Map<Long, RecipeGroup> recipeGroups) {
        if (intake.getFoodId() == null || !intake.getFoodId().startsWith(RECIPE_PREFIX)) {
            return false;
        }
        try {
            Long templateId = Long.valueOf(intake.getFoodId().substring(RECIPE_PREFIX.length()));
            MealTemplate template = templateRepository.findByIdAndUserId(templateId, userId)
                    .filter(MealTemplate::isRecipe).orElse(null);
            if (template == null || template.getItems() == null
                    || template.getTotalYieldAmount() == null
                    || template.getTotalYieldAmount() <= 0) {
                return false;
            }
            String recipeName = template.getName() == null || template.getName().isBlank()
                    ? intake.getFoodName() : template.getName();
            RecipeGroup group = recipeGroups.computeIfAbsent(templateId,
                    ignored -> new RecipeGroup(templateId, recipeName));
            double ratio = intake.getAmount() / (double) template.getTotalYieldAmount();
            for (MealTemplateItem item : template.getItems()) {
                group.add(item, item.getAmount() * ratio);
            }
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void addGeneral(Map<String, ShoppingListItemDto> grouped, String foodId,
                            String foodName, int amount, UnitType unitType) {
        String identity = foodId == null || foodId.isBlank() ? foodName : foodId;
        String key = identity + "|" + unitType;
        ShoppingListItemDto existing = grouped.get(key);
        if (existing == null) {
            grouped.put(key, ShoppingListItemDto.builder().foodId(foodId)
                    .foodName(foodName).amount(amount).unitType(unitType).build());
        } else {
            existing.setAmount(existing.getAmount() + amount);
        }
    }

    private static final class RecipeGroup {
        private final Long recipeId;
        private final String recipeName;
        private final Map<String, AggregatedIngredient> items = new LinkedHashMap<>();

        private RecipeGroup(Long recipeId, String recipeName) {
            this.recipeId = recipeId;
            this.recipeName = recipeName;
        }

        private void add(MealTemplateItem item, double amount) {
            String identity = item.getFoodId() == null || item.getFoodId().isBlank()
                    ? item.getFoodName() : item.getFoodId();
            String key = identity + "|" + item.getUnitType();
            AggregatedIngredient existing = items.get(key);
            if (existing == null) {
                items.put(key, new AggregatedIngredient(item.getFoodId(), item.getFoodName(),
                        amount, item.getUnitType()));
            } else {
                existing.amount += amount;
            }
        }
    }

    private static final class AggregatedIngredient {
        private final String foodId;
        private final String foodName;
        private double amount;
        private final UnitType unitType;

        private AggregatedIngredient(String foodId, String foodName, double amount,
                                     UnitType unitType) {
            this.foodId = foodId;
            this.foodName = foodName;
            this.amount = amount;
            this.unitType = unitType;
        }
    }
}
