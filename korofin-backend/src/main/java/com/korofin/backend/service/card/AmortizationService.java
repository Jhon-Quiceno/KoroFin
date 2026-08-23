package com.korofin.backend.service.card;

import com.korofin.backend.entity.card.CreditCard;
import com.korofin.backend.entity.card.Installment;
import com.korofin.backend.entity.card.InstallmentStatus;
import com.korofin.backend.exception.card.InstallmentAmountTooLowException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Cálculo puro (sin persistencia) del cronograma de cuotas de una compra diferida: sistema de
 * capital fijo con interés sobre el saldo pendiente decreciente de ESA compra, no del saldo
 * global de la tarjeta.
 *
 * <p>{@link #buildSchedule} nunca lee ni escribe en base de datos — {@code CardMovementService} es
 * el responsable de persistir el {@code InstallmentPlan} resultante junto con cada
 * {@link Installment}.
 */
@Service
public class AmortizationService {

    /**
     * Construye el cronograma de {@code installmentCount} cuotas de capital fijo para una compra
     * de {@code amount}, con interés mensual {@code monthlyRate} calculado sobre el saldo
     * pendiente de esta compra.
     *
     * <p>Algoritmo (BigDecimal, escala 2, HALF_UP):
     * <ol>
     *   <li>{@code capitalPerInstallment = amount / installmentCount}, redondeado HALF_UP.</li>
     *   <li>El residuo del redondeo
     *       ({@code amount - capitalPerInstallment * installmentCount}) se suma al capital de la
     *       ÚLTIMA cuota, de modo que la suma de capitales sea exactamente {@code amount}.</li>
     *   <li><b>Resguardo anti-capital-negativo:</b> si el capital fijo, o el de la última cuota
     *       con el residuo ya aplicado, quedara {@code <= 0}, se rechaza la compra completa ANTES
     *       de construir el cronograma — nunca se devuelve una cuota inválida. Sin esto, compras
     *       muy chicas a muchas cuotas (ej. $0,35 a 48 cuotas) generarían una última cuota con
     *       capital negativo.</li>
     *   <li>El interés de la cuota k se calcula sobre el saldo pendiente de esta compra ANTES de
     *       restarle el capital de esa cuota, por lo que es estrictamente decreciente.</li>
     *   <li>La {@code dueDate} de la primera cuota es la próxima fecha de corte de la tarjeta
     *       igual o posterior a {@code purchaseDate}; las siguientes suman un mes cada una.</li>
     * </ol>
     *
     * @param amount           monto total de la compra, siempre positivo
     * @param installmentCount número de cuotas, siempre &gt;= 2 (las compras a 1 cuota no pasan
     *                         por este servicio)
     * @param monthlyRate      tasa mensual efectiva vigente de la tarjeta al momento de la
     *                         compra; quien llama es responsable de congelarla como
     *                         {@code rateAtPurchase}
     * @param purchaseDate     fecha de la compra
     * @param card             tarjeta de la compra, usada únicamente para leer su
     *                         {@code cutoffDay} al calcular el vencimiento de la primera cuota
     * @return la lista de {@link Installment} en estado {@link InstallmentStatus#PENDING}, sin
     *         persistir ni asociar todavía a un {@code InstallmentPlan}
     * @throws InstallmentAmountTooLowException si el monto es demasiado bajo para la cantidad de
     *                                          cuotas pedida
     */
    public List<Installment> buildSchedule(
            BigDecimal amount,
            int installmentCount,
            BigDecimal monthlyRate,
            LocalDate purchaseDate,
            CreditCard card
    ) {
        BigDecimal installmentCountDecimal = BigDecimal.valueOf(installmentCount);
        BigDecimal capitalPerInstallment = amount.divide(installmentCountDecimal, 2, RoundingMode.HALF_UP);
        BigDecimal roundingRemainder = amount.subtract(capitalPerInstallment.multiply(installmentCountDecimal));
        BigDecimal lastInstallmentCapital = capitalPerInstallment.add(roundingRemainder);

        if (capitalPerInstallment.compareTo(BigDecimal.ZERO) <= 0
                || lastInstallmentCapital.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InstallmentAmountTooLowException();
        }

        LocalDate firstCycleClose = nextCutoffDate(purchaseDate, card.getCutoffDay());
        BigDecimal outstanding = amount;
        List<Installment> schedule = new ArrayList<>(installmentCount);

        for (int number = 1; number <= installmentCount; number++) {
            BigDecimal capital = number < installmentCount ? capitalPerInstallment : lastInstallmentCapital;
            BigDecimal interest = outstanding.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);

            Installment installment = new Installment();
            installment.setNumber(number);
            installment.setCapitalAmount(capital);
            installment.setInterestAmount(interest);
            installment.setDueDate(firstCycleClose.plusMonths(number - 1L));
            installment.setStatus(InstallmentStatus.PENDING);
            schedule.add(installment);

            outstanding = outstanding.subtract(capital);
        }

        return schedule;
    }

    /**
     * Próxima fecha de corte igual o posterior a {@code from}: el día {@code cutoffDay} del mes de
     * {@code from} si todavía no pasó, o el mismo día del mes siguiente si ya pasó. En meses más
     * cortos que {@code cutoffDay} (por ejemplo {@code cutoffDay = 31} en febrero) se ajusta al
     * último día calendario de ese mes.
     */
    private LocalDate nextCutoffDate(LocalDate from, int cutoffDay) {
        LocalDate candidate = cutoffDateInMonth(from.getYear(), from.getMonthValue(), cutoffDay);
        if (!candidate.isBefore(from)) {
            return candidate;
        }
        LocalDate nextMonth = from.plusMonths(1);
        return cutoffDateInMonth(nextMonth.getYear(), nextMonth.getMonthValue(), cutoffDay);
    }

    private LocalDate cutoffDateInMonth(int year, int month, int cutoffDay) {
        YearMonth yearMonth = YearMonth.of(year, month);
        int day = Math.min(cutoffDay, yearMonth.lengthOfMonth());
        return LocalDate.of(year, month, day);
    }
}
