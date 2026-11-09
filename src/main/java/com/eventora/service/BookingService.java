package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private final BookingRepo bookings;
    private final TicketCategoryRepo tiers;
    private final EventRepo events;
    private final PaymentRepo payments;
    private final TicketRepo tickets;
    private final NotificationService notes;
    private final UserRepo users;
    @Value("${eventora.payment-window-minutes}") private int payMinutes;
    @Value("${eventora.request-window-hours}") private int requestHours;

    public BookingService(BookingRepo bookings, TicketCategoryRepo tiers, EventRepo events, PaymentRepo payments,
                          TicketRepo tickets, NotificationService notes, UserRepo users) {
        this.users = users;
        this.bookings = bookings; this.tiers = tiers; this.events = events; this.payments = payments;
        this.tickets = tickets; this.notes = notes;
    }

    /** Reserves seats under a database row lock, so overselling is impossible even with concurrent buyers. */
    @Transactional
    public Booking request(AppUser customer, Long tierId, int qty, String attendeeName, String attendeePhone) {
        if (attendeeName == null || attendeeName.isBlank() || attendeeName.length() > 100)
            throw new IllegalArgumentException("Please enter the attendee name.");
        if (attendeePhone == null || !attendeePhone.matches("[0-9+()\\-\\s]{7,20}"))
            throw new IllegalArgumentException("Please enter a valid phone number.");
        if (qty < 1 || qty > 10) throw new IllegalArgumentException("You can book 1 to 10 tickets at a time.");
        TicketCategory t = tiers.lockById(tierId).orElseThrow(() -> new IllegalArgumentException("Ticket category not found."));
        Event e = t.event;
        if (e.status != EventStatus.PUBLISHED) throw new IllegalStateException("This event is not open for booking.");
        if (e.eventDate.isBefore(LocalDate.now())) throw new IllegalStateException("This event has already taken place.");
        if (t.getAvailable() < qty) throw new IllegalStateException("Only " + t.getAvailable() + " ticket(s) left in this category.");
        t.held += qty;

        Booking b = new Booking();
        b.reference = "EVB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        b.attendeeName = attendeeName.trim(); b.attendeePhone = attendeePhone.trim();
        b.customer = customer; b.event = e; b.tier = t; b.quantity = qty;
        b.total = t.price.multiply(BigDecimal.valueOf(qty));
        if (e.requiresApproval) {
            b.status = BookingStatus.REQUESTED;
            b.expiresAt = LocalDateTime.now().plusHours(requestHours);
        } else {
            b.status = BookingStatus.AWAITING_PAYMENT;
            b.expiresAt = LocalDateTime.now().plusMinutes(payMinutes);
        }
        b = bookings.save(b);
        if (e.requiresApproval) {
            notes.notify(e.organizer, customer.getDisplayName() + " requested " + qty + " x " + t.name + " for " + e.title, "/organizer#requests");
            notes.notify(customer, "Your request for " + e.title + " was sent to the organizer.", "/customer#bookings");
        }
        return b;
    }

    @Transactional
    public void organizerDecision(AppUser organizer, Long bookingId, boolean approve, String note) {
        Booking b = bookings.lockById(bookingId).orElseThrow();
        if (!b.event.organizer.id.equals(organizer.id)) throw new SecurityException("Not your event");
        if (b.status != BookingStatus.REQUESTED) throw new IllegalStateException("This request was already handled or expired.");
        b.organizerNote = note;
        if (approve) {
            b.status = BookingStatus.AWAITING_PAYMENT;
            b.expiresAt = LocalDateTime.now().plusMinutes(payMinutes);
            notes.notify(b.customer, "Approved! Pay within " + payMinutes + " minutes to confirm your tickets for " + b.event.title + ".", "/customer/pay/" + b.id);
        } else {
            release(b, BookingStatus.REJECTED);
            notes.notify(b.customer, "Your request for " + b.event.title + " was declined." + (note != null && !note.isBlank() ? " Note: " + note : ""), "/customer#bookings");
        }
    }

    @Transactional
    public void cancelByCustomer(AppUser customer, Long id) {
        Booking b = bookings.lockById(id).orElseThrow();
        if (!b.customer.id.equals(customer.id)) throw new SecurityException("Not your booking");
        if (b.status != BookingStatus.REQUESTED && b.status != BookingStatus.AWAITING_PAYMENT)
            throw new IllegalStateException("Only unpaid bookings can be cancelled. Paid bookings need a refund request.");
        release(b, BookingStatus.CANCELLED);
    }

    @Transactional
    public void requestRefund(AppUser customer, Long id, String reason) {
        Booking b = bookings.lockById(id).orElseThrow();
        if (!b.customer.id.equals(customer.id)) throw new SecurityException("Not your booking");
        if (b.status != BookingStatus.PAID) throw new IllegalStateException("Only paid bookings can be refunded.");
        if (b.event.isPast()) throw new IllegalStateException("This event has already taken place.");
        if (tickets.findByBooking(b).stream().anyMatch(t -> t.checkedIn)) throw new IllegalStateException("Tickets were already used.");
        b.status = BookingStatus.REFUND_REQUESTED;
        b.refundReason = reason == null ? null : reason.trim();
        for (AppUser a : adminsOf()) notes.notify(a, "Refund requested for booking " + b.reference, "/admin#refunds");
    }

    @Transactional
    public void refundDecision(Long id, boolean approve) {
        Booking b = bookings.lockById(id).orElseThrow();
        if (b.status != BookingStatus.REFUND_REQUESTED) throw new IllegalStateException("No pending refund for this booking.");
        if (approve) {
            release(b, BookingStatus.REFUNDED);
            tickets.findByBooking(b).forEach(t -> t.voided = true);
            payments.findByBooking(b).stream().filter(p -> p.status == PaymentStatus.SUCCESS).forEach(p -> p.status = PaymentStatus.REFUNDED);
            notes.notify(b.customer, "Your refund of " + b.total + " for " + b.event.title + " was approved.", "/customer#payments");
        } else {
            b.status = BookingStatus.PAID;
            notes.notify(b.customer, "Your refund request for " + b.event.title + " was declined.", "/customer#bookings");
        }
    }

    /** Frees held seats exactly once, then sets the final status. */
    void release(Booking b, BookingStatus finalStatus) {
        TicketCategory t = tiers.lockById(b.tier.id).orElseThrow();
        t.held = Math.max(0, t.held - b.quantity);
        b.status = finalStatus;
        b.expiresAt = null;
    }

    /** Unpaid reservations (and unanswered requests) expire automatically. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStale() {
        List<Booking> stale = bookings.findByStatusInAndExpiresAtBefore(
                EnumSet.of(BookingStatus.REQUESTED, BookingStatus.AWAITING_PAYMENT), LocalDateTime.now());
        for (Booking s : stale) {
            Booking b = bookings.lockById(s.id).orElse(null);
            if (b == null || (b.status != BookingStatus.REQUESTED && b.status != BookingStatus.AWAITING_PAYMENT)) continue;
            release(b, BookingStatus.EXPIRED);
            notes.notify(b.customer, "Your reservation for " + b.event.title + " expired and the tickets were released.", "/customer#bookings");
            log.info("Expired booking {}", b.reference);
        }
    }

    private List<AppUser> adminsOf() {
        return users.findAllByOrderByCreatedAtDesc().stream().filter(u -> u.role == Role.ADMIN).toList();
    }
}
