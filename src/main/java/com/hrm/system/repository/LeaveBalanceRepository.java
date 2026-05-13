package com.hrm.system.repository;

import com.hrm.system.model.LeaveBalance;
import com.hrm.system.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByUserIdAndLeaveTypeAndYear(Long userId, String leaveType, int year);

    List<LeaveBalance> findByUserIdAndYear(Long userId, int year);

    List<LeaveBalance> findByYear(int year);

    /** Used during year-end carry-forward processing */
    List<LeaveBalance> findByUserAndYear(User user, int year);

    @Modifying
    @Query("UPDATE LeaveBalance lb SET lb.usedDays = lb.usedDays + :days " +
            "WHERE lb.user.id = :userId AND lb.leaveType = :leaveType AND lb.year = :year")
    void incrementUsedDays(@Param("userId") Long userId,
                           @Param("leaveType") String leaveType,
                           @Param("year") int year,
                           @Param("days") int days);

    @Modifying
    @Query("UPDATE LeaveBalance lb SET lb.usedDays = lb.usedDays - :days " +
            "WHERE lb.user.id = :userId AND lb.leaveType = :leaveType AND lb.year = :year")
    void decrementUsedDays(@Param("userId") Long userId,
                           @Param("leaveType") String leaveType,
                           @Param("year") int year,
                           @Param("days") int days);

    @Modifying
    @Query("UPDATE LeaveBalance lb SET lb.pendingDays = lb.pendingDays + :days " +
            "WHERE lb.user.id = :userId AND lb.leaveType = :leaveType AND lb.year = :year")
    void incrementPendingDays(@Param("userId") Long userId,
                              @Param("leaveType") String leaveType,
                              @Param("year") int year,
                              @Param("days") int days);

    @Modifying
    @Query("UPDATE LeaveBalance lb SET lb.pendingDays = lb.pendingDays - :days " +
            "WHERE lb.user.id = :userId AND lb.leaveType = :leaveType AND lb.year = :year")
    void decrementPendingDays(@Param("userId") Long userId,
                              @Param("leaveType") String leaveType,
                              @Param("year") int year,
                              @Param("days") int days);
}