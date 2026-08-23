package com.korofin.backend.common.repository;

import java.time.LocalDate;

/**
 * Proyección de la fecha de actividad más reciente de un usuario, agrupada entre todas las filas
 * que le pertenecen, devuelta por
 * {@code ExpenseRepository#findLatestExpenseDatePerUser()} y
 * {@code IncomeRepository#findLatestIncomeDatePerUser()}.
 *
 * <p>Alimenta a {@code InactivityReminderJob} (dominio {@code scheduling}), que combina ambas
 * proyecciones en memoria en vez de consultar por usuario, así que el costo del job no crece
 * linealmente con la cantidad de usuarios elegibles.
 */
public interface UserLastActivityProjection {

    Long getUserId();

    LocalDate getLastDate();
}
