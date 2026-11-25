package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.Group;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository  extends JpaRepository<Group,Long> {
}
