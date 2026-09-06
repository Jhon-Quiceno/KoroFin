package com.korofin.backend.card.service;

import com.korofin.backend.card.dto.CreditCardRequest;
import com.korofin.backend.card.dto.CreditCardResponse;
import com.korofin.backend.card.dto.CreditCardUpdateRequest;
import com.korofin.backend.card.entity.CardFranchise;
import com.korofin.backend.card.entity.CreditCard;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.card.exception.CreditCardNotFoundException;
import com.korofin.backend.card.mapper.CreditCardMapper;
import com.korofin.backend.card.repository.CreditCardRepository;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditCardServiceTest {

    @Mock
    private CreditCardRepository creditCardRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CreditCardMapper creditCardMapper;

    @InjectMocks
    private CreditCardService creditCardService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createCardStartsTheBalanceAtZero() {
        setAuthenticatedUser(1L);
        CreditCardRequest request = new CreditCardRequest(
                "Visa Oro", "Bancolombia", CardFranchise.VISA,
                new BigDecimal("5000000"), new BigDecimal("0.0250"), 15, 5
        );
        CreditCard mappedCard = new CreditCard();

        when(creditCardMapper.toEntity(request)).thenReturn(mappedCard);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(creditCardRepository.save(mappedCard)).thenReturn(mappedCard);
        when(creditCardMapper.toResponse(mappedCard)).thenReturn(response("5000000", "0"));

        CreditCardResponse created = creditCardService.createCard(request);

        assertThat(mappedCard.getCurrentBalance()).isEqualByComparingTo("0");
        assertThat(mappedCard.getUser().getId()).isEqualTo(1L);
        assertThat(created.availableCredit()).isEqualByComparingTo("5000000");
    }

    @Test
    void updateCardDelegatesToMapperWhichCannotChangeLimitNorBalance() {
        setAuthenticatedUser(1L);
        CreditCard existing = new CreditCard();
        existing.setId(8L);
        existing.setCreditLimit(new BigDecimal("5000000"));
        existing.setCurrentBalance(new BigDecimal("1200000"));
        CreditCardUpdateRequest request =
                new CreditCardUpdateRequest("Nuevo nombre", null, new BigDecimal("0.0300"), 20, 10);

        when(creditCardRepository.findByIdAndUser_Id(8L, 1L)).thenReturn(Optional.of(existing));
        when(creditCardRepository.save(existing)).thenReturn(existing);
        when(creditCardMapper.toResponse(existing)).thenReturn(response("5000000", "1200000"));

        CreditCardResponse updated = creditCardService.updateCard(8L, request);

        verify(creditCardMapper).updateEntityFromRequest(request, existing);
        assertThat(updated.creditLimit()).isEqualByComparingTo("5000000");
        assertThat(updated.currentBalance()).isEqualByComparingTo("1200000");
    }

    @Test
    void updateCardThrowsNotFoundWhenCardBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        when(creditCardRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditCardService.updateCard(
                99L, new CreditCardUpdateRequest("X", null, BigDecimal.ZERO, 1, 1))
        ).isInstanceOf(CreditCardNotFoundException.class);
        verify(creditCardRepository, never()).save(any());
    }

    @Test
    void getCardThrowsNotFoundWhenCardBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        when(creditCardRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditCardService.getCard(99L))
                .isInstanceOf(CreditCardNotFoundException.class);
    }

    @Test
    void deleteCardDeletesWhenOwnedByCurrentUser() {
        setAuthenticatedUser(2L);
        CreditCard card = new CreditCard();
        card.setId(8L);
        when(creditCardRepository.findByIdAndUser_Id(8L, 2L)).thenReturn(Optional.of(card));

        creditCardService.deleteCard(8L);

        verify(creditCardRepository).delete(card);
    }

    @Test
    void deleteCardThrowsNotFoundWhenOwnedByAnotherUser() {
        setAuthenticatedUser(2L);
        when(creditCardRepository.findByIdAndUser_Id(8L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> creditCardService.deleteCard(8L))
                .isInstanceOf(CreditCardNotFoundException.class);
        verify(creditCardRepository, never()).delete(any());
    }

    @Test
    void getCardsScopesTheQueryToTheCurrentUser() {
        setAuthenticatedUser(4L);
        Pageable pageable = PageRequest.of(0, 20);
        CreditCard card = new CreditCard();
        Page<CreditCard> page = new PageImpl<>(List.of(card), pageable, 1);

        when(creditCardRepository.findAllByUser_Id(4L, pageable)).thenReturn(page);
        when(creditCardMapper.toResponse(card)).thenReturn(response("5000000", "0"));

        Page<CreditCardResponse> result = creditCardService.getCards(pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(creditCardRepository).findAllByUser_Id(4L, pageable);
    }

    private CreditCardResponse response(String creditLimit, String balance) {
        BigDecimal limit = new BigDecimal(creditLimit);
        BigDecimal currentBalance = new BigDecimal(balance);
        return new CreditCardResponse(
                8L, "Visa Oro", "Bancolombia", CardFranchise.VISA, limit, new BigDecimal("0.0250"),
                15, 5, currentBalance, limit.subtract(currentBalance), null, null, null
        );
    }

    private void setAuthenticatedUser(Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null)
        );
    }

    private User buildUser(Long userId) {
        User user = new User();
        user.setId(userId);
        return user;
    }
}
