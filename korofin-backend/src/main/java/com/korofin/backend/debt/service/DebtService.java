package com.korofin.backend.debt.service;

import com.korofin.backend.debt.dto.DebtRequest;
import com.korofin.backend.debt.dto.DebtResponse;
import com.korofin.backend.debt.dto.DebtUpdateRequest;
import com.korofin.backend.debt.entity.Debt;
import com.korofin.backend.debt.exception.DebtNotFoundException;
import com.korofin.backend.debt.mapper.DebtMapper;
import com.korofin.backend.debt.repository.DebtRepository;
import com.korofin.backend.user.repository.UserRepository;
import com.korofin.backend.common.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio para administrar las deudas del usuario actual.
 *
 * <p>Toda operación resuelve al llamador vía {@link SecurityUtils#getCurrentUserId()} y limita
 * lecturas/escrituras estrictamente a ese usuario. Las mutaciones sobre una deuda ajena lanzan
 * {@link DebtNotFoundException} (404) en vez de 403, para no filtrar su existencia a quien no es
 * su dueño.
 *
 * <p>{@link #updateDebt} deliberadamente no puede cambiar {@code totalAmount} ni
 * {@code remainingAmount} — ver el Javadoc de {@link DebtUpdateRequest}. Los únicos lugares donde
 * {@code remainingAmount} cambia después de la creación son {@code DebtPaymentService} (lo
 * reduce) y {@code DebtChargeService} (lo incrementa), ambos vía {@code UPDATE} atómico.
 */
@Service
public class DebtService {

    private final DebtRepository debtRepository;
    private final UserRepository userRepository;
    private final DebtMapper debtMapper;

    public DebtService(DebtRepository debtRepository, UserRepository userRepository, DebtMapper debtMapper) {
        this.debtRepository = debtRepository;
        this.userRepository = userRepository;
        this.debtMapper = debtMapper;
    }

    @Transactional(readOnly = true)
    public Page<DebtResponse> getDebts(Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        return debtRepository.findAllByUser_Id(userId, pageable)
                .map(debtMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public DebtResponse getDebt(Long debtId) {
        Long userId = SecurityUtils.getCurrentUserId();
        return debtMapper.toResponse(findOwnedDebt(debtId, userId));
    }

    @Transactional
    public DebtResponse createDebt(DebtRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();

        Debt debt = debtMapper.toEntity(request);
        debt.setUser(userRepository.getReferenceById(userId));
        // El saldo restante arranca igual al total: todavía no hubo ni abonos ni cargos.
        debt.setRemainingAmount(request.totalAmount());

        Debt savedDebt = debtRepository.save(debt);
        return debtMapper.toResponse(savedDebt);
    }

    @Transactional
    public DebtResponse updateDebt(Long debtId, DebtUpdateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Debt debt = findOwnedDebt(debtId, userId);

        debtMapper.updateEntityFromRequest(request, debt);

        Debt updatedDebt = debtRepository.save(debt);
        return debtMapper.toResponse(updatedDebt);
    }

    @Transactional
    public void deleteDebt(Long debtId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Debt debt = findOwnedDebt(debtId, userId);
        debtRepository.delete(debt);
    }

    private Debt findOwnedDebt(Long debtId, Long userId) {
        return debtRepository.findByIdAndUser_Id(debtId, userId)
                .orElseThrow(DebtNotFoundException::new);
    }
}
