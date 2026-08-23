package com.korofin.backend.mapper.debt;

import com.korofin.backend.dto.debt.DebtPaymentRequest;
import com.korofin.backend.dto.debt.DebtPaymentResponse;
import com.korofin.backend.entity.debt.DebtPayment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper MapStruct entre {@link DebtPayment} y sus DTOs de request/response.
 *
 * <p>{@link DebtPayment#getDebt()} y {@link DebtPayment#getPaymentDate()} los resuelve y asigna
 * {@code DebtPaymentService} por separado — la deuda debe validarse contra el usuario actual
 * primero, y la fecha del abono es hoy por defecto cuando se omite en el request. El
 * {@code expenseId} de {@link DebtPaymentResponse} también lo asigna el servicio aparte, porque
 * viene del {@code Expense} creado junto al abono y no de la entidad {@link DebtPayment}.
 */
@Mapper(componentModel = "spring")
public interface DebtPaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "debt", ignore = true)
    @Mapping(target = "paymentDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    DebtPayment toEntity(DebtPaymentRequest request);

    @Mapping(target = "debtId", source = "debt.id")
    @Mapping(target = "expenseId", ignore = true)
    DebtPaymentResponse toResponse(DebtPayment debtPayment);
}
