package com.olehprukhnytskyi.macrotrackerintakeservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.config.AbstractIntegrationTest;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateIntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.FoodClientService;
import com.olehprukhnytskyi.util.CustomHeaders;
import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Sql(scripts = "classpath:database/add-intake.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "classpath:database/remove-intake.sql",
        executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class IntakeControllerTest extends AbstractIntegrationTest {
    protected static MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private NutrimentsMapper nutrimentsMapper;

    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;
    @MockitoBean
    private ValueOperations<String, String> valueOperations;
    @MockitoBean
    private FoodClientService foodClientService;

    @Autowired
    private IntakeRepository intakeRepository;

    @BeforeAll
    static void beforeAll(
            @Autowired WebApplicationContext applicationContext
    ) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .build();
    }

    @Test
    @DisplayName("When no intakes on given date, should return empty list")
    void findByDate_whenNoIntakes_shouldReturnEmptyList() throws Exception {
        // Given
        String date = "2030-01-01";

        // When
        MvcResult mvcResult = mockMvc.perform(
                        get("/api/intake")
                                .header(CustomHeaders.X_USER_ID, 1L)
                                .param("date", date)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String jsonResponse = mvcResult.getResponse().getContentAsString();
        List<IntakeResponseDto> response = objectMapper.readValue(
                jsonResponse,
                objectMapper.getTypeFactory()
                        .constructParametricType(List.class, IntakeResponseDto.class)
        );

        assertThat(response).isEmpty();
    }

    @Test
    @DisplayName("When valid date, should return intakes for that day")
    void findByDate_whenValidDate_shouldReturnIntakesForThatDay() throws Exception {
        // Given
        String date = "2025-09-06";

        // When
        MvcResult mvcResult = mockMvc.perform(
                        get("/api/intake")
                                .header(CustomHeaders.X_USER_ID, 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .param("date", date)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String jsonResponse = mvcResult.getResponse().getContentAsString();
        List<IntakeResponseDto> response = objectMapper.readValue(
                jsonResponse,
                objectMapper.getTypeFactory()
                        .constructParametricType(List.class, IntakeResponseDto.class)
        );

        assertThat(response)
                .extracting(IntakeResponseDto::getFoodName)
                .contains("Potato");
        assertThat(response)
                .extracting(IntakeResponseDto::getDate)
                .contains(LocalDate.parse(date));
    }

    @Test
    @DisplayName("When invalid date, should return BAD_REQUEST")
    void findByDate_whenInvalidDate_shouldReturnBadRequest() throws Exception {
        // Given
        String invalidDate = "06-09-2025";

        // When
        mockMvc.perform(
                        get("/api/intake")
                                .header(CustomHeaders.X_USER_ID, 1L)
                                .param("date", invalidDate)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid date format"));
    }

    @Test
    @DisplayName("When no date, should return all intakes for user")
    void findByDate_whenNoDate_shouldReturnAllIntakesForUser() throws Exception {
        // When
        MvcResult mvcResult = mockMvc.perform(
                        get("/api/intake")
                                .header(CustomHeaders.X_USER_ID, 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String jsonResponse = mvcResult.getResponse().getContentAsString();
        List<IntakeResponseDto> response = objectMapper.readValue(
                jsonResponse,
                objectMapper.getTypeFactory()
                        .constructParametricType(List.class, IntakeResponseDto.class)
        );

        assertThat(response).isNotEmpty();
    }

    @Test
    @DisplayName("When request is not duplicated, should return created intake")
    void addIntake_whenNotDuplicated_shouldReturnCreatedWithBody() throws Exception {
        // Given
        IntakeResponseDto responseDto = IntakeResponseDto.builder()
                .id(1L)
                .amount(200)
                .nutriments(NutrimentsDto.builder()
                        .calories(BigDecimal.valueOf(10))
                        .carbohydrates(BigDecimal.valueOf(12))
                        .fat(BigDecimal.valueOf(14))
                        .protein(BigDecimal.valueOf(16))
                        .build())
                .date(LocalDate.now())
                .foodId("food-1")
                .foodName("Oatmeal")
                .unitType(UnitType.GRAMS)
                .availableUnits(List.of(UnitType.GRAMS))
                .intakePeriod(IntakePeriod.SNACK)
                .build();

        FoodDto foodDto = FoodDto.builder()
                .productName("Oatmeal")
                .userId(1L)
                .nutriments(NutrimentsDto.builder()
                        .calories(BigDecimal.valueOf(5))
                        .carbohydrates(BigDecimal.valueOf(6))
                        .fat(BigDecimal.valueOf(7))
                        .protein(BigDecimal.valueOf(8))
                        .build())
                .availableUnits(List.of(UnitType.GRAMS))
                .build();

        String jsonRequest = objectMapper.writeValueAsString(IntakeRequestDto.builder()
                .foodId("food-1")
                .amount(200)
                .date(LocalDate.now())
                .unitType(UnitType.GRAMS)
                .intakePeriod(IntakePeriod.SNACK)
                .build());

        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(foodClientService.getFoodById(anyString())).thenReturn(foodDto);

        // When
        MvcResult mvcResult = mockMvc.perform(post("/api/intake")
                        .header(CustomHeaders.X_USER_ID, 1L)
                        .header(CustomHeaders.X_REQUEST_ID, "req-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isCreated())
                .andReturn();

        // Then
        IntakeResponseDto intakeResponseDto = objectMapper.readValue(
                mvcResult.getResponse().getContentAsString(), IntakeResponseDto.class);
        assertThat(intakeResponseDto)
                .usingRecursiveComparison()
                .ignoringFields("id")
                .ignoringCollectionOrder()
                .isEqualTo(responseDto);
        verify(foodClientService).getFoodById("food-1");
    }

    @Test
    @DisplayName("When request is duplicated, should return ok")
    void addIntake_whenDuplicated_shouldReturnOkBody() throws Exception {
        // Given
        String requestJson = objectMapper.writeValueAsString(IntakeRequestDto.builder()
                .foodId("food-2")
                .amount(200)
                .date(LocalDate.now())
                .intakePeriod(IntakePeriod.BREAKFAST)
                .build());

        FoodDto foodDto = FoodDto.builder()
                .productName("Oatmeal")
                .userId(1L)
                .nutriments(new NutrimentsDto())
                .availableUnits(List.of(UnitType.GRAMS))
                .build();

        when(redisTemplate.hasKey(anyString())).thenReturn(true);
        when(foodClientService.getFoodById(anyString())).thenReturn(foodDto);

        // When
        mockMvc.perform(
                post("/api/intake")
                        .header(CustomHeaders.X_USER_ID, 1L)
                        .header(CustomHeaders.X_REQUEST_ID, "req-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foodName").value("Oatmeal"))
                .andExpect(jsonPath("$.amount").value(200));
    }

    @Test
    @DisplayName("When creating intake from Global Food (userId=null), "
                 + "should NOT overwrite userId with null")
    void addIntake_whenFoodIsGlobal_shouldPersistUserIdCorrectly() throws Exception {
        // Given
        Long headerUserId = 101L;
        String globalFoodId = "global-apple-id";

        IntakeRequestDto requestDto = IntakeRequestDto.builder()
                .foodId(globalFoodId)
                .amount(150)
                .date(LocalDate.now())
                .intakePeriod(IntakePeriod.SNACK)
                .build();

        FoodDto globalFoodDto = FoodDto.builder()
                .id(globalFoodId)
                .productName("Global Apple")
                .userId(null)
                .availableUnits(List.of(UnitType.GRAMS))
                .nutriments(new NutrimentsDto())
                .build();

        when(foodClientService.getFoodById(globalFoodId)).thenReturn(globalFoodDto);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // When
        mockMvc.perform(
                        post("/api/intake")
                                .header(CustomHeaders.X_USER_ID, headerUserId)
                                .header(CustomHeaders.X_REQUEST_ID, "req-integrity-check")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(requestDto))
                )
                .andExpect(status().isCreated());

        // Then
        List<Intake> savedIntakes = intakeRepository.findAll();
        Intake targetIntake = savedIntakes.stream()
                .filter(i -> globalFoodId.equals(i.getFoodId()) && i.getAmount() == 150)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Intake was not saved to DB"));
        assertThat(targetIntake.getUserId())
                .as("User ID must correspond to X-USER-ID header and not be overwritten by FoodDto")
                .isNotNull()
                .isEqualTo(headerUserId);
    }

    @Test
    @DisplayName("When request is valid, should update intake")
    void updateIntake_whenValidRequest_shouldUpdateIntake() throws Exception {
        // Given
        UpdateIntakeRequestDto requestDto = UpdateIntakeRequestDto.builder()
                .amount(20)
                .intakePeriod(IntakePeriod.BREAKFAST)
                .build();
        String requestJson = objectMapper.writeValueAsString(requestDto);

        Intake intake = getRandomIntakeFromDb();
        intake.getNutriments().setCalories(BigDecimal.valueOf(100));
        intake.getNutriments().setCarbohydrates(BigDecimal.valueOf(120));
        intake.getNutriments().setFat(BigDecimal.valueOf(140));
        intake.getNutriments().setProtein(BigDecimal.valueOf(160));

        IntakeResponseDto responseDto = IntakeResponseDto.builder()
                .id(intake.getId())
                .foodId("1")
                .foodName("Potato")
                .date(LocalDate.parse("2025-09-06"))
                .unitType(intake.getUnitType())
                .amount(requestDto.getAmount())
                .availableUnits(List.of(UnitType.GRAMS, UnitType.PIECES))
                .nutriments(nutrimentsMapper.toDto(intake.getNutriments()))
                .intakePeriod(IntakePeriod.BREAKFAST)
                .build();
        String expected = objectMapper.writeValueAsString(responseDto);

        // When
        mockMvc.perform(
                        patch("/api/intake/{id}", intake.getId())
                                .header(CustomHeaders.X_USER_ID, 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(expected));
    }

    @Test
    @DisplayName("When request is valid, should delete intake")
    void deleteById_whenValidRequest_shouldDeleteIntake() throws Exception {
        // Given
        Long userId = 1L;
        Long intakeId = getRandomIntakeFromDb().getId();

        assertThat(intakeRepository.findById(intakeId)).isPresent();

        // When
        mockMvc.perform(
                delete("/api/intake/{id}", intakeId)
                        .header(CustomHeaders.X_USER_ID, userId)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // Then
        assertThat(intakeRepository.findById(intakeId)).isEmpty();
    }

    @Test
    @DisplayName("When valid groupId, should delete all intakes")
    void undoIntakeGroup_whenValidGroupId_shouldDeleteBatch() throws Exception {
        // Given
        Long userId = 103L;
        String groupIdToDelete = UUID.randomUUID().toString();
        String otherGroupId = UUID.randomUUID().toString();

        saveIntakeWithGroup(userId, groupIdToDelete);
        saveIntakeWithGroup(userId, groupIdToDelete);

        saveIntakeWithGroup(userId, otherGroupId);

        assertThat(intakeRepository.findByUserId(userId).size()).isEqualTo(3);

        // When
        mockMvc.perform(
                        delete("/api/intake/group/{mealGroupId}", groupIdToDelete)
                                .header("X-User-Id", userId)
                )
                .andExpect(status().isNoContent());

        // Then
        List<Intake> remainingIntakes = intakeRepository.findByUserId(userId);
        assertThat(remainingIntakes).hasSize(1);
        assertThat(remainingIntakes.get(0).getMealGroupId()).isEqualTo(otherGroupId);
    }

    @Test
    @DisplayName("When group belongs to another user, should NOT delete intake")
    void undoIntakeGroup_whenGroupBelongsToAnotherUser_shouldNotDelete() throws Exception {
        // Given
        Long victimId = 100L;
        String victimGroupId = UUID.randomUUID().toString();

        Intake intake = new Intake();
        intake.setUserId(victimId);
        intake.setFoodId("apple");
        intake.setMealGroupId(victimGroupId);
        intake.setDate(LocalDate.now());
        intake.setAmount(100);
        intakeRepository.save(intake);

        // When
        mockMvc.perform(
                        delete("/api/intake/group/{mealGroupId}", victimGroupId)
                                .header("X-User-Id", 2L)
                )
                .andExpect(status().isNoContent());

        // Then
        assertThat(intakeRepository.findByUserId(victimId).size()).isEqualTo(1);
    }

    private Intake getRandomIntakeFromDb() {
        return intakeRepository.findAll().stream()
                .findFirst()
                .orElseThrow();
    }

    private void saveIntakeWithGroup(Long userId, String groupId) {
        Intake intake = new Intake();
        intake.setUserId(userId);
        intake.setFoodId("test-food");
        intake.setDate(LocalDate.now());
        intake.setMealGroupId(groupId);
        intake.setAmount(100);
        intakeRepository.save(intake);
    }
}
