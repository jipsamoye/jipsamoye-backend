package com.jipsamoye.backend.global.config;

import com.jipsamoye.backend.global.config.security.CustomAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                )
                .authorizeHttpRequests(auth -> auth
                        // 인증 불필요 — 조회 API
                        .requestMatchers(GET, "/api/posts/**").permitAll()
                        .requestMatchers(GET, "/api/boards/**").permitAll()
                        .requestMatchers(GET, "/api/users/**").permitAll()
                        .requestMatchers(GET, "/api/search").permitAll()

                        // 인증 불필요 — 게스트 로그인
                        .requestMatchers(POST, "/api/auth/guest").permitAll()

                        // 인증 불필요 — Swagger, 헬스체크, WebSocket
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/").permitAll()
                        .requestMatchers("/ws/**").permitAll()

                        // 그 외 모든 요청은 인증 필수
                        .anyRequest().authenticated()
                );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of(
                "https://jipsamoye.com",
                "https://www.jipsamoye.com",
                "https://jipsamoyefrontend.vercel.app",
                "http://localhost:3000"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        // CORS 프리플라이트 캐싱 — 브라우저별 캡: Chrome 2h, Firefox 24h, Safari 24h+
        configuration.setMaxAge(Duration.ofHours(24));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
