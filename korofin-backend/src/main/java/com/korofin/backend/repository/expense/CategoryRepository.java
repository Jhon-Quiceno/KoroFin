package com.korofin.backend.repository.expense;

import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.expense.CategoryType;
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
