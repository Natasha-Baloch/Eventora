package com.eventora.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "feedbacks")
public class Feedback {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    public Booking booking;
    @ManyToOne(optional = false)
    public Event event;
    @ManyToOne(optional = false)
    public AppUser customer;
    public int rating;
    @Column(length = 1000)
    public String comment;
    public LocalDateTime createdAt = LocalDateTime.now();
}
