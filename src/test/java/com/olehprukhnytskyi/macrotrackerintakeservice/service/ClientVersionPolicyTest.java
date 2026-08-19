package com.olehprukhnytskyi.macrotrackerintakeservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientVersionPolicyTest {
    private final ClientVersionPolicy policy = new ClientVersionPolicy(45);

    @Test
    void missingOrInvalidVersionUsesLegacyContract() {
        assertThat(policy.supportsPlanning(null)).isFalse();
        assertThat(policy.supportsPlanning(" ")).isFalse();
        assertThat(policy.supportsPlanning("invalid")).isFalse();
    }

    @Test
    void planningStartsAtConfiguredVersion() {
        assertThat(policy.supportsPlanning("44")).isFalse();
        assertThat(policy.supportsPlanning(" 45 ")).isTrue();
        assertThat(policy.supportsPlanning("46")).isTrue();
    }
}
