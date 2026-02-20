package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.GramsCalculationStrategy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientStrategyFactory;
import com.olehprukhnytskyi.util.UnitType;
import feign.FeignException;
import feign.Request;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IntakeServiceTest {
    @Mock
    private FoodClientService foodClientService;
    @Mock
    private IntakeRepository intakeRepository;
    @Mock
    private IntakeMapper intakeMapper;
    @Mock
    private NutrientStrategyFactory nutrientStrategyFactory;
    @Mock
    private NutrimentsMapper nutrimentsMapper;

    @InjectMocks
    private IntakeService intakeService;

    private final Long userId = 456L;

    @Test
    @DisplayName("When valid request with existing food, should save intake and return DTO")
    void save_whenFoodExists_shouldSaveAndReturnDto() {
        // Given
        IntakeRequestDto requestDto = new IntakeRequestDto("food123");
        FoodDto foodDto = FoodDto.builder()
                .id("food123")
                .productName("Apple")
                .availableUnits(List.of(UnitType.GRAMS))
                .build();

        Intake intake = new Intake();
        Intake savedIntake = new Intake();
        savedIntake.setId(1L);
        IntakeResponseDto responseDto = IntakeResponseDto.builder()
                .id(1L)
                .foodName("Apple")
                .build();

        when(foodClientService.getFoodById("food123")).thenReturn(foodDto);
        when(intakeMapper.toModel(requestDto)).thenReturn(intake);
        doAnswer(inv -> {
            inv.<Intake>getArgument(0).setFoodName((foodDto.getProductName()));
            return null;
        }).when(intakeMapper).updateIntakeFromFoodDto(intake, foodDto);
        when(intakeRepository.save(intake)).thenReturn(savedIntake);
        when(intakeMapper.toDto(savedIntake)).thenReturn(responseDto);
        when(nutrientStrategyFactory.getStrategy(UnitType.GRAMS))
                .thenReturn(new GramsCalculationStrategy());
        when(nutrimentsMapper.fromFoodNutriments(any())).thenReturn(new Nutriments());

        // When
        final IntakeResponseDto result = intakeService.save(requestDto, userId);

        // Then
        verify(intakeMapper).updateIntakeFromFoodDto(intake, foodDto);
        verify(intakeRepository).save(intake);

        assertEquals(responseDto, result);
        assertEquals(userId, intake.getUserId());
        assertEquals(requestDto.getFoodId(), intake.getFoodId());
        assertEquals("Apple", intake.getFoodName());
    }

    @Test
    @DisplayName("When food not found, should throw BAD_REQUEST")
    void save_whenFoodNotFound_shouldThrowBadRequest() {
        // Given
        IntakeRequestDto requestDto = new IntakeRequestDto("invalid");

        when(foodClientService.getFoodById("invalid")).thenThrow(new FeignException
                .NotFound("Not found", mock(Request.class), null, null));

        // When & Then
        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> intakeService.save(requestDto, userId));

        assertEquals("Food not found", ex.getMessage());
        verify(intakeRepository, never()).save(any());
    }

    @Test
    @DisplayName("When food service unavailable, should throw SERVICE_UNAVAILABLE")
    void save_whenFoodServiceUnavailable_shouldThrowServiceUnavailable() {
        // Given
        IntakeRequestDto requestDto = new IntakeRequestDto("food123");

        when(foodClientService.getFoodById("food123"))
                .thenThrow(new FeignException.InternalServerError(
                        "Service Unavailable",
                        mock(Request.class),
                        null,
                        null
                ));

        // When & Then
        assertThrows(ExternalServiceException.class,
                () -> intakeService.save(requestDto, userId));

        verify(intakeRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should undo intake group")
    void undoIntakeGroup_shouldDelete() {
        // Given
        String groupId = "uuid-123";
        Long userId = 1L;

        // When
        intakeService.undoIntakeGroup(groupId, userId);

        // Then
        verify(intakeRepository, times(1)).deleteByMealGroupIdAndUserId(groupId, userId);
    }
}
