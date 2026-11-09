package com.eventora.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "events")
public class Event {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne(optional = false)
    public AppUser organizer;
    @Column(nullable = false)
    public String title;
    @Column(columnDefinition = "TEXT")
    public String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public Category category = Category.CONCERT;
    public String venue;
    public String city;
    public LocalDate eventDate;
    public LocalTime eventTime;
    public String imagePath;
    public boolean requiresApproval = true;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public EventStatus status = EventStatus.DRAFT;
    @Column(length = 1000)
    public String adminNote;
    public LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("price ASC")
    public List<TicketCategory> tiers = new ArrayList<>();

    public BigDecimal getMinPrice() {
        return tiers.stream().map(t -> t.price).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
    }
    public int getAvailable() { return tiers.stream().mapToInt(TicketCategory::getAvailable).sum(); }
    public int getCapacity() { return tiers.stream().mapToInt(t -> t.capacity).sum(); }
    public boolean isPast() { return eventDate != null && eventDate.isBefore(LocalDate.now()); }
}
