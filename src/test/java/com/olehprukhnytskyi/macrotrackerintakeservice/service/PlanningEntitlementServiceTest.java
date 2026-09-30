package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.macrotrackerintakeservice.client.UserEntitlementClient;
import com.olehprukhnytskyi.macrotrackerintakeservice.dto.UserEntitlementDto;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PlanningEntitlementServiceTest {
    private final UserEntitlementClient client = mock(UserEntitlementClient.class);
    private final PlanningEntitlementService service = new PlanningEntitlementService(client);

    @Test
    void freeUserCanPlanTomorrowWithoutEntitlementLookup() {
        assertDoesNotThrow(() -> service.validatePlanningDate(
                1L, LocalDate.now().plusDays(1)));

        verify(client, never()).getEntitlement(1L);
    }

    @Test
    void freeUserCannotPlanMoreThanOneDayAhead() {
        when(client.getEntitlement(1L)).thenReturn(new UserEntitlementDto());

        assertThrows(BadRequestException.class, () -> service.validatePlanningDate(
                1L, LocalDate.now().plusDays(2)));
    }

    @Test
    void premiumUserCanPlanFourteenDaysAhead() {
        UserEntitlementDto entitlement = new UserEntitlementDto();
        entitlement.setPlan("PRO");
        when(client.getEntitlement(1L)).thenReturn(entitlement);

        assertDoesNotThrow(() -> service.validatePlanningDate(
                1L, LocalDate.now().plusDays(14)));
    }

    @Test
    void nobodyCanPlanBeyondFourteenDays() {
        assertThrows(BadRequestException.class, () -> service.validatePlanningDate(
                1L, LocalDate.now().plusDays(15)));

        verify(client, never()).getEntitlement(1L);
    }
}
