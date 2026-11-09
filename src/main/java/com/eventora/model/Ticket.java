package com.eventora.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
public class Ticket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    public Booking booking;
    @Column(unique = true, nullable = false)
    public String code;
    public boolean checkedIn = false;
    public LocalDateTime checkedInAt;
    public boolean voided = false;
}
