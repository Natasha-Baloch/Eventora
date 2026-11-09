package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the event manager (admin), 3 verified demo organizers (with passwords) and 7 demo events - all stored in the database.
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final UserRepo users;
    private final EventRepo events;
    private final BookingRepo bookings;
    private final PasswordEncoder encoder;
    @Value("${eventora.admin-email}") private String adminEmail;
    @Value("${eventora.admin-password}") private String adminPassword;
    @Value("${eventora.demo-organizer-password}") private String orgPassword;
    @Value("${eventora.seed-sample-data}") private boolean seedSamples;

    public DataSeeder(UserRepo users, EventRepo events, BookingRepo bookings, PasswordEncoder encoder) {
        this.users = users; this.events = events; this.bookings = bookings; this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppUser admin = users.findByEmailIgnoreCase(adminEmail).orElseGet(AppUser::new);
        if (admin.id == null) { admin.email = adminEmail.toLowerCase(); admin.fullName = "Event Manager"; admin.role = Role.ADMIN; }
        if (admin.passwordHash == null) { admin.passwordHash = encoder.encode(adminPassword); log.info("Event manager account ready: {}", adminEmail); }
        users.save(admin);

        attachChaiAndBeatsPoster();
        if (!seedSamples) return;
        removeLegacyDemoOrganizer();

        AppUser o1 = organizer("sunset@eventora.local", "Ayesha Khan", "Sunset Entertainment", "+92 300 1111111", "Concerts, DJ nights and food festivals across Karachi.");
        AppUser o2 = organizer("techverse@eventora.local", "Bilal Ahmed", "TechVerse Events", "+92 321 2222222", "Technology and business conferences in Lahore.");
        AppUser o3 = organizer("heritage@eventora.local", "Sana Memon", "Heritage & Sports Co.", "+92 333 3333333", "Cultural festivals and sports tournaments.");

        if (events.findByOrganizerOrderByCreatedAtDesc(o1).isEmpty()) {
            sample(o1, "Neon Nights Live Concert", Category.CONCERT, "Expo Arena", "Karachi", 21, 20, false,
                "A massive night of live music with lasers, LED walls and headline artists performing back-to-back. Doors open at 6 PM.",
                "concert-1", t("General", 3500, 800), t("VIP", 9000, 150), t("Backstage Pass", 18000, 30));
            sample(o1, "Sunset DJ Beach Party", Category.DJ_PARTY, "Seaview Beach Club", "Karachi", 12, 21, true,
                "Dance until sunrise with international DJs, a live visual show and a beachfront dance floor.",
                "dj-party-1", t("Early Bird", 2500, 300), t("Standard", 4000, 500), t("Table (4 guests)", 20000, 25));
            sample(o1, "Street Food Carnival", Category.FOOD, "Riverside Park", "Karachi", 9, 12, false,
                "More than 80 food stalls, live cooking battles, desserts and music. Come hungry!",
                "food-1", t("Entry", 500, 3000), t("Tasting Pass", 1800, 600));
        }
        if (events.findByOrganizerOrderByCreatedAtDesc(o2).isEmpty()) {
            sample(o2, "Future Tech Summit", Category.TECH, "Grand Convention Hall", "Lahore", 35, 9, false,
                "Two days of keynotes, AI and cloud workshops, startup demos and networking with industry leaders.",
                "tech-1", t("Student", 1500, 200), t("Professional", 6000, 400), t("Corporate (5 seats)", 25000, 40));
            sample(o2, "Startup Founders Business Summit", Category.BUSINESS, "Business Bay Centre", "Lahore", 50, 10, true,
                "Meet investors, pitch your idea and learn from founders who built successful companies.",
                "business-1", t("Founder", 3000, 250), t("Investor Lounge", 12000, 40));
        }
        if (events.findByOrganizerOrderByCreatedAtDesc(o3).isEmpty()) {
            sample(o3, "City Football Cup Final", Category.SPORTS, "National Stadium", "Karachi", 18, 19, false,
                "The season finale! Cheer for your team in a packed stadium with food stalls and fan zones.",
                "sports-1", t("Stand", 1200, 4000), t("Premium", 3500, 1000), t("Box", 15000, 60));
            sample(o3, "Sindh Cultural Festival", Category.CULTURAL, "Heritage Grounds", "Hyderabad", 40, 16, false,
                "Folk music, Ajrak and handicraft stalls, traditional dance, and heritage food for the whole family.",
                "cultural-1", t("Adult", 800, 1500), t("Family (4)", 2400, 400));
        }
    }

    private void attachChaiAndBeatsPoster() {
        String title = "Chai and Beats Musical Night";
        String imagePath = "/images/events/chai-and-beats.png";
        events.findAll().stream()
            .filter(event -> event.title != null && event.title.trim().equalsIgnoreCase(title))
            .filter(event -> !imagePath.equals(event.imagePath))
            .forEach(event -> {
                event.imagePath = imagePath;
                events.save(event);
                log.info("Assigned the Chai and Beats poster to event {}", event.id);
            });
    }

    /** The old single demo organizer is removed when none of its events has bookings. */
    private void removeLegacyDemoOrganizer() {
        users.findByEmailIgnoreCase("organizer@eventora.local").ifPresent(old -> {
            List<Event> evs = events.findByOrganizerOrderByCreatedAtDesc(old);
            boolean used = !bookings.findByEventOrganizerOrderByCreatedAtDesc(old).isEmpty();
            if (!used) { events.deleteAll(evs); users.delete(old); log.info("Removed legacy demo organizer and its events."); }
        });
    }

    private AppUser organizer(String email, String name, String company, String phone, String bio) {
        AppUser o = users.findByEmailIgnoreCase(email).orElseGet(AppUser::new);
        if (o.id == null) {
            o.email = email; o.fullName = name; o.companyName = company; o.phone = phone; o.orgBio = bio;
            o.role = Role.ORGANIZER; o.organizerStatus = OrgStatus.VERIFIED;
        }
        if (o.passwordHash == null) o.passwordHash = encoder.encode(orgPassword);
        return users.save(o);
    }

    private record Tier(String name, int price, int cap) {}
    private static Tier t(String n, int p, int c) { return new Tier(n, p, c); }

    private void sample(AppUser org, String title, Category cat, String venue, String city, int days, int hour,
                        boolean approval, String desc, String img, Tier... tiers) {
        Event e = new Event();
        e.organizer = org; e.title = title; e.category = cat; e.venue = venue; e.city = city;
        e.eventDate = LocalDate.now().plusDays(days); e.eventTime = LocalTime.of(hour, 0);
        e.description = desc; e.requiresApproval = approval; e.status = EventStatus.PUBLISHED;
        e.imagePath = "/images/events/" + img + ".jpg";
        for (Tier x : tiers) {
            TicketCategory tc = new TicketCategory();
            tc.event = e; tc.name = x.name(); tc.price = BigDecimal.valueOf(x.price()); tc.capacity = x.cap();
            e.tiers.add(tc);
        }
        events.save(e);
    }
}
