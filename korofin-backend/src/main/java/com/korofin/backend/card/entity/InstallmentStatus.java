package com.korofin.backend.card.entity;

/**
 * Estado de facturación de una {@link Installment}.
 *
 * <p>Toda cuota arranca en {@code PENDING} y pasa a {@code BILLED} exactamente una vez, cuando
 * {@code CycleCloseService} materializa su interés en un {@link CardMovement} agregado de tipo
 * {@link CardMovementType#INTEREST}. Se persiste como {@code VARCHAR} + {@code CHECK}, misma
 * convención que {@link CardFranchise}.
 */
public enum InstallmentStatus {
    PENDING,
    BILLED
}
