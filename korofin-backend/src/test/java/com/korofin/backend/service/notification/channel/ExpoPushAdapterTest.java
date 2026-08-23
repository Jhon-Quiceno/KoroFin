package com.korofin.backend.service.notification.channel;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExpoPushAdapterTest {

    /**
     * {@code @Async} está desactivado en este test unitario puro (no hay contexto Spring): la
     * llamada a {@link ExpoPushAdapter#send} corre síncrona en el hilo de test, lo cual es
     * exactamente lo que se necesita para poder verificar contra {@link MockRestServiceServer}.
     */
    @Test
    void sendPostsToExpoPushApiWithTheExpectedPayload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://exp.host/--/api/v2/push/send"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{}", org.springframework.http.MediaType.APPLICATION_JSON));

        ExpoPushAdapter adapter = new ExpoPushAdapter(builder);
        adapter.send(new PushRecipient(1L, "ExponentPushToken[abc]"), "Título", "Cuerpo");

        server.verify();
    }

    @Test
    void sendSwallowsExceptionsWhenExpoRespondsWithAnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://exp.host/--/api/v2/push/send"))
                .andRespond(withServerError());

        ExpoPushAdapter adapter = new ExpoPushAdapter(builder);

        // No debe propagar: un canal push caido nunca debe romper la notificacion in-app.
        adapter.send(new PushRecipient(1L, "ExponentPushToken[abc]"), "Título", "Cuerpo");

        server.verify();
    }
}
