package com.olehprukhnytskyi.macrotrackerintakeservice.mapper;

import com.olehprukhnytskyi.macrotrackerintakeservice.config.MapperConfig;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.FoodDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeSyncItemDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UpdateIntakeRequestDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import java.util.ArrayList;
import org.mapstruct.AfterMapping;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(
        config = MapperConfig.class,
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {NutrimentsMapper.class}
)
public interface IntakeMapper {
    Intake toModel(IntakeRequestDto dto);

    IntakeResponseDto toDto(Intake model);

    IntakeSyncItemDto toSyncDto(Intake model);

    @Mappings({
            @Mapping(target = "foodName", source = "productName"),
            @Mapping(target = "brand", source = "brands"),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "userId", ignore = true),
            @Mapping(target = "nutriments", ignore = true)
    })
    void updateIntakeFromFoodDto(@MappingTarget Intake intake, FoodDto food);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateFromDto(@MappingTarget Intake intake, UpdateIntakeRequestDto dto);

    @AfterMapping
    default void determineAvailableUnits(Intake intake,
                                         @MappingTarget
                                         IntakeResponseDto.IntakeResponseDtoBuilder builder) {
        if (intake.getNutriments() != null) {
            builder.availableUnits(new ArrayList<>(intake.getNutriments().getAvailableUnits()));
        }
    }

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "unitType", defaultValue = "GRAMS")
    @Mapping(target = "intakePeriod", defaultValue = "SNACK")
    void updateEntityFromSyncDto(IntakeSyncItemDto dto, @MappingTarget Intake intake);
}
