package com.hrm.system.service;

import com.hrm.system.dto.UserDTO;
import com.hrm.system.model.ProbationStatus;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
import com.hrm.system.repository.ResignationRepository;
import com.hrm.system.repository.DocumentRepository;
import com.hrm.system.repository.ConversationRepository;
import com.hrm.system.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    private ResignationRepository resignationRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    public UserDTO createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);
        leaveBalanceService.initializeBalancesForUser(savedUser, LocalDate.now().getYear());
        // Probation starts when HR saves the employee profile with a joining date.
        return convertToDTO(savedUser);
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));
        return convertToDTO(user);
    }

    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email " + email));
        return convertToDTO(user);
    }

    public UserDTO updateUser(Long id, User userDetails) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));

        existingUser.setName(userDetails.getName());
        existingUser.setEmail(userDetails.getEmail());

        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }

        existingUser.setRole(userDetails.getRole());
        return convertToDTO(userRepository.save(existingUser));
    }

    public UserDTO updateProfile(Long id, String name, String email) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));

        if (name != null && !name.isBlank()) {
            existingUser.setName(name.trim());
        }
        if (email != null && !email.isBlank()) {
            existingUser.setEmail(email.trim());
        }

        return convertToDTO(userRepository.save(existingUser));
    }

    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));
        
        // Nullify foreign keys referencing this user in other tables before deleting
        resignationRepository.nullifyApprovedBy(id);
        documentRepository.nullifyUploadedBy(id);
        conversationRepository.nullifyCreatedBy(id);
        auditLogRepository.nullifyPerformedBy(id);
        
        userRepository.delete(user);
    }

    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public List<UserDTO> getUsersOnProbation() {
        return userRepository.findByProbationStatus(ProbationStatus.ON_PROBATION)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<UserDTO> getUsersPendingProbationConfirmation() {
        return userRepository.findByProbationStatus(ProbationStatus.COMPLETED)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getProbationStartDate(),
                user.getProbationEndDate(),
                user.getProbationStatus(),
                user.isWebCheckInAllowed(),
                user.isOutsideAccessAllowed()
        );
    }

    /** Admin-only: enable or disable web-based check-in for a specific employee. */
    public UserDTO updateWebCheckInAccess(Long userId, boolean allowed) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + userId));
        user.setWebCheckInAllowed(allowed);
        return convertToDTO(userRepository.save(user));
    }

    /** Admin-only: enable or disable access outside Office Wi-Fi for a specific employee. */
    public UserDTO updateOutsideAccess(Long userId, boolean allowed) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + userId));
        user.setOutsideAccessAllowed(allowed);
        return convertToDTO(userRepository.save(user));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // JWT subject is name — try name first, then email as fallback
        User user = userRepository.findByName(username)
                .orElseGet(() -> userRepository.findByEmail(username)
                        .orElseThrow(() -> new UsernameNotFoundException(
                                "User not found: " + username)));

        return new com.hrm.system.security.CustomUserDetails(user);
    }
}