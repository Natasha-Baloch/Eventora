package com.eventora.config;

import com.eventora.model.AppUser;
import com.eventora.repo.NotificationRepo;
import com.eventora.repo.UserRepo;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalAdvice {
    private final UserRepo users;
    private final NotificationRepo notes;
    @Value("${eventora.currency}") private String currency;

    public GlobalAdvice(UserRepo users, NotificationRepo notes) { this.users = users; this.notes = notes; }

    @ModelAttribute
    void globals(Model m, Principal p) {
        m.addAttribute("currency", currency);
        if (p != null) {
            AppUser u = users.findByEmailIgnoreCase(p.getName()).orElse(null);
            if (u != null) {
                m.addAttribute("me", u);
                m.addAttribute("unread", notes.countByUserAndSeenFalse(u));
            }
        }
    }
}
