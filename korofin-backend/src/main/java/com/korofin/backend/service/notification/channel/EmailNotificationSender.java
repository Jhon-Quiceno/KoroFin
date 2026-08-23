package com.korofin.backend.service.notification.channel;

import com.korofin.backend.config.AsyncConfig;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Adaptador de {@link NotificationSender} que entrega notificaciones por email a través de Resend
 * SMTP.
 *
 * <p>Degrada en silencio cuando no hay API key o dirección remitente configuradas (sin
 * {@code RESEND_API_KEY}/{@code MAIL_FROM}) — {@code spring.mail.host} siempre apunta al relay de
 * Resend en {@code application.properties}, así que {@link JavaMailSender} normalmente existe
 * como bean sin importar eso, pero {@link #send} igual verifica las credenciales/remitente reales
 * antes de intentar el envío, ya que un host sin contraseña fallaría la autenticación en vez de
 * fallar al arrancar. Esto es lo que permite correr la aplicación con notificaciones solo in-app
 * cuando no hay proveedor de email configurado.
 *
 * <p>Envía un mensaje multipart/alternative (texto plano + HTML de marca) en vez de un
 * {@code SimpleMailMessage}: la parte de texto plano es el mismo string que se muestra in-app (ver
 * {@link NotificationDispatcher#dispatch}), así que clientes que no puedan renderizar HTML — o
 * filtros de spam que penalicen HTML-only — igual reciben una notificación legible.
 *
 * <p>{@code recipient.name()} viene del usuario (se fija al registrarse), así que se escapa como
 * HTML antes de interpolarse en {@link #HTML_TEMPLATE}, igual que el asunto/cuerpo — aunque ambos
 * son hoy strings generados por el servidor, escaparlos también no cuesta nada y evita tener que
 * reauditar este método si algún futuro llamador pasa entrada de usuario por acá.
 *
 * <p>{@link #send} captura {@code Exception} de forma amplia a propósito: este método corre en el
 * executor {@code @Async} de mail, donde cualquier excepción no capturada solo la loguea el
 * manejador de excepciones async por defecto de Spring y nunca llega a quien llama — un canal de
 * email caído/fallido nunca debe romper la entrega de la notificación, así que cada camino de
 * falla acá se captura y loguea en vez de dejarse propagar.
 */
@Component
public class EmailNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="es">
              <body style="margin:0;padding:24px 16px;background-color:#ecfdf5;background-image:linear-gradient(160deg,#ecfdf5 0%%,#f8fafc 55%%,#eff6ff 100%%);font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0">
                  <tr>
                    <td align="center">
                      <table role="presentation" width="100%%" style="max-width:480px;background-color:#ffffff;border-radius:12px;overflow:hidden;" cellpadding="0" cellspacing="0">
                        <tr>
                          <td style="background-color:#15803d;padding:20px 24px;">
                            <span style="color:#ffffff;font-size:20px;font-weight:700;letter-spacing:0.3px;">KoroFin</span>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:28px 24px 8px 24px;">
                            <p style="margin:0 0 16px 0;color:#111827;font-size:15px;">Hola %s,</p>
                            <h1 style="margin:0 0 12px 0;color:#111827;font-size:18px;font-weight:600;">%s</h1>
                            <p style="margin:0;color:#374151;font-size:15px;line-height:1.5;">%s</p>
                          </td>
                        </tr>
                        <tr>
                          <td style="padding:24px;">
                            <hr style="border:none;border-top:1px solid #e5e7eb;margin:0 0 16px 0;">
                            <p style="margin:0;color:#9ca3af;font-size:12px;line-height:1.4;">
                              Este es un mensaje automático de KoroFin. Podés administrar tus preferencias de notificación desde la app.
                            </p>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>
                </table>
              </body>
            </html>
            """;

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean mailConfigured;

    public EmailNotificationSender(
            @Autowired(required = false) JavaMailSender mailSender,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${app.mail.from:}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.mailConfigured = mailSender != null && !mailPassword.isBlank() && !fromAddress.isBlank();

        if (!mailConfigured) {
            log.info("email_notifications_disabled reason=missing_smtp_credentials_or_sender");
        }
    }

    @Async(AsyncConfig.MAIL_EXECUTOR)
    @Override
    public void send(EmailRecipient recipient, String subject, String body) {
        if (!mailConfigured) {
            log.debug("skip_email_notification userId={} reason=email_disabled", recipient.userId());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipient.email());
            helper.setSubject(subject);
            helper.setText(body, buildHtmlBody(recipient.name(), subject, body));
            mailSender.send(message);
        } catch (Exception ex) {
            log.warn("failed_to_send_email_notification userId={}", recipient.userId(), ex);
        }
    }

    private static String buildHtmlBody(String recipientName, String subject, String body) {
        String escapedBody = HtmlUtils.htmlEscape(body).replace("\n", "<br>");
        return HTML_TEMPLATE.formatted(HtmlUtils.htmlEscape(recipientName), HtmlUtils.htmlEscape(subject), escapedBody);
    }
}
