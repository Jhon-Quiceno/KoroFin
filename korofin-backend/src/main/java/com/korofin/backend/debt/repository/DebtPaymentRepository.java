package com.korofin.backend.debt.repository;

import com.korofin.backend.debt.entity.DebtPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a persistencia de {@link DebtPayment}.
 *
 * <p>La pertenencia de la {@link com.korofin.backend.debt.entity.Debt} padre la valida
 * {@code DebtPaymentService} antes de ejecutar cualquier consulta acá, así que no hace falta un
 * filtro adicional por usuario en este nivel.
 */
public interface DebtPaymentRepository extends JpaRepository<DebtPayment, Long> {

    Page<DebtPayment> findAllByDebt_IdOrderByPaymentDateDescIdDesc(Long debtId, Pageable pageable);
}
