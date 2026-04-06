package com.parking.smartparking.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class MailStartupDiagnostics implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MailStartupDiagnostics.class);

    private final Environment env;
    private final ObjectProvider<JavaMailSender> javaMailSenderProvider;

    public MailStartupDiagnostics(Environment env, ObjectProvider<JavaMailSender> javaMailSenderProvider) {
        this.env = env;
        this.javaMailSenderProvider = javaMailSenderProvider;
    }

    @Override
    public void run(ApplicationArguments args) {
        JavaMailSender javaMailSender = javaMailSenderProvider.getIfAvailable();
        boolean enabled = env.getProperty("app.mail.enabled", Boolean.class, false);
        String from = env.getProperty("app.mail.from");
        String host = env.getProperty("spring.mail.host");
        Integer port = env.getProperty("spring.mail.port", Integer.class);
        String username = env.getProperty("spring.mail.username");
        Boolean auth = env.getProperty("spring.mail.properties.mail.smtp.auth", Boolean.class);
        Boolean starttls = env.getProperty("spring.mail.properties.mail.smtp.starttls.enable", Boolean.class);

        log.info("Mail config: enabled={}, from={}, host={}, port={}, username={}, auth={}, starttls={}, javaMailSenderPresent={}",
                enabled,
                safe(from),
                safe(host),
                port,
                safe(username),
                auth,
                starttls,
                javaMailSender != null);

        if (!enabled) {
            return;
        }

        boolean hostMissing = (host == null || host.isBlank());
        boolean usernameMissing = (username == null || username.isBlank());
        boolean authRequired = Boolean.TRUE.equals(auth);

        if (hostMissing) {
            log.warn("Mail is enabled but spring.mail.host is missing. In Docker/VPS, set SPRING_MAIL_HOST/PORT/USERNAME/PASSWORD (or point to mailpit for dev).");
        }
        if (authRequired && usernameMissing) {
            log.warn("Mail is enabled and SMTP auth is required, but spring.mail.username is missing.");
        }
        if (javaMailSender == null) {
            log.warn("Mail is enabled but JavaMailSender bean is not available. Check spring.mail.* configuration and that host/port are valid.");
        }
        if (from == null || from.isBlank()) {
            log.warn("Mail is enabled but app.mail.from is missing.");
        }

        String hostTrim = host != null ? host.trim().toLowerCase() : "";
        if (hostTrim.equals("mailpit") || hostTrim.equals("localhost") || hostTrim.equals("127.0.0.1")) {
            log.warn("Mail host appears to be a local/dev SMTP (host={}). Emails will NOT reach real inboxes; check Mailpit UI or configure a real SMTP provider.", safe(host));
        }

        if (hostTrim.equals("smtp.gmail.com") && from != null && !from.isBlank() && username != null && !username.isBlank()) {
            String fromTrim = from.trim().toLowerCase();
            String userTrim = username.trim().toLowerCase();
            if (!fromTrim.equals(userTrim)) {
                log.warn("Gmail SMTP detected but app.mail.from ({}) differs from spring.mail.username ({}). Gmail often rejects unauthorised 'From' addresses unless configured as an alias.",
                        safe(from),
                        safe(username));
            }
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            return "";
        }
        return v;
    }
}
