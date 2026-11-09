package com.eventora.repo;

import com.eventora.model.TicketCategory;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TicketCategoryRepo extends JpaRepository<TicketCategory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from TicketCategory c where c.id = :id")
    Optional<TicketCategory> lockById(@Param("id") Long id);
}
