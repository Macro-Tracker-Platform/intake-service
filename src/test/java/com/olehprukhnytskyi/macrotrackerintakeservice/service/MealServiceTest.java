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
import com.olehprukhnytskyi.exception.error.IntakeErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.MealTemplateRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.MealTemplateMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateApplicationRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.GramsCalculationStrategy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientStrategyFactory;
import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
    private MealTemplateApplicationRepository applicationRepository;
    @Mock
    private MealTemplateApplicationService applicationService;
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
    @DisplayName("When request id already exists, should return persisted template")
    void createTemplate_whenRequestIdExists_shouldReturnPersistedTemplate() {
        UUID requestId = UUID.randomUUID();
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        MealTemplate existing = MealTemplate.builder().id(10L).userId(1L)
                .requestId(requestId).build();

        when(mealTemplateRepository.findByUserIdAndRequestId(1L, requestId))
                .thenReturn(Optional.of(existing));

        Long result = mealService.createTemplate(request, 1L, requestId);

        assertThat(result).isEqualTo(10L);
        verify(foodClientService, never()).getFoodsByIds(anyList());
        verify(mealTemplateRepository, never()).saveAndFlush(any());
    }

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
                .brands("Oat Company")
                .nutriments(new NutrimentsDto())
                .availableUnits(List.of(UnitType.GRAMS))
                .build();

        when(foodClientService.getFoodsByIds(List.of(foodId))).thenReturn(List.of(foodDto));
        when(nutrientStrategyFactory.getStrategy(UnitType.GRAMS))
                .thenReturn(new GramsCalculationStrategy());
        when(nutrimentsMapper.fromFoodNutriments(any())).thenReturn(new Nutriments());

        MealTemplate savedTemplateMock = mock(MealTemplate.class);
        when(savedTemplateMock.getId()).thenReturn(10L);
        when(mealTemplateRepository.saveAndFlush(any(MealTemplate.class)))
                .thenReturn(savedTemplateMock);
        UUID requestId = UUID.randomUUID();

        // When
        Long resultId = mealService.createTemplate(request, 1L, requestId);

        // Then
        assertThat(resultId).isEqualTo(10L);

        ArgumentCaptor<MealTemplate> captor = ArgumentCaptor.forClass(MealTemplate.class);
        verify(mealTemplateRepository).saveAndFlush(captor.capture());

        MealTemplate captured = captor.getValue();
        assertThat(captured.getName()).isEqualTo("My Breakfast");
        assertThat(captured.getRequestId()).isEqualTo(requestId);
        assertThat(captured.getItems()).hasSize(1);
        assertThat(captured.getItems().get(0).getFoodName()).isEqualTo("Oats");
        assertThat(captured.getItems().get(0).getBrand()).isEqualTo("Oat Company");
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
                mealService.createTemplate(request, 1L, UUID.randomUUID()));
        verify(mealTemplateRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("When valid request, should calculate nutriments and save intakes with batchId")
    void applyTemplate_whenValid_shouldCreateIntakes() {
        // Given
        Long userId = 1L;
        Long templateId = 100L;
        LocalDate date = LocalDate.now();
        UUID requestId = UUID.randomUUID();
        UUID mealGroupId = UUID.randomUUID();
        List<IntakeResponseDto> expected = List.of(new IntakeResponseDto());
        when(applicationService.create(templateId, date, IntakePeriod.LUNCH,
                mealGroupId, userId, requestId)).thenReturn(expected);

        // When
        List<IntakeResponseDto> result = mealService.applyTemplate(
                templateId, date, IntakePeriod.LUNCH, mealGroupId, userId, requestId);

        // Then
        assertThat(result).isEqualTo(expected);
        verify(applicationService).create(templateId, date, IntakePeriod.LUNCH,
                mealGroupId, userId, requestId);
    }

    @Test
    @DisplayName("When template not found, should throw NotFoundException")
    void applyTemplate_whenTemplateNotFound_shouldThrowException() {
        // Given
        UUID requestId = UUID.randomUUID();
        UUID mealGroupId = UUID.randomUUID();
        LocalDate date = LocalDate.now();
        when(applicationService.create(1L, date, IntakePeriod.SNACK,
                mealGroupId, 1L, requestId))
                .thenThrow(new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Template not found"));

        // When & Then
        assertThrows(NotFoundException.class, () ->
                mealService.applyTemplate(1L, date, IntakePeriod.SNACK,
                        mealGroupId, 1L, requestId));
    }
}
