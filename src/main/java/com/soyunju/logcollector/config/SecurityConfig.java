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
                        // [수정] 메인 대시보드 및 정적 리소스, 에러 페이지 허용
                        .requestMatchers("/", "/index.html", "/static/**", "/error").permitAll()

                        // 로그 수집 API
                        .requestMatchers(HttpMethod.POST, "/api/logs").permitAll()

                        // [수정] 로그 조회 API: /api/logs(기본)와 /api/logs/**(하위)를 모두 명시해야 함
                        .requestMatchers(HttpMethod.GET, "/api/logs", "/api/logs/**").permitAll()

                        // 상태 업데이트 및 삭제
                        .requestMatchers(HttpMethod.PATCH, "/api/logs/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/logs/**").permitAll()
                        .requestMatchers("/api/logs/ai/**").permitAll()

                        // 인시던트 및 KB 조회 권한 추가
                        .requestMatchers(HttpMethod.GET, "/api/incidents/**", "/api/kb/**").permitAll()

                        .anyRequest().authenticated() // 나머지는 인증 필요
                );

        // 3. CORS 설정 (프론트엔드 연결 대비)
        // cors(cors -> cors.configurationSource(corsConfigurationSource()));

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