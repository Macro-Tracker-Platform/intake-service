package com.olehprukhnytskyi.macrotrackerintakeservice.controller;

import com.olehprukhnytskyi.annotation.Idempotent;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateIntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.IntakeService;
import com.olehprukhnytskyi.util.CustomHeaders;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/intake")
@Tag(
        name = "Food Intake API",
        description = "Track and manage daily food consumption with nutrition calculations"
)
public class IntakeController {
    private final IntakeService intakeService;

    @Operation(
            summary = "Get intake records",
            description = """
            Retrieve paginated food intake records for a specific date.
            
            **Date format:**
            - 'yyyy-MM-dd': Specific date (e.g., 2024-01-15)
            - Empty: All dates
            
            Automatically calculates nutrition values based on food amount.
            """
    )
    @GetMapping
    public ResponseEntity<List<IntakeResponseDto>> findByDate(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        log.debug("Fetching intake records for userId={} date={}", userId, date);
        List<IntakeResponseDto> intakes = intakeService.findByDate(date, userId);
        log.debug("Fetched {} intake records for userId={}", intakes.size(), userId);
        return ResponseEntity.ok(intakes);
    }

    @Operation(
            summary = "Add food intake",
            description = "Record food consumption with automatic nutrition calculation"
    )
    @Idempotent
    @PostMapping
    public ResponseEntity<IntakeResponseDto> addIntake(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @Valid @RequestBody IntakeRequestDto intakeRequest) {
        log.info("Creating new intake record for userId={}", userId);
        IntakeResponseDto saved = intakeService.save(intakeRequest, userId);
        log.debug("Intake record created successfully for userId={} intakeId={}",
                userId, saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(saved);
    }

    @Operation(
            summary = "Update intake amount",
            description = "Update the amount of consumed food with recalculated nutrition values"
    )
    @PatchMapping("/{id}")
    public ResponseEntity<IntakeResponseDto> updateIntake(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable Long id,
            @Valid @RequestBody UpdateIntakeRequestDto intakeRequest) {
        log.info("Updating intake record id={} for userId={}", id, userId);
        IntakeResponseDto updated = intakeService.update(id, intakeRequest, userId);
        log.debug("Intake record updated id={} for userId={}", id, userId);
        return ResponseEntity.ok(updated);
    }

    @Operation(
            summary = "Delete intake record",
            description = "Remove food intake record by ID"
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteById(
            @PathVariable Long id,
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        log.info("Deleting intake record id={} for userId={}", id, userId);
        intakeService.deleteById(id, userId);
        log.debug("Deleted intake record id={} for userId={}", id, userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Undo intake group",
            description = """
            Deletes a group of intake records created by a single template application.
            Use the 'mealGroupId' returned from the apply endpoint.
            """)
    @DeleteMapping("/group/{mealGroupId}")
    public ResponseEntity<Void> undoIntakeGroup(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @Parameter(description = "UUID string identifying the batch of records",
                    required = true)
            @PathVariable String mealGroupId) {
        log.info("Request to revert intake group {} for userId={}", mealGroupId, userId);
        intakeService.undoIntakeGroup(mealGroupId, userId);
        log.debug("Intake group {} reverted successfully", mealGroupId);
        return ResponseEntity.noContent().build();
    }
}
