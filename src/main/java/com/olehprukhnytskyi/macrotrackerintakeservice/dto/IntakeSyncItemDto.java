package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.olehprukhnytskyi.util.IntakePeriod;
import com.olehprukhnytskyi.util.UnitType;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntakeSyncItemDto {
    private Long id;
    private UUID requestId;
    private String mealGroupId;
    private String mealTemplateName;
    private Integer mealItemPosition;
    private String foodId;
    private String foodName;
    private String brand;

    @Min(1)
    private Integer amount;

    private UnitType unitType;
    private LocalDate date;
    private IntakePeriod intakePeriod;
    private NutrimentsDto nutriments;
    private String originalFoodId;
    private String moderationStatus;
    private boolean verifiedByAdmin;
    private Instant updatedAt;
    private boolean deleted;
    private Long version;
}
