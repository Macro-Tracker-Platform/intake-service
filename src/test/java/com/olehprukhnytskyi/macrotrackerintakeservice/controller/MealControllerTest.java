package com.olehprukhnytskyi.macrotrackerintakeservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.config.AbstractIntegrationTest;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.MealTemplateRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.FoodClientService;
import com.olehprukhnytskyi.util.UnitType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Transactional
class MealControllerTest extends AbstractIntegrationTest {
    protected static MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private IntakeRepository intakeRepository;
    @Autowired
    private MealTemplateRepository mealTemplateRepository;

    @MockitoBean
    private FoodClientService foodClientService;

    @BeforeAll
    static void beforeAll(
            @Autowired WebApplicationContext applicationContext
    ) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .build();
    }

    @Test
    @DisplayName("When request is valid, should create meal template with snapshot data")
    void createTemplate_whenValidRequest_shouldSaveTemplateAndItems() throws Exception {
        // Given
        String foodId1 = "food-oats";
        String foodId2 = "food-milk";

        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Morning Porridge");
        request.setItems(List.of(
                MealTemplateRequestDto.TemplateItemDto.builder()
                        .foodId(foodId1)
                        .unitType(UnitType.GRAMS)
                        .amount(50)
                        .build(),
                MealTemplateRequestDto.TemplateItemDto.builder()
                        .foodId(foodId2)
                        .unitType(UnitType.GRAMS)
                        .amount(200)
                        .build()
        ));

        FoodDto oats = createMockFood(foodId1, "Oats", 350);
        FoodDto milk = createMockFood(foodId2, "Milk", 50);

        given(foodClientService.getFoodsByIds(anyList()))
                .willReturn(List.of(oats, milk));

        // When
        String responseJson = mockMvc.perform(
                        post("/api/meal-templates")
                                .header("X-User-Id", 101L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Then
        Long templateId = Long.parseLong(responseJson);
        assertThat(mealTemplateRepository.findById(templateId)).isPresent();

        MealTemplate savedTemplate = mealTemplateRepository.findById(templateId).get();
        assertThat(savedTemplate.getItems()).hasSize(2);

        MealTemplateItem item1 = savedTemplate.getItems().stream()
                .filter(i -> i.getFoodId().equals(foodId1)).findFirst().get();
        assertThat(item1.getFoodName()).isEqualTo("Oats");
        assertThat(item1.getNutriments().getCaloriesPer100()).isEqualByComparingTo("350");
    }

    @Test
    @DisplayName("When service fails, should rollback transaction")
    void createTemplate_whenFoodServiceFails_shouldRollback() throws Exception {
        // Given
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Failed Template");
        request.setItems(List.of(MealTemplateRequestDto.TemplateItemDto.builder()
                .foodId("f1")
                .amount(100)
                .unitType(UnitType.GRAMS)
                .build()));

        given(foodClientService.getFoodsByIds(anyList()))
                .willThrow(new ExternalServiceException(
                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE, "Service Down"));

        // When
        mockMvc.perform(
                        post("/api/meal-templates")
                                .header("X-User-Id", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isServiceUnavailable());

        // Then
        assertThat(mealTemplateRepository.count()).isZero();
    }

    @Test
    @DisplayName("When amounts are negative, should return 400 Bad Request")
    void createTemplate_whenAmountIsNegative_shouldReturn400() throws Exception {
        // Given
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Bad Request Template");

        var badItem = new MealTemplateRequestDto.TemplateItemDto();
        badItem.setFoodId("f1");
        badItem.setAmount(-50);

        request.setItems(List.of(badItem));

        // When
        mockMvc.perform(
                        post("/api/meal-templates")
                                .header("X-User-Id", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("When items list is empty, should return 400")
    void createTemplate_whenItemsEmpty_shouldReturn400() throws Exception {
        // Given
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Empty Template");
        request.setItems(List.of());

        // When
        mockMvc.perform(
                        post("/api/meal-templates")
                                .header("X-User-Id", 1L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("When trying to apply another user's template, should return 404")
    void applyTemplate_whenTemplateBelongsToAnotherUser_shouldFail() throws Exception {
        // Given
        Long ownerId = 100L;
        Long attackerId = 666L;

        MealTemplate template = createAndSaveTemplateInDb(ownerId, "Private Diet");

        // When
        mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply", template.getId())
                                .header("X-User-Id", attackerId)
                                .param("date", LocalDate.now().toString())
                )
                .andExpect(status().isNotFound());

        // Then
        assertThat(intakeRepository.count()).isZero();
    }

    @Test
    @DisplayName("When valid id, should apply template and create intakes with groupId")
    void applyTemplate_whenValidId_shouldCreateIntakes() throws Exception {
        // Given
        Long userId = 102L;
        LocalDate date = LocalDate.now();

        MealTemplate template = createAndSaveTemplateInDb(userId, "Lunch Box");
        Long templateId = template.getId();

        // When
        mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply", templateId)
                                .header("X-User-Id", userId)
                                .param("date", date.toString())
                                .param("period", "LUNCH")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mealGroupId").exists())
                .andExpect(jsonPath("$[0].date").value(date.toString()));

        // Then
        List<Intake> intakes = intakeRepository.findAll();
        assertThat(intakes).hasSize(2);
        assertThat(intakes.get(0).getMealGroupId()).isNotNull();
        assertThat(intakes.get(0).getMealGroupId()).isEqualTo(intakes.get(1).getMealGroupId());
        assertThat(intakes.get(0).getNutriments().getCaloriesPer100())
                .isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("When applying non-existent template, should return 404")
    void applyTemplate_whenTemplateNotFound_shouldReturn404() throws Exception {
        // Given
        Long userId = 1L;
        Long wrongId = 9999L;

        // When & Then
        mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply", wrongId)
                                .header("X-User-Id", userId)
                                .param("date", LocalDate.now().toString())
                )
                .andExpect(status().isNotFound());
    }

    private FoodDto createMockFood(String id, String name, double kcal) {
        NutrimentsDto nutriments = NutrimentsDto.builder()
                .calories(BigDecimal.valueOf(kcal))
                .carbohydrates(BigDecimal.TEN)
                .fat(BigDecimal.TEN)
                .protein(BigDecimal.TEN)
                .caloriesPer100(BigDecimal.valueOf(kcal))
                .carbohydratesPer100(BigDecimal.TEN)
                .fatPer100(BigDecimal.TEN)
                .proteinPer100(BigDecimal.TEN)
                .build();
        return FoodDto.builder()
                .id(id)
                .productName(name)
                .nutriments(nutriments)
                .availableUnits(List.of(UnitType.GRAMS))
                .build();
    }

    private MealTemplate createAndSaveTemplateInDb(Long userId, String name) {
        MealTemplate template = MealTemplate.builder()
                .userId(userId)
                .name(name)
                .build();

        MealTemplateItem item1 = MealTemplateItem.builder()
                .template(template)
                .foodId("f1")
                .foodName("Food 1")
                .amount(110)
                .nutriments(new Nutriments(
                        BigDecimal.valueOf(100), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .build();

        MealTemplateItem item2 = MealTemplateItem.builder()
                .template(template)
                .foodId("f2")
                .foodName("Food 2")
                .amount(50)
                .nutriments(new Nutriments(
                        BigDecimal.valueOf(200), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .build();

        template.setItems(List.of(item1, item2));
        return mealTemplateRepository.save(template);
    }
}
