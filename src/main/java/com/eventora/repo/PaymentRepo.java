package com.eventora.repo;

import com.eventora.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepo extends JpaRepository<Payment, Long> {
    List<Payment> findByBookingCustomerOrderByCreatedAtDesc(AppUser customer);
    List<Payment> findAllByOrderByCreatedAtDesc();
    List<Payment> findByBookingEventOrganizerOrderByCreatedAtDesc(AppUser organizer);
    List<Payment> findByBooking(Booking b);
}
