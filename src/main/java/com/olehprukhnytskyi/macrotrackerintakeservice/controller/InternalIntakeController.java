package com.olehprukhnytskyi.macrotrackerintakeservice.controller;

import com.olehprukhnytskyi.macrotrackerintakeservice.dto.DailyIntakeSummaryDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.projection.DailyIntakeSummaryProjection;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.util.CustomHeaders;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/intakes")
public class InternalIntakeController {
    private final IntakeRepository intakeRepository;

    @GetMapping("/daily-summary")
    public ResponseEntity<List<DailyIntakeSummaryDto>> dailySummary(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from.isAfter(to)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "from must not be after to");
        }
        List<DailyIntakeSummaryDto> result = intakeRepository
                .summarizeByUserIdAndDateRange(userId, from, to)
                .stream()
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(result);
    }

    private DailyIntakeSummaryDto toDto(DailyIntakeSummaryProjection projection) {
        return DailyIntakeSummaryDto.builder()
                .date(projection.getDate())
                .calories(projection.getCalories())
                .protein(projection.getProtein())
                .fat(projection.getFat())
                .carbohydrates(projection.getCarbohydrates())
                .build();
    }
}
