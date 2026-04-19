package com.olehprukhnytskyi.macrotrackerintakeservice.dto;

import com.olehprukhnytskyi.util.UnitType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Food product reference")
public class FoodDto {
    @Schema(description = "Food ID", example = "507f1f77bcf86cd799439011")
    private String id;

    @Schema(description = "Product barcode", example = "5901234123457")
    private String code;

    @Schema(description = "ID of the user who created the product", example = "12345")
    private Long userId;

    @Schema(description = "Product name", example = "Chicken Breast")
    private String productName;

    @Schema(description = "Generic name", example = "Poultry")
    private String genericName;

    @Schema(description = "Image URL", example = "https://example.com/images/chicken.jpg")
    private String imageUrl;

    @Schema(description = "Product brands", example = "Organic Farms")
    private String brands;

    @Schema(description = "Nutrition information")
    @Builder.Default
    private NutrimentsDto nutriments = new NutrimentsDto();

    @Schema(
            description = "Available measurement units for the product",
            example = "[\"GRAMS\", \"PIECES\"]"
    )
    private List<UnitType> availableUnits;

    private String originalFoodId;

    private String moderationStatus;

    private boolean verifiedByAdmin;
}
