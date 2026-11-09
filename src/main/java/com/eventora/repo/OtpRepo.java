package com.eventora.repo;

import com.eventora.model.OtpToken;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpRepo extends JpaRepository<OtpToken, Long> {
    Optional<OtpToken> findTopByEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    @Modifying @Query("delete from OtpToken o where o.expiresAt < :t")
    int purge(@Param("t") LocalDateTime t);
}
