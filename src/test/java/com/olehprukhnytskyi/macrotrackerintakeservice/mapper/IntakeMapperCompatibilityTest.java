package com.olehprukhnytskyi.macrotrackerintakeservice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeSyncItemDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.impl.IntakeMapperImpl;
import com.olehprukhnytskyi.macrotrackerintakeservice.mapper.impl.NutrimentsMapperImpl;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.Intake;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.IntakeStatus;
import org.junit.jupiter.api.Test;

class IntakeMapperCompatibilityTest {
    private final IntakeMapper mapper = new IntakeMapperImpl(new NutrimentsMapperImpl());

    @Test
    void legacySyncWithoutStatusPreservesExistingStatus() {
        Intake intake = Intake.builder().status(IntakeStatus.PLANNED).build();

        mapper.updateEntityFromSyncDto(new IntakeSyncItemDto(), intake);

        assertThat(intake.getStatus()).isEqualTo(IntakeStatus.PLANNED);
    }

    @Test
    void legacySyncWithoutStatusKeepsConsumedDefaultForNewIntake() {
        Intake intake = new Intake();

        mapper.updateEntityFromSyncDto(new IntakeSyncItemDto(), intake);

        assertThat(intake.getStatus()).isEqualTo(IntakeStatus.CONSUMED);
    }
}
