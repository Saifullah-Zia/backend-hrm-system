package com.hrm.system.repository;
import com.hrm.system.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    List<Announcement> findByActiveTrue();
}