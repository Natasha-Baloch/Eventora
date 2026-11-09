package com.eventora.controller;

import com.eventora.model.*;
import com.eventora.repo.*;
import com.eventora.service.EventService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class HomeController {
    private final EventService eventService;
    private final EventRepo events;
    private final UserRepo users;

    public HomeController(EventService eventService, EventRepo events, UserRepo users) {
        this.eventService = eventService; this.events = events; this.users = users;
    }

    @GetMapping("/")
    String home(Model m) {
        List<Event> all = eventService.search(null, null, null, null, null, null, null);
        m.addAttribute("featured", all.stream().limit(6).toList());
        m.addAttribute("eventCount", all.size());
        m.addAttribute("userCount", users.count());
        m.addAttribute("categories", Category.values());
        return "index";
    }

    @GetMapping("/events")
    String list(@RequestParam(name = "q", required = false) String q, @RequestParam(name = "category", required = false) Category category,
                @RequestParam(name = "city", required = false) String city,
                @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @RequestParam(name = "minPrice", required = false) BigDecimal minPrice, @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice, Model m) {
        m.addAttribute("events", eventService.search(q, category, city, from, to, minPrice, maxPrice));
        m.addAttribute("categories", Category.values());
        m.addAttribute("q", q); m.addAttribute("cat", category); m.addAttribute("city", city);
        m.addAttribute("from", from); m.addAttribute("to", to); m.addAttribute("minPrice", minPrice); m.addAttribute("maxPrice", maxPrice);
        return "events";
    }

    @GetMapping("/events/{id}")
    String detail(@PathVariable(name = "id") Long id, Principal p, Model m) {
        Event e = events.findById(id).orElse(null);
        if (e == null) return "redirect:/events";
        boolean preview = false;
        if (e.status != EventStatus.PUBLISHED) {
            AppUser u = p == null ? null : users.findByEmailIgnoreCase(p.getName()).orElse(null);
            preview = u != null && (u.role == Role.ADMIN || e.organizer.id.equals(u.id));
            if (!preview) return "redirect:/events";
        }
        m.addAttribute("event", e);
        m.addAttribute("preview", preview);
        return "event-detail";
    }
}
