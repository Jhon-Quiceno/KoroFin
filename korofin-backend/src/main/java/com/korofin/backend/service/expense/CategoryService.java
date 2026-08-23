package com.korofin.backend.service.expense;

import com.korofin.backend.dto.expense.CategoryRequest;
import com.korofin.backend.dto.expense.CategoryResponse;
import com.korofin.backend.entity.expense.Category;
import com.korofin.backend.entity.expense.CategoryType;
import com.korofin.backend.exception.ResourceNotFoundException;
import com.korofin.backend.exception.expense.DuplicateCategoryException;
import com.korofin.backend.mapper.expense.CategoryMapper;
import com.korofin.backend.repository.expense.CategoryRepository;
import com.korofin.backend.repository.user.UserRepository;
import com.korofin.backend.security.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lógica de negocio para administrar las categorías del usuario actual.
 *
 * <p>Toda operación resuelve al llamador vía {@link SecurityUtils#getCurrentUserId()} y limita
 * lecturas/escrituras estrictamente a ese usuario. Las mutaciones sobre una categoría ajena
 * lanzan {@link ResourceNotFoundException} (404) en vez de 403, para no filtrar su existencia a
 * quien no es su dueño.
 */
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final CategoryMapper categoryMapper;

    public CategoryService(
            CategoryRepository categoryRepository,
            UserRepository userRepository,
            CategoryMapper categoryMapper
    ) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.categoryMapper = categoryMapper;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(CategoryType type) {
        Long userId = SecurityUtils.getCurrentUserId();
        List<Category> categories = type == null
                ? categoryRepository.findAllByUser_IdOrderByNameAsc(userId)
                : categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(userId, type);

        return categories.stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        String normalizedName = request.name().trim();
        validateNameNotDuplicated(userId, normalizedName, request.type(), null);

        Category category = categoryMapper.toEntity(request);
        category.setName(normalizedName);
        category.setUser(userRepository.getReferenceById(userId));

        Category savedCategory = categoryRepository.save(category);
        return categoryMapper.toResponse(savedCategory);
    }

    @Transactional
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        Category category = findOwnedCategory(categoryId, userId);

        String normalizedName = request.name().trim();
        validateNameNotDuplicated(userId, normalizedName, request.type(), category);

        categoryMapper.updateEntityFromRequest(request, category);
        category.setName(normalizedName);

        Category updatedCategory = categoryRepository.save(category);
        return categoryMapper.toResponse(updatedCategory);
    }

    /**
     * Borra la categoría. La FK de {@code category_id} en {@code expenses}/{@code incomes} usa
     * {@code ON DELETE SET NULL} (ver {@code V2__create_categories_expenses_incomes.sql}), así
     * que borrar una categoría en uso no falla: los gastos/ingresos que la referenciaban quedan
     * sin clasificar en vez de bloquear el borrado — mismo comportamiento que FinSmart.
     */
    @Transactional
    public void deleteCategory(Long categoryId) {
        Long userId = SecurityUtils.getCurrentUserId();
        Category category = findOwnedCategory(categoryId, userId);
        categoryRepository.delete(category);
    }

    private Category findOwnedCategory(Long categoryId, Long userId) {
        return categoryRepository.findByIdAndUser_Id(categoryId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
    }

    private void validateNameNotDuplicated(Long userId, String name, CategoryType type, Category categoryBeingUpdated) {
        boolean unchanged = categoryBeingUpdated != null
                && categoryBeingUpdated.getName().equalsIgnoreCase(name)
                && categoryBeingUpdated.getType() == type;
        if (unchanged) {
            return;
        }

        if (categoryRepository.existsByUser_IdAndNameIgnoreCaseAndType(userId, name, type)) {
            throw new DuplicateCategoryException("Ya existe una categoría con ese nombre y tipo");
        }
    }
}
