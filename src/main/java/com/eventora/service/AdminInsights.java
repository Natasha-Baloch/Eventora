package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Money, budget and rating figures for the admin dashboard. Platform profit = commission % of every paid booking. */
@Service
public class AdminInsights {

    public static class Summary {
        public BigDecimal gross = BigDecimal.ZERO, profit = BigDecimal.ZERO, payouts = BigDecimal.ZERO, refunded = BigDecimal.ZERO,
                pending = BigDecimal.ZERO, potential = BigDecimal.ZERO, today = BigDecimal.ZERO, thisMonth = BigDecimal.ZERO;
        public int ticketsSold, checkedIn, capacity, fillPct, ratingCount, achievedPct;
        public double avgRating;
        public int[] ratingDist = new int[5];      // index 0 = 1 star ... index 4 = 5 stars
        public int[] ratingPct = new int[5];
    }

    public static class EventRow {
        public Event event; public int sold, capacity, fillPct, budgetPct, ratingCount; public double avgRating;
        public BigDecimal potential = BigDecimal.ZERO, revenue = BigDecimal.ZERO, fee = BigDecimal.ZERO, payout = BigDecimal.ZERO;
    }

    public static class OrgRow {
        public AppUser organizer; public int events, live, sold, sharePct;
        public BigDecimal revenue = BigDecimal.ZERO, fee = BigDecimal.ZERO, payout = BigDecimal.ZERO;
    }

    private final BookingRepo bookings; private final EventRepo events; private final UserRepo users;
    private final FeedbackRepo feedbacks; private final TicketRepo tickets;
    @Value("${eventora.commission-percent:10}") private int pct;

    public AdminInsights(BookingRepo bookings, EventRepo events, UserRepo users, FeedbackRepo feedbacks, TicketRepo tickets) {
        this.bookings = bookings; this.events = events; this.users = users; this.feedbacks = feedbacks; this.tickets = tickets;
    }

    public int getPct() { return pct; }

    private static BigDecimal n(BigDecimal x) { return x == null ? BigDecimal.ZERO : x; }
    private static boolean paid(Booking b) { return b.status == BookingStatus.PAID || b.status == BookingStatus.REFUND_REQUESTED; }
    private BigDecimal fee(BigDecimal revenue) { return revenue.multiply(BigDecimal.valueOf(pct)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP); }
    private static int percent(long part, long whole) { return whole <= 0 ? 0 : (int) Math.min(100, Math.round(part * 100.0 / whole)); }

    @Transactional(readOnly = true)
    public Summary summary() {
        Summary s = new Summary();
        LocalDate today = LocalDate.now();
        for (Booking b : bookings.findAllByOrderByCreatedAtDesc()) {
            BigDecimal t = n(b.total);
            if (paid(b)) {
                s.gross = s.gross.add(t); s.ticketsSold += b.quantity;
                if (b.createdAt != null && b.createdAt.toLocalDate().equals(today)) s.today = s.today.add(t);
                if (b.createdAt != null && YearMonth.from(b.createdAt).equals(YearMonth.from(today))) s.thisMonth = s.thisMonth.add(t);
            } else if (b.status == BookingStatus.REFUNDED) s.refunded = s.refunded.add(t);
            else if (b.status == BookingStatus.AWAITING_PAYMENT) s.pending = s.pending.add(t);
        }
        s.profit = fee(s.gross); s.payouts = s.gross.subtract(s.profit);
        for (Event e : events.findAllByOrderByCreatedAtDesc()) {
            if (e.status != EventStatus.PUBLISHED && e.status != EventStatus.APPROVED) continue;
            for (TicketCategory t : e.tiers) { s.potential = s.potential.add(n(t.price).multiply(BigDecimal.valueOf(t.capacity))); s.capacity += t.capacity; }
        }
        s.fillPct = percent(s.ticketsSold, Math.max(s.capacity, s.ticketsSold));
        s.achievedPct = s.potential.signum() <= 0 ? 0 : (int) Math.min(100, s.gross.multiply(BigDecimal.valueOf(100)).divide(s.potential, 0, RoundingMode.HALF_UP).longValue());
        s.checkedIn = (int) tickets.findAll().stream().filter(t -> t.checkedIn && !t.voided).count();
        int sum = 0;
        for (Feedback f : feedbacks.findAllByOrderByCreatedAtDesc()) {
            int r = Math.max(1, Math.min(5, f.rating)); s.ratingDist[r - 1]++; s.ratingCount++; sum += r;
        }
        s.avgRating = s.ratingCount == 0 ? 0 : Math.round(sum * 10.0 / s.ratingCount) / 10.0;
        for (int i = 0; i < 5; i++) s.ratingPct[i] = percent(s.ratingDist[i], s.ratingCount);
        return s;
    }

    @Transactional(readOnly = true)
    public List<EventRow> eventRows() {
        List<Booking> paidBookings = bookings.findAllByOrderByCreatedAtDesc().stream().filter(AdminInsights::paid).toList();
        Map<Long, int[]> rate = new HashMap<>();
        for (Feedback f : feedbacks.findAllByOrderByCreatedAtDesc()) { int[] a = rate.computeIfAbsent(f.event.id, k -> new int[2]); a[0] += f.rating; a[1]++; }
        List<EventRow> out = new ArrayList<>();
        for (Event e : events.findAllByOrderByCreatedAtDesc()) {
            EventRow r = new EventRow(); r.event = e;
            for (TicketCategory t : e.tiers) { r.capacity += t.capacity; r.potential = r.potential.add(n(t.price).multiply(BigDecimal.valueOf(t.capacity))); }
            for (Booking b : paidBookings) if (b.event.id.equals(e.id)) { r.sold += b.quantity; r.revenue = r.revenue.add(n(b.total)); }
            r.fee = fee(r.revenue); r.payout = r.revenue.subtract(r.fee);
            r.fillPct = percent(r.sold, r.capacity);
            r.budgetPct = r.potential.signum() <= 0 ? 0 : (int) Math.min(100, r.revenue.multiply(BigDecimal.valueOf(100)).divide(r.potential, 0, RoundingMode.HALF_UP).longValue());
            int[] a = rate.get(e.id);
            if (a != null) { r.ratingCount = a[1]; r.avgRating = Math.round(a[0] * 10.0 / a[1]) / 10.0; }
            out.add(r);
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<OrgRow> orgRows(List<EventRow> rows) {
        Map<Long, OrgRow> m = new LinkedHashMap<>();
        for (AppUser u : users.findAllByOrderByCreatedAtDesc()) if (u.role == Role.ORGANIZER) { OrgRow o = new OrgRow(); o.organizer = u; m.put(u.id, o); }
        for (EventRow r : rows) {
            OrgRow o = m.get(r.event.organizer.id);
            if (o == null) continue;
            o.events++; if (r.event.status == EventStatus.PUBLISHED) o.live++;
            o.sold += r.sold; o.revenue = o.revenue.add(r.revenue); o.fee = o.fee.add(r.fee); o.payout = o.payout.add(r.payout);
        }
        List<OrgRow> list = new ArrayList<>(m.values());
        BigDecimal total = list.stream().map(o -> o.revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (OrgRow o : list) o.sharePct = total.signum() <= 0 ? 0 : o.revenue.multiply(BigDecimal.valueOf(100)).divide(total, 0, RoundingMode.HALF_UP).intValue();
        list.sort((a, b) -> b.revenue.compareTo(a.revenue));
        return list;
    }

    /** Revenue (and platform profit) per calendar month for the last {@code months} months. */
    @Transactional(readOnly = true)
    public ReportService.Series monthlyRevenue(int months) {
        List<Booking> paidBookings = bookings.findAllByOrderByCreatedAtDesc().stream().filter(AdminInsights::paid).toList();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);
        List<String> labels = new ArrayList<>(); List<BigDecimal> vals = new ArrayList<>();
        YearMonth now = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth ym = now.minusMonths(i);
            labels.add(ym.format(f));
            vals.add(paidBookings.stream().filter(b -> b.createdAt != null && YearMonth.from(b.createdAt).equals(ym)).map(b -> n(b.total)).reduce(BigDecimal.ZERO, BigDecimal::add));
        }
        return new ReportService.Series(labels, vals);
    }
}
