package com.korofin.backend.service.integration.telegram;

import com.korofin.backend.exception.integration.TelegramImplausibleMovementException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramMessageParserTest {

    private final TelegramMessageParser parser = new TelegramMessageParser();

    @Test
    void parsesASimpleAmountAndDescription() {
        TelegramMessageParser.ParsedMovement result = parser.parse("Uber 15000");

        assertThat(result.amount()).isEqualByComparingTo("15000");
        assertThat(result.description()).isEqualTo("Uber");
    }

    @Test
    void parsesAnAmountWithThousandsSeparators() {
        TelegramMessageParser.ParsedMovement result = parser.parse("Mercado $45.000");

        assertThat(result.amount()).isEqualByComparingTo("45000");
        assertThat(result.description()).isEqualTo("Mercado");
    }

    @Test
    void usesDefaultDescriptionWhenOnlyAnAmountIsGiven() {
        TelegramMessageParser.ParsedMovement result = parser.parse("15000");

        assertThat(result.description()).isEqualTo("Movimiento por Telegram");
    }

    @Test
    void throwsImplausibleMovementWhenNoAmountIsFound() {
        assertThatThrownBy(() -> parser.parse("hola como estas"))
                .isInstanceOf(TelegramImplausibleMovementException.class);
    }

    @Test
    void throwsImplausibleMovementWhenAmountIsTooLow() {
        assertThatThrownBy(() -> parser.parse("Chicle 5"))
                .isInstanceOf(TelegramImplausibleMovementException.class);
    }

    @Test
    void throwsImplausibleMovementWhenAmountIsTooHigh() {
        assertThatThrownBy(() -> parser.parse("Carro 500000000"))
                .isInstanceOf(TelegramImplausibleMovementException.class);
    }
}
