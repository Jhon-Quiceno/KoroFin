package com.korofin.backend.expense.exception;

/**
 * Se lanza al crear o actualizar una categoría con un nombre ya usado por el mismo usuario para
 * el mismo {@code CategoryType}. Mapeada a {@code 409 Conflict}, siguiendo el mismo criterio que
 * {@code EmailAlreadyExistsException} para el dominio {@code user}.
 */
public class DuplicateCategoryException extends RuntimeException {

    public DuplicateCategoryException(String message) {
        super(message);
    }
}
