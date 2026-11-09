package com.eventora.repo;

import com.eventora.model.*;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepo extends JpaRepository<Event, Long> {
    List<Event> findByStatusAndEventDateGreaterThanEqualOrderByEventDateAsc(EventStatus s, LocalDate d);
    List<Event> findByOrganizerOrderByCreatedAtDesc(AppUser organizer);
    List<Event> findAllByOrderByCreatedAtDesc();
    long countByStatus(EventStatus s);
}
