package com.fintrack.notification;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailServiceTest {

    @Mock JavaMailSender mailSender;

    private MailProperties properties;
    private MailService service;

    @BeforeEach
    void setUp() {
        properties = new MailProperties();
        properties.setFromAddress("noreply@fintrack.local");
        properties.setFromName("FinTrack Pro");
        properties.setBaseUrl("http://app");
        service = new MailService(mailSender, properties);
    }

    private MimeMessage realMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void isEnabledFalseWhenNoEnabledValue() {
        assertThat(service.isEnabled()).isFalse();
    }

    @Test
    void isEnabledTrueWhenNonBlank() {
        properties.setEnabled("yes");
        assertThat(service.isEnabled()).isTrue();
    }

    @Test
    void baseUrlPassesThroughProperty() {
        assertThat(service.baseUrl()).isEqualTo("http://app");
    }

    @Test
    void sendHtmlDoesNothingWhenMailDisabled() {
        service.sendHtml("user@example.com", "Subject", "<p>body</p>");

        verify(mailSender, never()).createMimeMessage();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendHtmlSendsMessageWhenEnabled() {
        properties.setEnabled("yes");
        MimeMessage msg = realMessage();
        when(mailSender.createMimeMessage()).thenReturn(msg);

        service.sendHtml("user@example.com", "Hello", "<p>body</p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue()).isSameAs(msg);
    }

    @Test
    void sendHtmlSwallowsMailExceptions() {
        properties.setEnabled("yes");
        when(mailSender.createMimeMessage()).thenReturn(realMessage());
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        service.sendHtml("user@example.com", "Hello", "<p>body</p>");

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void sendHtmlWithAttachmentSkipsSendWhenDisabled() {
        service.sendHtmlWithAttachment("user@example.com", "Subj", "<p>x</p>",
                "report.pdf", new byte[]{1, 2, 3}, "application/pdf");

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendHtmlWithAttachmentSendsWhenEnabled() {
        properties.setEnabled("yes");
        MimeMessage msg = realMessage();
        when(mailSender.createMimeMessage()).thenReturn(msg);

        service.sendHtmlWithAttachment("user@example.com", "Report", "<p>body</p>",
                "report.pdf", new byte[]{1, 2, 3}, "application/pdf");

        verify(mailSender).send(msg);
    }

    @Test
    void sendHtmlWithAttachmentSwallowsSendFailure() {
        properties.setEnabled("yes");
        when(mailSender.createMimeMessage()).thenReturn(realMessage());
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        service.sendHtmlWithAttachment("user@example.com", "Report", "<p>body</p>",
                "report.pdf", new byte[]{1, 2, 3}, "application/pdf");

        verify(mailSender).send(any(MimeMessage.class));
    }
}
