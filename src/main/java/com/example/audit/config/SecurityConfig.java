package com.example.audit.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = PageSerializationMode.VIA_DTO)
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            @Value("${security.write-api.username}") String writerUsername,
            @Value("${security.write-api.password}") String writerPassword,
            @Value("${security.audit-api.username}") String auditorUsername,
            @Value("${security.audit-api.password}") String auditorPassword,
            PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.withUsername(writerUsername)
                        .password(passwordEncoder.encode(writerPassword))
                        .roles("WRITER")
                        .build(),
                User.withUsername(auditorUsername)
                        .password(passwordEncoder.encode(auditorPassword))
                        .roles("AUDITOR")
                        .build()
        );
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(new AntPathRequestMatcher("/actuator/health"),
                                new AntPathRequestMatcher("/actuator/info")).permitAll()
                        .requestMatchers(new AntPathRequestMatcher("/api/v1/events", HttpMethod.POST.name()),
                                new AntPathRequestMatcher("/api/v1/events", HttpMethod.GET.name()),
                                new AntPathRequestMatcher("/api/v1/events/export", HttpMethod.GET.name()),
                                new AntPathRequestMatcher("/api/v1/events/*/redact", HttpMethod.POST.name()),
                                new AntPathRequestMatcher("/audit/verify", HttpMethod.GET.name()))
                        .hasRole("WRITER")
                        .requestMatchers(new AntPathRequestMatcher("/api/v1/compliance/**", HttpMethod.GET.name()))
                        .hasRole("AUDITOR")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
