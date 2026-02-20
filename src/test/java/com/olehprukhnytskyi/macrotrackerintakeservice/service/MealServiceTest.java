package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.MealTemplateRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.MealTemplateMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.GramsCalculationStrategy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientStrategyFactory;
import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MealServiceTest {
    @Mock
    private IntakeRepository intakeRepository;
    @Mock
    private MealTemplateRepository mealTemplateRepository;
    @Mock
    private IntakeMapper intakeMapper;
    @Mock
    private MealTemplateMapper mealTemplateMapper;
    @Mock
    private NutrimentsMapper nutrimentsMapper;
    @Mock
    private FoodClientService foodClientService;
    @Mock
    private NutrientStrategyFactory nutrientStrategyFactory;

    @InjectMocks
    private MealService mealService;

    @Test
    @DisplayName("When valid request, should create template")
    void createTemplate_whenValidRequest_shouldSaveTemplate() {
        // Given
        String foodId = "f1";
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("My Breakfast");
        request.setItems(List.of(
                MealTemplateRequestDto.TemplateItemDto.builder()
                        .foodId(foodId)
                        .unitType(UnitType.GRAMS)
                        .amount(100)
                        .build()
        ));

        FoodDto foodDto = FoodDto.builder()
                .id(foodId)
                .productName("Oats")
                .nutriments(new NutrimentsDto())
                .availableUnits(List.of(UnitType.GRAMS))
                .build();

        when(foodClientService.getFoodsByIds(List.of(foodId))).thenReturn(List.of(foodDto));
        when(nutrientStrategyFactory.getStrategy(UnitType.GRAMS))
                .thenReturn(new GramsCalculationStrategy());
        when(nutrimentsMapper.fromFoodNutriments(any())).thenReturn(new Nutriments());

        MealTemplate savedTemplateMock = mock(MealTemplate.class);
        when(savedTemplateMock.getId()).thenReturn(10L);
        when(mealTemplateRepository.save(any(MealTemplate.class)))
                .thenReturn(savedTemplateMock);

        // When
        Long resultId = mealService.createTemplate(request, 1L);

        // Then
        assertThat(resultId).isEqualTo(10L);

        ArgumentCaptor<MealTemplate> captor = ArgumentCaptor.forClass(MealTemplate.class);
        verify(mealTemplateRepository).save(captor.capture());

        MealTemplate captured = captor.getValue();
        assertThat(captured.getName()).isEqualTo("My Breakfast");
        assertThat(captured.getItems()).hasSize(1);
        assertThat(captured.getItems().get(0).getFoodName()).isEqualTo("Oats");
        assertThat(captured.getItems().get(0).getAmount()).isEqualTo(100);
    }

    @Test
    @DisplayName("When food not found, should throw NotFoundException")
    void createTemplate_whenFoodNotFound_shouldThrowException() {
        // Given
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Partial");
        request.setItems(List.of(
                MealTemplateRequestDto.TemplateItemDto.builder()
                        .foodId("exists")
                        .amount(100)
                        .build(),
                MealTemplateRequestDto.TemplateItemDto.builder()
                        .foodId("missing")
                        .amount(50)
                        .build()
        ));

        FoodDto foodDto = FoodDto.builder()
                .id("exists")
                .productName("Food")
                .nutriments(mock(NutrimentsDto.class))
                .build();

        when(foodClientService.getFoodsByIds(anyList())).thenReturn(List.of(foodDto));

        // When & Then
        assertThrows(NotFoundException.class, () ->
                mealService.createTemplate(request, 1L));
        verify(mealTemplateRepository, never()).save(any());
    }

    @Test
    @DisplayName("When valid request, should calculate nutriments and save intakes with batchId")
    void applyTemplate_whenValid_shouldCreateIntakes() {
        // Given
        Long userId = 1L;
        Long templateId = 100L;

        Nutriments baseNutriments = new Nutriments();
        baseNutriments.setCaloriesPer100(BigDecimal.valueOf(200));
        baseNutriments.setCalories(BigDecimal.valueOf(100));

        MealTemplateItem item = MealTemplateItem.builder()
                .foodId("f1")
                .foodName("Rice")
                .amount(50)
                .nutriments(baseNutriments)
                .build();

        MealTemplate template = MealTemplate.builder()
                .id(templateId)
                .userId(userId)
                .items(List.of(item))
                .build();

        when(mealTemplateRepository.findByIdAndUserId(templateId, userId))
                .thenReturn(Optional.of(template));
        when(intakeRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(intakeMapper.toDto(any(Intake.class))).thenReturn(new IntakeResponseDto());
        when(nutrimentsMapper.clone(any(Nutriments.class)))
                .thenAnswer(invocation -> invocation.<Nutriments>getArgument(0));

        // When
        mealService.applyTemplate(templateId, LocalDate.now(), IntakePeriod.LUNCH, userId);

        // Then
        ArgumentCaptor<List<Intake>> intakeCaptor = ArgumentCaptor.forClass(List.class);
        verify(intakeRepository).saveAll(intakeCaptor.capture());

        List<Intake> savedIntakes = intakeCaptor.getValue();
        assertThat(savedIntakes).hasSize(1);

        Intake savedIntake = savedIntakes.getFirst();
        assertThat(savedIntake.getMealGroupId()).isNotNull();
        assertThat(savedIntake.getAmount()).isEqualTo(50);
        assertThat(savedIntake.getFoodName()).isEqualTo("Rice");
        assertThat(savedIntake.getNutriments().getCalories())
                .isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("When template not found, should throw NotFoundException")
    void applyTemplate_whenTemplateNotFound_shouldThrowException() {
        // Given
        when(mealTemplateRepository.findByIdAndUserId(any(), any()))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(NotFoundException.class, () ->
                mealService.applyTemplate(1L, LocalDate.now(), IntakePeriod.SNACK, 1L));

        verify(intakeRepository, never()).saveAll(any());
    }
}
