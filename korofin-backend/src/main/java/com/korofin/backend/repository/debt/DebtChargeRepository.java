package com.korofin.backend.repository.debt;

import com.korofin.backend.entity.debt.DebtCharge;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a persistencia de {@link DebtCharge}.
 *
 * <p>La pertenencia de la {@link com.korofin.backend.entity.debt.Debt} padre la valida
 * {@code DebtChargeService} antes de ejecutar cualquier consulta acá, así que no hace falta un
 * filtro adicional por usuario en este nivel.
 */
public interface DebtChargeRepository extends JpaRepository<DebtCharge, Long> {

    Page<DebtCharge> findAllByDebt_IdOrderByChargeDateDescIdDesc(Long debtId, Pageable pageable);
}
