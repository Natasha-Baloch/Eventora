package com.eventora.repo;

import com.eventora.model.*;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepo extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    List<AppUser> findAllByOrderByCreatedAtDesc();
    long countByRole(Role role);
}
