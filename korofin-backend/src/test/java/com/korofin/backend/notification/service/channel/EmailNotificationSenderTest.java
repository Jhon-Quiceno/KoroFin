package com.korofin.backend.notification.service.channel;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.internet.MimeMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationSenderTest {

    /**
     * Sin {@code RESEND_API_KEY}/{@code MAIL_FROM} configurados, el sender debe degradar en
     * silencio: nunca debe intentar enviar el mensaje, aunque haya un {@link JavaMailSenderImpl}
     * bean disponible (siempre lo hay, ver {@code application.properties}).
     */
    @Test
    void sendDoesNothingWhenMailPasswordIsBlank() {
        JavaMailSenderImpl mailSender = spy(new JavaMailSenderImpl());
        EmailNotificationSender sender = new EmailNotificationSender(mailSender, "", "from@korofin.dev");

        sender.send(new EmailRecipient(1L, "user@korofin.dev", "Ana"), "Asunto", "Cuerpo");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void sendDoesNothingWhenFromAddressIsBlank() {
        JavaMailSenderImpl mailSender = spy(new JavaMailSenderImpl());
        EmailNotificationSender sender = new EmailNotificationSender(mailSender, "api-key", "");

        sender.send(new EmailRecipient(1L, "user@korofin.dev", "Ana"), "Asunto", "Cuerpo");

        verify(mailSender, never()).send(org.mockito.ArgumentMatchers.any(MimeMessage.class));
    }

    @Test
    void sendDoesNothingWhenMailSenderBeanIsAbsent() {
        EmailNotificationSender sender = new EmailNotificationSender(null, "api-key", "from@korofin.dev");

        // No lanza excepcion aunque mailSender sea null: degrada en silencio.
        sender.send(new EmailRecipient(1L, "user@korofin.dev", "Ana"), "Asunto", "Cuerpo");
    }

    @Test
    void sendBuildsAndSendsAMimeMessageWhenFullyConfigured() {
        JavaMailSenderImpl mailSender = spy(new JavaMailSenderImpl());
        EmailNotificationSender sender = new EmailNotificationSender(mailSender, "api-key", "from@korofin.dev");

        sender.send(new EmailRecipient(1L, "user@korofin.dev", "Ana"), "Asunto", "Cuerpo del mensaje");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    void sendSwallowsExceptionsInsteadOfPropagating() {
        JavaMailSenderImpl mailSender = mock(JavaMailSenderImpl.class);
        when(mailSender.createMimeMessage()).thenThrow(new RuntimeException("smtp down"));
        EmailNotificationSender sender = new EmailNotificationSender(mailSender, "api-key", "from@korofin.dev");

        // No debe propagar la excepcion: un canal de email caido nunca debe romper la notificacion in-app.
        sender.send(new EmailRecipient(1L, "user@korofin.dev", "Ana"), "Asunto", "Cuerpo");
    }
}
