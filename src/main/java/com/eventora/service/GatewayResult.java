package com.eventora.service;

import java.math.BigDecimal;

public record GatewayResult(String reference, BigDecimal amount, boolean approved, String message, String signature) {}
