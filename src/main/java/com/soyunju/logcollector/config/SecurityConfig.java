package com.soyunju.logcollector.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 및 세션 관리 설정 (Stateless API 기준)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 2. 엔드포인트별 접근 제어
                .authorizeHttpRequests(auth -> auth
                        // 로그 수집 API는 외부 서비스로부터 들어오므로 항상 허용
                        .requestMatchers(HttpMethod.POST, "/api/logs").permitAll()
                        // 모든 조회 API는 내부 개발자용이므로 인증 필요 (현재는 개발 편의를 위해 허용)
                        .requestMatchers(HttpMethod.GET, "/api/logs/**").permitAll()
                        // 삭제 및 AI 분석 요청은 특정 역할(ADMIN)만 가능하도록 설정 가능
                        .requestMatchers(HttpMethod.DELETE, "/api/logs").hasRole("ADMIN")
                        .requestMatchers("/api/logs/ai/**").authenticated()
                        .anyRequest().authenticated()
                )

                // 3. CORS 설정 (프론트엔드 연결 대비)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:3000")); // 프론트 주소
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}