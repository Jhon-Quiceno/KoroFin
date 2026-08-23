package com.korofin.backend.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserIdReturnsPrincipalWhenItIsANumber() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(7L, null, AuthorityUtils.NO_AUTHORITIES));

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(7L);
    }

    @Test
    void getCurrentUserIdParsesStringPrincipal() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("9", null, AuthorityUtils.NO_AUTHORITIES));

        assertThat(SecurityUtils.getCurrentUserId()).isEqualTo(9L);
    }

    @Test
    void getCurrentUserIdThrowsWhenNoAuthenticationPresent() {
        assertThatThrownBy(SecurityUtils::getCurrentUserId).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getCurrentUserIdThrowsWhenPrincipalIsNotAValidId() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("not-a-number", null, AuthorityUtils.NO_AUTHORITIES));

        assertThatThrownBy(SecurityUtils::getCurrentUserId).isInstanceOf(AccessDeniedException.class);
    }
}
