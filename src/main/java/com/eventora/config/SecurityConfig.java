package com.eventora.config;

import com.eventora.repo.UserRepo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(11);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http, UserRepo users, SecurityContextRepository repo) throws Exception {
        http
            .authorizeHttpRequests(a -> a
                .requestMatchers("/", "/login", "/login/**", "/register/**", "/events", "/events/**", "/verify", "/verify/**", "/css/**", "/js/**",
                        "/images/**", "/videos/**", "/uploads/**", "/error", "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/organizer/**").hasRole("ORGANIZER")
                .requestMatchers("/customer/**").hasRole("CUSTOMER")
                .anyRequest().authenticated())
            .securityContext(c -> c.securityContextRepository(repo))
            .exceptionHandling(e -> e.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
            .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/").deleteCookies("JSESSIONID"))
            .addFilterAfter(new SuspendedUserFilter(users), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    /** Logs out accounts that were suspended (or deleted) while a session was still open. */
    static class SuspendedUserFilter extends OncePerRequestFilter {
        private final UserRepo users;
        SuspendedUserFilter(UserRepo users) { this.users = users; }

        @Override
        protected boolean shouldNotFilter(HttpServletRequest r) {
            String p = r.getRequestURI();
            return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/images/")
                    || p.startsWith("/uploads/") || p.startsWith("/videos/");
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            Authentication a = SecurityContextHolder.getContext().getAuthentication();
            if (a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken)) {
                var u = users.findByEmailIgnoreCase(a.getName());
                if (u.isEmpty() || u.get().suspended) {
                    SecurityContextHolder.clearContext();
                    var s = req.getSession(false);
                    if (s != null) s.invalidate();
                    res.sendRedirect("/login?suspended");
                    return;
                }
            }
            chain.doFilter(req, res);
        }
    }
}
