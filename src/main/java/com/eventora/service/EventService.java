package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.EventRepo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EventService {
    private final EventRepo events;
    private final ImageStorage images;
    private final NotificationService notes;
    private final com.eventora.repo.UserRepo users;
    private final com.eventora.repo.BookingRepo bookings;

    public EventService(EventRepo events, ImageStorage images, NotificationService notes, com.eventora.repo.UserRepo users, com.eventora.repo.BookingRepo bookings) {
        this.bookings = bookings;
        this.events = events; this.images = images; this.notes = notes; this.users = users;
    }

    @Transactional(readOnly = true)
    public List<Event> search(String q, Category cat, String city, LocalDate from, LocalDate to, BigDecimal minP, BigDecimal maxP) {
        return events.findByStatusAndEventDateGreaterThanEqualOrderByEventDateAsc(EventStatus.PUBLISHED, LocalDate.now()).stream()
            .filter(e -> cat == null || e.category == cat)
            .filter(e -> q == null || q.isBlank() || e.title.toLowerCase().contains(q.toLowerCase()) || e.venue.toLowerCase().contains(q.toLowerCase()))
            .filter(e -> city == null || city.isBlank() || e.city.toLowerCase().contains(city.toLowerCase()))
            .filter(e -> from == null || !e.eventDate.isBefore(from))
            .filter(e -> to == null || !e.eventDate.isAfter(to))
            .filter(e -> minP == null || e.tiers.stream().anyMatch(t -> t.price.compareTo(minP) >= 0))
            .filter(e -> maxP == null || e.getMinPrice().compareTo(maxP) <= 0)
            .collect(Collectors.toList());
    }

    public List<String> validate(EventForm f, MultipartFile image, boolean imageRequired) {
        List<String> errs = new ArrayList<>();
        if (f.eventDate != null && f.eventDate.isBefore(LocalDate.now())) errs.add("Event date must be today or later.");
        int n = f.tierName.size();
        if (n == 0 || f.tierPrice.size() != n || f.tierCapacity.size() != n) errs.add("Add at least one ticket category.");
        else for (int i = 0; i < n; i++) {
            String name = f.tierName.get(i);
            BigDecimal p = f.tierPrice.get(i);
            Integer c = f.tierCapacity.get(i);
            if (name == null || name.isBlank()) errs.add("Ticket category " + (i + 1) + ": name required.");
            if (p == null || p.signum() < 0 || p.compareTo(new BigDecimal("10000000")) > 0) errs.add("Ticket category " + (i + 1) + ": invalid price.");
            if (c == null || c < 1 || c > 1_000_000) errs.add("Ticket category " + (i + 1) + ": capacity must be 1 or more.");
        }
        boolean hasImage = image != null && !image.isEmpty();
        if (imageRequired && !hasImage) errs.add("Please upload an event image (JPG or PNG).");
        return errs;
    }

    /** Creates or updates an event. action = "draft" | "submit" */
    @Transactional
    public Event save(AppUser organizer, Event existing, EventForm f, MultipartFile image, String action) throws Exception {
        Event e = existing != null ? existing : new Event();
        if (existing == null) { e.organizer = organizer; }
        e.title = f.title.trim(); e.description = f.description.trim(); e.category = f.category;
        e.venue = f.venue.trim(); e.city = f.city.trim(); e.eventDate = f.eventDate; e.eventTime = f.eventTime;
        e.requiresApproval = f.requiresApproval;
        if (image != null && !image.isEmpty()) e.imagePath = images.store(image);
        e.tiers.clear();
        for (int i = 0; i < f.tierName.size(); i++) {
            TicketCategory t = new TicketCategory();
            t.event = e; t.name = f.tierName.get(i).trim(); t.price = f.tierPrice.get(i); t.capacity = f.tierCapacity.get(i);
            e.tiers.add(t);
        }
        boolean submit = "submit".equals(action);
        e.status = submit ? EventStatus.PENDING_APPROVAL : EventStatus.DRAFT;
        e.adminNote = submit ? null : e.adminNote;
        e = events.save(e);
        if (submit) {
            for (AppUser a : users.findAllByOrderByCreatedAtDesc()) if (a.role == Role.ADMIN)
                notes.notify(a, "New event awaiting approval: " + e.title, "/admin#approvals");
        }
        return e;
    }

    @Transactional
    public void adminDecision(Long id, String decision, String note) {
        Event e = events.findById(id).orElseThrow();
        switch (decision) {
            case "approve" -> e.status = EventStatus.PUBLISHED;
            case "reject" -> e.status = EventStatus.REJECTED;
            case "changes" -> e.status = EventStatus.CHANGES_REQUESTED;
            case "suspend" -> e.status = EventStatus.SUSPENDED;
            case "restore" -> e.status = EventStatus.PUBLISHED;
            default -> throw new IllegalArgumentException("Unknown action");
        }
        e.adminNote = (note == null || note.isBlank()) ? null : note.trim();
        String msg = switch (decision) {
            case "approve" -> "Your event \"" + e.title + "\" was approved and is now live for customers.";
            case "reject" -> "Your event \"" + e.title + "\" was rejected." + (e.adminNote != null ? " Reason: " + e.adminNote : "");
            case "changes" -> "Changes requested for \"" + e.title + "\": " + (e.adminNote == null ? "see admin note" : e.adminNote);
            case "suspend" -> "Your event \"" + e.title + "\" was suspended by an admin.";
            default -> "Your event \"" + e.title + "\" was restored.";
        };
        notes.notify(e.organizer, msg, "/organizer#events");
    }

    /** Organizer changes the date/time of a live event; everyone with an active booking is notified. */
    @Transactional
    public void reschedule(AppUser organizer, Long id, LocalDate date, java.time.LocalTime time) {
        Event e = events.findById(id).orElseThrow();
        if (!e.organizer.id.equals(organizer.id)) throw new SecurityException("Not your event");
        if (e.status != EventStatus.PUBLISHED && e.status != EventStatus.APPROVED) throw new IllegalStateException("Only live events can be rescheduled.");
        if (date == null || time == null || date.isBefore(LocalDate.now())) throw new IllegalArgumentException("Choose a valid future date and time.");
        e.eventDate = date; e.eventTime = time;
        events.save(e);
        java.util.Set<Long> done = new java.util.HashSet<>();
        for (Booking b : bookings.findByEventOrganizerOrderByCreatedAtDesc(organizer)) {
            if (!b.event.id.equals(id) || !done.add(b.customer.id)) continue;
            if (b.status == BookingStatus.PAID || b.status == BookingStatus.AWAITING_PAYMENT || b.status == BookingStatus.REQUESTED)
                notes.notify(b.customer, "\"" + e.title + "\" has been rescheduled to " + date + " at " + time + ".", "/customer#bookings");
        }
    }

    @Transactional
    public void publish(AppUser organizer, Long id) {
        Event e = events.findById(id).orElseThrow();
        if (!e.organizer.id.equals(organizer.id)) throw new SecurityException("Not your event");
        if (e.status != EventStatus.APPROVED) throw new IllegalStateException("Only approved events can be published.");
        e.status = EventStatus.PUBLISHED;
    }
}
