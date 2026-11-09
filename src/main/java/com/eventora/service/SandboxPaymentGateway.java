package com.eventora.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Test gateway for card / Easypaisa / JazzCash / bank: approved unless the card, mobile or account number ends in 0002. Results are HMAC-signed and verified server-side. */
@Component
public class SandboxPaymentGateway implements PaymentGateway {
    @Value("${eventora.payment-secret}") private String secret;

    @Override
    public GatewayResult charge(String method, String bookingRef, BigDecimal amount, String accountId) {
        boolean approved = !accountId.endsWith("0002");
        String ref = "SBX-" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        return new GatewayResult(ref, amount, approved, approved ? "Approved" : "Card declined by issuer", sign(ref, amount, approved));
    }

    @Override
    public boolean verify(GatewayResult r) {
        return MessageDigest.isEqual(sign(r.reference(), r.amount(), r.approved()).getBytes(StandardCharsets.UTF_8),
                r.signature().getBytes(StandardCharsets.UTF_8));
    }

    @Override public String name() { return "Sandbox"; }

    private String sign(String ref, BigDecimal amount, boolean approved) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String data = ref + "|" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + "|" + approved;
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
