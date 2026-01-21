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
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. [추가] Spring Boot가 제공하는 기본 정적 리소스 위치(favicon.ico, css, js 등) 모두 허용
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers("/favicon.ico").permitAll()

                        // 2. [기존] 메인 페이지 및 에러 페이지 허용
                        .requestMatchers("/", "/index.html", "/static/**", "/error").permitAll()
                        .requestMatchers("/actuator/**").permitAll()

                        // 3. 로그 관련 API (조회, 생성, 상태변경)
                        .requestMatchers(HttpMethod.POST, "/api/logs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/logs", "/api/logs/**").permitAll()
                        .requestMatchers(HttpMethod.PATCH, "/api/logs/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/logs/**").permitAll()
                        .requestMatchers("/api/logs/ai/**").permitAll()

                        // 4. [수정] 인시던트 및 KB 관련 API 권한 확장
                        // 기존 GET만 허용하던 것을 POST, PUT까지 허용하여 index.html에서 '저장'이 가능하도록 변경
                        .requestMatchers("/api/incidents/**").permitAll()
                        .requestMatchers("/api/kb/**").permitAll()

                        .anyRequest().authenticated()
                );
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