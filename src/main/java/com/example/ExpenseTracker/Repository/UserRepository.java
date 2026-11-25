package com.example.ExpenseTracker.Repository;

import com.example.ExpenseTracker.Model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User,Long> {


}
