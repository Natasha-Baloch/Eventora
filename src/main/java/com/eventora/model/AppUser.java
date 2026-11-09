package com.eventora.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true, nullable = false)
    public String email;
    public String fullName;
    /** BCrypt hash - only organizers and event managers (admins) sign in with a password. */
    public String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public Role role = Role.CUSTOMER;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public OrgStatus organizerStatus = OrgStatus.NONE;
    public boolean suspended = false;
    public String companyName;
    public String phone;
    @Column(length = 1000)
    public String orgBio;
    public String adminNote;
    public LocalDateTime createdAt = LocalDateTime.now();
    public LocalDateTime lastLoginAt;

    public String getDisplayName() {
        return (fullName != null && !fullName.isBlank()) ? fullName : email.substring(0, email.indexOf('@'));
    }
}
