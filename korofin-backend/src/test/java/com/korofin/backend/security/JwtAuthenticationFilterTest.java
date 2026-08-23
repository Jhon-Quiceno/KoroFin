package com.korofin.backend.security;

import com.korofin.backend.repository.user.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void populatesSecurityContextWhenBearerTokenIsValidAndUserExists() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("5");
        when(jwtService.parseAccessToken("valid-token")).thenReturn(claims);
        when(userRepository.existsById(5L)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(5L);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void doesNotPopulateSecurityContextWhenUserNoLongerExists() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("5");
        when(jwtService.parseAccessToken("valid-token")).thenReturn(claims);
        when(userRepository.existsById(5L)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }

    @Test
    void skipsAuthenticationWhenNoAuthorizationHeaderIsPresent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
        Mockito.verifyNoInteractions(jwtService);
    }

    @Test
    void clearsSecurityContextWhenTokenIsInvalid() throws Exception {
        when(jwtService.parseAccessToken("bad-token"))
                .thenThrow(new com.korofin.backend.exception.user.InvalidRefreshTokenException("token inválido"));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(any(), any());
    }
}
