package com.korofin.backend.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload de {@code POST /api/receipts/scan}: la foto de un recibo tomada desde la cámara de la
 * app móvil, autenticada por JWT estándar.
 *
 * @param imageDataUri la imagen del recibo como data URI {@code data:image/jpeg;base64,...}. El
 *                      cliente es la app móvil tomando una foto con la cámara — un tamaño acotado
 *                      y predecible (~11MB crudos en base64) — por eso el {@code @Size}: sin tope,
 *                      un usuario autenticado podría bufferizar payloads arbitrariamente grandes
 *                      en memoria antes de que se valide nada.
 */
public record ReceiptScanRequest(
        @NotBlank(message = "La imagen es obligatoria")
        @Size(max = 15_000_000, message = "La imagen es demasiado grande")
        String imageDataUri
) {
}
