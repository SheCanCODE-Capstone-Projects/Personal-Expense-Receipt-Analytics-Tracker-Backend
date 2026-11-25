package com.example.ExpenseTracker.Config;

import com.example.ExpenseTracker.Model.Category;
import com.example.ExpenseTracker.Model.ETransactionType;
import com.example.ExpenseTracker.Repository.CategoryRepository;
import com.example.ExpenseTracker.service.CategoryService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/** Ensures a fresh database has shared categories so transactions can be created immediately. */
@Component
@Order(1)
public class DefaultCategorySeeder implements ApplicationRunner {

    static final Map<ETransactionType, List<String>> DEFAULTS = Map.of(
            ETransactionType.EXPENSE, List.of(
                    "Food & Groceries", "Transport", "Rent & Housing", "Utilities", "Airtime & Data",
                    "Health", "Education", "Entertainment", "Shopping", CategoryService.FALLBACK_EXPENSE),
            ETransactionType.INCOME, List.of(
                    "Salary", "Business", "Gifts", CategoryService.FALLBACK_INCOME));

    private final CategoryRepository categoryRepository;

    public DefaultCategorySeeder(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        DEFAULTS.forEach((type, names) -> names.forEach(name -> {
            if (categoryRepository.findByUserIsNullAndNameIgnoreCaseAndType(name, type).isEmpty()) {
                Category category = new Category();
                category.setName(name);
                category.setType(type);
                categoryRepository.save(category);
            }
        }));
    }
}
