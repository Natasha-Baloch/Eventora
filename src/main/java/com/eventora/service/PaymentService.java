package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.*;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    private final BookingRepo bookings;
    private final PaymentRepo payments;
    private final PaymentGateway gateway;
    private final NotificationService notes;
    private final UserRepo users;

    public PaymentService(BookingRepo bookings, PaymentRepo payments, PaymentGateway gateway, NotificationService notes, UserRepo users) {
        this.bookings = bookings; this.payments = payments; this.gateway = gateway; this.notes = notes; this.users = users;
    }

    @Transactional
    public Payment pay(AppUser customer, Long bookingId, PayRequest r) {
        Booking b = bookings.lockById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!b.customer.id.equals(customer.id)) throw new SecurityException("Not your booking");
        if (b.status != BookingStatus.AWAITING_PAYMENT) throw new IllegalStateException("This booking is not waiting for payment.");
        if (b.expiresAt != null && b.expiresAt.isBefore(LocalDateTime.now())) throw new IllegalStateException("The payment window has expired.");

        String type = r.method == null ? "" : r.method.trim().toUpperCase();
        String accountId, label, payer;
        switch (type) {
            case "CARD" -> {
                String card = clean(r.cardNumber).replace("-", "");
                if (!card.matches("\\d{13,19}") || !luhn(card)) throw new IllegalArgumentException("Invalid card number.");
                if (r.cvv == null || !r.cvv.matches("\\d{3,4}")) throw new IllegalArgumentException("Invalid CVV.");
                if (blank(r.holder)) throw new IllegalArgumentException("Card holder name is required.");
                validateExpiry(r.expiry);
                accountId = card; payer = r.holder.trim(); label = "Card ****" + last4(card);
            }
            case "EASYPAISA", "JAZZCASH" -> {
                String mob = normalizeMobile(r.mobile);
                if (!mob.matches("03\\d{9}")) throw new IllegalArgumentException("Enter a valid mobile account number like 0300 1234567.");
                if (r.pin == null || !r.pin.matches("\\d{4,6}")) throw new IllegalArgumentException("Enter your 4-6 digit wallet PIN / OTP.");
                if (blank(r.holder)) throw new IllegalArgumentException("Account holder name is required.");
                accountId = mob; payer = r.holder.trim();
                label = ("EASYPAISA".equals(type) ? "Easypaisa " : "JazzCash ") + "****" + last4(mob);
            }
            case "BANK" -> {
                String acc = clean(r.accountNumber).toUpperCase();
                if (!acc.matches("PK\\d{2}[A-Z]{4}\\d{16}|\\d{8,24}")) throw new IllegalArgumentException("Enter a valid bank account number or IBAN (PK..).");
                if (blank(r.bankName) || r.bankName.length() > 60) throw new IllegalArgumentException("Choose your bank.");
                if (blank(r.holder)) throw new IllegalArgumentException("Account title is required.");
                accountId = acc; payer = r.holder.trim(); label = r.bankName.trim() + " ****" + last4(acc);
            }
            default -> throw new IllegalArgumentException("Please choose a payment method.");
        }

        GatewayResult res = gateway.charge(type, b.reference, b.total, accountId);
        Payment p = new Payment();
        p.booking = b; p.amount = b.total; p.gatewayRef = res.reference();
        p.methodType = type; p.payerName = payer; p.method = label + " (" + gateway.name() + ")";

        if (!res.approved()) { p.status = PaymentStatus.FAILED; p.failureReason = res.message(); return payments.save(p); }
        if (!gateway.verify(res) || res.amount().compareTo(b.total) != 0) {
            p.status = PaymentStatus.FAILED; p.failureReason = "Payment verification failed"; return payments.save(p);
        }
        p.status = PaymentStatus.SUCCESS;
        payments.save(p);
        b.status = BookingStatus.PAID;
        b.expiresAt = null;

        notes.notify(customer, "Payment received for " + b.event.title + ". The organizer will send your " + b.quantity + " ticket(s) shortly.", "/customer#bookings");
        // payment + registration details go to the organizer who owns the event
        notes.notify(b.event.organizer, "PAID: " + b.attendeeName + " (" + b.attendeePhone + ") paid " + b.total + " via " + label + " for "
                + b.quantity + " x " + b.tier.name + " - " + b.event.title + ". Please issue the ticket.", "/organizer#issue");
        // and the event manager (admin) is notified
        for (AppUser a : users.findAllByOrderByCreatedAtDesc())
            if (a.role == Role.ADMIN) notes.notify(a, "Payment " + b.total + " received (" + p.methodType + ") for \"" + b.event.title + "\" - organizer "
                    + (b.event.organizer.companyName != null ? b.event.organizer.companyName : b.event.organizer.email), "/admin#payments", false);
        return p;
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }
    private static String clean(String s) { return s == null ? "" : s.replaceAll("\\s", ""); }
    private static String last4(String s) { return s.substring(s.length() - 4); }

    private static String normalizeMobile(String m) {
        String s = clean(m).replace("-", "");
        if (s.startsWith("+92")) s = "0" + s.substring(3);
        else if (s.startsWith("92") && s.length() == 12) s = "0" + s.substring(2);
        return s;
    }

    private static void validateExpiry(String exp) {
        if (exp == null || !exp.matches("(0[1-9]|1[0-2])/\\d{2}")) throw new IllegalArgumentException("Expiry must be MM/YY.");
        YearMonth ym = YearMonth.of(2000 + Integer.parseInt(exp.substring(3)), Integer.parseInt(exp.substring(0, 2)));
        if (ym.isBefore(YearMonth.now())) throw new IllegalArgumentException("Card has expired.");
    }

    static boolean luhn(String n) {
        int sum = 0; boolean alt = false;
        for (int i = n.length() - 1; i >= 0; i--) {
            int d = n.charAt(i) - '0';
            if (alt) { d *= 2; if (d > 9) d -= 9; }
            sum += d; alt = !alt;
        }
        return sum % 10 == 0;
    }
}
