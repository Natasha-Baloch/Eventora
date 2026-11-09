package com.eventora.service;

import com.eventora.model.Category;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;

public class EventForm {
    @NotBlank(message = "Title is required") @Size(max = 150, message = "Title is too long")
    public String title;
    @NotBlank(message = "Description is required") @Size(max = 5000)
    public String description;
    @NotNull(message = "Choose a category")
    public Category category;
    @NotBlank(message = "Venue is required") @Size(max = 150)
    public String venue;
    @NotBlank(message = "City is required") @Size(max = 100)
    public String city;
    @NotNull(message = "Date is required") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    public LocalDate eventDate;
    @NotNull(message = "Time is required") @DateTimeFormat(iso = DateTimeFormat.ISO.TIME)
    public LocalTime eventTime;
    public boolean requiresApproval = true;
    public List<String> tierName = new ArrayList<>();
    public List<BigDecimal> tierPrice = new ArrayList<>();
    public List<Integer> tierCapacity = new ArrayList<>();

    // Thymeleaf binding needs getters/setters for th:field
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public Category getCategory() { return category; } public void setCategory(Category v) { category = v; }
    public String getVenue() { return venue; } public void setVenue(String v) { venue = v; }
    public String getCity() { return city; } public void setCity(String v) { city = v; }
    public LocalDate getEventDate() { return eventDate; } public void setEventDate(LocalDate v) { eventDate = v; }
    public LocalTime getEventTime() { return eventTime; } public void setEventTime(LocalTime v) { eventTime = v; }
    public boolean isRequiresApproval() { return requiresApproval; } public void setRequiresApproval(boolean v) { requiresApproval = v; }
    public List<String> getTierName() { return tierName; } public void setTierName(List<String> v) { tierName = v; }
    public List<BigDecimal> getTierPrice() { return tierPrice; } public void setTierPrice(List<BigDecimal> v) { tierPrice = v; }
    public List<Integer> getTierCapacity() { return tierCapacity; } public void setTierCapacity(List<Integer> v) { tierCapacity = v; }
}
