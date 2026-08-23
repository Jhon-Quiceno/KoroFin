package com.korofin.backend.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test unitario standalone (sin contexto Spring), construyendo el filtro directamente con sus
 * dependencias {@code @Value} primitivas — mismo criterio que FinSmart (ver el Javadoc de
 * {@link RateLimitFilter}). Cubre las cuatro reglas: {@code login}/{@code register} (solo IP) y
 * {@code ai-chat}/{@code receipt-scan} (IP+userId, dominio {@code ai}).
 */
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter(2, 60, 2, 60, 2, 60, 2, 60);

    @Test
    void allowsRequestsUnderTheLoginLimit() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(requestTo("/api/users/login"), new MockHttpServletResponse(), chain);
        filter.doFilter(requestTo("/api/users/login"), new MockHttpServletResponse(), chain);

        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksLoginRequestsOnceLimitIsExceeded() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(requestTo("/api/users/login"), new MockHttpServletResponse(), chain);
        filter.doFilter(requestTo("/api/users/login"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestTo("/api/users/login"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("\"status\":429");
        verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksRegisterRequestsOnceLimitIsExceededIndependentlyFromLogin() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(requestTo("/api/users/register"), new MockHttpServletResponse(), chain);
        filter.doFilter(requestTo("/api/users/register"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(requestTo("/api/users/register"), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    void doesNotRateLimitUnrelatedPaths() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        for (int i = 0; i < 5; i++) {
            filter.doFilter(requestTo("/api/users/profile"), new MockHttpServletResponse(), chain);
        }

        verify(chain, times(5)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksAiChatRequestsOnceLimitIsExceeded() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L, null));

            filter.doFilter(requestTo("/api/ai/chat"), new MockHttpServletResponse(), chain);
            filter.doFilter(requestTo("/api/ai/chat"), new MockHttpServletResponse(), chain);

            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestTo("/api/ai/chat"), response, chain);

            assertThat(response.getStatus()).isEqualTo(429);
            verify(chain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void blocksReceiptScanRequestsOnceLimitIsExceeded() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L, null));

            filter.doFilter(requestTo("/api/receipts/scan"), new MockHttpServletResponse(), chain);
            filter.doFilter(requestTo("/api/receipts/scan"), new MockHttpServletResponse(), chain);

            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestTo("/api/receipts/scan"), response, chain);

            assertThat(response.getStatus()).isEqualTo(429);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void aiChatBucketIsKeyedByUserIdSoTwoUsersBehindTheSameIpDoNotShareIt() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(1L, null));
            filter.doFilter(requestTo("/api/ai/chat"), new MockHttpServletResponse(), chain);
            filter.doFilter(requestTo("/api/ai/chat"), new MockHttpServletResponse(), chain);

            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(2L, null));
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestTo("/api/ai/chat"), response, chain);

            assertThat(response.getStatus()).isNotEqualTo(429);
            verify(chain, times(3)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private MockHttpServletRequest requestTo(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
