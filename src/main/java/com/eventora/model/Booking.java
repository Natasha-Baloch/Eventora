package com.eventora.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String reference;
    @ManyToOne(optional = false)
    public AppUser customer;
    @ManyToOne(optional = false)
    public Event event;
    @ManyToOne(optional = false)
    public TicketCategory tier;
    public int quantity;
    public String attendeeName;
    public String attendeePhone;
    public boolean ticketsIssued = false;
    @Column(precision = 12, scale = 2)
    public BigDecimal total;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public BookingStatus status;
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime expiresAt;
    @Column(length = 500)
    public String organizerNote;
    @Column(length = 500)
    public String refundReason;
}
