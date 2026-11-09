package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {
    private final BookingRepo bookings;
    private final FeedbackRepo feedbacks;
    private final NotificationService notes;

    public FeedbackService(BookingRepo bookings, FeedbackRepo feedbacks, NotificationService notes) {
        this.bookings = bookings; this.feedbacks = feedbacks; this.notes = notes;
    }

    @Transactional
    public void submit(AppUser customer, Long bookingId, int rating, String comment) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!b.customer.id.equals(customer.id)) throw new SecurityException("Not your booking");
        if (b.status != BookingStatus.PAID || !b.ticketsIssued) throw new IllegalStateException("You can review an event after receiving your ticket.");
        if (feedbacks.existsByBooking(b)) throw new IllegalStateException("You already sent feedback for this booking.");
        if (rating < 1 || rating > 5) throw new IllegalArgumentException("Choose a rating from 1 to 5.");
        if (comment == null || comment.isBlank() || comment.length() > 1000) throw new IllegalArgumentException("Please write a short comment (max 1000 characters).");
        Feedback f = new Feedback();
        f.booking = b; f.event = b.event; f.customer = customer; f.rating = rating; f.comment = comment.trim();
        feedbacks.save(f);
        notes.notify(b.event.organizer, "New " + rating + "-star feedback for " + b.event.title + " from " + b.attendeeName, "/organizer#feedback");
    }
}
