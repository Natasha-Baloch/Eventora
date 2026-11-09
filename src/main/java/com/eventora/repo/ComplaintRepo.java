package com.eventora.repo;

import com.eventora.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComplaintRepo extends JpaRepository<Complaint, Long> {
    List<Complaint> findAllByOrderByCreatedAtDesc();
    List<Complaint> findByUserOrderByCreatedAtDesc(AppUser u);
}
