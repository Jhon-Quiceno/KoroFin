package com.korofin.backend.expense.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.expense.entity.CategoryType;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CategoryRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findAllByUser_IdOrderByNameAscReturnsOnlyOwnedCategoriesSortedByName() {
        User owner = userRepository.saveAndFlush(newUser("owner-cat@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("other-cat@korofin.dev"));
        categoryRepository.saveAndFlush(newCategory(owner, "Transporte", CategoryType.EXPENSE));
        categoryRepository.saveAndFlush(newCategory(owner, "Alimentación", CategoryType.EXPENSE));
        categoryRepository.saveAndFlush(newCategory(other, "Ajena", CategoryType.EXPENSE));

        List<Category> result = categoryRepository.findAllByUser_IdOrderByNameAsc(owner.getId());

        assertThat(result).extracting(Category::getName).containsExactly("Alimentación", "Transporte");
    }

    @Test
    void findAllByUser_IdAndTypeOrderByNameAscFiltersByType() {
        User owner = userRepository.saveAndFlush(newUser("filter-cat@korofin.dev"));
        categoryRepository.saveAndFlush(newCategory(owner, "Salario", CategoryType.INCOME));
        categoryRepository.saveAndFlush(newCategory(owner, "Comida", CategoryType.EXPENSE));

        List<Category> result = categoryRepository.findAllByUser_IdAndTypeOrderByNameAsc(owner.getId(), CategoryType.INCOME);

        assertThat(result).extracting(Category::getName).containsExactly("Salario");
    }

    @Test
    void findByIdAndUser_IdReturnsEmptyForAnotherUsersCategory() {
        User owner = userRepository.saveAndFlush(newUser("owner-find@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("other-find@korofin.dev"));
        Category category = categoryRepository.saveAndFlush(newCategory(owner, "Ahorro", CategoryType.EXPENSE));

        assertThat(categoryRepository.findByIdAndUser_Id(category.getId(), owner.getId())).isPresent();
        assertThat(categoryRepository.findByIdAndUser_Id(category.getId(), other.getId())).isEmpty();
    }

    @Test
    void existsByUser_IdAndNameIgnoreCaseAndTypeIsCaseInsensitive() {
        User owner = userRepository.saveAndFlush(newUser("exists-cat@korofin.dev"));
        categoryRepository.saveAndFlush(newCategory(owner, "Ocio", CategoryType.EXPENSE));

        assertThat(categoryRepository.existsByUser_IdAndNameIgnoreCaseAndType(owner.getId(), "OCIO", CategoryType.EXPENSE)).isTrue();
        assertThat(categoryRepository.existsByUser_IdAndNameIgnoreCaseAndType(owner.getId(), "Ocio", CategoryType.INCOME)).isFalse();
    }

    @Test
    void uniqueConstraintOnUserNameTypeIsEnforcedByTheDatabase() {
        User owner = userRepository.saveAndFlush(newUser("dup-cat@korofin.dev"));
        categoryRepository.saveAndFlush(newCategory(owner, "Duplicada", CategoryType.EXPENSE));

        assertThatThrownBy(() -> categoryRepository.saveAndFlush(newCategory(owner, "Duplicada", CategoryType.EXPENSE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Category newCategory(User owner, String name, CategoryType type) {
        Category category = new Category();
        category.setUser(owner);
        category.setName(name);
        category.setType(type);
        return category;
    }

    private User newUser(String email) {
        User user = new User();
        user.setName("Test");
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setActive(true);
        return user;
    }
}
