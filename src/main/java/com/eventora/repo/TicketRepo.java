package com.eventora.repo;

import com.eventora.model.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface TicketRepo extends JpaRepository<Ticket, Long> {
    List<Ticket> findByBookingCustomerOrderByIdDesc(AppUser customer);
    List<Ticket> findByBookingEventOrganizerOrderByIdDesc(AppUser organizer);
    List<Ticket> findByBooking(Booking b);
    Optional<Ticket> findByCode(String code);

    /** Atomic check-in: returns 1 only for the first successful scan. */
    @Modifying @Query("update Ticket t set t.checkedIn = true, t.checkedInAt = :now where t.id = :id and t.checkedIn = false and t.voided = false")
    int markCheckedIn(@Param("id") Long id, @Param("now") LocalDateTime now);
}
