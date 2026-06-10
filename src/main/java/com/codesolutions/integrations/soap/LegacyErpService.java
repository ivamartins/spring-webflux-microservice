package com.codesolutions.integrations.soap;

import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;

/**
 * Stub SOAP service contract — the kind of legacy endpoint a typical
 * Brazilian cooperative (Sicredi-style) exposes for back-office ops.
 *
 * In production this would be auto-generated from a WSDL via wsimport.
 * Here we declare the contract and the implementation in one file
 * (good for unit-testing the integration).
 */
@WebService(targetNamespace = "http://codesolutions.com/legacy/erp", name = "LegacyErpService")
public interface LegacyErpService {

    @WebMethod
    String getCustomerStatus(@WebParam(name = "customerId") String customerId);

    @WebMethod
    boolean applyPayment(@WebParam(name = "orderId") String orderId,
                         @WebParam(name = "amount") String amount);
}
