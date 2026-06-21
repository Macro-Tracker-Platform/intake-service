package com.olehprukhnytskyi.macrotrackerintakeservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.config.AbstractIntegrationTest;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.MealTemplateRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.NutrimentsDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateMealTemplateDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Nutriments;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateApplicationRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.FoodClientService;
import com.olehprukhnytskyi.util.CustomHeaders;
import com.olehprukhnytskyi.util.UnitType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    @Autowired
    private MealTemplateApplicationRepository applicationRepository;

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
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Then
        Long templateId = Long.parseLong(responseJson);
        assertThat(mealTemplateRepository.findById(templateId)).isPresent();

        MealTemplate savedTemplate = mealTemplateRepository.findById(templateId).get();
        assertThat(savedTemplate.isRecipe()).isFalse();
        assertThat(savedTemplate.getTotalYieldAmount()).isNull();
        assertThat(savedTemplate.getYieldUnitType()).isNull();
        assertThat(savedTemplate.getItems()).hasSize(2);

        MealTemplateItem item1 = savedTemplate.getItems().stream()
                .filter(i -> i.getFoodId().equals(foodId1)).findFirst().get();
        assertThat(item1.getFoodName()).isEqualTo("Oats");
        assertThat(item1.getNutriments().getCaloriesPer100()).isEqualByComparingTo("350");
    }

    @Test
    @DisplayName("When recipe request is valid, should create recipe template")
    void createTemplate_whenRecipeRequest_shouldSaveRecipeFields() throws Exception {
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Cheese Pie");
        request.setRecipe(true);
        request.setTotalYieldAmount(12);
        request.setYieldUnitType(UnitType.PIECES);
        request.setItems(List.of(MealTemplateRequestDto.TemplateItemDto.builder()
                .foodId("food-cheese")
                .unitType(UnitType.GRAMS)
                .amount(1000)
                .build()));

        given(foodClientService.getFoodsByIds(anyList()))
                .willReturn(List.of(createMockFood("food-cheese", "Cheese", 250)));

        String responseJson = mockMvc.perform(
                        post("/api/meal-templates")
                                .header(CustomHeaders.X_USER_ID, 101L)
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        MealTemplate savedTemplate = mealTemplateRepository.findById(Long.parseLong(responseJson))
                .get();
        assertThat(savedTemplate.isRecipe()).isTrue();
        assertThat(savedTemplate.getTotalYieldAmount()).isEqualTo(12);
        assertThat(savedTemplate.getYieldUnitType()).isEqualTo(UnitType.PIECES);
    }

    @Test
    @DisplayName("When recipe has no yield, should return 400")
    void createTemplate_whenRecipeWithoutYield_shouldReturn400() throws Exception {
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Broken Recipe");
        request.setRecipe(true);
        request.setItems(List.of(MealTemplateRequestDto.TemplateItemDto.builder()
                .foodId("food-cheese")
                .unitType(UnitType.GRAMS)
                .amount(1000)
                .build()));

        mockMvc.perform(
                        post("/api/meal-templates")
                                .header(CustomHeaders.X_USER_ID, 101L)
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("When update request is valid, should update template fields and item order")
    void updateTemplate_whenValidRequest_shouldUpdateTemplateAndItems() throws Exception {
        Long userId = 107L;
        MealTemplate template = createAndSaveTemplateInDb(userId, "Lunch Box");
        UpdateMealTemplateDto request = UpdateMealTemplateDto.builder()
                .name("Updated Lunch Box")
                .items(List.of(
                        UpdateMealTemplateDto.TemplateItemDto.builder()
                                .foodId("f2")
                                .amount(80)
                                .unitType(UnitType.GRAMS)
                                .build(),
                        UpdateMealTemplateDto.TemplateItemDto.builder()
                                .foodId("f3")
                                .amount(25)
                                .unitType(UnitType.GRAMS)
                                .build()
                ))
                .build();

        given(foodClientService.getFoodsByIds(anyList()))
                .willReturn(List.of(createMockFood("f3", "Food 3", 300)));

        mockMvc.perform(
                        put("/api/meal-templates/{templateId}", template.getId())
                                .header(CustomHeaders.X_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk());

        MealTemplate updated = mealTemplateRepository.findById(template.getId()).get();
        assertThat(updated.getName()).isEqualTo("Updated Lunch Box");
        assertThat(updated.isRecipe()).isFalse();
        assertThat(updated.getItems()).hasSize(2);
        assertThat(updated.getItems())
                .extracting(MealTemplateItem::getFoodId)
                .containsExactly("f2", "f3");
        assertThat(updated.getItems().get(0).getAmount()).isEqualTo(80);
        assertThat(updated.getItems().get(1).getFoodName()).isEqualTo("Food 3");
    }

    @Test
    @DisplayName("When recipe update request is valid, should update yield and ingredients")
    void updateTemplate_whenRecipeRequest_shouldUpdateRecipeFieldsAndItems() throws Exception {
        Long userId = 108L;
        MealTemplateRequestDto createRequest = MealTemplateRequestDto.builder()
                .name("Cheese Pie")
                .recipe(true)
                .totalYieldAmount(800)
                .yieldUnitType(UnitType.GRAMS)
                .items(List.of(MealTemplateRequestDto.TemplateItemDto.builder()
                        .foodId("food-cheese")
                        .amount(1000)
                        .unitType(UnitType.GRAMS)
                        .build()))
                .build();

        given(foodClientService.getFoodsByIds(anyList()))
                .willReturn(List.of(createMockFood("food-cheese", "Cheese", 250)))
                .willReturn(List.of(createMockFood("food-flour", "Flour", 350)));

        String responseJson = mockMvc.perform(
                        post("/api/meal-templates")
                                .header(CustomHeaders.X_USER_ID, userId)
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createRequest))
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long templateId = Long.parseLong(responseJson);

        UpdateMealTemplateDto updateRequest = UpdateMealTemplateDto.builder()
                .name("Cheese Buns")
                .recipe(true)
                .totalYieldAmount(12)
                .yieldUnitType(UnitType.PIECES)
                .items(List.of(
                        UpdateMealTemplateDto.TemplateItemDto.builder()
                                .foodId("food-cheese")
                                .amount(1000)
                                .unitType(UnitType.GRAMS)
                                .build(),
                        UpdateMealTemplateDto.TemplateItemDto.builder()
                                .foodId("food-flour")
                                .amount(200)
                                .unitType(UnitType.GRAMS)
                                .build()
                ))
                .build();

        mockMvc.perform(
                        put("/api/meal-templates/{templateId}", templateId)
                                .header(CustomHeaders.X_USER_ID, userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(updateRequest))
                )
                .andExpect(status().isOk());

        MealTemplate updated = mealTemplateRepository.findById(templateId).get();
        assertThat(updated.getName()).isEqualTo("Cheese Buns");
        assertThat(updated.isRecipe()).isTrue();
        assertThat(updated.getTotalYieldAmount()).isEqualTo(12);
        assertThat(updated.getYieldUnitType()).isEqualTo(UnitType.PIECES);
        assertThat(updated.getItems())
                .extracting(MealTemplateItem::getFoodId)
                .containsExactly("food-cheese", "food-flour");
    }

    @Test
    @DisplayName("When request id is repeated, should return previously created template")
    void createTemplate_whenRequestIdRepeated_shouldBeIdempotent() throws Exception {
        final UUID requestId = UUID.randomUUID();
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Offline Breakfast");
        request.setItems(List.of(MealTemplateRequestDto.TemplateItemDto.builder()
                .foodId("food-oats")
                .unitType(UnitType.GRAMS)
                .amount(50)
                .build()));

        given(foodClientService.getFoodsByIds(anyList()))
                .willReturn(List.of(createMockFood("food-oats", "Oats", 350)));

        String requestJson = objectMapper.writeValueAsString(request);
        String firstResponse = mockMvc.perform(
                        post("/api/meal-templates")
                                .header(CustomHeaders.X_USER_ID, 101L)
                                .header(CustomHeaders.X_REQUEST_ID, requestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String repeatedResponse = mockMvc.perform(
                        post("/api/meal-templates")
                                .header(CustomHeaders.X_USER_ID, 101L)
                                .header(CustomHeaders.X_REQUEST_ID, requestId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson)
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(repeatedResponse).isEqualTo(firstResponse);
        assertThat(mealTemplateRepository.findAllByUserId(101L)).hasSize(1);
    }

    @Test
    @DisplayName("When different users reuse request id, should create both templates")
    void createTemplate_whenDifferentUsersReuseRequestId_shouldCreateBoth() throws Exception {
        UUID requestId = UUID.randomUUID();
        MealTemplateRequestDto request = new MealTemplateRequestDto();
        request.setName("Shared Request ID");
        request.setItems(List.of(MealTemplateRequestDto.TemplateItemDto.builder()
                .foodId("food-oats")
                .unitType(UnitType.GRAMS)
                .amount(50)
                .build()));
        given(foodClientService.getFoodsByIds(anyList()))
                .willReturn(List.of(createMockFood("food-oats", "Oats", 350)));
        String requestJson = objectMapper.writeValueAsString(request);

        for (long userId : List.of(201L, 202L)) {
            mockMvc.perform(
                            post("/api/meal-templates")
                                    .header(CustomHeaders.X_USER_ID, userId)
                                    .header(CustomHeaders.X_REQUEST_ID, requestId)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson)
                    )
                    .andExpect(status().isCreated());
        }

        assertThat(mealTemplateRepository.findAllByUserId(201L)).hasSize(1);
        assertThat(mealTemplateRepository.findAllByUserId(202L)).hasSize(1);
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
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
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
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
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
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
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
        UUID requestId = UUID.randomUUID();
        UUID mealGroupId = UUID.randomUUID();

        // When
        mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply", template.getId())
                                .header("X-User-Id", attackerId)
                                .header(CustomHeaders.X_REQUEST_ID, requestId)
                                .param("date", LocalDate.now().toString())
                                .param("mealGroupId", mealGroupId.toString())
                )
                .andExpect(status().isNotFound());

        // Then
        assertThat(intakeRepository.findByUserId(attackerId)).isEmpty();
    }

    @Test
    @DisplayName("When valid id, should apply template and create intakes with groupId")
    void applyTemplate_whenValidId_shouldCreateIntakes() throws Exception {
        // Given
        Long userId = 102L;
        LocalDate date = LocalDate.now();
        UUID requestId = UUID.randomUUID();
        UUID mealGroupId = UUID.randomUUID();

        MealTemplate template = createAndSaveTemplateInDb(userId, "Lunch Box");
        Long templateId = template.getId();

        // When
        String firstResponse = mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply", templateId)
                                .header("X-User-Id", userId)
                                .header(CustomHeaders.X_REQUEST_ID, requestId)
                                .param("date", date.toString())
                                .param("period", "LUNCH")
                                .param("mealGroupId", mealGroupId.toString())
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].mealGroupId").value(mealGroupId.toString()))
                .andExpect(jsonPath("$[0].mealTemplateName").value("Lunch Box"))
                .andExpect(jsonPath("$[0].date").value(date.toString()))
                .andExpect(jsonPath("$[0].foodId").value("f1"))
                .andExpect(jsonPath("$[0].brand").value("Brand 1"))
                .andExpect(jsonPath("$[1].foodId").value("f2"))
                .andReturn().getResponse().getContentAsString();

        String repeatedResponse = mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply", templateId)
                                .header("X-User-Id", userId)
                                .header(CustomHeaders.X_REQUEST_ID, requestId)
                                .param("date", date.toString())
                                .param("period", "LUNCH")
                                .param("mealGroupId", UUID.randomUUID().toString())
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(repeatedResponse).isEqualTo(firstResponse);
        assertThat(applicationRepository.findByUserIdAndRequestId(userId, requestId)).isPresent();

        // Then
        List<Intake> intakes = intakeRepository.findByUserIdAndDate(userId, date);
        assertThat(intakes).hasSize(2);
        assertThat(intakes.get(0).getMealGroupId()).isEqualTo(mealGroupId.toString());
        assertThat(intakes.get(0).getMealGroupId()).isEqualTo(intakes.get(1).getMealGroupId());
        assertThat(intakes)
                .extracting(Intake::getMealTemplateName)
                .containsOnly("Lunch Box");
        assertThat(intakes)
                .extracting(Intake::getBrand)
                .containsExactlyInAnyOrder("Brand 1", "Brand 2");
        assertThat(intakes.getFirst().getNutriments().getCaloriesPer100())
                .isGreaterThan(BigDecimal.ZERO);

        mockMvc.perform(
                        get("/api/intake")
                                .header("X-User-Id", userId)
                                .param("date", date.toString())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].mealTemplateName").value("Lunch Box"));
    }

    @Test
    @DisplayName("When valid recipe id, should create one intake with proportional nutriments")
    void applyRecipe_whenValidId_shouldCreateOneSnapshotIntake() throws Exception {
        Long userId = 103L;
        LocalDate date = LocalDate.now();
        UUID requestId = UUID.randomUUID();
        MealTemplate template = createAndSaveRecipeTemplateInDb(userId);

        String firstResponse = mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply-recipe", template.getId())
                                .header(CustomHeaders.X_USER_ID, userId)
                                .header(CustomHeaders.X_REQUEST_ID, requestId)
                                .param("date", date.toString())
                                .param("period", "LUNCH")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"consumedAmount\":200,\"unitType\":\"GRAMS\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.foodId").value("RECIPE_" + template.getId()))
                .andExpect(jsonPath("$.foodName").value("Cheese Pie"))
                .andExpect(jsonPath("$.amount").value(200))
                .andExpect(jsonPath("$.unitType").value("GRAMS"))
                .andExpect(jsonPath("$.intakePeriod").value("LUNCH"))
                .andExpect(jsonPath("$.nutriments.calories").value(250.0))
                .andExpect(jsonPath("$.nutriments.protein").value(20.0))
                .andExpect(jsonPath("$.nutriments.fat").value(10.0))
                .andExpect(jsonPath("$.nutriments.carbohydrates").value(30.0))
                .andReturn().getResponse().getContentAsString();

        String repeatedResponse = mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply-recipe", template.getId())
                                .header(CustomHeaders.X_USER_ID, userId)
                                .header(CustomHeaders.X_REQUEST_ID, requestId)
                                .param("date", date.toString())
                                .param("period", "DINNER")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"consumedAmount\":300,\"unitType\":\"GRAMS\"}")
                )
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(repeatedResponse).isEqualTo(firstResponse);
        List<Intake> intakes = intakeRepository.findByUserIdAndDate(userId, date);
        assertThat(intakes).hasSize(1);
        Intake intake = intakes.getFirst();
        assertThat(intake.getFoodId()).isEqualTo("RECIPE_" + template.getId());
        assertThat(intake.getNutriments().getCalories()).isEqualByComparingTo("250.00");
        assertThat(intake.getNutriments().getCaloriesPer100()).isEqualByComparingTo("125.00");
        assertThat(applicationRepository.findByUserIdAndRequestId(userId, requestId)).isPresent();
    }

    @Test
    @DisplayName("When recipe yield is pieces, should create proportional pieces intake")
    void applyRecipe_whenYieldIsPieces_shouldCreatePiecesIntake() throws Exception {
        Long userId = 105L;
        LocalDate date = LocalDate.now();
        MealTemplate template = createAndSaveRecipeTemplateInDb(
                userId, "Cheese Buns", 12, UnitType.PIECES);

        mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply-recipe", template.getId())
                                .header(CustomHeaders.X_USER_ID, userId)
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
                                .param("date", date.toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"consumedAmount\":2,\"unitType\":\"PIECES\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(2))
                .andExpect(jsonPath("$.unitType").value("PIECES"))
                .andExpect(jsonPath("$.nutriments.calories").value(166.67))
                .andExpect(jsonPath("$.nutriments.caloriesPerPiece").value(83.33));

        Intake intake = intakeRepository.findByUserIdAndDate(userId, date).getFirst();
        assertThat(intake.getNutriments().getCaloriesPer100()).isNull();
        assertThat(intake.getNutriments().getCaloriesPerPiece()).isEqualByComparingTo("83.33");
    }

    @Test
    @DisplayName("When consumed unit differs from recipe yield unit, should return 400")
    void applyRecipe_whenUnitDoesNotMatchYield_shouldReturn400() throws Exception {
        Long userId = 106L;
        MealTemplate template = createAndSaveRecipeTemplateInDb(
                userId, "Cheese Buns", 12, UnitType.PIECES);

        mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply-recipe", template.getId())
                                .header(CustomHeaders.X_USER_ID, userId)
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
                                .param("date", LocalDate.now().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"consumedAmount\":200,\"unitType\":\"GRAMS\"}")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("When recipe is applied through regular endpoint, should return 400")
    void applyTemplate_whenTemplateIsRecipe_shouldReturn400() throws Exception {
        Long userId = 104L;
        MealTemplate template = createAndSaveRecipeTemplateInDb(userId);

        mockMvc.perform(
                        post("/api/meal-templates/{templateId}/apply", template.getId())
                                .header(CustomHeaders.X_USER_ID, userId)
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
                                .param("date", LocalDate.now().toString())
                                .param("mealGroupId", UUID.randomUUID().toString())
                )
                .andExpect(status().isBadRequest());
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
                                .header(CustomHeaders.X_REQUEST_ID, UUID.randomUUID())
                                .param("date", LocalDate.now().toString())
                                .param("mealGroupId", UUID.randomUUID().toString())
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
                .brand("Brand 1")
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
                .brand("Brand 2")
                .amount(50)
                .nutriments(new Nutriments(
                        BigDecimal.valueOf(200), BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO))
                .build();

        template.setItems(new ArrayList<>(List.of(item1, item2)));
        return mealTemplateRepository.save(template);
    }

    private MealTemplate createAndSaveRecipeTemplateInDb(Long userId) {
        return createAndSaveRecipeTemplateInDb(userId, "Cheese Pie", 800, UnitType.GRAMS);
    }

    private MealTemplate createAndSaveRecipeTemplateInDb(Long userId, String name,
                                                         Integer totalYieldAmount,
                                                         UnitType yieldUnitType) {
        MealTemplate template = MealTemplate.builder()
                .userId(userId)
                .name(name)
                .recipe(true)
                .totalYieldAmount(totalYieldAmount)
                .yieldUnitType(yieldUnitType)
                .build();

        MealTemplateItem item1 = MealTemplateItem.builder()
                .template(template)
                .foodId("f1")
                .foodName("Food 1")
                .amount(500)
                .nutriments(Nutriments.builder()
                        .calories(BigDecimal.valueOf(600))
                        .protein(BigDecimal.valueOf(50))
                        .fat(BigDecimal.valueOf(25))
                        .carbohydrates(BigDecimal.valueOf(80))
                        .build())
                .build();

        MealTemplateItem item2 = MealTemplateItem.builder()
                .template(template)
                .foodId("f2")
                .foodName("Food 2")
                .amount(500)
                .nutriments(Nutriments.builder()
                        .calories(BigDecimal.valueOf(400))
                        .protein(BigDecimal.valueOf(30))
                        .fat(BigDecimal.valueOf(15))
                        .carbohydrates(BigDecimal.valueOf(40))
                        .build())
                .build();

        template.setItems(new ArrayList<>(List.of(item1, item2)));
        return mealTemplateRepository.save(template);
    }
}
