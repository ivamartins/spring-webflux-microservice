package com.codesolutions.integrations.soap;

import com.codesolutions.integrations.soap.LegacyErpService;
import jakarta.xml.ws.Service;

import javax.xml.namespace.QName;
import java.net.URL;

/**
 * Client wrapper for the legacy SOAP service. Demonstrates the
 * "integração com sistemas legados" requirement of the JD.
 *
 * Synchronous on purpose — SOAP is intrinsically blocking; we keep
 * the blocking call clearly isolated so the caller can wrap it in
 * Mono.fromCallable if running on the WebFlux pipeline.
 */
public class LegacyErpClient {

    private final LegacyErpService port;

    public LegacyErpClient(URL wsdl) {
        QName qname = new QName("http://codesolutions.com/legacy/erp", "LegacyErpService");
        Service service = Service.create(wsdl, qname);
        this.port = service.getPort(LegacyErpService.class);
    }

    public String getCustomerStatus(String customerId) {
        return port.getCustomerStatus(customerId);
    }

    public boolean applyPayment(String orderId, String amount) {
        return port.applyPayment(orderId, amount);
    }
}
