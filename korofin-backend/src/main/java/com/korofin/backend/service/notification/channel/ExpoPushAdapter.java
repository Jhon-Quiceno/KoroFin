package com.korofin.backend.service.notification.channel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.korofin.backend.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adaptador de {@link PushNotificationSender} que entrega notificaciones a través del servicio de
 * push de Expo (ver {@code https://docs.expo.dev/push-notifications/sending-notifications/}).
 *
 * <p>Se construye un {@link RestClient} nuevo por llamada, igual que el resto de clientes HTTP
 * salientes del proyecto (ver {@code AiChatClient}): el {@link RestClient.Builder} inyectado se
 * clona con la base URL fija de Expo, nunca se muta, así que sus timeouts de conexión/lectura
 * configurados sobreviven a cada {@code clone()} y un test puede sustituir un builder atado a
 * {@code MockRestServiceServer} sin que esta clase le sobrescriba la fábrica de requests.
 *
 * <p>{@link #send} captura toda excepción de forma amplia, mismo razonamiento que
 * {@link EmailNotificationSender#send}: este método corre en el executor {@code @Async} general
 * (KoroFin no tiene un executor dedicado a push — ver {@code AsyncConfig}), donde una excepción no
 * capturada solo la loguea el manejador de excepciones async por defecto de Spring y nunca llega
 * a quien llama — un canal push inalcanzable/mal configurado/expirado nunca debe romper la
 * notificación in-app que ya se creó.
 */
@Component
public class ExpoPushAdapter implements PushNotificationSender {

    private static final Logger log = LoggerFactory.getLogger(ExpoPushAdapter.class);
    private static final String EXPO_PUSH_BASE_URL = "https://exp.host";
    private static final String EXPO_PUSH_PATH = "/--/api/v2/push/send";

    private final RestClient.Builder restClientBuilder;

    public ExpoPushAdapter(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Async(AsyncConfig.GENERAL_EXECUTOR)
    @Override
    public void send(PushRecipient recipient, String title, String body) {
        try {
            restClientBuilder.clone()
                    .baseUrl(EXPO_PUSH_BASE_URL)
                    .build()
                    .post()
                    .uri(EXPO_PUSH_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ExpoPushMessage(recipient.expoPushToken(), title, body))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("failed_to_send_push_notification userId={}", recipient.userId(), ex);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ExpoPushMessage(String to, String title, String body) {
    }
}
