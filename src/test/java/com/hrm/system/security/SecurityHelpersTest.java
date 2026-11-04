package com.hrm.system.security;

import com.hrm.system.config.JwtUtil;
import com.hrm.system.util.IpUtil;
import com.hrm.system.util.PageLimits;
import com.hrm.system.util.UploadRules;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class SecurityHelpersTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";
    private static final String OFFICE_IP = "58.65.129.12";

    @Test
    void jwtUtilRefusesMissingOrShortSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("", 1000));
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short", 1000));
    }

    @Test
    void jwtTokenRoundTripsAndRejectsOtherKeys() {
        JwtUtil util = new JwtUtil(SECRET, 60_000);
        String token = util.generateToken("a@b.c", "EMPLOYEE", 7L, "a@b.c");

        Claims claims = util.parseClaims(token);
        assertEquals(7L, JwtUtil.userIdOf(claims));
        assertNotNull(claims.getIssuedAt());

        JwtUtil other = new JwtUtil("another-secret-another-secret-987654", 60_000);
        assertThrows(JwtException.class, () -> other.parseClaims(token));
    }

    @Test
    void spoofedHeadersCannotFakeTheOfficeIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("CF-Connecting-IP", OFFICE_IP);
        request.addHeader("X-Real-IP", "203.0.113.9");
        request.addHeader("X-Forwarded-For", OFFICE_IP + ", 203.0.113.9");

        String clientIp = IpUtil.getClientIp(request);
        assertEquals("203.0.113.9", clientIp);
        assertFalse(IpUtil.isAllowedIp(clientIp, OFFICE_IP, false));
    }

    @Test
    void lastForwardedForEntryIsUsedWithoutRealIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", OFFICE_IP + ", 203.0.113.9");
        assertEquals("203.0.113.9", IpUtil.getClientIp(request));
    }

    @Test
    void rateLimiterBlocksAboveLimitAndRecoversAfterClear() {
        RateLimiter limiter = new RateLimiter();
        Duration window = Duration.ofMinutes(1);

        limiter.consume("k", 2, window);
        limiter.consume("k", 2, window);
        ResponseStatusException blocked =
                assertThrows(ResponseStatusException.class, () -> limiter.consume("k", 2, window));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, blocked.getStatusCode());

        limiter.clear("k");
        assertDoesNotThrow(() -> limiter.consume("k", 2, window));
    }

    @Test
    void rateLimiterWindowExpires() throws InterruptedException {
        RateLimiter limiter = new RateLimiter();
        limiter.consume("k", 1, Duration.ofMillis(20));
        Thread.sleep(40);
        assertDoesNotThrow(() -> limiter.consume("k", 1, Duration.ofMillis(20)));
    }

    @Test
    void uploadsAreLimitedToKnownTypes() {
        assertEquals("pdf", UploadRules.requireAllowedExtension(file("Medical.PDF")));
        assertThrows(ResponseStatusException.class, () -> UploadRules.requireAllowedExtension(file("page.html")));
        assertThrows(ResponseStatusException.class, () -> UploadRules.requireAllowedExtension(file("image.svg")));
        assertThrows(ResponseStatusException.class, () -> UploadRules.requireAllowedExtension(file("noextension")));
    }

    @Test
    void fileNamesLoseAnyPath() {
        assertEquals("passwd", UploadRules.safeFileName("../../etc/passwd"));
        assertEquals("evil.pdf", UploadRules.safeFileName("..\\..\\evil.pdf"));
        assertFalse(UploadRules.safeFileName("a\"\r\nb.pdf").contains("\""));
    }

    @Test
    void unknownTypesAreNeverRenderedInline() {
        String disposition = UploadRules.fileResponse("old.html").build()
                .getHeaders().getFirst("Content-Disposition");
        assertTrue(disposition.startsWith("attachment"));
    }

    @Test
    void pageSizeIsCapped() {
        assertEquals(PageLimits.MAX_PAGE_SIZE, PageLimits.of(0, 5_000_000, Sort.unsorted()).getPageSize());
        assertEquals(1, PageLimits.of(-1, 0, Sort.unsorted()).getPageSize());
    }

    private static MockMultipartFile file(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream", new byte[]{1});
    }
}
