package com.hrm.system.service;

import com.hrm.system.dto.UserDTO;
import com.hrm.system.model.ProbationStatus;
import com.hrm.system.model.User;
import com.hrm.system.repository.UserRepository;
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
    private ProbationService probationService;

    // ─── Create user ──────────────────────────────────────────────────────
    public UserDTO createUser(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);
        leaveBalanceService.initializeBalancesForUser(savedUser, LocalDate.now().getYear());
        // Start probation for newly created user
        probationService.startProbation(savedUser);
        return convertToDTO(savedUser);
    }

    // ─── Get all users ────────────────────────────────────────────────────
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ─── Get user by ID ───────────────────────────────────────────────────
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));
        return convertToDTO(user);
    }

    // ─── Get user by email ────────────────────────────────────────────────
    public UserDTO getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found with email " + email));
        return convertToDTO(user);
    }

    // ─── Update user ──────────────────────────────────────────────────────
    public UserDTO updateUser(Long id, User userDetails) {
        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));

        existingUser.setName(userDetails.getName());
        existingUser.setEmail(userDetails.getEmail());

        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }

        existingUser.setRole(userDetails.getRole());
        User updatedUser = userRepository.save(existingUser);
        return convertToDTO(updatedUser);
    }

    // ─── Delete user ──────────────────────────────────────────────────────
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));
        userRepository.delete(user);
    }

    // ─── Change password ──────────────────────────────────────────────────
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with ID " + id));

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("Old password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // ─── Get users on probation ───────────────────────────────────────────
    public List<UserDTO> getUsersOnProbation() {
        return userRepository.findByProbationStatus(ProbationStatus.ON_PROBATION)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ─── Get users with completed probation (pending HR confirmation) ─────
    public List<UserDTO> getUsersPendingProbationConfirmation() {
        return userRepository.findByProbationStatus(ProbationStatus.COMPLETED)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ─── Convert Entity → DTO ─────────────────────────────────────────────
    private UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getProbationStartDate(),
                user.getProbationEndDate(),
                user.getProbationStatus()
        );
    }

    // ─── Required by Spring Security ─────────────────────────────────────
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseGet(() -> userRepository.findByName(username)
                        .orElseThrow(() -> new UsernameNotFoundException(
                                "User not found with email or name: " + username)));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}