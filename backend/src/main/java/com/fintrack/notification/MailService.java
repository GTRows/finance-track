package com.fintrack.notification;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
class MailConfig {}

@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final MailProperties properties;

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public String baseUrl() {
        return properties.getBaseUrl();
    }

    @Async
    public void sendHtml(String toAddress, String subject, String htmlBody) {
        if (!isEnabled()) {
            log.debug("Mail disabled; would have sent '{}' to {}", subject, toAddress);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom(new InternetAddress(properties.getFromAddress(), properties.getFromName()));
            mailSender.send(message);
            log.info("Sent mail '{}' to {}", subject, toAddress);
        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send mail '{}' to {}: {}", subject, toAddress, e.getMessage());
        }
    }

    @Async
    public void sendHtmlWithAttachment(String toAddress, String subject, String htmlBody,
                                       String attachmentName, byte[] attachmentBytes, String mimeType) {
        if (!isEnabled()) {
            log.debug("Mail disabled; would have sent '{}' to {} with attachment {}", subject, toAddress, attachmentName);
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            helper.setFrom(new InternetAddress(properties.getFromAddress(), properties.getFromName()));
            helper.addAttachment(attachmentName, new ByteArrayResource(attachmentBytes), mimeType);
            mailSender.send(message);
            log.info("Sent mail '{}' to {} with attachment {} ({} bytes)",
                    subject, toAddress, attachmentName, attachmentBytes.length);
        } catch (MailException | jakarta.mail.MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to send mail '{}' to {}: {}", subject, toAddress, e.getMessage());
        }
    }
}
