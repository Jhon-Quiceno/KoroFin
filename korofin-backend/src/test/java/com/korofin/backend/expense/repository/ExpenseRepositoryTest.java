package com.korofin.backend.expense.repository;

import com.korofin.backend.PostgresContainerSupport;
import com.korofin.backend.expense.entity.Category;
import com.korofin.backend.expense.entity.CategoryType;
import com.korofin.backend.expense.entity.Expense;
import com.korofin.backend.expense.entity.PaymentMethodType;
import com.korofin.backend.user.entity.User;
import com.korofin.backend.common.repository.MonthlyTotalProjection;
import com.korofin.backend.common.repository.UserLastActivityProjection;
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
class ExpenseRepositoryTest implements PostgresContainerSupport {

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByIdAndUser_IdReturnsEmptyForAnotherUsersExpense() {
        User owner = userRepository.saveAndFlush(newUser("owner-exp@korofin.dev"));
        User other = userRepository.saveAndFlush(newUser("other-exp@korofin.dev"));
        Expense expense = expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.TEN, LocalDate.of(2026, 1, 5)));

        assertThat(expenseRepository.findByIdAndUser_Id(expense.getId(), owner.getId())).isPresent();
        assertThat(expenseRepository.findByIdAndUser_Id(expense.getId(), other.getId())).isEmpty();
    }

    @Test
    void findAllWithSpecificationAppliesOwnershipAndDateRangeFilters() {
        User owner = userRepository.saveAndFlush(newUser("spec-exp@korofin.dev"));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.valueOf(100), LocalDate.of(2026, 1, 5)));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.valueOf(200), LocalDate.of(2026, 3, 5)));

        var spec = ExpenseSpecifications.ownedBy(owner.getId())
                .and(ExpenseSpecifications.dateFrom(LocalDate.of(2026, 1, 1)))
                .and(ExpenseSpecifications.dateTo(LocalDate.of(2026, 1, 31)));

        List<Expense> result = expenseRepository.findAll(spec);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAmount()).isEqualByComparingTo("100");
    }

    @Test
    void findTopCategoriesByUserAndPeriodGroupsAndOrdersByTotalDescending() {
        User owner = userRepository.saveAndFlush(newUser("top-cat@korofin.dev"));
        Category food = categoryRepository.saveAndFlush(newCategory(owner, "Comida"));
        Category transport = categoryRepository.saveAndFlush(newCategory(owner, "Transporte"));
        expenseRepository.saveAndFlush(newExpense(owner, food, BigDecimal.valueOf(300), LocalDate.of(2026, 2, 1)));
        expenseRepository.saveAndFlush(newExpense(owner, food, BigDecimal.valueOf(200), LocalDate.of(2026, 2, 10)));
        expenseRepository.saveAndFlush(newExpense(owner, transport, BigDecimal.valueOf(100), LocalDate.of(2026, 2, 15)));

        List<CategoryTotalProjection> result = expenseRepository.findTopCategoriesByUserAndPeriod(
                owner.getId(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)
        );

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCategoryName()).isEqualTo("Comida");
        assertThat(result.get(0).getTotal()).isEqualByComparingTo("500");
        assertThat(result.get(1).getCategoryName()).isEqualTo("Transporte");
    }

    @Test
    void sumAmountByUserGroupedByMonthReturnsOneRowPerMonthWithExpenses() {
        User owner = userRepository.saveAndFlush(newUser("monthly-exp@korofin.dev"));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.valueOf(50), LocalDate.of(2026, 1, 5)));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.valueOf(70), LocalDate.of(2026, 1, 20)));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.valueOf(30), LocalDate.of(2026, 2, 1)));

        List<MonthlyTotalProjection> result = expenseRepository.sumAmountByUserGroupedByMonth(
                owner.getId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 28)
        );

        assertThat(result).hasSize(2);
        MonthlyTotalProjection january = result.stream().filter(row -> row.getPeriodMonth() == 1).findFirst().orElseThrow();
        assertThat(january.getTotal()).isEqualByComparingTo("120");
    }

    @Test
    void sumAmountByUserAndPeriodSumsOnlyWithinTheGivenRange() {
        User owner = userRepository.saveAndFlush(newUser("sum-period-exp@korofin.dev"));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.valueOf(100), LocalDate.of(2026, 3, 1)));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.valueOf(200), LocalDate.of(2026, 3, 15)));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.valueOf(999), LocalDate.of(2026, 4, 1)));

        BigDecimal total = expenseRepository.sumAmountByUserAndPeriod(
                owner.getId(), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)
        );

        assertThat(total).isEqualByComparingTo("300");
    }

    @Test
    void findDistinctUserIdsByDateBetweenReturnsOnlyUsersWithActivityInWindow() {
        User active = userRepository.saveAndFlush(newUser("active-window@korofin.dev"));
        User outside = userRepository.saveAndFlush(newUser("outside-window@korofin.dev"));
        expenseRepository.saveAndFlush(newExpense(active, null, BigDecimal.TEN, LocalDate.of(2026, 5, 5)));
        expenseRepository.saveAndFlush(newExpense(outside, null, BigDecimal.TEN, LocalDate.of(2026, 1, 1)));

        List<Long> ids = expenseRepository.findDistinctUserIdsByDateBetween(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)
        );

        assertThat(ids).contains(active.getId());
        assertThat(ids).doesNotContain(outside.getId());
    }

    @Test
    void findLatestExpenseDatePerUserReturnsTheMostRecentDatePerUser() {
        User owner = userRepository.saveAndFlush(newUser("latest-exp@korofin.dev"));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.TEN, LocalDate.of(2026, 1, 1)));
        expenseRepository.saveAndFlush(newExpense(owner, null, BigDecimal.TEN, LocalDate.of(2026, 1, 20)));

        List<UserLastActivityProjection> result = expenseRepository.findLatestExpenseDatePerUser();

        UserLastActivityProjection row = result.stream()
                .filter(projection -> projection.getUserId().equals(owner.getId()))
                .findFirst().orElseThrow();
        assertThat(row.getLastDate()).isEqualTo(LocalDate.of(2026, 1, 20));
    }

    private Expense newExpense(User owner, Category category, BigDecimal amount, LocalDate date) {
        Expense expense = new Expense();
        expense.setUser(owner);
        expense.setCategory(category);
        expense.setAmount(amount);
        expense.setDate(date);
        expense.setPaymentMethod(PaymentMethodType.CASH);
        return expense;
    }

    private Category newCategory(User owner, String name) {
        Category category = new Category();
        category.setUser(owner);
        category.setName(name);
        category.setType(CategoryType.EXPENSE);
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
