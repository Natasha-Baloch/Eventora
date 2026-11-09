package com.eventora.controller;

import com.eventora.model.*;
import com.eventora.repo.*;
import com.eventora.service.*;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer")
public class CustomerController {
    private final CurrentUser current;
    private final BookingRepo bookings;
    private final TicketRepo tickets;
    private final PaymentRepo payments;
    private final NotificationRepo notes;
    private final ComplaintRepo complaints;
    private final BookingService bookingService;
    private final PaymentService paymentService;
    private final EventService eventService;
    private final FeedbackRepo feedbackRepo;
    private final FeedbackService feedbackService;

    public CustomerController(CurrentUser current, BookingRepo bookings, TicketRepo tickets, PaymentRepo payments,
                              NotificationRepo notes, ComplaintRepo complaints, BookingService bookingService, PaymentService paymentService, EventService eventService,
                              FeedbackRepo feedbackRepo, FeedbackService feedbackService) {
        this.eventService = eventService; this.feedbackRepo = feedbackRepo; this.feedbackService = feedbackService;
        this.current = current; this.bookings = bookings; this.tickets = tickets; this.payments = payments;
        this.notes = notes; this.complaints = complaints; this.bookingService = bookingService; this.paymentService = paymentService;
    }

    @GetMapping
    String home(Principal p, Model m,
                @RequestParam(name = "q", required = false) String q, @RequestParam(name = "category", required = false) Category category,
                @RequestParam(name = "city", required = false) String city,
                @RequestParam(name = "from", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate from,
                @RequestParam(name = "to", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate to,
                @RequestParam(name = "minPrice", required = false) java.math.BigDecimal minPrice, @RequestParam(name = "maxPrice", required = false) java.math.BigDecimal maxPrice) {
        AppUser u = current.of(p);
        List<Booking> all = bookings.findByCustomerOrderByCreatedAtDesc(u);
        List<Ticket> tk = tickets.findByBookingCustomerOrderByIdDesc(u).stream().filter(t -> !t.voided).toList();
        Map<Long, List<Ticket>> byBooking = tk.stream().collect(Collectors.groupingBy(t -> t.booking.id));
        m.addAttribute("discover", eventService.search(q, category, city, from, to, minPrice, maxPrice));
        m.addAttribute("categories", Category.values());
        m.addAttribute("q", q); m.addAttribute("cat", category); m.addAttribute("city", city);
        m.addAttribute("from", from); m.addAttribute("to", to); m.addAttribute("minPrice", minPrice); m.addAttribute("maxPrice", maxPrice);
        m.addAttribute("awaiting", all.stream().filter(b -> b.status == BookingStatus.PAID && !b.ticketsIssued).toList());
        List<Feedback> myFeedback = feedbackRepo.findByCustomerOrderByCreatedAtDesc(u);
        m.addAttribute("myFeedback", myFeedback);
        m.addAttribute("feedbackIds", myFeedback.stream().map(f -> f.booking.id).collect(Collectors.toSet()));
        m.addAttribute("bookings", all);
        m.addAttribute("ticketsByBooking", byBooking);
        m.addAttribute("paidBookings", all.stream().filter(b -> (b.status == BookingStatus.PAID || b.status == BookingStatus.REFUND_REQUESTED) && b.ticketsIssued).toList());
        m.addAttribute("upcoming", all.stream().filter(b -> b.status == BookingStatus.PAID && !b.event.isPast()).toList());
        m.addAttribute("payments", payments.findByBookingCustomerOrderByCreatedAtDesc(u));
        m.addAttribute("notifications", notes.findTop50ByUserOrderByCreatedAtDesc(u));
        m.addAttribute("complaints", complaints.findByUserOrderByCreatedAtDesc(u));
        return "customer/dashboard";
    }

    @PostMapping("/book")
    String book(Principal p, @RequestParam(name = "tierId") Long tierId, @RequestParam(name = "quantity") int quantity, @RequestParam(name = "attendeeName") String attendeeName,
               @RequestParam(name = "attendeePhone") String attendeePhone, RedirectAttributes ra) {
        try {
            Booking b = bookingService.request(current.of(p), tierId, quantity, attendeeName, attendeePhone);
            if (b.status == BookingStatus.AWAITING_PAYMENT) return "redirect:/customer/pay/" + b.id;
            ra.addFlashAttribute("success", "Request sent! The organizer will review it - you'll be notified.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer#bookings";
    }

    @GetMapping("/pay/{id}")
    String payPage(Principal p, @PathVariable(name = "id") Long id, Model m, RedirectAttributes ra) {
        AppUser u = current.of(p);
        Booking b = bookings.findById(id).orElse(null);
        if (b == null || !b.customer.id.equals(u.id) || b.status != BookingStatus.AWAITING_PAYMENT) {
            ra.addFlashAttribute("error", "That booking is not waiting for payment.");
            return "redirect:/customer#bookings";
        }
        m.addAttribute("booking", b);
        return "customer/pay";
    }

    @PostMapping("/pay/{id}")
    String pay(Principal p, @PathVariable(name = "id") Long id, @RequestParam Map<String, String> form, RedirectAttributes ra) {
        try {
            Payment pay = paymentService.pay(current.of(p), id, PayRequest.from(form));
            if (pay.status == PaymentStatus.SUCCESS) {
                ra.addFlashAttribute("success", "Payment successful! Your payment and registration details were sent to the organizer, who will issue your ticket.");
                return "redirect:/customer#bookings";
            }
            ra.addFlashAttribute("error", "Payment failed: " + pay.failureReason + ". You can try again before the window expires.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/customer/pay/" + id;
    }

    @PostMapping("/bookings/{id}/feedback")
    String feedback(Principal p, @PathVariable(name = "id") Long id, @RequestParam(name = "rating") int rating, @RequestParam(name = "comment") String comment, RedirectAttributes ra) {
        try { feedbackService.submit(current.of(p), id, rating, comment); ra.addFlashAttribute("success", "Thanks! Your feedback was sent to the organizer."); }
        catch (IllegalArgumentException | IllegalStateException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/customer#bookings";
    }

    @PostMapping("/bookings/{id}/cancel")
    String cancel(Principal p, @PathVariable(name = "id") Long id, RedirectAttributes ra) {
        try { bookingService.cancelByCustomer(current.of(p), id); ra.addFlashAttribute("success", "Booking cancelled."); }
        catch (IllegalStateException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/customer#bookings";
    }

    @PostMapping("/bookings/{id}/refund")
    String refund(Principal p, @PathVariable(name = "id") Long id, @RequestParam(name = "reason", required = false) String reason, RedirectAttributes ra) {
        try { bookingService.requestRefund(current.of(p), id, reason); ra.addFlashAttribute("success", "Refund request sent to admin."); }
        catch (IllegalStateException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/customer#bookings";
    }

    @PostMapping("/notifications/read")
    @Transactional
    String read(Principal p) { notes.markAllSeen(current.of(p)); return "redirect:/customer#notifications"; }

    @PostMapping("/complaints")
    String complain(Principal p, @RequestParam(name = "subject") String subject, @RequestParam(name = "message") String message, RedirectAttributes ra) {
        if (subject.isBlank() || message.isBlank() || subject.length() > 150 || message.length() > 2000) {
            ra.addFlashAttribute("error", "Please fill in the subject and message.");
        } else {
            Complaint c = new Complaint(); c.user = current.of(p); c.subject = subject.trim(); c.message = message.trim();
            complaints.save(c);
            ra.addFlashAttribute("success", "Your complaint was sent to the admin team.");
        }
        return "redirect:/customer#support";
    }
}
