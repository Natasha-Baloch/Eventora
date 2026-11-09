package com.eventora.controller;

import com.eventora.model.*;
import com.eventora.repo.*;
import com.eventora.service.*;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/organizer")
public class OrganizerController {
    private static final Set<EventStatus> EDITABLE = EnumSet.of(EventStatus.DRAFT, EventStatus.CHANGES_REQUESTED, EventStatus.REJECTED);
    private final CurrentUser current;
    private final EventRepo events;
    private final BookingRepo bookings;
    private final TicketRepo tickets;
    private final UserRepo users;
    private final EventService eventService;
    private final BookingService bookingService;
    private final TicketService ticketService;
    private final ReportService reports;
    private final NotificationRepo notes;
    private final NotificationService notifier;
    private final PaymentRepo paymentRepo;
    private final FeedbackRepo feedbackRepo;

    public OrganizerController(CurrentUser current, EventRepo events, BookingRepo bookings, TicketRepo tickets, UserRepo users,
                               EventService eventService, BookingService bookingService, TicketService ticketService,
                               ReportService reports, NotificationRepo notes, NotificationService notifier,
                               PaymentRepo paymentRepo, FeedbackRepo feedbackRepo) {
        this.paymentRepo = paymentRepo; this.feedbackRepo = feedbackRepo;
        this.current = current; this.events = events; this.bookings = bookings; this.tickets = tickets; this.users = users;
        this.eventService = eventService; this.bookingService = bookingService; this.ticketService = ticketService;
        this.reports = reports; this.notes = notes; this.notifier = notifier;
    }

    @GetMapping
    String home(Principal p, Model m) {
        AppUser u = current.of(p);
        List<Booking> all = bookings.findByEventOrganizerOrderByCreatedAtDesc(u);
        List<ReportService.EventSales> sales = reports.organizerSales(u);
        m.addAttribute("sales", sales);
        m.addAttribute("requests", all.stream().filter(b -> b.status == BookingStatus.REQUESTED).toList());
        m.addAttribute("allBookings", all);
        m.addAttribute("toIssue", all.stream().filter(b -> b.status == BookingStatus.PAID && !b.ticketsIssued).toList());
        m.addAttribute("attendees", tickets.findByBookingEventOrganizerOrderByIdDesc(u).stream().filter(t -> !t.voided).toList());
        m.addAttribute("payments", paymentRepo.findByBookingEventOrganizerOrderByCreatedAtDesc(u));
        List<Feedback> fb = feedbackRepo.findByEventOrganizerOrderByCreatedAtDesc(u);
        m.addAttribute("feedbacks", fb);
        m.addAttribute("avgRating", fb.isEmpty() ? 0.0 : Math.round(fb.stream().mapToInt(f -> f.rating).average().orElse(0) * 10) / 10.0);
        m.addAttribute("daily", reports.dailyRevenue(all, 14));
        m.addAttribute("totalRevenue", reports.totalRevenue(all));
        m.addAttribute("ticketsSold", sales.stream().mapToInt(x -> x.sold).sum());
        m.addAttribute("notifications", notes.findTop50ByUserOrderByCreatedAtDesc(u));
        m.addAttribute("scanEvents", events.findByOrganizerOrderByCreatedAtDesc(u).stream().filter(e -> e.status == EventStatus.PUBLISHED).toList());
        return "organizer/dashboard";
    }

    // ---------- profile verification ----------
    @GetMapping("/profile")
    String profile(Principal p, Model m) { m.addAttribute("user", current.of(p)); return "organizer/profile"; }

    @PostMapping("/profile")
    String saveProfile(Principal p, @RequestParam(name = "fullName") String fullName, @RequestParam(name = "companyName") String companyName,
                       @RequestParam(name = "phone") String phone, @RequestParam(name = "orgBio") String orgBio, RedirectAttributes ra) {
        if (fullName.isBlank() || companyName.isBlank() || phone.isBlank() || orgBio.isBlank()
                || !phone.matches("[0-9+()\\-\\s]{7,20}") || fullName.length() > 100 || companyName.length() > 120 || orgBio.length() > 1000) {
            ra.addFlashAttribute("error", "Please fill in all fields with valid values.");
            return "redirect:/organizer/profile";
        }
        AppUser u = current.of(p);
        u.fullName = fullName.trim(); u.companyName = companyName.trim(); u.phone = phone.trim(); u.orgBio = orgBio.trim();
        if (u.organizerStatus == OrgStatus.PENDING_PROFILE || u.organizerStatus == OrgStatus.REJECTED || u.organizerStatus == OrgStatus.NONE) {
            u.organizerStatus = OrgStatus.PENDING_REVIEW;
            users.findAllByOrderByCreatedAtDesc().stream().filter(a -> a.role == Role.ADMIN)
                .forEach(a -> notifier.notify(a, "Organizer verification requested: " + u.companyName, "/admin#organizers"));
        }
        users.save(u);
        ra.addFlashAttribute("success", "Profile saved. An admin will review it shortly.");
        return "redirect:/organizer";
    }

    // ---------- events ----------
    private String guardVerified(AppUser u, RedirectAttributes ra) {
        if (u.organizerStatus != OrgStatus.VERIFIED) {
            ra.addFlashAttribute("error", "Your organizer profile must be verified by an admin before you can create events.");
            return "redirect:/organizer/profile";
        }
        return null;
    }

    @GetMapping("/events/new")
    String newEvent(Principal p, Model m, RedirectAttributes ra) {
        String g = guardVerified(current.of(p), ra); if (g != null) return g;
        EventForm f = new EventForm();
        f.tierName.add("General"); f.tierPrice.add(new BigDecimal("1000")); f.tierCapacity.add(100);
        m.addAttribute("form", f); m.addAttribute("categories", Category.values()); m.addAttribute("editing", false);
        return "organizer/event-form";
    }

    @PostMapping("/events")
    String create(Principal p, @Valid @ModelAttribute("form") EventForm form, BindingResult br,
                  @RequestParam(name = "image", required = false) MultipartFile image, @RequestParam(name = "action", defaultValue = "submit") String action,
                  Model m, RedirectAttributes ra) throws Exception {
        AppUser u = current.of(p);
        String g = guardVerified(u, ra); if (g != null) return g;
        List<String> errs = eventService.validate(form, image, true);
        if (br.hasErrors() || !errs.isEmpty()) return formError(m, form, errs, false, null);
        try { eventService.save(u, null, form, image, action); }
        catch (IllegalArgumentException ex) { return formError(m, form, List.of(ex.getMessage()), false, null); }
        ra.addFlashAttribute("success", "submit".equals(action) ? "Event submitted for admin approval." : "Draft saved.");
        return "redirect:/organizer#events";
    }

    @GetMapping("/events/{id}/edit")
    String edit(Principal p, @PathVariable(name = "id") Long id, Model m, RedirectAttributes ra) {
        Event e = events.findById(id).orElse(null);
        if (e == null || !e.organizer.id.equals(current.of(p).id) || !EDITABLE.contains(e.status)) {
            ra.addFlashAttribute("error", "This event can't be edited right now."); return "redirect:/organizer#events";
        }
        EventForm f = new EventForm();
        f.title = e.title; f.description = e.description; f.category = e.category; f.venue = e.venue; f.city = e.city;
        f.eventDate = e.eventDate; f.eventTime = e.eventTime; f.requiresApproval = e.requiresApproval;
        for (TicketCategory t : e.tiers) { f.tierName.add(t.name); f.tierPrice.add(t.price); f.tierCapacity.add(t.capacity); }
        m.addAttribute("form", f); m.addAttribute("categories", Category.values()); m.addAttribute("editing", true); m.addAttribute("event", e);
        return "organizer/event-form";
    }

    @PostMapping("/events/{id}")
    String update(Principal p, @PathVariable(name = "id") Long id, @Valid @ModelAttribute("form") EventForm form, BindingResult br,
                  @RequestParam(name = "image", required = false) MultipartFile image, @RequestParam(name = "action", defaultValue = "submit") String action,
                  Model m, RedirectAttributes ra) throws Exception {
        AppUser u = current.of(p);
        Event e = events.findById(id).orElse(null);
        if (e == null || !e.organizer.id.equals(u.id) || !EDITABLE.contains(e.status)) {
            ra.addFlashAttribute("error", "This event can't be edited right now."); return "redirect:/organizer#events";
        }
        List<String> errs = eventService.validate(form, image, e.imagePath == null);
        if (br.hasErrors() || !errs.isEmpty()) return formError(m, form, errs, true, e);
        try { eventService.save(u, e, form, image, action); }
        catch (IllegalArgumentException ex) { return formError(m, form, List.of(ex.getMessage()), true, e); }
        ra.addFlashAttribute("success", "submit".equals(action) ? "Event re-submitted for approval." : "Draft saved.");
        return "redirect:/organizer#events";
    }

    private String formError(Model m, EventForm f, List<String> errs, boolean editing, Event e) {
        m.addAttribute("categories", Category.values()); m.addAttribute("editing", editing); m.addAttribute("errors", errs);
        if (e != null) m.addAttribute("event", e);
        return "organizer/event-form";
    }

    @PostMapping("/events/{id}/reschedule")
    String reschedule(Principal p, @PathVariable(name = "id") Long id,
                      @RequestParam(name = "date") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date,
                      @RequestParam(name = "time") @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.TIME) java.time.LocalTime time,
                      RedirectAttributes ra) {
        try { eventService.reschedule(current.of(p), id, date, time); ra.addFlashAttribute("success", "Event rescheduled - booked customers were notified."); }
        catch (IllegalStateException | IllegalArgumentException | SecurityException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/organizer#events";
    }

    @PostMapping("/events/{id}/publish")
    String publish(Principal p, @PathVariable(name = "id") Long id, RedirectAttributes ra) {
        try { eventService.publish(current.of(p), id); ra.addFlashAttribute("success", "Event published - customers can now book it!"); }
        catch (IllegalStateException | SecurityException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/organizer#events";
    }

    // ---------- booking requests ----------
    @PostMapping("/bookings/{id}/{decision}")
    String decide(Principal p, @PathVariable(name = "id") Long id, @PathVariable(name = "decision") String decision,
                  @RequestParam(name = "note", required = false) String note, RedirectAttributes ra) {
        try {
            bookingService.organizerDecision(current.of(p), id, "approve".equals(decision), note);
            ra.addFlashAttribute("success", "approve".equals(decision) ? "Request approved - the customer can now pay." : "Request declined.");
        } catch (IllegalStateException | SecurityException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/organizer#requests";
    }

    // ---------- issue tickets ----------
    @PostMapping("/tickets/{id}/issue")
    String issue(Principal p, @PathVariable(name = "id") Long id, RedirectAttributes ra) {
        try { ticketService.issue(current.of(p), id); ra.addFlashAttribute("success", "Tickets issued and sent to the customer (account + email)."); }
        catch (IllegalStateException | IllegalArgumentException | SecurityException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        catch (Exception ex) { ra.addFlashAttribute("error", "Could not issue tickets: " + ex.getMessage()); }
        return "redirect:/organizer#issue";
    }

    // ---------- QR scanner ----------
    @PostMapping("/scan")
    @ResponseBody
    Map<String, Object> scan(Principal p, @RequestParam(name = "code") String code) {
        TicketService.CheckInResult r = ticketService.checkIn(current.of(p), code);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.ok()); out.put("message", r.message());
        out.put("holder", r.holder()); out.put("event", r.event()); out.put("tier", r.tier());
        return out;
    }
}
