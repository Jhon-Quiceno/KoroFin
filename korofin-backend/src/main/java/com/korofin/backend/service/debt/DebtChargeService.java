package com.korofin.backend.service.debt;

import com.korofin.backend.dto.debt.DebtChargeRequest;
import com.korofin.backend.dto.debt.DebtChargeResponse;
import com.korofin.backend.dto.debt.DebtResponse;
import com.korofin.backend.entity.debt.Debt;
import com.korofin.backend.entity.debt.DebtCharge;
import com.korofin.backend.exception.debt.DebtNotFoundException;
import com.korofin.backend.mapper.debt.DebtChargeMapper;
import com.korofin.backend.mapper.debt.DebtMapper;
import com.korofin.backend.repository.debt.DebtChargeRepository;
import com.korofin.backend.repository.debt.DebtRepository;
import com.korofin.backend.security.SecurityUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Lógica de negocio para registrar y listar cargos ({@link DebtCharge}) contra una {@link Debt}
 * del usuario actual.
 *
 * <p>{@link #createCharge} es el espejo de {@code DebtPaymentService#createPayment}: en vez de
 * decrementar {@link Debt#getRemainingAmount()}, lo incrementa vía
 * {@link DebtRepository#incrementRemainingAmount} (un {@code UPDATE} atómico, no
 * lectura-y-luego-escritura) antes de persistir el {@link DebtCharge}. Después vuelve a leer la
 * deuda, porque ese {@code UPDATE} masivo invalida el contexto de persistencia y la instancia
 * cargada antes quedaría con el saldo viejo.
 *
 * <p>A diferencia de un abono, un cargo <b>no</b> crea un
 * {@link com.korofin.backend.entity.expense.Expense} vinculado: aumentar lo que se debe no es una
 * salida de dinero del usuario, así que no corresponde registrarlo como gasto.
 *
 * <p>Devuelve el {@link DebtResponse} actualizado y no el cargo creado, porque lo que le importa
 * al cliente después de registrar un cargo es el nuevo saldo de la deuda.
 */
@Service
public class DebtChargeService {

    private final DebtChargeRepository debtChargeRepository;
    private final DebtRepository debtRepository;
    private final DebtChargeMapper debtChargeMapper;
    private final DebtMapper debtMapper;
    private final Clock clock;

    public DebtChargeService(
            DebtChargeRepository debtChargeRepository,
            DebtRepository debtRepository,
            DebtChargeMapper debtChargeMapper,
            DebtMapper debtMapper,
            Clock clock
    ) {
        this.debtChargeRepository = debtChargeRepository;
        this.debtRepository = debtRepository;
        this.debtChargeMapper = debtChargeMapper;
        this.debtMapper = debtMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page<DebtChargeResponse> getCharges(Long debtId, Pageable pageable) {
        Long userId = SecurityUtils.getCurrentUserId();
        findOwnedDebt(debtId, userId);

        return debtChargeRepository.findAllByDebt_IdOrderByChargeDateDescIdDesc(debtId, pageable)
                .map(debtChargeMapper::toResponse);
    }

    @Transactional
    public DebtResponse createCharge(Long debtId, DebtChargeRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        findOwnedDebt(debtId, userId);

        int updatedRows = debtRepository.incrementRemainingAmount(debtId, request.amount());
        if (updatedRows == 0) {
            // La deuda fue borrada de forma concurrente entre la verificación de pertenencia de
            // arriba y este UPDATE; se rechaza sin persistir el DebtCharge.
            throw new DebtNotFoundException();
        }

        // Relectura después del UPDATE masivo (que limpió el contexto de persistencia), para que
        // la deuda refleje el nuevo remainingAmount en vez del valor obsoleto leído arriba.
        Debt updatedDebt = debtRepository.findById(debtId)
                .orElseThrow(DebtNotFoundException::new);

        DebtCharge charge = debtChargeMapper.toEntity(request);
        charge.setDebt(updatedDebt);
        LocalDate chargeDate = request.chargeDate() != null ? request.chargeDate() : LocalDate.now(clock);
        charge.setChargeDate(chargeDate);
        debtChargeRepository.save(charge);

        return debtMapper.toResponse(updatedDebt);
    }

    private Debt findOwnedDebt(Long debtId, Long userId) {
        return debtRepository.findByIdAndUser_Id(debtId, userId)
                .orElseThrow(DebtNotFoundException::new);
    }
}
