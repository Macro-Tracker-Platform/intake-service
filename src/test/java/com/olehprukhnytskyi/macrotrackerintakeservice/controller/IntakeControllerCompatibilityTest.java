package com.olehprukhnytskyi.macrotrackerintakeservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeSyncItemDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.IntakeSyncResponseDto;
import com.olehprukhnytskyi.macrotrackerintakeservice.model.IntakeStatus;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.ClientVersionPolicy;
import com.olehprukhnytskyi.macrotrackerintakeservice.service.IntakeService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IntakeControllerCompatibilityTest {
    private IntakeService intakeService;
    private IntakeController controller;

    @BeforeEach
    void setUp() {
        intakeService = mock(IntakeService.class);
        controller = new IntakeController(intakeService, new ClientVersionPolicy(45));
    }

    @Test
    void legacyListHidesPlannedEntries() {
        LocalDate date = LocalDate.now();
        when(intakeService.findByDate(date, 1L)).thenReturn(List.of(
                IntakeResponseDto.builder().id(1L).status(IntakeStatus.CONSUMED).build(),
                IntakeResponseDto.builder().id(2L).status(IntakeStatus.PLANNED).build()));

        List<IntakeResponseDto> legacy = controller.findByDate(1L, null, date).getBody();
        List<IntakeResponseDto> current = controller.findByDate(1L, "45", date).getBody();

        assertThat(legacy).extracting(IntakeResponseDto::getId).containsExactly(1L);
        assertThat(current).extracting(IntakeResponseDto::getId).containsExactly(1L, 2L);
    }

    @Test
    void legacySyncReceivesPlannedEntryAsDeletion() {
        IntakeSyncItemDto planned = IntakeSyncItemDto.builder()
                .id(2L)
                .status(IntakeStatus.PLANNED)
                .build();
        when(intakeService.pullSync(1L, Instant.EPOCH, 100)).thenReturn(
                IntakeSyncResponseDto.builder().data(List.of(planned)).build());

        IntakeSyncResponseDto response = controller
                .pullSync(1L, null, null, 100)
                .getBody();

        assertThat(response.getData().getFirst().isDeleted()).isTrue();
    }
}
