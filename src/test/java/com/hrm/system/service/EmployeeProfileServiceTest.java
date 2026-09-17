package com.hrm.system.service;

import com.hrm.system.dto.EmployeeProfileDto;
import com.hrm.system.model.EmployeeProfile;
import com.hrm.system.model.Role;
import com.hrm.system.model.User;
import com.hrm.system.repository.EmployeeProfileRepository;
import com.hrm.system.repository.UserRepository;
import com.hrm.system.util.AvatarImageUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmployeeProfileRepository employeeProfileRepository;

    @InjectMocks
    private EmployeeProfileService employeeProfileService;

    private User employeeUser;
    private User adminUser;
    private User superAdminUser;
    private EmployeeProfile employeeProfile;

    @BeforeEach
    void setUp() {
        employeeUser = new User();
        employeeUser.setId(10L);
        employeeUser.setName("Employee User");
        employeeUser.setEmail("employee@test.com");
        employeeUser.setRole(Role.EMPLOYEE);

        adminUser = new User();
        adminUser.setId(20L);
        adminUser.setName("Admin User");
        adminUser.setEmail("admin@test.com");
        adminUser.setRole(Role.ADMIN);

        superAdminUser = new User();
        superAdminUser.setId(30L);
        superAdminUser.setName("SuperAdmin User");
        superAdminUser.setEmail("superadmin@test.com");
        superAdminUser.setRole(Role.SUPERADMIN);

        employeeProfile = new EmployeeProfile();
        employeeProfile.setId(100L);
        employeeProfile.setUser(employeeUser);
        employeeProfile.setFirstName("Employee");
        employeeProfile.setLastName("User");
    }

    private byte[] createValidTestImageBytes() throws IOException {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    @Test
    void testMagicByteValidation_RejectsInvalidNonImageFile() {
        // Given a malicious file with a fake .png extension containing plain text/script
        MockMultipartFile fakePng = new MockMultipartFile(
                "file",
                "malicious.png",
                "image/png",
                "<?php echo 'malicious code'; ?>".getBytes()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            AvatarImageUtil.validateAndProcessAvatar(fakePng);
        });
    }

    @Test
    void testMagicByteValidation_AcceptsValidImage() throws IOException {
        byte[] imageBytes = createValidTestImageBytes();
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                imageBytes
        );

        AvatarImageUtil.ProcessedImage processed = AvatarImageUtil.validateAndProcessAvatar(validFile);
        assertNotNull(processed);
        assertNotNull(processed.data());
        assertTrue(processed.data().length > 0);
    }

    @Test
    void testRoleHierarchyGuard_AdminCannotModifySuperAdminAvatar() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                "fake".getBytes()
        );

        when(userRepository.findById(20L)).thenReturn(Optional.of(adminUser));
        when(userRepository.findById(30L)).thenReturn(Optional.of(superAdminUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> {
            employeeProfileService.uploadAvatar(20L, 30L, file);
        });

        assertEquals(403, ex.getStatusCode().value());
        assertTrue(ex.getReason().contains("Admins cannot modify SuperAdmin profile pictures"));
    }

    @Test
    void testUploadAvatar_SuccessForSelfUpload() throws IOException {
        byte[] imageBytes = createValidTestImageBytes();
        MockMultipartFile validFile = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                imageBytes
        );

        when(userRepository.findById(10L)).thenReturn(Optional.of(employeeUser));
        when(employeeProfileRepository.findByUserId(10L)).thenReturn(Optional.of(employeeProfile));
        when(employeeProfileRepository.save(any(EmployeeProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmployeeProfileDto result = employeeProfileService.uploadAvatar(10L, 10L, validFile);

        assertNotNull(result);
        assertNotNull(result.getProfilePicture());
        assertTrue(result.getProfilePicture().startsWith("/api/employee-profiles/avatars/avatar_10_"));
    }
}
