package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    public static class Series {
        public final List<String> labels; public final List<BigDecimal> values;
        public Series(List<String> labels, List<BigDecimal> values) { this.labels = labels; this.values = values; }
    }
    public static class EventSales {
        public final Event event; public final int sold; public final int checkedIn; public final BigDecimal revenue;
        public EventSales(Event event, int sold, int checkedIn, BigDecimal revenue) {
            this.event = event; this.sold = sold; this.checkedIn = checkedIn; this.revenue = revenue; }
    }

    private final BookingRepo bookings;
    private final TicketRepo tickets;
    private final UserRepo users;
    private final EventRepo events;

    public ReportService(BookingRepo bookings, TicketRepo tickets, UserRepo users, EventRepo events) {
        this.bookings = bookings; this.tickets = tickets; this.users = users; this.events = events;
    }

    private static boolean paid(Booking b) { return b.status == BookingStatus.PAID || b.status == BookingStatus.REFUND_REQUESTED; }

    @Transactional(readOnly = true)
    public List<EventSales> organizerSales(AppUser org) {
        List<Booking> all = bookings.findByEventOrganizerOrderByCreatedAtDesc(org).stream().filter(ReportService::paid).toList();
        Map<Long, List<Ticket>> tk = tickets.findByBookingEventOrganizerOrderByIdDesc(org).stream().filter(t -> !t.voided)
                .collect(Collectors.groupingBy(t -> t.booking.event.id));
        List<EventSales> out = new ArrayList<>();
        for (Event e : events.findByOrganizerOrderByCreatedAtDesc(org)) {
            int sold = all.stream().filter(b -> b.event.id.equals(e.id)).mapToInt(b -> b.quantity).sum();
            BigDecimal rev = all.stream().filter(b -> b.event.id.equals(e.id)).map(b -> b.total).reduce(BigDecimal.ZERO, BigDecimal::add);
            int in = (int) tk.getOrDefault(e.id, List.of()).stream().filter(t -> t.checkedIn).count();
            out.add(new EventSales(e, sold, in, rev));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public Series dailyRevenue(List<Booking> source, int days) {
        LocalDate today = LocalDate.now();
        List<String> labels = new ArrayList<>(); List<BigDecimal> vals = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            labels.add(d.getDayOfMonth() + "/" + d.getMonthValue());
            vals.add(source.stream().filter(b -> paid(b) && b.createdAt.toLocalDate().equals(d)).map(b -> b.total).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return new Series(labels, vals);
    }

    @Transactional(readOnly = true)
    public Series revenueByCategory() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (Category c : Category.values()) m.put(c.getLabel(), BigDecimal.ZERO);
        bookings.findAllByOrderByCreatedAtDesc().stream().filter(ReportService::paid)
                .forEach(b -> m.merge(b.event.category.getLabel(), b.total, BigDecimal::add));
        return new Series(new ArrayList<>(m.keySet()), new ArrayList<>(m.values()));
    }

    @Transactional(readOnly = true)
    public Series bookingsByStatus() {
        List<String> l = new ArrayList<>(); List<BigDecimal> v = new ArrayList<>();
        for (BookingStatus s : BookingStatus.values()) { l.add(s.name().replace('_', ' ')); v.add(BigDecimal.valueOf(bookings.countByStatus(s))); }
        return new Series(l, v);
    }

    @Transactional(readOnly = true)
    public Series usersByRole() {
        List<String> l = new ArrayList<>(); List<BigDecimal> v = new ArrayList<>();
        for (Role r : Role.values()) { l.add(r.name()); v.add(BigDecimal.valueOf(users.countByRole(r))); }
        return new Series(l, v);
    }

    public BigDecimal totalRevenue(List<Booking> source) {
        return source.stream().filter(ReportService::paid).map(b -> b.total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
