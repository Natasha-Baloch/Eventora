package com.eventora.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    public Booking booking;
    @Column(precision = 12, scale = 2)
    public BigDecimal amount;
    @Enumerated(EnumType.STRING)
    public PaymentStatus status;
    public String gatewayRef;
    public String method;
    public String methodType;
    public String payerName;
    public String failureReason;
    public LocalDateTime createdAt = LocalDateTime.now();
}
