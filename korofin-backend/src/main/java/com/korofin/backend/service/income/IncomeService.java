package com.korofin.backend.service.income;

import com.korofin.backend.dto.income.IncomeRequest;
import com.korofin.backend.dto.income.IncomeResponse;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.income.Income;
import com.korofin.backend.exception.ResourceNotFoundException;
import com.korofin.backend.mapper.income.IncomeMapper;
import com.korofin.backend.repository.expense.CategoryRepository;
import com.korofin.backend.repository.income.IncomeRepository;
import com.korofin.backend.repository.income.IncomeSpecifications;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio para administrar los ingresos del usuario actual.
 *
 * <p>Toda operación resuelve al llamador vía {@link SecurityUtils#getCurrentUserId()} y limita
 * lecturas/escrituras estrictamente a ese usuario. Las mutaciones sobre un ingreso ajeno, o un
 * {@code categoryId} que referencia una categoría ajena, lanzan {@link ResourceNotFoundException}
 * (404) en vez de 403, para no filtrar su existencia a quien no es su dueño.
 */
@Service
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final IncomeMapper incomeMapper;

    public IncomeService(
            IncomeRepository incomeRepository,
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            IncomeMapper incomeMapper
    ) {
        this.incomeRepository = incomeRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.incomeMapper = incomeMapper;
    }

    @Transactional(readOnly = true)
    public Page<IncomeResponse> getIncomes(Integer month, Integer year, Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        Specification<Income> spec = IncomeSpecifications.ownedBy(userId)
                .and(IncomeSpecifications.inPeriod(month, year));

        return incomeRepository.findAll(spec, pageable)
                .map(incomeMapper::toResponse);
    }

    @Transactional
    public IncomeResponse createIncome(IncomeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Income income = incomeMapper.toEntity(request);
        income.setUser(userRepository.getReferenceById(userId));
        income.setCategory(resolveOwnedCategory(request.categoryId(), userId));

        Income savedIncome = incomeRepository.save(income);
        return incomeMapper.toResponse(savedIncome);
    }

    @Transactional
    public IncomeResponse updateIncome(Long incomeId, IncomeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Income income = findOwnedIncome(incomeId, userId);

        incomeMapper.updateEntityFromRequest(request, income);
        income.setCategory(resolveOwnedCategory(request.categoryId(), userId));

        Income updatedIncome = incomeRepository.save(income);
        return incomeMapper.toResponse(updatedIncome);
    }

    @Transactional
    public void deleteIncome(Long incomeId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Income income = findOwnedIncome(incomeId, userId);
        incomeRepository.delete(income);
    }

    private Income findOwnedIncome(Long incomeId, Long userId) {
        return incomeRepository.findByIdAndUser_Id(incomeId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Ingreso no encontrado"));
    }

    private Category resolveOwnedCategory(Long categoryId, Long userId) {
        if (categoryId == null) {
            return null;
        }

        return categoryRepository.findByIdAndUser_Id(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
    }
}
