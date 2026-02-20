package com.olehprukhnytskyi.macrotrackerintakeservice.controller;

import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.MealTemplateRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.MealTemplateResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateMealTemplateDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.MealService;
import com.olehprukhnytskyi.util.CustomHeaders;
import com.olehprukhnytskyi.util.IntakePeriod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meal-templates")
@Tag(
        name = "Meal Templates API",
        description = "Manage reusable meal templates (grouping multiple food items)"
)
public class MealController {
    private final MealService mealService;

    @Operation(
            summary = "Get user templates",
            description = """
            Retrieve all meal templates created by the user.
            Results are cached to improve performance.
            """)
    @GetMapping
    public ResponseEntity<List<MealTemplateResponseDto>> getAllTemplates(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        log.info("Request to get all templates for userId={}", userId);
        List<MealTemplateResponseDto> templates = mealService.getTemplates(userId);
        log.debug("Retrieved {} templates for userId={}", templates.size(), userId);
        return ResponseEntity.ok(templates);
    }

    @Operation(
            summary = "Create meal template",
            description = """
            Create a reusable template (e.g., 'Morning Porridge')
            containing multiple food items.
            """)
    @PostMapping
    public ResponseEntity<Long> createMealTemplate(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @Valid @RequestBody MealTemplateRequestDto request) {
        log.info("Request to create template '{}' for userId={}", request.getName(), userId);
        Long templateId = mealService.createTemplate(request, userId);
        log.debug("Template created id={} for userId={}", templateId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(templateId);
    }

    @Operation(
            summary = "Apply meal template",
            description = """
            Apply a template to a specific date.\s
            Creates individual intake records for each item in the template.
            Returns the list of created intakes containing a 'mealGroupId' for easy rollback.
            """)
    @PostMapping("/{templateId}/apply")
    public ResponseEntity<List<IntakeResponseDto>> applyTemplate(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable Long templateId,
            @Parameter(description = "Date to apply the template to (yyyy-MM-dd)",
                    required = true)
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Meal period (BREAKFAST, LUNCH, etc.)")
            @RequestParam(required = false, defaultValue = "SNACK") IntakePeriod period) {
        log.info("Applying template id={} for userId={} on date={}", templateId, userId, date);
        List<IntakeResponseDto> createdIntakes = mealService
                .applyTemplate(templateId, date, period, userId);
        log.debug("Template applied successfully, created {} records", createdIntakes.size());
        return ResponseEntity.status(HttpStatus.CREATED).body(createdIntakes);
    }

    @Operation(
            summary = "Delete meal template",
            description = "Permanently deletes a created meal template.")
    @DeleteMapping("/{templateId}")
    public ResponseEntity<Void> deleteTemplate(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @Parameter(description = "ID of the template to delete", required = true)
            @PathVariable Long templateId) {
        log.info("Request to delete template id={} for userId={}", templateId, userId);
        mealService.deleteTemplate(templateId, userId);
        log.debug("Template id={} deleted successfully for userId={}", templateId, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Update meal template",
            description = """
            Updates an existing template: renames it, changes amounts,
            adds new items or removes existing ones.
            """)
    @PutMapping("/{templateId}")
    public ResponseEntity<Void> updateTemplate(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable Long templateId,
            @Valid @RequestBody UpdateMealTemplateDto request) {
        log.info("Request to update template id={} for userId={}", templateId, userId);
        mealService.updateTemplate(templateId, request, userId);
        log.debug("Template id={} updated successfully", templateId);
        return ResponseEntity.ok().build();
    }
}
