package com.eventora.controller;

import com.eventora.model.*;
import com.eventora.repo.*;
import com.eventora.service.*;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/tickets")
public class TicketController {
    private final CurrentUser current;
    private final BookingRepo bookings;
    private final TicketRepo tickets;
    private final TicketService ticketService;

    public TicketController(CurrentUser current, BookingRepo bookings, TicketRepo tickets, TicketService ticketService) {
        this.current = current; this.bookings = bookings; this.tickets = tickets; this.ticketService = ticketService;
    }

    private boolean allowed(AppUser u, Booking b) {
        return u.role == Role.ADMIN || b.customer.id.equals(u.id);
    }

    @GetMapping("/{bookingId}/view")
    String view(@PathVariable(name = "bookingId") Long bookingId, Principal p, org.springframework.ui.Model m) {
        Booking b = bookings.findById(bookingId).orElse(null);
        if (b == null || !allowed(current.of(p), b) || b.status != BookingStatus.PAID || !b.ticketsIssued) return "redirect:/dashboard";
        m.addAttribute("booking", b);
        m.addAttribute("tickets", tickets.findByBooking(b).stream().filter(t -> !t.voided).toList());
        return "ticket";
    }

    @GetMapping("/{bookingId}/pdf")
    ResponseEntity<byte[]> pdf(@PathVariable(name = "bookingId") Long bookingId, Principal p) throws Exception {
        Booking b = bookings.findById(bookingId).orElse(null);
        if (b == null || !allowed(current.of(p), b) || b.status != BookingStatus.PAID || !b.ticketsIssued) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"eventora-" + b.reference + ".pdf\"")
            .contentType(MediaType.APPLICATION_PDF).body(ticketService.pdf(b));
    }

    @GetMapping("/qr/{code}.png")
    ResponseEntity<byte[]> qr(@PathVariable(name = "code") String code, Principal p) throws Exception {
        Ticket t = tickets.findByCode(code).orElse(null);
        if (t == null || !allowed(current.of(p), t.booking) || t.voided) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .cacheControl(CacheControl.noStore()).body(ticketService.qrPng(t.code, 320));
    }
}
