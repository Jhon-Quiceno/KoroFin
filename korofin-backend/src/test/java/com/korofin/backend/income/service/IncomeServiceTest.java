package com.korofin.backend.income.service;

import com.korofin.backend.income.dto.IncomeRequest;
import com.korofin.backend.income.dto.IncomeResponse;
import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.income.entity.Income;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.common.exception.ResourceNotFoundException;
import com.korofin.backend.income.mapper.IncomeMapper;
import com.korofin.backend.expense.repository.CategoryRepository;
import com.korofin.backend.income.repository.IncomeRepository;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncomeServiceTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IncomeMapper incomeMapper;

    @InjectMocks
    private IncomeService incomeService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createIncomeShouldSaveIncomeForCurrentUserWithoutCategory() {
        setAuthenticatedUser(1L);
        IncomeRequest request = new IncomeRequest(BigDecimal.valueOf(500), "Bono", LocalDate.now(), null);
        Income mappedIncome = new Income();
        Income savedIncome = new Income();
        savedIncome.setId(10L);
        IncomeResponse response = new IncomeResponse(10L, BigDecimal.valueOf(500), "Bono", LocalDate.now(), null, null);

        when(incomeMapper.toEntity(request)).thenReturn(mappedIncome);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(incomeRepository.save(mappedIncome)).thenReturn(savedIncome);
        when(incomeMapper.toResponse(savedIncome)).thenReturn(response);

        IncomeResponse createdIncome = incomeService.createIncome(request);

        Assertions.assertEquals(10L, createdIncome.id());
        Assertions.assertNull(mappedIncome.getCategory());
        Assertions.assertEquals(1L, mappedIncome.getUser().getId());
    }

    @Test
    void createIncomeShouldResolveOwnedCategoryWhenCategoryIdProvided() {
        setAuthenticatedUser(1L);
        IncomeRequest request = new IncomeRequest(BigDecimal.valueOf(500), "Bono", LocalDate.now(), 4L);
        Income mappedIncome = new Income();
        Category category = new Category();
        category.setId(4L);
        category.setName("Salario");

        when(incomeMapper.toEntity(request)).thenReturn(mappedIncome);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(categoryRepository.findByIdAndUser_Id(4L, 1L)).thenReturn(Optional.of(category));
        when(incomeRepository.save(mappedIncome)).thenReturn(mappedIncome);
        when(incomeMapper.toResponse(mappedIncome)).thenReturn(
                new IncomeResponse(1L, BigDecimal.valueOf(500), "Bono", LocalDate.now(), 4L, "Salario")
        );

        IncomeResponse createdIncome = incomeService.createIncome(request);

        Assertions.assertEquals(4L, createdIncome.categoryId());
        Assertions.assertEquals(category, mappedIncome.getCategory());
    }

    @Test
    void createIncomeShouldThrowNotFoundWhenCategoryBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        IncomeRequest request = new IncomeRequest(BigDecimal.valueOf(500), "Bono", LocalDate.now(), 99L);
        Income mappedIncome = new Income();

        when(incomeMapper.toEntity(request)).thenReturn(mappedIncome);
        when(userRepository.getReferenceById(1L)).thenReturn(buildUser(1L));
        when(categoryRepository.findByIdAndUser_Id(99L, 1L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> incomeService.createIncome(request));
    }

    @Test
    void updateIncomeShouldThrowNotFoundWhenIncomeBelongsToAnotherUser() {
        setAuthenticatedUser(1L);
        IncomeRequest request = new IncomeRequest(BigDecimal.valueOf(100), null, LocalDate.now(), null);
        when(incomeRepository.findByIdAndUser_Id(20L, 1L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> incomeService.updateIncome(20L, request));
    }

    @Test
    void deleteIncomeShouldDeleteWhenOwnedByCurrentUser() {
        setAuthenticatedUser(3L);
        Income income = new Income();
        income.setId(55L);
        income.setUser(buildUser(3L));
        when(incomeRepository.findByIdAndUser_Id(55L, 3L)).thenReturn(Optional.of(income));

        incomeService.deleteIncome(55L);

        verify(incomeRepository).delete(income);
    }

    @Test
    void deleteIncomeShouldThrowNotFoundWhenOwnedByAnotherUser() {
        setAuthenticatedUser(3L);
        when(incomeRepository.findByIdAndUser_Id(55L, 3L)).thenReturn(Optional.empty());

        Assertions.assertThrows(ResourceNotFoundException.class, () -> incomeService.deleteIncome(55L));
    }

    @Test
    void getIncomesShouldApplyMonthYearFilters() {
        setAuthenticatedUser(1L);
        Pageable pageable = PageRequest.of(0, 20);
        Income income = new Income();
        Page<Income> page = new PageImpl<>(List.of(income), pageable, 1);
        IncomeResponse response = new IncomeResponse(1L, BigDecimal.TEN, null, LocalDate.now(), null, null);

        when(incomeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);
        when(incomeMapper.toResponse(income)).thenReturn(response);

        Page<IncomeResponse> result = incomeService.getIncomes(6, 2026, pageable);

        Assertions.assertEquals(1, result.getTotalElements());
    }

    @Test
    void getIncomesShouldAllowNullFilters() {
        setAuthenticatedUser(1L);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Income> page = new PageImpl<>(List.of(), pageable, 0);

        when(incomeRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<IncomeResponse> result = incomeService.getIncomes(null, null, pageable);

        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void createIncomeWithExplicitUserIdDoesNotReadSecurityContext() {
        IncomeRequest request = new IncomeRequest(BigDecimal.valueOf(500000), "Freelance", LocalDate.now(), null);
        Income mappedIncome = new Income();
        Income savedIncome = new Income();
        savedIncome.setId(88L);

        when(incomeMapper.toEntity(request)).thenReturn(mappedIncome);
        when(userRepository.getReferenceById(9L)).thenReturn(buildUser(9L));
        when(incomeRepository.save(mappedIncome)).thenReturn(savedIncome);
        when(incomeMapper.toResponse(savedIncome)).thenReturn(
                new IncomeResponse(88L, BigDecimal.valueOf(500000), "Freelance", LocalDate.now(), null, null)
        );

        IncomeResponse response = incomeService.createIncome(9L, request);

        Assertions.assertEquals(88L, response.id());
        Assertions.assertEquals(9L, mappedIncome.getUser().getId());
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
