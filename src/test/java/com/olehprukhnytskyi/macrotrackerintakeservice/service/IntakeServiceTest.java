package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateApplicationRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.GramsCalculationStrategy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.strategy.NutrientStrategyFactory;
import com.olehprukhnytskyi.util.UnitType;
import feign.FeignException;
import feign.Request;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class IntakeServiceTest {
    @Mock
    private FoodClientService foodClientService;
    @Mock
    private IntakeRepository intakeRepository;
    @Mock
    private MealTemplateApplicationRepository applicationRepository;
    @Mock
    private IntakeMapper intakeMapper;
    @Mock
    private NutrientStrategyFactory nutrientStrategyFactory;
    @Mock
    private NutrimentsMapper nutrimentsMapper;
    @Mock
    private CacheInvalidationProducer cacheInvalidationProducer;

    @InjectMocks
    private IntakeService intakeService;

    private final Long userId = 456L;

    @Test
    @DisplayName("When request id already exists, should return persisted intake")
    void save_whenRequestIdExists_shouldReturnPersistedIntake() {
        UUID requestId = UUID.randomUUID();
        IntakeRequestDto requestDto = new IntakeRequestDto("food123");
        Intake existing = Intake.builder().id(10L).userId(userId).requestId(requestId).build();
        IntakeResponseDto responseDto = IntakeResponseDto.builder().id(10L).build();

        when(intakeRepository.findByUserIdAndRequestId(userId, requestId))
                .thenReturn(Optional.of(existing));
        when(intakeMapper.toDto(existing)).thenReturn(responseDto);

        IntakeResponseDto result = intakeService.save(requestDto, userId, requestId);

        assertEquals(responseDto, result);
        verify(foodClientService, never()).getFoodById(any());
        verify(intakeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("When valid request with existing food, should save intake and return DTO")
    void save_whenFoodExists_shouldSaveAndReturnDto() {
        // Given
        IntakeRequestDto requestDto = new IntakeRequestDto("food123");
        FoodDto foodDto = FoodDto.builder()
                .id("food123")
                .productName("Apple")
                .brands("Fresh Farms")
                .availableUnits(List.of(UnitType.GRAMS))
                .build();

        Intake intake = new Intake();
        Intake savedIntake = new Intake();
        savedIntake.setId(1L);
        IntakeResponseDto responseDto = IntakeResponseDto.builder()
                .id(1L)
                .foodName("Apple")
                .build();
        UUID requestId = UUID.randomUUID();

        when(foodClientService.getFoodById("food123")).thenReturn(foodDto);
        when(intakeMapper.toModel(requestDto)).thenReturn(intake);
        doAnswer(inv -> {
            inv.<Intake>getArgument(0).setFoodName((foodDto.getProductName()));
            inv.<Intake>getArgument(0).setBrand(foodDto.getBrands());
            return null;
        }).when(intakeMapper).updateIntakeFromFoodDto(intake, foodDto);
        when(intakeRepository.saveAndFlush(intake)).thenReturn(savedIntake);
        when(intakeMapper.toDto(savedIntake)).thenReturn(responseDto);
        when(nutrientStrategyFactory.getStrategy(UnitType.GRAMS))
                .thenReturn(new GramsCalculationStrategy());
        when(nutrimentsMapper.fromFoodNutriments(any())).thenReturn(new Nutriments());

        // When
        final IntakeResponseDto result = intakeService.save(requestDto, userId, requestId);

        // Then
        verify(intakeMapper).updateIntakeFromFoodDto(intake, foodDto);
        verify(intakeRepository).saveAndFlush(intake);

        assertEquals(responseDto, result);
        assertEquals(userId, intake.getUserId());
        assertEquals(requestDto.getFoodId(), intake.getFoodId());
        assertEquals("Apple", intake.getFoodName());
        assertEquals("Fresh Farms", intake.getBrand());
        assertEquals(requestId, intake.getRequestId());
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
                () -> intakeService.save(requestDto, userId, UUID.randomUUID()));

        assertEquals("Food not found", ex.getMessage());
        verify(intakeRepository, never()).saveAndFlush(any());
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
                () -> intakeService.save(requestDto, userId, UUID.randomUUID()));

        verify(intakeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should undo intake group")
    void undoIntakeGroup_shouldSoftDelete() {
        // Given
        UUID groupId = UUID.randomUUID();
        Long userId = 1L;

        // When
        intakeService.undoIntakeGroup(groupId, userId);

        // Then
        verify(intakeRepository, times(1))
                .softDeleteByMealGroupIdAndUserId(
                        any(), any(), any(Instant.class));
        verify(applicationRepository).deleteByUserIdAndMealGroupId(userId, groupId);
    }

    @Test
    @DisplayName("When intake is deleted, should keep tombstone")
    void deleteById_shouldSoftDelete() {
        Long intakeId = 10L;
        Intake intake = Intake.builder()
                .id(intakeId)
                .userId(userId)
                .date(LocalDate.of(2026, 6, 19))
                .build();
        when(intakeRepository.findByIdAndUserId(intakeId, userId))
                .thenReturn(Optional.of(intake));

        intakeService.deleteById(intakeId, userId);

        assertTrue(intake.isDeleted());
        verify(intakeRepository).saveAndFlush(intake);
    }

    @Test
    @DisplayName("When update uses stale version, should throw conflict")
    void update_whenVersionIsStale_shouldThrowConflict() {
        Long intakeId = 10L;
        Intake intake = Intake.builder()
                .id(intakeId)
                .userId(userId)
                .date(LocalDate.of(2026, 6, 19))
                .amount(100)
                .unitType(UnitType.GRAMS)
                .nutriments(new Nutriments())
                .version(3L)
                .build();
        UpdateIntakeRequestDto request = UpdateIntakeRequestDto.builder()
                .amount(120)
                .version(2L)
                .build();
        when(intakeRepository.findByIdAndUserId(intakeId, userId))
                .thenReturn(Optional.of(intake));

        assertThrows(ResponseStatusException.class,
                () -> intakeService.update(intakeId, request, userId));
        verify(intakeRepository, never()).save(any());
    }

    @Test
    @DisplayName("When sync push is older, should keep server row")
    void pushSync_whenChangeIsOlder_shouldKeepServerRow() {
        Instant serverUpdatedAt = Instant.parse("2026-06-19T08:00:00Z");
        Intake existing = Intake.builder()
                .id(10L)
                .userId(userId)
                .foodId("food123")
                .amount(100)
                .unitType(UnitType.GRAMS)
                .date(LocalDate.of(2026, 6, 19))
                .nutriments(new Nutriments())
                .updatedAt(serverUpdatedAt)
                .build();
        IntakeSyncItemDto serverDto = IntakeSyncItemDto.builder()
                .id(10L)
                .amount(100)
                .updatedAt(serverUpdatedAt)
                .build();
        IntakeSyncItemDto staleChange = IntakeSyncItemDto.builder()
                .id(10L)
                .foodId("food123")
                .amount(200)
                .unitType(UnitType.GRAMS)
                .date(LocalDate.of(2026, 6, 19))
                .nutriments(NutrimentsDto.builder()
                        .calories(BigDecimal.ONE)
                        .build())
                .updatedAt(serverUpdatedAt.minusSeconds(60))
                .build();

        when(intakeRepository.findAnyByIdAndUserId(10L, userId))
                .thenReturn(Optional.of(existing));
        when(intakeMapper.toSyncDto(existing)).thenReturn(serverDto);

        IntakeSyncResponseDto response = intakeService.pushSync(userId,
                IntakeSyncPushRequestDto.builder()
                        .changes(List.of(staleChange))
                        .build());

        assertEquals(100, response.getData().getFirst().getAmount());
        verify(intakeRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("When sync delete is older, should still tombstone server row")
    void pushSync_whenDeleteIsOlder_shouldTombstoneServerRow() {
        Instant serverUpdatedAt = Instant.parse("2026-06-19T08:00:00Z");
        Intake existing = Intake.builder()
                .id(10L)
                .userId(userId)
                .foodId("food123")
                .amount(100)
                .unitType(UnitType.GRAMS)
                .date(LocalDate.of(2026, 6, 19))
                .nutriments(new Nutriments())
                .updatedAt(serverUpdatedAt)
                .build();
        IntakeSyncItemDto tombstoneDto = IntakeSyncItemDto.builder()
                .id(10L)
                .updatedAt(serverUpdatedAt)
                .deleted(true)
                .build();
        IntakeSyncItemDto staleDelete = IntakeSyncItemDto.builder()
                .id(10L)
                .updatedAt(serverUpdatedAt.minusSeconds(60))
                .deleted(true)
                .build();

        when(intakeRepository.findAnyByIdAndUserId(10L, userId))
                .thenReturn(Optional.of(existing));
        when(intakeRepository.saveAndFlush(existing)).thenReturn(existing);
        when(intakeMapper.toSyncDto(existing)).thenReturn(tombstoneDto);

        IntakeSyncResponseDto response = intakeService.pushSync(userId,
                IntakeSyncPushRequestDto.builder()
                        .changes(List.of(staleDelete))
                        .build());

        assertTrue(existing.isDeleted());
        assertTrue(existing.getUpdatedAt().isAfter(serverUpdatedAt));
        assertTrue(response.getData().getFirst().isDeleted());
        verify(intakeRepository).saveAndFlush(existing);
    }
}
