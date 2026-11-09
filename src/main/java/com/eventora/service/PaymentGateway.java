package com.eventora.service;

import java.math.BigDecimal;

/**
 * Payment provider abstraction. The bundled implementation is a SANDBOX gateway.
 * To go live, add a Stripe / PayFast / JazzCash / Razorpay implementation of this interface
 * (charge on the provider, verify the provider's webhook/signature in verify()) and mark it @Primary.
 */
public interface PaymentGateway {
    /** method = CARD | EASYPAISA | JAZZCASH | BANK; accountId = card number, mobile number or bank account. */
    GatewayResult charge(String method, String bookingRef, BigDecimal amount, String accountId);
    /** Must cryptographically verify that the result really came from the gateway and was not tampered with. */
    boolean verify(GatewayResult result);
    String name();
}
