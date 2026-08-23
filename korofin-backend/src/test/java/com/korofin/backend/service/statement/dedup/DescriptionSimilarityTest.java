package com.korofin.backend.service.statement.dedup;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DescriptionSimilarityTest {

    @Test
    void normalizeLowercasesRemovesAccentsAndCollapsesWhitespace() {
        assertThat(DescriptionSimilarity.normalize("  Supermercado Éxito!!  ")).isEqualTo("supermercado exito");
    }

    @Test
    void normalizeReturnsEmptyStringForNull() {
        assertThat(DescriptionSimilarity.normalize(null)).isEmpty();
    }

    @Test
    void isSimilarReturnsTrueForIdenticalDescriptionsWithDifferentCaseAndAccents() {
        assertThat(DescriptionSimilarity.isSimilar("Supermercado Éxito", "SUPERMERCADO EXITO")).isTrue();
    }

    @Test
    void isSimilarReturnsTrueWhenOneDescriptionContainsTheOther() {
        assertThat(DescriptionSimilarity.isSimilar("Uber", "Uber *Trip Help.uber.com")).isTrue();
    }

    @Test
    void isSimilarReturnsTrueWhenJaccardOverlapMeetsThreshold() {
        assertThat(DescriptionSimilarity.isSimilar(
                "Pago transferencia Nequi Juan Perez",
                "Transferencia recibida Nequi Juan"
        )).isTrue();
    }

    @Test
    void isSimilarReturnsFalseForUnrelatedDescriptions() {
        assertThat(DescriptionSimilarity.isSimilar("Netflix suscripcion mensual", "Farmacia La Rebaja compra")).isFalse();
    }

    @Test
    void isSimilarReturnsTrueWhenEitherDescriptionIsBlank() {
        assertThat(DescriptionSimilarity.isSimilar("", "Cualquier cosa")).isTrue();
        assertThat(DescriptionSimilarity.isSimilar(null, "Cualquier cosa")).isTrue();
        assertThat(DescriptionSimilarity.isSimilar("Cualquier cosa", "   ")).isTrue();
    }
}
