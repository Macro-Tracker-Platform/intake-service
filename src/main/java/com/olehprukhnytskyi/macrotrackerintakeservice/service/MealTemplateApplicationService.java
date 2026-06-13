package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.IntakeErrorCode;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.IntakeMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.NutrimentsMapper;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplate;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateApplication;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.MealTemplateItem;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.IntakeRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateApplicationRepository;
import com.olehprukhnytskyi.macrotrackerintakeservice.repository.jpa.MealTemplateRepository;
import com.olehprukhnytskyi.util.IntakePeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MealTemplateApplicationService {
    private final IntakeRepository intakeRepository;
    private final MealTemplateRepository mealTemplateRepository;
    private final MealTemplateApplicationRepository applicationRepository;
    private final IntakeMapper intakeMapper;
    private final NutrimentsMapper nutrimentsMapper;

    @Transactional
    public List<IntakeResponseDto> create(Long templateId, LocalDate date, IntakePeriod period,
                                          UUID mealGroupId, Long userId, UUID requestId) {
        MealTemplate template = mealTemplateRepository.findByIdAndUserId(templateId, userId)
                .orElseThrow(() -> new NotFoundException(IntakeErrorCode.INTAKE_NOT_FOUND,
                        "Template not found"));
        MealTemplateApplication application = MealTemplateApplication.builder()
                .userId(userId)
                .requestId(requestId)
                .mealGroupId(mealGroupId)
                .templateId(templateId)
                .date(date)
                .intakePeriod(period)
                .build();
        applicationRepository.saveAndFlush(application);

        List<Intake> intakes = createIntakes(template, date, period, mealGroupId, userId);
        List<Intake> savedIntakes = intakeRepository.saveAllAndFlush(intakes);
        return savedIntakes.stream().map(intakeMapper::toDto).toList();
    }

    private List<Intake> createIntakes(MealTemplate template, LocalDate date,
                                       IntakePeriod period, UUID mealGroupId, Long userId) {
        List<Intake> intakes = new ArrayList<>();
        for (int position = 0; position < template.getItems().size(); position++) {
            MealTemplateItem item = template.getItems().get(position);
            Intake intake = new Intake();
            intake.setMealGroupId(mealGroupId.toString());
            intake.setMealTemplateName(template.getName());
            intake.setMealItemPosition(position);
            intake.setUserId(userId);
            intake.setFoodId(item.getFoodId());
            intake.setFoodName(item.getFoodName());
            intake.setDate(date);
            intake.setUnitType(item.getUnitType());
            intake.setIntakePeriod(period);
            intake.setAmount(item.getAmount());
            intake.setNutriments(nutrimentsMapper.clone(item.getNutriments()));
            intake.setOriginalFoodId(item.getOriginalFoodId());
            intake.setModerationStatus(item.getModerationStatus());
            intake.setVerifiedByAdmin(item.isVerifiedByAdmin());
            intakes.add(intake);
        }
        return intakes;
    }
}
