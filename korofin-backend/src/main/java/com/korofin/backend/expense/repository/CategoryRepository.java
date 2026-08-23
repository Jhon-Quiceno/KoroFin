package com.korofin.backend.expense.repository;

import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.expense.entity.CategoryType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Acceso a persistencia de {@link Category}, siempre delimitado por dueño.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByUser_IdOrderByNameAsc(Long userId);

    List<Category> findAllByUser_IdAndTypeOrderByNameAsc(Long userId, CategoryType type);

    Optional<Category> findByIdAndUser_Id(Long id, Long userId);

    boolean existsByUser_IdAndNameIgnoreCaseAndType(Long userId, String name, CategoryType type);
}
