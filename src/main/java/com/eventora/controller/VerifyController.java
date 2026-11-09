package com.eventora.controller;

import com.eventora.model.*;
import com.eventora.repo.TicketRepo;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/** Public ticket verification: reports validity without consuming the ticket. */
@Controller
public class VerifyController {
    private final TicketRepo tickets;
    public VerifyController(TicketRepo tickets) { this.tickets = tickets; }

    @GetMapping({"/verify", "/verify/{path}"})
    String verify(@RequestParam(name = "code", required = false) String code, @PathVariable(name = "path", required = false) String path, Model m) {
        String c = code != null ? code : path;
        if (c == null || c.isBlank()) return "verify";
        c = c.trim().toUpperCase();
        m.addAttribute("code", c);
        Ticket t = c.length() > 80 ? null : tickets.findByCode(c).orElse(null);
        if (t == null) { m.addAttribute("result", "INVALID"); return "verify"; }
        Booking b = t.booking;
        String result = (t.voided || b.status != BookingStatus.PAID) ? "CANCELLED" : (t.checkedIn ? "USED" : "VALID");
        String name = b.attendeeName == null ? "Guest" : b.attendeeName.trim().split("\\s+")[0];
        m.addAttribute("result", result); m.addAttribute("ticket", t); m.addAttribute("firstName", name);
        return "verify";
    }
}
