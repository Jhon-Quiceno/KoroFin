package com.korofin.backend.income.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.expense.entity.CategoryType;
import com.korofin.backend.income.entity.Income;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.common.repository.MonthlyTotalProjection;
import com.korofin.backend.common.repository.UserLastActivityProjection;
import com.korofin.backend.expense.repository.CategoryRepository;
import com.korofin.backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class IncomeRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByIdAndUser_IdReturnsEmptyForAnotherUsersIncome() {
        User owner = userRepository.saveAndFlush(newUser("owner-inc@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("other-inc@korofin.dev"));
        Income income = incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.TEN, LocalDate.of(2026, 1, 5)));

        assertThat(incomeRepository.findByIdAndUser_Id(income.getId(), owner.getId())).isPresent();
        assertThat(incomeRepository.findByIdAndUser_Id(income.getId(), other.getId())).isEmpty();
    }

    @Test
    void findAllWithSpecificationAppliesOwnershipAndPeriodFilters() {
        User owner = userRepository.saveAndFlush(newUser("spec-inc@korofin.dev"));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.valueOf(1000), LocalDate.of(2026, 6, 5)));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.valueOf(2000), LocalDate.of(2026, 7, 5)));

        var spec = IncomeSpecifications.ownedBy(owner.getId())
                .and(IncomeSpecifications.inPeriod(6, 2026));

        List<Income> result = incomeRepository.findAll(spec);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void findTopCategoriesByUserAndPeriodGroupsAndOrdersByTotalDescending() {
        User owner = userRepository.saveAndFlush(newUser("top-cat-inc@korofin.dev"));
        Category salary = categoryRepository.saveAndFlush(newCategory(owner, "Salario"));
        Category freelance = categoryRepository.saveAndFlush(newCategory(owner, "Freelance"));
        incomeRepository.saveAndFlush(newIncome(owner, salary, BigDecimal.valueOf(3000), LocalDate.of(2026, 2, 1)));
        incomeRepository.saveAndFlush(newIncome(owner, salary, BigDecimal.valueOf(200), LocalDate.of(2026, 2, 10)));
        incomeRepository.saveAndFlush(newIncome(owner, freelance, BigDecimal.valueOf(500), LocalDate.of(2026, 2, 15)));

        List<IncomeCategoryTotalProjection> result = incomeRepository.findTopCategoriesByUserAndPeriod(
                owner.getId(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCategoryName()).isEqualTo("Salario");
        assertThat(result.get(0).getTotal()).isEqualByComparingTo("3200");
        assertThat(result.get(1).getCategoryName()).isEqualTo("Freelance");
    }

    @Test
    void sumAmountByUserGroupedByMonthReturnsOneRowPerMonthWithIncomes() {
        User owner = userRepository.saveAndFlush(newUser("monthly-inc@korofin.dev"));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.valueOf(1000), LocalDate.of(2026, 1, 5)));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.valueOf(500), LocalDate.of(2026, 1, 20)));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.valueOf(200), LocalDate.of(2026, 2, 1)));

        List<MonthlyTotalProjection> result = incomeRepository.sumAmountByUserGroupedByMonth(
                owner.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)
        );

        assertThat(result).hasSize(2);
        MonthlyTotalProjection january = result.stream().filter(row -> row.getPeriodMonth() == 1).findFirst().orElseThrow();
        assertThat(january.getTotal()).isEqualByComparingTo("1500");
    }

    @Test
    void sumAmountByUserAndPeriodSumsOnlyWithinTheGivenRange() {
        User owner = userRepository.saveAndFlush(newUser("sum-period-inc@korofin.dev"));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.valueOf(1000), LocalDate.of(2026, 3, 1)));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.valueOf(500), LocalDate.of(2026, 3, 15)));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.valueOf(999), LocalDate.of(2026, 4, 1)));

        BigDecimal total = incomeRepository.sumAmountByUserAndPeriod(
                owner.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)
        );

        assertThat(total).isEqualByComparingTo("1500");
    }

    @Test
    void findDistinctUserIdsByDateBetweenReturnsOnlyUsersWithActivityInWindow() {
        User active = userRepository.saveAndFlush(newUser("active-window-inc@korofin.dev"));
        User outside = userRepository.saveAndFlush(newUser("outside-window-inc@korofin.dev"));
        incomeRepository.saveAndFlush(newIncome(active, null, BigDecimal.TEN, LocalDate.of(2026, 5, 5)));
        incomeRepository.saveAndFlush(newIncome(outside, null, BigDecimal.TEN, LocalDate.of(2026, 1, 1)));

        List<Long> ids = incomeRepository.findDistinctUserIdsByDateBetween(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)
        );

        assertThat(ids).contains(active.getId());
        assertThat(ids).doesNotContain(outside.getId());
    }

    @Test
    void findLatestIncomeDatePerUserReturnsTheMostRecentDatePerUser() {
        User owner = userRepository.saveAndFlush(newUser("latest-inc@korofin.dev"));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.TEN, LocalDate.of(2026, 1, 1)));
        incomeRepository.saveAndFlush(newIncome(owner, null, BigDecimal.TEN, LocalDate.of(2026, 1, 20)));

        List<UserLastActivityProjection> result = incomeRepository.findLatestIncomeDatePerUser();

        UserLastActivityProjection row = result.stream()
                .filter(projection -> projection.getUserId().equals(owner.getId()))
                .findFirst().orElseThrow();
        assertThat(row.getLastDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    private Income newIncome(User owner, Category category, BigDecimal amount, LocalDate date) {
        Income income = new Income();
        income.setUser(owner);
        income.setCategory(category);
        income.setAmount(amount);
        income.setDate(date);
        return income;
    }

    private Category newCategory(User owner, String name) {
        Category category = new Category();
        category.setUser(owner);
        category.setName(name);
        category.setType(CategoryType.INCOME);
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
