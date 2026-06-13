package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.config.AbstractIntegrationTest;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateIntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.util.CacheConstants;
import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class IntakeServiceCacheTest extends AbstractIntegrationTest {
    @MockitoBean
    private FoodClientService foodClientService;
    @MockitoBean
    private IntakeMapper intakeMapper;
    @MockitoSpyBean
    private IntakeRepository intakeRepository;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private IntakeService intakeService;
    @Autowired
    private ObjectMapper objectMapper;

    private Long intakeId = 1L;
    private final Long userId = 1L;
    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        Objects.requireNonNull(redisTemplate.getConnectionFactory())
                .getConnection().serverCommands().flushAll();

        FoodDto foodDto = FoodDto.builder()
                .nutriments(NutrimentsDto.builder()
                        .calories(BigDecimal.valueOf(100))
                        .carbohydrates(BigDecimal.valueOf(20))
                        .fat(BigDecimal.valueOf(10))
                        .protein(BigDecimal.valueOf(5))
                        .build())
                .availableUnits(List.of(UnitType.GRAMS))
                .build();
        when(foodClientService.getFoodById(anyString())).thenReturn(foodDto);
        when(foodClientService.getFoodsByIds(anyList())).thenReturn(List.of(foodDto));

        Nutriments nutriments = Nutriments.builder()
                .calories(BigDecimal.valueOf(200))
                .carbohydrates(BigDecimal.valueOf(40))
                .fat(BigDecimal.valueOf(20))
                .protein(BigDecimal.valueOf(10))
                .caloriesPer100(BigDecimal.valueOf(100))
                .carbohydratesPer100(BigDecimal.valueOf(20))
                .fatPer100(BigDecimal.valueOf(10))
                .proteinPer100(BigDecimal.valueOf(5))
                .build();

        Intake intake = Intake.builder()
                .date(LocalDate.now())
                .userId(1L)
                .foodName("Rice")
                .foodId("00000000")
                .nutriments(nutriments)
                .amount(200)
                .intakePeriod(IntakePeriod.SNACK)
                .build();
        intakeId = intakeRepository.save(intake).getId();
    }

    @Test
    @DisplayName("Should clear cache")
    void save_shouldClearCache() {
        // Given
        Long userId = 1L;
        LocalDate today = LocalDate.now();

        intakeService.findByDate(today, userId);

        Set<String> keysBefore = redisTemplate.keys(CacheConstants.USER_INTAKES + "*");
        assertThat(keysBefore).isNotEmpty();

        IntakeRequestDto request = IntakeRequestDto.builder()
                .amount(100)
                .date(today)
                .intakePeriod(IntakePeriod.SNACK)
                .foodId("12345678").build();

        Intake intake = Intake.builder()
                .userId(1L)
                .foodName("apple")
                .amount(100)
                .date(today)
                .foodId("12345678")
                .build();

        when(intakeMapper.toModel(any())).thenReturn(intake);

        // When
        intakeService.save(request, userId, UUID.randomUUID());

        // Then
        Set<String> keysAfter = redisTemplate.keys(CacheConstants.USER_INTAKES + "*");
        assertThat(keysAfter).isEmpty();
    }

    @Test
    @DisplayName("Should use cache on second call")
    void findByDate_shouldUseCacheOnSecondCall() {
        // Given
        Intake mockIntake = new Intake();
        List<Intake> intakeEntities = List.of(mockIntake);

        IntakeResponseDto mockDto = new IntakeResponseDto();
        mockDto.setFoodName("Apple");

        doReturn(intakeEntities).when(intakeRepository).findByUserIdAndDate(anyLong(), any());
        when(intakeMapper.toDto(any(Intake.class))).thenReturn(mockDto);

        // When
        List<IntakeResponseDto> intakes1 = intakeService.findByDate(today, userId);
        verify(intakeRepository, times(1)).findByUserIdAndDate(anyLong(), any());

        List<IntakeResponseDto> intakes2 = intakeService.findByDate(today, userId);
        verify(intakeRepository, times(1)).findByUserIdAndDate(anyLong(), any());

        // Then
        assertThat(intakes1).hasSize(intakes2.size());
        assertThat(intakes1.getFirst().getFoodName()).isEqualTo(intakes2.getFirst().getFoodName());
    }

    @Test
    @DisplayName("Should clear cache")
    void update_shouldClearCache() {
        intakeService.findByDate(today, userId);
        assertThat(redisTemplate.keys(CacheConstants.USER_INTAKES + "*")).isNotEmpty();

        UpdateIntakeRequestDto intakeRequest = UpdateIntakeRequestDto.builder()
                .amount(200)
                .build();

        // When
        intakeService.update(intakeId, intakeRequest, userId);

        // Then
        assertThat(redisTemplate.keys(CacheConstants.USER_INTAKES + "*")).isEmpty();
    }

    @Test
    @DisplayName("Should clear cache")
    void deleteById_shouldClearCache() {
        intakeService.findByDate(today, userId);
        assertThat(redisTemplate.keys(CacheConstants.USER_INTAKES + "*")).isNotEmpty();

        // When
        intakeService.deleteById(intakeId, userId);

        // Then
        assertThat(redisTemplate.keys(CacheConstants.USER_INTAKES + "*")).isEmpty();
    }
}
