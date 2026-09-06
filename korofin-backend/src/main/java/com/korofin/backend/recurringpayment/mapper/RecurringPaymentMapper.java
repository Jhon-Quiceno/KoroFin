package com.korofin.backend.recurringpayment.mapper;

import com.korofin.backend.recurringpayment.dto.RecurringPaymentRequest;
import com.korofin.backend.recurringpayment.dto.RecurringPaymentResponse;
import com.korofin.backend.recurringpayment.dto.RecurringPaymentUpdateRequest;
import com.korofin.backend.recurringpayment.entity.RecurringPayment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * Mapper MapStruct entre {@link RecurringPayment} y sus DTOs de request/response.
 *
 * <p>{@link RecurringPayment#getUser()}, {@link RecurringPayment#getNextPaymentDate()} y
 * {@link RecurringPayment#isActive()} los resuelve y asigna aparte {@code RecurringPaymentService}:
 * el dueño se resuelve desde el contexto de seguridad, la fecha del próximo pago se siembra desde
 * {@code firstPaymentDate} (y después solo avanza vía el endpoint {@code /pay}), y el flag activo
 * solo cambia vía el endpoint {@code /toggle}.
 */
@Mapper(componentModel = "spring")
public interface RecurringPaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "nextPaymentDate", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    RecurringPayment toEntity(RecurringPaymentRequest request);

    @Mapping(target = "isActive", source = "active")
    RecurringPaymentResponse toResponse(RecurringPayment recurringPayment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "nextPaymentDate", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntityFromRequest(RecurringPaymentUpdateRequest request, @MappingTarget RecurringPayment recurringPayment);
}
