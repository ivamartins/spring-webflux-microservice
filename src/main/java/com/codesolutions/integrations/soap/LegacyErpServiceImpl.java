package com.codesolutions.integrations.soap;

import jakarta.jws.WebService;
import org.springframework.stereotype.Component;

/**
 * In-process SOAP implementation used as a fallback / local dev mode.
 * In prod you'd point at the real WSDL endpoint.
 */
@Component
@WebService(endpointInterface = "com.codesolutions.integrations.soap.LegacyErpService")
public class LegacyErpServiceImpl implements LegacyErpService {

    @Override
    public String getCustomerStatus(String customerId) {
        // Pretend to look up in a legacy mainframe
        return customerId == null || customerId.isEmpty() ? "UNKNOWN" : "ACTIVE";
    }

    @Override
    public boolean applyPayment(String orderId, String amount) {
        return orderId != null && amount != null;
    }
}
