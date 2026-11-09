package com.eventora.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_tokens", indexes = @Index(columnList = "email"))
public class OtpToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String email;
    public String codeHash;
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime expiresAt;
    public int attempts = 0;
    public boolean used = false;
}
