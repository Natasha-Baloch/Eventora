package com.eventora.repo;

import com.eventora.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepo extends JpaRepository<Feedback, Long> {
    List<Feedback> findByEventOrganizerOrderByCreatedAtDesc(AppUser organizer);
    List<Feedback> findByCustomerOrderByCreatedAtDesc(AppUser customer);
    List<Feedback> findAllByOrderByCreatedAtDesc();
    boolean existsByBooking(Booking booking);
}
