package com.hrm.system.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Removed @Autowired @Lazy private JwtFilter jwtFilter;
    // JwtFilter is now injected as a method parameter in filterChain() below.
    // This avoids Spring creating a CGLIB proxy for JwtFilter, which fails because
    // GenericFilterBean.init() is final and cannot be overridden by the proxy.

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "https://jcatsolutions-hrm.vercel.app"
        ));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtFilter jwtFilter) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**", "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/swagger-resources/**", "/webjars/**").permitAll()
                        .requestMatchers("/api/users/**").permitAll()
                        .requestMatchers("/ws-chat/**").permitAll()
                        .requestMatchers("/api/hikvision/webhook").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/employee-profiles/me").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/employee-profiles/user/**").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/employee-profiles/**").hasAnyRole("ADMIN", "SUPERADMIN", "EMPLOYEE")
                        .requestMatchers(HttpMethod.POST, "/api/employee-profiles/salary-otp/request", "/api/employee-profiles/salary-otp/verify").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/employee-profiles", "/api/employee-profiles/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/employee-profiles/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/employee-profiles/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/resignations").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/attendance/checkout").hasAnyRole("EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/attendance/checkin").hasAnyRole("EMPLOYEE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/chat/files/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/leaves/files/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/documents/files/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/notices/user/**").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")
                        .requestMatchers("/api/notices/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/attendance/user/*/paged").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/attendance/checkin", "/api/attendance/checkout").permitAll()
                        .requestMatchers("/api/attendance/**").hasAnyRole("ADMIN", "SUPERADMIN")
                        // Payroll endpoints
                        .requestMatchers(HttpMethod.POST, "/api/payroll/generate", "/api/payroll/generate/bulk").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/payroll/*/approve", "/api/payroll/*/pay", "/api/payroll/*/regenerate").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payroll/period/*").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/payroll").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/payroll/*").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/payroll/*").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payroll", "/api/payroll/*").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payroll/user/*").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")
                        // Payroll period endpoints
                        .requestMatchers(HttpMethod.POST, "/api/payroll/periods", "/api/payroll/periods/*/lock", "/api/payroll/periods/*/unlock").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payroll/periods", "/api/payroll/periods/*", "/api/payroll/periods/check").authenticated()
                        // Payroll policy endpoints
                        .requestMatchers(HttpMethod.POST, "/api/payroll/policies", "/api/payroll/policies/*").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payroll/policies", "/api/payroll/policies/*", "/api/payroll/policies/active").hasAnyRole("ADMIN", "SUPERADMIN")
                        // Payslip endpoints
                        .requestMatchers(HttpMethod.POST, "/api/payslips/generate").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/payslips/*", "/api/payslips/download/*").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/holidays").hasAnyRole("EMPLOYEE", "ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/holidays", "/api/holidays/*").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/holidays/*").hasAnyRole("ADMIN", "SUPERADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/holidays/*").hasAnyRole("ADMIN", "SUPERADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}