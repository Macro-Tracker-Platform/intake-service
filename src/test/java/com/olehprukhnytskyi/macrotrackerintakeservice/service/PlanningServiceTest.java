package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerintakeservice.dto.ShoppingListItemDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.IntakeStatus;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.util.UnitType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlanningServiceTest {
    @Mock
    private IntakeRepository intakeRepository;
    @Mock
    private MealTemplateRepository templateRepository;
    @Mock
    private PlanningEntitlementService entitlementService;
    @InjectMocks
    private PlanningService planningService;

    @Test
    void shoppingListAggregatesFoodsAndExpandsRecipes() {
        Long userId = 7L;
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(14);
        Intake oats = Intake.builder().foodId("oats").foodName("Oats").amount(100)
                .unitType(UnitType.GRAMS).build();
        Intake recipe = Intake.builder().foodId("RECIPE_12").foodName("Porridge")
                .amount(1).unitType(UnitType.PIECES).build();
        MealTemplate template = MealTemplate.builder().id(12L).userId(userId).recipe(true)
                .totalYieldAmount(2).items(List.of(
                        MealTemplateItem.builder().foodId("oats").foodName("Oats")
                                .amount(200).unitType(UnitType.GRAMS).build(),
                        MealTemplateItem.builder().foodId("milk").foodName("Milk")
                                .amount(300).unitType(UnitType.GRAMS).build())).build();
        when(intakeRepository.findByUserIdAndDateBetweenAndStatus(
                userId, from, to, IntakeStatus.PLANNED)).thenReturn(List.of(oats, recipe));
        when(templateRepository.findByIdAndUserId(12L, userId))
                .thenReturn(Optional.of(template));

        List<ShoppingListItemDto> result = planningService.shoppingList(userId, from, to);

        assertThat(result).extracting(ShoppingListItemDto::getFoodName,
                        ShoppingListItemDto::getAmount,
                        ShoppingListItemDto::getRecipeId,
                        ShoppingListItemDto::getRecipeName)
                .containsExactly(
                        tuple("Oats", 100, null, null),
                        tuple("Oats", 100, 12L, "Porridge"),
                        tuple("Milk", 150, 12L, "Porridge"));
    }

    @Test
    void shoppingListMergesRepeatedRecipeButKeepsDifferentRecipesSeparate() {
        Long userId = 7L;
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(14);
        Intake firstServing = Intake.builder().foodId("RECIPE_12").foodName("Porridge")
                .amount(1).unitType(UnitType.PIECES).build();
        Intake secondServing = Intake.builder().foodId("RECIPE_12").foodName("Porridge")
                .amount(1).unitType(UnitType.PIECES).build();
        Intake otherRecipe = Intake.builder().foodId("RECIPE_13").foodName("Cookies")
                .amount(1).unitType(UnitType.PIECES).build();
        MealTemplate porridge = MealTemplate.builder().id(12L).userId(userId).name("Porridge")
                .recipe(true).totalYieldAmount(10).items(List.of(
                        MealTemplateItem.builder().foodId("salt").foodName("Salt")
                                .amount(3).unitType(UnitType.GRAMS).build())).build();
        MealTemplate cookies = MealTemplate.builder().id(13L).userId(userId).name("Cookies")
                .recipe(true).totalYieldAmount(1).items(List.of(
                        MealTemplateItem.builder().foodId("salt").foodName("Salt")
                                .amount(2).unitType(UnitType.GRAMS).build())).build();
        when(intakeRepository.findByUserIdAndDateBetweenAndStatus(
                userId, from, to, IntakeStatus.PLANNED))
                .thenReturn(List.of(firstServing, secondServing, otherRecipe));
        when(templateRepository.findByIdAndUserId(12L, userId))
                .thenReturn(Optional.of(porridge));
        when(templateRepository.findByIdAndUserId(13L, userId))
                .thenReturn(Optional.of(cookies));

        List<ShoppingListItemDto> result = planningService.shoppingList(userId, from, to);

        assertThat(result).extracting(ShoppingListItemDto::getFoodName,
                        ShoppingListItemDto::getAmount,
                        ShoppingListItemDto::getRecipeId)
                .containsExactly(
                        tuple("Salt", 1, 12L),
                        tuple("Salt", 2, 13L));
    }
}
