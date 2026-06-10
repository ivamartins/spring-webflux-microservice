package com.codesolutions.integrations.soap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for the legacy SOAP contract.
 *
 * Uses the in-process @Component implementation directly — no WSDL
 * roundtrip, no network. Verifies the contract logic.
 */
class LegacyErpServiceTest {

    private final LegacyErpService service = new LegacyErpServiceImpl();

    @Test
    void shouldReturnActiveForKnownCustomer() {
        assertThat(service.getCustomerStatus("c-1")).isEqualTo("ACTIVE");
    }

    @Test
    void shouldReturnUnknownForEmptyCustomer() {
        assertThat(service.getCustomerStatus("")).isEqualTo("UNKNOWN");
        assertThat(service.getCustomerStatus(null)).isEqualTo("UNKNOWN");
    }

    @Test
    void shouldAcceptValidPayment() {
        assertThat(service.applyPayment("o-1", "99.90")).isTrue();
    }

    @Test
    void shouldRejectNullPayment() {
        assertThat(service.applyPayment(null, "99.90")).isFalse();
        assertThat(service.applyPayment("o-1", null)).isFalse();
    }
}
