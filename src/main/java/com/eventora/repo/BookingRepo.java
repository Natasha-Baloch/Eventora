package com.eventora.repo;

import com.eventora.model.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BookingRepo extends JpaRepository<Booking, Long> {
    List<Booking> findByCustomerOrderByCreatedAtDesc(AppUser customer);
    List<Booking> findByEventOrganizerOrderByCreatedAtDesc(AppUser organizer);
    List<Booking> findAllByOrderByCreatedAtDesc();
    List<Booking> findByStatusInAndExpiresAtBefore(Collection<BookingStatus> s, LocalDateTime t);
    long countByStatus(BookingStatus s);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> lockById(@Param("id") Long id);
}
