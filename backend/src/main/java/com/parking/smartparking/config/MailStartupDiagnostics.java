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

        if (enabled && (host == null || host.isBlank() || username == null || username.isBlank())) {
            log.warn("Mail is enabled but spring.mail.host or spring.mail.username is missing. Local runs need .env loader or application-secrets.properties.");
        }

        if (enabled && (from == null || from.isBlank())) {
            log.warn("Mail is enabled but app.mail.from is missing.");
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
