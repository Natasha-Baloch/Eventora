package com.eventora.controller;

import com.eventora.model.*;
import com.eventora.repo.UserRepo;
import com.eventora.service.LoginThrottle;
import com.eventora.service.NotificationService;
import com.eventora.service.OtpService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Three different sign-in experiences:
 *  - Customer: email + one-time code
 *  - Organizer: email + password (self-registration, then verified by an event manager)
 *  - Event manager (ADMIN): email + password (accounts are created by the system / another manager)
 */
@Controller
public class AuthController {
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^[0-9+()\\-\\s]{7,20}$");
    private final OtpService otp;
    private final UserRepo users;
    private final SecurityContextRepository ctxRepo;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final NotificationService notifier;
    private final String dummyHash;
    @org.springframework.beans.factory.annotation.Value("${eventora.customer-otp-required:false}") private boolean otpRequired;

    public AuthController(OtpService otp, UserRepo users, SecurityContextRepository ctxRepo, PasswordEncoder encoder,
                          LoginThrottle throttle, NotificationService notifier) {
        this.otp = otp; this.users = users; this.ctxRepo = ctxRepo; this.encoder = encoder; this.throttle = throttle; this.notifier = notifier;
        this.dummyHash = encoder.encode("dummy-password-for-timing");
    }

    // ------------------------------------------------------------------ chooser
    @GetMapping("/login")
    String chooser(Principal p, Model m, @RequestParam(name = "suspended", required = false) String suspended) {
        if (p != null) return "redirect:/dashboard";
        if (suspended != null) m.addAttribute("error", "This account has been suspended. Contact support.");
        return "login";
    }

    // ------------------------------------------------------------------ CUSTOMER (email + code)
    @GetMapping("/login/customer")
    String customerPage(Principal p, Model m) {
        if (p != null) return "redirect:/dashboard";
        m.addAttribute("otpRequired", otpRequired);
        return "login-customer";
    }

    @PostMapping("/login/email")
    String sendCode(@RequestParam(name = "email") String email, @RequestParam(name = "fullName", required = false) String fullName, HttpSession session,
                    HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        email = email == null ? "" : email.trim().toLowerCase();
        if (email.length() > 150 || !EMAIL.matcher(email).matches()) {
            ra.addFlashAttribute("error", "Please enter a valid email address.");
            return "redirect:/login/customer";
        }
        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent() && existing.get().role != Role.CUSTOMER) {
            ra.addFlashAttribute("error", "This email belongs to an organizer / event manager account. Please use that sign-in.");
            return "redirect:/login/customer";
        }
        if (existing.isPresent() && existing.get().suspended) {
            ra.addFlashAttribute("error", "This account has been suspended. Contact support.");
            return "redirect:/login/customer";
        }
        if (!otpRequired) {   // email-only sign in (no code)
            AppUser u = existing.orElseGet(AppUser::new);
            if (u.id == null) { u.email = email; u.role = Role.CUSTOMER; u.fullName = fullName == null || fullName.isBlank() ? null : fullName.trim(); }
            u.lastLoginAt = LocalDateTime.now();
            users.save(u);
            signIn(u, req, res);
            return "redirect:/dashboard";
        }
        try { otp.send(email); }
        catch (IllegalStateException ex) { ra.addFlashAttribute("error", ex.getMessage()); return "redirect:/login/customer"; }
        session.setAttribute("otpEmail", email);
        session.setAttribute("otpName", fullName == null ? null : fullName.trim());
        return "redirect:/login/verify";
    }

    @GetMapping("/login/verify")
    String verifyPage(HttpSession session, Model m) {
        String email = (String) session.getAttribute("otpEmail");
        if (email == null) return "redirect:/login/customer";
        m.addAttribute("email", email);
        return "verify-otp";
    }

    @PostMapping("/login/resend")
    String resend(HttpSession session, RedirectAttributes ra) {
        String email = (String) session.getAttribute("otpEmail");
        if (email == null) return "redirect:/login/customer";
        try { otp.send(email); ra.addFlashAttribute("info", "A new code was sent."); }
        catch (IllegalStateException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/login/verify";
    }

    @PostMapping("/login/verify")
    @Transactional
    String verify(@RequestParam(name = "code") String code, HttpServletRequest req, HttpServletResponse res, HttpSession session, RedirectAttributes ra) {
        String email = (String) session.getAttribute("otpEmail");
        if (email == null) return "redirect:/login/customer";
        if (code == null || !code.trim().matches("\\d{6}") || !otp.verify(email, code)) {
            ra.addFlashAttribute("error", "Invalid or expired code.");
            return "redirect:/login/verify";
        }
        AppUser u = users.findByEmailIgnoreCase(email).orElse(null);
        if (u == null) {
            u = new AppUser();
            u.email = email; u.fullName = (String) session.getAttribute("otpName"); u.role = Role.CUSTOMER;
        }
        if (u.role != Role.CUSTOMER || u.suspended) { ra.addFlashAttribute("error", "This account cannot sign in here."); return "redirect:/login/customer"; }
        u.lastLoginAt = LocalDateTime.now();
        users.save(u);
        session.removeAttribute("otpEmail"); session.removeAttribute("otpName");
        signIn(u, req, res);
        return "redirect:/dashboard";
    }

    // ------------------------------------------------------------------ ORGANIZER (email + password)
    @GetMapping("/login/organizer")
    String organizerPage(Principal p) { return p != null ? "redirect:/dashboard" : "login-organizer"; }

    @PostMapping("/login/organizer")
    String organizerLogin(@RequestParam(name = "email") String email, @RequestParam(name = "password") String password, HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        return passwordLogin(Role.ORGANIZER, email, password, req, res, ra, "/login/organizer");
    }

    @GetMapping("/register/organizer")
    String registerPage(Principal p) { return p != null ? "redirect:/dashboard" : "register-organizer"; }

    @PostMapping("/register/organizer")
    String register(@RequestParam(name = "email") String email, @RequestParam(name = "password") String password, @RequestParam(name = "confirm") String confirm,
                    @RequestParam(name = "fullName") String fullName, @RequestParam(name = "companyName") String companyName, @RequestParam(name = "phone") String phone,
                    @RequestParam(name = "orgBio") String orgBio, RedirectAttributes ra) {
        email = email.trim().toLowerCase();
        String err = null;
        if (!EMAIL.matcher(email).matches() || email.length() > 150) err = "Please enter a valid email address.";
        else if (!password.equals(confirm)) err = "Passwords do not match.";
        else if (password.length() < 8 || password.length() > 72 || !password.matches(".*[a-z].*") || !password.matches(".*[A-Z].*") || !password.matches(".*\\d.*"))
            err = "Password needs 8+ characters with upper-case, lower-case and a number.";
        else if (fullName.isBlank() || fullName.length() > 100 || companyName.isBlank() || companyName.length() > 120 || orgBio.isBlank() || orgBio.length() > 1000)
            err = "Please fill in all fields.";
        else if (!PHONE.matcher(phone).matches()) err = "Please enter a valid phone number.";
        else if (users.findByEmailIgnoreCase(email).isPresent()) err = "This email is already registered.";
        if (err != null) { ra.addFlashAttribute("error", err); return "redirect:/register/organizer"; }

        AppUser u = new AppUser();
        u.email = email; u.role = Role.ORGANIZER; u.organizerStatus = OrgStatus.PENDING_REVIEW;
        u.passwordHash = encoder.encode(password);
        u.fullName = fullName.trim(); u.companyName = companyName.trim(); u.phone = phone.trim(); u.orgBio = orgBio.trim();
        users.save(u);
        users.findAllByOrderByCreatedAtDesc().stream().filter(a -> a.role == Role.ADMIN)
            .forEach(a -> notifier.notify(a, "New organizer registered and awaiting verification: " + u.companyName + " (" + u.email + ")", "/admin#organizers"));
        ra.addFlashAttribute("success", "Registration saved! An event manager will verify you. You can sign in now, but you can create events only after verification.");
        return "redirect:/login/organizer";
    }

    // ------------------------------------------------------------------ EVENT MANAGER / ADMIN (email + password)
    @GetMapping("/login/manager")
    String managerPage(Principal p) { return p != null ? "redirect:/dashboard" : "login-manager"; }

    @PostMapping("/login/manager")
    String managerLogin(@RequestParam(name = "email") String email, @RequestParam(name = "password") String password, HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        return passwordLogin(Role.ADMIN, email, password, req, res, ra, "/login/manager");
    }

    private String passwordLogin(Role role, String email, String password, HttpServletRequest req, HttpServletResponse res,
                                 RedirectAttributes ra, String back) {
        String e = email == null ? "" : email.trim().toLowerCase();
        if (throttle.isLocked(e)) { ra.addFlashAttribute("error", "Too many failed attempts. Try again in 10 minutes."); return "redirect:" + back; }
        AppUser u = users.findByEmailIgnoreCase(e).orElse(null);
        boolean ok;
        if (u == null || u.passwordHash == null) { encoder.matches(String.valueOf(password), dummyHash); ok = false; }
        else ok = u.role == role && encoder.matches(password, u.passwordHash);
        if (!ok) { throttle.fail(e); ra.addFlashAttribute("error", "Invalid email or password."); return "redirect:" + back; }
        if (u.suspended) { ra.addFlashAttribute("error", "This account has been suspended."); return "redirect:" + back; }
        throttle.reset(e);
        u.lastLoginAt = LocalDateTime.now();
        users.save(u);
        signIn(u, req, res);
        return "redirect:/dashboard";
    }

    private void signIn(AppUser u, HttpServletRequest req, HttpServletResponse res) {
        req.getSession(true);
        req.changeSessionId();   // prevent session fixation
        var auth = new UsernamePasswordAuthenticationToken(u.email, null, List.of(new SimpleGrantedAuthority("ROLE_" + u.role.name())));
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        ctxRepo.saveContext(ctx, req, res);
    }

    /** Sends each role to its own dashboard. */
    @GetMapping("/dashboard")
    String dashboard(Principal p) {
        AppUser u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
        return switch (u.role) {
            case ADMIN -> "redirect:/admin";
            case ORGANIZER -> "redirect:/organizer";
            default -> "redirect:/customer";
        };
    }
}
