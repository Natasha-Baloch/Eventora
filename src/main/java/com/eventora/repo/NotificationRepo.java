package com.eventora.repo;

import com.eventora.model.*;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface NotificationRepo extends JpaRepository<Notification, Long> {
    List<Notification> findTop50ByUserOrderByCreatedAtDesc(AppUser user);
    long countByUserAndSeenFalse(AppUser user);
    @Modifying @Query("update Notification n set n.seen = true where n.user = :u")
    int markAllSeen(@Param("u") AppUser u);
}
