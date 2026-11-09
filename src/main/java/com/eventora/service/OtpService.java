package com.eventora.service;

import com.eventora.model.OtpToken;
import com.eventora.repo.OtpRepo;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OtpService {
    public static final int COOLDOWN_SECONDS = 30, MAX_ATTEMPTS = 5, TTL_MINUTES = 10;
    private final OtpRepo repo;
    private final MailService mail;
    private final SecureRandom random = new SecureRandom();
    @Value("${eventora.otp-secret}") private String secret;

    public OtpService(OtpRepo repo, MailService mail) { this.repo = repo; this.mail = mail; }

    @Transactional
    public void send(String email) {
        var last = repo.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (last.isPresent() && last.get().createdAt.plusSeconds(COOLDOWN_SECONDS).isAfter(LocalDateTime.now()))
            throw new IllegalStateException("Please wait " + COOLDOWN_SECONDS + " seconds before requesting another code.");
        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpToken t = new OtpToken();
        t.email = email.toLowerCase();
        t.codeHash = hash(email, code);
        t.expiresAt = LocalDateTime.now().plusMinutes(TTL_MINUTES);
        repo.save(t);
        mail.sendNow(email, "Your Eventora login code",
                "Your Eventora verification code is: " + code + "\n\nIt expires in " + TTL_MINUTES + " minutes. If you did not request it, ignore this email.");
    }

    @Transactional
    public boolean verify(String email, String code) {
        var opt = repo.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (opt.isEmpty()) return false;
        OtpToken t = opt.get();
        if (t.used || t.attempts >= MAX_ATTEMPTS || t.expiresAt.isBefore(LocalDateTime.now())) return false;
        t.attempts++;
        boolean ok = MessageDigest.isEqual(hash(email, code.trim()).getBytes(StandardCharsets.UTF_8), t.codeHash.getBytes(StandardCharsets.UTF_8));
        if (ok) t.used = true;
        return ok;
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void purge() { repo.purge(LocalDateTime.now().minusDays(1)); }

    private String hash(String email, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((email.toLowerCase() + ":" + code).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
