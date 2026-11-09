# Eventora - Event booking platform

Java 17 · Spring Boot 3 · Thymeleaf · Bootstrap 5 · Spring Security · JPA/Hibernate · MySQL · ZXing · OpenPDF

## Run in VS Code
1. Install: JDK 17+, Maven, MySQL 8, and the VS Code "Extension Pack for Java" + "Spring Boot Extension Pack".
2. Start MySQL. Edit `src/main/resources/application.properties` (`spring.datasource.username/password`). The `eventora` database is created automatically.
3. Open the folder in VS Code, open `src/main/java/com/eventora/Main.java` and click **Run** above `main()` (or F5). The browser opens by itself - no link to type.

## Three sign-in types (GET STARTED)
| Role | How they sign in | Demo account |
|---|---|---|
| Customer | email only (no password, no code). Set `eventora.customer-otp-required=true` to add an emailed 6-digit code | any email |
| Organizer | email + password (self-registration, then verified by the event manager) | `sunset@eventora.local`, `techverse@eventora.local`, `heritage@eventora.local` / `Organizer@123` |
| Event Manager (admin) | email + password | `admin@eventora.local` / `Admin@12345` |

Passwords are stored as BCrypt hashes; 5 wrong passwords lock that email for 10 minutes. Change the demo passwords in `application.properties` (they only apply when an account is first created).

## Demo data (7 events, 3 organizers)
- Sunset Entertainment: Neon Nights Live Concert, Sunset DJ Beach Party (needs approval), Street Food Carnival
- TechVerse Events: Future Tech Summit, Startup Founders Business Summit (needs approval)
- Heritage & Sports Co.: City Football Cup Final, Sindh Cultural Festival

## Workflow
1. Customer signs in with email, sees all events, selects tickets and enters registration details (name, phone).
2. Organizer approves the request (only for approval events).
3. Customer pays with Card, Easypaisa, JazzCash or Bank account.
4. Payment + registration details appear in the organizer's Payments / Issue Tickets tabs; the event manager gets a notification.
5. Organizer clicks **Issue & send** - a unique QR ticket (with event details) goes to the customer's account and email (PDF).
6. Customer views/downloads the ticket, can verify it at /verify, and can leave feedback for the organizer.
Organizers also reschedule events (customers are notified), scan QR codes, and read feedback. Event manager verifies organizers, approves events and sees every user, payment, refund, complaint and feedback.
Unpaid reservations expire automatically (30 min). Seats are reserved under a DB row lock, so overselling is impossible.

## Payments - IMPORTANT
Card, Easypaisa, JazzCash and bank payments all run through a **sandbox** gateway - no real money moves. Real Easypaisa/JazzCash/bank integration needs merchant accounts from those providers. The bundled gateway is a **sandbox** (any valid-looking details approve; a card, mobile or account number ending 0002 is declined; results are HMAC-signed and verified server-side). For real money implement `PaymentGateway` for Stripe / PayFast / JazzCash etc. and mark it `@Primary`.

## Images & video
- The 7 demo events use your own photos in `src/main/resources/static/images/events/` (`concert-1`, `dj-party-1`, `tech-1`, `sports-1`, `cultural-1`, `food-1`, `business-1` .jpg). Replace a file (same name) to change a picture. Make sure you have the right to use each photo before publishing the site.
- `chai-and-beats.png` is the poster for an existing event titled **Chai and Beats Musical Night**. On startup, the app assigns it to matching event records.
- Hero video: put a licensed MP4 from pexels.com/videos at `src/main/resources/static/videos/hero.mp4`. Without it the page shows the animated poster + light beams + confetti.
- Organizer uploads are validated (JPG/PNG, magic bytes, 5 MB, min size) and stored in `uploads/events/`.
- Browser needs internet for Bootstrap/Chart.js/fonts CDNs.

## Update notes
- **Self-healing database** (`SchemaRepair`): on start-up it makes leftover unused NOT NULL columns nullable (fixes
  `Field 'price' doesn't have a default value` when issuing tickets) and removes rows whose user/event link is broken
  (fixes `Unable to find AppUser with id 0` on `/admin`). Valid data is never touched.
- **Tickets** now take the colours of the event category (concert, DJ, tech, wedding, sports, cultural, food, business)
  on the web ticket and the downloadable PDF. Customers download from *My Tickets -> Download PDF*.
- Flow: customer pays -> organizer clicks *Issue & send* -> customer is notified, emailed the PDF and sees the ticket.
- Organizers register at `/register/organizer`; the event manager (admin) verifies them and approves their events in `/admin`.

## Admin control center (added)
- Sign in at `/login/manager` (default `admin@eventora.local` / `Admin@12345`).
- Tabs: Overview, Profit & Budget, Reports (CSV exports + print), New Event approvals, Organizer approvals, Refunds, Users, All Events, Bookings, Payments, Feedback, Issues, Notifications.
- Platform profit = `eventora.commission-percent` (default 10) % of every paid booking. Change it in `application.properties`.
- Startup self-repair (`SchemaRepair`) now converts old/invalid status values (e.g. `PENDING` -> `PENDING_APPROVAL`) so the
  "No enum constant ...EventStatus.PENDING" 500 error cannot happen again.
