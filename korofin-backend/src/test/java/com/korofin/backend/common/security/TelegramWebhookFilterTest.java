package com.korofin.backend.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test unitario standalone (sin contexto Spring), mismo patrón que {@link RateLimitFilterTest}:
 * el filtro se construye directo con su única dependencia {@code @Value} primitiva.
 */
class TelegramWebhookFilterTest {

    private static final String SECRET = "shared-secret-value";
    private static final String HEADER = "X-Telegram-Webhook-Secret";

    @Test
    void allowsRequestWithTheCorrectSecret() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter(SECRET);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = requestTo("/api/integrations/telegram/expenses");
        request.addHeader(HEADER, SECRET);
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRequestWithAWrongSecretWith401() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter(SECRET);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = requestTo("/api/integrations/telegram/expenses");
        request.addHeader(HEADER, "wrong-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsRequestWithoutTheHeaderWith401() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter(SECRET);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestTo("/api/integrations/telegram/confirm-link"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void alwaysRejectsWith401WhenTheWebhookSecretIsNotConfigured() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter("");
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = requestTo("/api/integrations/telegram/receipts");
        request.addHeader(HEADER, "anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotProtectUnrelatedPaths() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter("");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/integrations/telegram/link-code"), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNotProtectCompletelyUnrelatedRoutes() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter("");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/expenses"), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/integrations/telegram/%65xpenses",
            "/api/integrations/telegram/receipts/",
            "/api/integrations/telegram/confirm%2Dlink",
            "/API/INTEGRATIONS/TELEGRAM/EXPENSES",
            "//api/integrations/telegram/expenses",
            "/api/integrations/telegram/expenses;x=1",
            "/api/integrations/telegram/./expenses",
            "/api/integrations/telegram/anything-new"
    })
    void alternateSpellingsAndUnknownRoutesUnderTheTelegramPrefixStillRequireTheSecret(String spelling)
            throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter(SECRET);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestTo(spelling), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void anEncodedSpellingWithTheCorrectSecretIsAllowed() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter(SECRET);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletRequest request = requestTo("/api/integrations/telegram/%65xpenses");
        request.addHeader(HEADER, SECRET);
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void theLinkCodeRouteStaysOutsideTheSecretCheckBecauseItUsesTheUsersJwt() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter(SECRET);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/integrations/telegram/link-code"), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void routesOutsideTheTelegramPrefixAreNotAffected() throws Exception {
        TelegramWebhookFilter filter = new TelegramWebhookFilter(SECRET);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/users/profile"), new MockHttpServletResponse(), chain);

        verify(chain, times(1)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private MockHttpServletRequest requestTo(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        return request;
    }
}
