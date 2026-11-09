package com.eventora.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    public AppUser user;
    @Column(length = 500)
    public String message;
    public String link;
    public boolean seen = false;
    public LocalDateTime createdAt = LocalDateTime.now();
}
