package com.eventora.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final ObjectProvider<JavaMailSender> sender;
    @Value("${eventora.mail-from}") private String from;

    public MailService(ObjectProvider<JavaMailSender> sender) { this.sender = sender; }

    @Async
    public void send(String to, String subject, String body) { sendNow(to, subject, body); }

    @Async
    public void sendWithAttachment(String to, String subject, String body, String filename, byte[] data) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) {
            log.warn("\n=========== EMAIL WITH ATTACHMENT (SMTP not configured) ===========\nTo: {}\nSubject: {}\nAttachment: {} ({} bytes)\n{}\n====================================================================", to, subject, filename, data.length, body);
            return;
        }
        try {
            jakarta.mail.internet.MimeMessage m = s.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper h = new org.springframework.mail.javamail.MimeMessageHelper(m, true, "UTF-8");
            h.setFrom(from); h.setTo(to); h.setSubject(subject); h.setText(body);
            h.addAttachment(filename, new org.springframework.core.io.ByteArrayResource(data));
            s.send(m);
        } catch (Exception ex) { log.error("Email with attachment to {} failed: {}", to, ex.getMessage()); }
    }

    /** @return true if really sent by SMTP, false if only logged (SMTP not configured / failed). */
    public boolean sendNow(String to, String subject, String body) {
        JavaMailSender s = sender.getIfAvailable();
        if (s == null) {
            log.warn("\n=========== EMAIL (SMTP not configured - printed here) ===========\nTo: {}\nSubject: {}\n{}\n==================================================================", to, subject, body);
            return false;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from); m.setTo(to); m.setSubject(subject); m.setText(body);
            s.send(m);
            return true;
        } catch (Exception ex) {
            log.error("Email to {} failed: {}", to, ex.getMessage());
            return false;
        }
    }
}
