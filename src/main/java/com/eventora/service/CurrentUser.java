package com.eventora.service;

import com.eventora.model.AppUser;
import com.eventora.repo.UserRepo;
import java.security.Principal;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private final UserRepo users;
    public CurrentUser(UserRepo users) { this.users = users; }
    public AppUser of(Principal p) {
        return users.findByEmailIgnoreCase(p.getName()).orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
