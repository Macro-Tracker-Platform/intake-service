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
        Map<String, ShoppingListItemDto> grouped = new LinkedHashMap<>();
        for (Intake intake : intakeRepository.findByUserIdAndDateBetweenAndStatus(
                userId, safeFrom, safeTo, IntakeStatus.PLANNED)) {
            if (!expandRecipe(userId, intake, grouped)) {
                add(grouped, intake.getFoodId(), intake.getFoodName(), intake.getAmount(),
                        intake.getUnitType());
            }
        }
        return grouped.values().stream().toList();
    }

    private boolean expandRecipe(Long userId, Intake intake,
                                 Map<String, ShoppingListItemDto> grouped) {
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
            double ratio = intake.getAmount() / (double) template.getTotalYieldAmount();
            for (MealTemplateItem item : template.getItems()) {
                add(grouped, item.getFoodId(), item.getFoodName(),
                        Math.max(1, (int) Math.ceil(item.getAmount() * ratio)),
                        item.getUnitType());
            }
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void add(Map<String, ShoppingListItemDto> grouped, String foodId, String foodName,
                     int amount, UnitType unitType) {
        String key = foodId + "|" + unitType;
        ShoppingListItemDto existing = grouped.get(key);
        if (existing == null) {
            grouped.put(key, ShoppingListItemDto.builder().foodId(foodId)
                    .foodName(foodName).amount(amount).unitType(unitType).build());
        } else {
            existing.setAmount(existing.getAmount() + amount);
        }
    }
}
