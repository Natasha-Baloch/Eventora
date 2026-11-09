package com.eventora.controller;

import com.eventora.model.*;
import com.eventora.repo.*;
import com.eventora.service.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private final CurrentUser current;
    private final UserRepo users;
    private final EventRepo events;
    private final BookingRepo bookings;
    private final PaymentRepo payments;
    private final ComplaintRepo complaints;
    private final EventService eventService;
    private final BookingService bookingService;
    private final ReportService reports;
    private final NotificationService notifier;
    private final FeedbackRepo feedbackRepo;
    private final NotificationRepo notificationRepo;
    private final AdminInsights insights;

    public AdminController(CurrentUser current, UserRepo users, EventRepo events, BookingRepo bookings, PaymentRepo payments,
                           ComplaintRepo complaints, EventService eventService, BookingService bookingService,
                           ReportService reports, NotificationService notifier,
                           FeedbackRepo feedbackRepo, NotificationRepo notificationRepo, AdminInsights insights) {
        this.insights = insights;
        this.feedbackRepo = feedbackRepo; this.notificationRepo = notificationRepo;
        this.current = current; this.users = users; this.events = events; this.bookings = bookings; this.payments = payments;
        this.complaints = complaints; this.eventService = eventService; this.bookingService = bookingService;
        this.reports = reports; this.notifier = notifier;
    }

    @GetMapping
    String home(Principal p, Model m) {
        m.addAttribute("feedbacks", feedbackRepo.findAllByOrderByCreatedAtDesc());
        m.addAttribute("notifications", notificationRepo.findTop50ByUserOrderByCreatedAtDesc(current.of(p)));
        List<Booking> allBookings = bookings.findAllByOrderByCreatedAtDesc();
        List<AppUser> all = users.findAllByOrderByCreatedAtDesc();
        m.addAttribute("pendingEvents", events.findAllByOrderByCreatedAtDesc().stream().filter(e -> e.status == EventStatus.PENDING_APPROVAL).toList());
        m.addAttribute("events", events.findAllByOrderByCreatedAtDesc());
        m.addAttribute("users", all);
        m.addAttribute("organizers", all.stream().filter(u -> u.role == Role.ORGANIZER).toList());
        m.addAttribute("bookings", allBookings);
        m.addAttribute("refunds", allBookings.stream().filter(b -> b.status == BookingStatus.REFUND_REQUESTED).toList());
        m.addAttribute("payments", payments.findAllByOrderByCreatedAtDesc());
        m.addAttribute("complaints", complaints.findAllByOrderByCreatedAtDesc());
        m.addAttribute("totalRevenue", reports.totalRevenue(allBookings));
        m.addAttribute("daily", reports.dailyRevenue(allBookings, 14));
        m.addAttribute("byCategory", reports.revenueByCategory());
        m.addAttribute("byStatus", reports.bookingsByStatus());
        m.addAttribute("byRole", reports.usersByRole());

        // finance / budget / approvals / issues
        AdminInsights.Summary sum = insights.summary();
        List<AdminInsights.EventRow> rows = insights.eventRows();
        m.addAttribute("sum", sum);
        m.addAttribute("pct", insights.getPct());
        m.addAttribute("eventRows", rows);
        m.addAttribute("orgRows", insights.orgRows(rows));
        m.addAttribute("monthly", insights.monthlyRevenue(6));
        m.addAttribute("topEvents", rows.stream().filter(r -> r.revenue.signum() > 0).sorted((a, b) -> b.revenue.compareTo(a.revenue)).limit(5).toList());
        m.addAttribute("pendingOrganizers", all.stream().filter(u -> u.role == Role.ORGANIZER && u.organizerStatus == OrgStatus.PENDING_REVIEW).toList());
        m.addAttribute("recentBookings", allBookings.stream().limit(8).toList());
        m.addAttribute("openComplaints", complaints.findAllByOrderByCreatedAtDesc().stream().filter(c -> !c.resolved).count());
        m.addAttribute("liveEvents", events.countByStatus(EventStatus.PUBLISHED));
        return "admin/dashboard";
    }

    // ----- CSV reports (open in Excel) -----
    @GetMapping("/export/{what}.csv")
    void export(@PathVariable(name = "what") String what, HttpServletResponse res) throws IOException {
        res.setContentType("text/csv; charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=eventora-" + what.replaceAll("[^a-z]", "") + ".csv");
        PrintWriter w = res.getWriter();
        w.write('\uFEFF');
        switch (what) {
            case "bookings" -> {
                w.println("Reference,Customer,Event,Tickets,Total,Status,Date");
                for (Booking b : bookings.findAllByOrderByCreatedAtDesc())
                    w.println(csv(b.reference, b.customer.email, b.event.title, b.quantity, b.total, b.status, b.createdAt));
            }
            case "users" -> {
                w.println("Name,Email,Role,Organizer status,Suspended,Joined");
                for (AppUser u : users.findAllByOrderByCreatedAtDesc())
                    w.println(csv(u.getDisplayName(), u.email, u.role, u.organizerStatus, u.suspended, u.createdAt));
            }
            case "events" -> {
                w.println("Event,Organizer,Category,City,Date,Status,Tickets sold,Capacity,Revenue,Platform profit,Organizer payout,Avg rating");
                for (AdminInsights.EventRow r : insights.eventRows())
                    w.println(csv(r.event.title, r.event.organizer.companyName != null ? r.event.organizer.companyName : r.event.organizer.email, r.event.category.getLabel(),
                            r.event.city, r.event.eventDate, r.event.status, r.sold, r.capacity, r.revenue, r.fee, r.payout, r.avgRating));
            }
            case "payments" -> {
                w.println("Date,Booking,Customer,Gateway ref,Amount,Status");
                for (Payment p : payments.findAllByOrderByCreatedAtDesc())
                    w.println(csv(p.createdAt, p.booking.reference, p.booking.customer.email, p.gatewayRef, p.amount, p.status));
            }
            case "organizers" -> {
                w.println("Organizer,Company,Events,Tickets sold,Revenue,Platform profit,Payout");
                for (AdminInsights.OrgRow o : insights.orgRows(insights.eventRows()))
                    w.println(csv(o.organizer.getDisplayName(), o.organizer.companyName, o.events, o.sold, o.revenue, o.fee, o.payout));
            }
            case "feedback" -> {
                w.println("Event,Customer,Rating,Comment,Date");
                for (Feedback f : feedbackRepo.findAllByOrderByCreatedAtDesc())
                    w.println(csv(f.event.title, f.customer.email, f.rating, f.comment, f.createdAt));
            }
            case "complaints" -> {
                w.println("User,Subject,Message,Resolved,Response,Date");
                for (Complaint c : complaints.findAllByOrderByCreatedAtDesc())
                    w.println(csv(c.user.email, c.subject, c.message, c.resolved, c.adminResponse, c.createdAt));
            }
            default -> w.println("Unknown report");
        }
        w.flush();
    }

    private static String csv(Object... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            String v = cells[i] == null ? "" : String.valueOf(cells[i]);
            if (v.startsWith("=") || v.startsWith("+") || v.startsWith("@") || v.startsWith("-") && !v.matches("-?[0-9.]+")) v = "'" + v;
            sb.append('"').append(v.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ")).append('"');
        }
        return sb.toString();
    }

    // ----- events -----
    @PostMapping("/events/{id}/{decision}")
    String eventDecision(@PathVariable(name = "id") Long id, @PathVariable(name = "decision") String decision, @RequestParam(name = "note", required = false) String note,
                         RedirectAttributes ra, @RequestHeader(value = "Referer", required = false) String ref) {
        try {
            if (("reject".equals(decision) || "changes".equals(decision)) && (note == null || note.isBlank())) {
                ra.addFlashAttribute("error", "Please write a note for the organizer.");
            } else {
                eventService.adminDecision(id, decision, note);
                ra.addFlashAttribute("success", "Event updated: " + decision);
            }
        } catch (IllegalArgumentException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin#" + ("suspend".equals(decision) || "restore".equals(decision) ? "events" : "approvals");
    }

    // ----- organizers -----
    @PostMapping("/organizers/{id}/{decision}")
    @Transactional
    String organizerDecision(@PathVariable(name = "id") Long id, @PathVariable(name = "decision") String decision, @RequestParam(name = "note", required = false) String note, RedirectAttributes ra) {
        AppUser o = users.findById(id).orElseThrow();
        if (o.role != Role.ORGANIZER) { ra.addFlashAttribute("error", "Not an organizer."); return "redirect:/admin#organizers"; }
        switch (decision) {
            case "verify" -> { o.organizerStatus = OrgStatus.VERIFIED; o.suspended = false; notifier.notify(o, "Your organizer profile is verified. You can now create events!", "/organizer"); }
            case "reject" -> { o.organizerStatus = OrgStatus.REJECTED; o.adminNote = note; notifier.notify(o, "Your organizer profile was not approved." + (note == null || note.isBlank() ? "" : " Reason: " + note), "/organizer/profile"); }
            case "suspend" -> {
                o.organizerStatus = OrgStatus.SUSPENDED; o.suspended = true; o.adminNote = note;
                events.findAllByOrderByCreatedAtDesc().stream().filter(e -> e.organizer.id.equals(o.id) && e.status == EventStatus.PUBLISHED)
                    .forEach(e -> e.status = EventStatus.SUSPENDED);
            }
            default -> { ra.addFlashAttribute("error", "Unknown action"); return "redirect:/admin#organizers"; }
        }
        ra.addFlashAttribute("success", "Organizer " + decision + " done.");
        return "redirect:/admin#organizers";
    }

    // ----- users -----
    @PostMapping("/users/{id}/{action}")
    String userAction(Principal p, @PathVariable(name = "id") Long id, @PathVariable(name = "action") String action, RedirectAttributes ra) {
        AppUser me = current.of(p);
        AppUser u = users.findById(id).orElseThrow();
        if (u.id.equals(me.id)) { ra.addFlashAttribute("error", "You can't change your own account."); return "redirect:/admin#users"; }
        u.suspended = "suspend".equals(action);
        users.save(u);
        ra.addFlashAttribute("success", u.email + (u.suspended ? " suspended." : " re-activated."));
        return "redirect:/admin#users";
    }

    /** Admin accounts can only be created by an existing admin (never via public sign-up). */
    @PostMapping("/users/admin")
    String addAdmin(@RequestParam(name = "email") String email, RedirectAttributes ra) {
        email = email.trim().toLowerCase();
        if (!EMAIL.matcher(email).matches()) { ra.addFlashAttribute("error", "Invalid email."); return "redirect:/admin#users"; }
        AppUser u = users.findByEmailIgnoreCase(email).orElseGet(AppUser::new);
        u.email = email; u.role = Role.ADMIN; u.organizerStatus = OrgStatus.NONE;
        if (u.fullName == null) u.fullName = "Admin";
        users.save(u);
        ra.addFlashAttribute("success", email + " is now an admin (they sign in with an email code).");
        return "redirect:/admin#users";
    }

    @PostMapping("/notifications/read")
    @Transactional
    String readNotes(Principal p) { notificationRepo.markAllSeen(current.of(p)); return "redirect:/admin#notifications"; }

    // ----- refunds / complaints -----
    @PostMapping("/refunds/{id}/{decision}")
    String refund(@PathVariable(name = "id") Long id, @PathVariable(name = "decision") String decision, RedirectAttributes ra) {
        try { bookingService.refundDecision(id, "approve".equals(decision)); ra.addFlashAttribute("success", "Refund " + decision + "d."); }
        catch (IllegalStateException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin#refunds";
    }

    @PostMapping("/complaints/{id}/resolve")
    String resolve(@PathVariable(name = "id") Long id, @RequestParam(name = "response") String response, RedirectAttributes ra) {
        Complaint c = complaints.findById(id).orElseThrow();
        c.resolved = true; c.adminResponse = response.trim();
        complaints.save(c);
        notifier.notify(c.user, "Your complaint \"" + c.subject + "\" was answered: " + c.adminResponse, "/customer#support");
        ra.addFlashAttribute("success", "Complaint resolved.");
        return "redirect:/admin#complaints";
    }
}
