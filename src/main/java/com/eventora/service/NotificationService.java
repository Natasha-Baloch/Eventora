package com.eventora.service;

import com.eventora.model.*;
import com.eventora.repo.NotificationRepo;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private final NotificationRepo repo;
    private final MailService mail;

    public NotificationService(NotificationRepo repo, MailService mail) { this.repo = repo; this.mail = mail; }

    public void notify(AppUser user, String message, String link) { notify(user, message, link, true); }

    /** In-app notification, optionally also emailed. */
    public void notify(AppUser user, String message, String link, boolean email) {
        Notification n = new Notification();
        n.user = user; n.message = message; n.link = link;
        repo.save(n);
        if (email) mail.send(user.email, "Eventora: " + (message.length() > 60 ? message.substring(0, 57) + "..." : message),
                "Hi " + user.getDisplayName() + ",\n\n" + message + "\n\nOpen Eventora: http://localhost:8080" + (link == null ? "" : link) + "\n\n- Team Eventora");
    }
}
