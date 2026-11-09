package com.eventora.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
public class Complaint {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    public AppUser user;
    public String subject;
    @Column(length = 2000)
    public String message;
    public boolean resolved = false;
    @Column(length = 1000)
    public String adminResponse;
    public LocalDateTime createdAt = LocalDateTime.now();
}
