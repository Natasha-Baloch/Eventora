package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {
    public record CheckInResult(boolean ok, String message, String holder, String event, String tier) {}

    private final TicketRepo tickets;
    private final BookingRepo bookings;
    private final MailService mail;
    private final NotificationService notes;
    @Value("${eventora.checkin-any-day:true}") private boolean anyDay;

    public TicketService(TicketRepo tickets, BookingRepo bookings, MailService mail, NotificationService notes) {
        this.tickets = tickets; this.bookings = bookings; this.mail = mail; this.notes = notes;
    }

    /** Organizer issues unique QR tickets for a paid booking; customer is notified in-app and by email (PDF attached). */
    @Transactional
    public void issue(AppUser organizer, Long bookingId) throws Exception {
        Booking b = bookings.lockById(bookingId).orElseThrow(() -> new IllegalArgumentException("Booking not found."));
        if (!b.event.organizer.id.equals(organizer.id)) throw new SecurityException("Not your event");
        if (b.status != BookingStatus.PAID) throw new IllegalStateException("Only paid bookings can receive tickets.");
        if (b.ticketsIssued) throw new IllegalStateException("Tickets were already issued for this booking.");
        if (tickets.findByBooking(b).isEmpty()) generate(b);
        b.ticketsIssued = true;
        bookings.flush();
        notes.notify(b.customer, "Your " + b.quantity + " ticket(s) for " + b.event.title + " are ready! View or download them in My Tickets.", "/customer#tickets", false);
        mail.sendWithAttachment(b.customer.email, "Your Eventora tickets - " + b.event.title,
            "Hi " + b.attendeeName + ",\n\nYour " + b.quantity + " ticket(s) (" + b.tier.name + ", ref " + b.reference + ") for " + b.event.title + " on " + b.event.eventDate
            + " at " + b.event.eventTime + ", " + b.event.venue + ", " + b.event.city + " (organizer: " + b.event.organizer.companyName + ") are attached as a PDF with unique QR codes. You can also find them in your Eventora account.\n\nSee you there!\n- Eventora",
            "eventora-" + b.reference + ".pdf", pdf(b));
    }

    /** One unique, unguessable code per ticket. */
    public void generate(Booking b) {
        for (int i = 0; i < b.quantity; i++) {
            Ticket t = new Ticket();
            t.booking = b;
            t.code = "EVT-" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
            tickets.save(t);
        }
    }

    public byte[] qrPng(String content, int size) throws Exception {
        BitMatrix m = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size, Map.of(EncodeHintType.MARGIN, 1));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(m, "PNG", out);
        return out.toByteArray();
    }

    @Value("${eventora.upload-dir:uploads}") private String uploadDir;

    private static Color lerp(Color a, Color b, float t) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t), Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /** Horizontal purple -> pink -> orange gradient built from thin slices. */
    private static void gradientBar(PdfContentByte c, float x, float y, float w, float h, float opacity, Category cat) {
        Color p = cat.awt(1), k = cat.awt(2), o = cat.awt(3);
        c.saveState();
        PdfGState g = new PdfGState(); g.setFillOpacity(opacity); c.setGState(g);
        int n = 70;
        for (int s = 0; s < n; s++) {
            float t = s / (float) (n - 1);
            c.setColorFill(t < .5f ? lerp(p, k, t * 2) : lerp(k, o, (t - .5f) * 2));
            c.rectangle(x + s * w / n, y, w / n + 0.8f, h);
            c.fill();
        }
        c.restoreState();
    }

    private byte[] bannerBytes(String path) {
        try {
            if (path == null) return null;
            if (path.startsWith("/uploads/")) return java.nio.file.Files.readAllBytes(java.nio.file.Path.of(uploadDir, path.substring("/uploads/".length())));
            var res = new org.springframework.core.io.ClassPathResource("static" + path);
            if (res.exists()) return res.getInputStream().readAllBytes();
            java.nio.file.Path dev = java.nio.file.Path.of("src/main/resources/static" + path);
            if (java.nio.file.Files.exists(dev)) return java.nio.file.Files.readAllBytes(dev);
        } catch (Exception ignored) { }
        return null;
    }

    private static PdfPCell cell(String label, String value, int colspan) {
        Font lf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, new Color(192, 38, 211));
        Font vf = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11f, new Color(27, 16, 56));
        Phrase ph = new Phrase();
        ph.add(new Chunk(label.toUpperCase() + "\n", lf));
        ph.add(new Chunk(value == null ? "-" : value, vf));
        PdfPCell c = new PdfPCell(ph);
        c.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
        c.setColspan(colspan);
        c.setPaddingBottom(7f);
        return c;
    }

    /** Colorful, print-friendly ticket: event photo banner, gradient, details, perforation and a big QR code. */
    public byte[] pdf(Booking b) throws Exception {
        List<Ticket> list = tickets.findByBooking(b).stream().filter(t -> !t.voided).toList();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        float W = PageSize.A5.getWidth(), H = PageSize.A5.getHeight(), bandH = 160f, y0 = H - bandH;
        Document doc = new Document(PageSize.A5, 28, 28, 178, 40);
        PdfWriter w = PdfWriter.getInstance(doc, out);
        doc.open();
        byte[] banner = bannerBytes(b.event.imagePath);
        DateTimeFormatter df = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy"), tf = DateTimeFormatter.ofPattern("hh:mm a");
        Font white = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 23, Color.WHITE);
        Font code = FontFactory.getFont(FontFactory.COURIER_BOLD, 8, new Color(90, 30, 140));
        Font hint = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, new Color(110, 90, 140));
        int i = 1;
        for (Ticket t : list) {
            if (i > 1) doc.newPage();
            PdfContentByte u = w.getDirectContentUnder();
            u.setColorFill(new Color(250, 245, 255)); u.rectangle(0, 0, W, y0); u.fill();          // soft lavender body
            u.saveState(); u.rectangle(0, y0, W, bandH); u.clip(); u.newPath();                       // photo banner (cover-fit)
            boolean drawn = false;
            if (banner != null) {
                try {
                    com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(banner);
                    float s = Math.max(W / img.getWidth(), bandH / img.getHeight());
                    float dw = img.getWidth() * s, dh = img.getHeight() * s;
                    u.addImage(img, dw, 0, 0, dh, (W - dw) / 2f, y0 + (bandH - dh) / 2f);
                    drawn = true;
                } catch (Exception ignored) { }
            }
            if (!drawn) { u.setColorFill(b.event.category.awt(1)); u.rectangle(0, y0, W, bandH); u.fill(); }
            u.restoreState();
            gradientBar(u, 0, y0, W, bandH, 0.72f, b.event.category);                                                   // colour wash over the photo
            u.setColorFill(b.event.category.awt(3)); u.rectangle(0, y0 - 6, W, 6); u.fill();           // accent edge
            gradientBar(u, 0, 0, W, 12, 1f, b.event.category);                                                          // footer bar
            u.saveState();                                                                            // perforation + notches
            u.setColorStroke(b.event.category.awt(2)); u.setLineWidth(1.3f); u.setLineDash(5, 4);
            u.moveTo(24, 214); u.lineTo(W - 24, 214); u.stroke();
            u.restoreState();
            u.setColorFill(new Color(15, 8, 40)); u.circle(0, 214, 11); u.fill(); u.circle(W, 214, 11); u.fill();

            PdfContentByte o = w.getDirectContent();
            ColumnText.showTextAligned(o, Element.ALIGN_LEFT, new Phrase("EVENTORA", white), 28, H - 32, 0);
            ColumnText.showTextAligned(o, Element.ALIGN_RIGHT, new Phrase("TICKET " + i + " / " + list.size(), white), W - 28, H - 32, 0);
            ColumnText.showTextAligned(o, Element.ALIGN_LEFT,
                new Phrase(b.event.category.getLabel().toUpperCase(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE)), 28, H - 58, 0);
            ColumnText ct = new ColumnText(o);
            ct.setSimpleColumn(28, y0 + 12, W - 28, H - 68);
            ct.setLeading(26f);
            ct.addText(new Phrase(b.event.title, titleFont));
            ct.go();

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.addCell(cell("Date", b.event.eventDate.format(df), 1));
            table.addCell(cell("Time", b.event.eventTime.format(tf), 1));
            table.addCell(cell("Venue", b.event.venue + ", " + b.event.city, 2));
            table.addCell(cell("Ticket type", b.tier.name, 1));
            table.addCell(cell("Booking ref", b.reference, 1));
            table.addCell(cell("Attendee", b.attendeeName != null ? b.attendeeName : b.customer.getDisplayName(), 1));
            table.addCell(cell("Phone", b.attendeePhone, 1));
            table.addCell(cell("Organizer", b.event.organizer.companyName != null ? b.event.organizer.companyName : b.event.organizer.getDisplayName(), 2));
            doc.add(table);

            float qs = 138f, qx = (W - qs) / 2f;
            o.setColorFill(Color.WHITE); o.setColorStroke(b.event.category.awt(2)); o.setLineWidth(2f);
            o.roundRectangle(qx - 9, 46, qs + 18, qs + 18, 14); o.fillStroke();
            com.lowagie.text.Image qr = com.lowagie.text.Image.getInstance(qrPng(t.code, 420));
            qr.scaleToFit(qs, qs); qr.setAbsolutePosition(qx, 55);
            o.addImage(qr);
            ColumnText.showTextAligned(o, Element.ALIGN_CENTER, new Phrase(t.code, code), W / 2f, 32, 0);
            ColumnText.showTextAligned(o, Element.ALIGN_CENTER, new Phrase("Show this QR code at the entrance - valid for one entry only", hint), W / 2f, 19, 0);
            i++;
        }
        doc.close();
        return out.toByteArray();
    }

    @Transactional
    public CheckInResult checkIn(AppUser organizer, String raw) {
        String code = raw == null ? "" : raw.trim().toUpperCase();
        var opt = tickets.findByCode(code);
        if (opt.isEmpty()) return new CheckInResult(false, "Invalid ticket - code not recognised.", null, null, null);
        Ticket t = opt.get();
        Booking b = t.booking;
        String holder = b.attendeeName != null ? b.attendeeName : b.customer.getDisplayName();
        if (!b.event.organizer.id.equals(organizer.id))
            return new CheckInResult(false, "This ticket is for a different organizer's event.", null, null, null);
        if (t.voided || b.status != BookingStatus.PAID)
            return new CheckInResult(false, "Ticket is not valid (refunded or unpaid).", holder, b.event.title, b.tier.name);
        if (b.event.isPast())
            return new CheckInResult(false, "This event has already ended.", holder, b.event.title, b.tier.name);
        if (!anyDay && !b.event.eventDate.equals(LocalDate.now()))
            return new CheckInResult(false, "This ticket is for " + b.event.eventDate + ", not today.", holder, b.event.title, b.tier.name);
        if (tickets.markCheckedIn(t.id, LocalDateTime.now()) == 0) {
            Ticket fresh = tickets.findById(t.id).orElse(t);
            return new CheckInResult(false, "DUPLICATE - already checked in at " + (fresh.checkedInAt == null ? "earlier" : fresh.checkedInAt.format(DateTimeFormatter.ofPattern("dd MMM HH:mm"))) + ".", holder, b.event.title, b.tier.name);
        }
        return new CheckInResult(true, "Welcome! Entry granted.", holder, b.event.title, b.tier.name);
    }
}
