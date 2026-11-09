package com.eventora.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "ticket_categories")
public class TicketCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    public Event event;
    public String name;
    @Column(precision = 12, scale = 2)
    public BigDecimal price;
    public int capacity;
    /** seats reserved by REQUESTED / AWAITING_PAYMENT / PAID / REFUND_REQUESTED bookings */
    public int held = 0;

    public int getAvailable() { return Math.max(0, capacity - held); }
}
