package com.eventora.service;

import java.util.Map;

/** Payment form data. Card numbers, PINs and CVVs are only forwarded to the gateway - never stored. */
public class PayRequest {
    public String method, holder, cardNumber, expiry, cvv, mobile, pin, bankName, accountNumber;

    public static PayRequest from(Map<String, String> m) {
        PayRequest r = new PayRequest();
        r.method = m.get("method"); r.holder = m.get("holder"); r.cardNumber = m.get("cardNumber");
        r.expiry = m.get("expiry"); r.cvv = m.get("cvv"); r.mobile = m.get("mobile"); r.pin = m.get("pin");
        r.bankName = m.get("bankName"); r.accountNumber = m.get("accountNumber");
        return r;
    }
}
