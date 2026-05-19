package com.checkdang.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 300; // 5분

    private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (isLoginRequest(request)) {
            String ip = getClientIp(request);

            if (isRateLimited(ip)) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(
                        "{\"success\":false,\"data\":null,\"message\":\"로그인 시도가 너무 많습니다. 5분 후 다시 시도해주세요.\"}"
                );
                return;
            }
            recordAttempt(ip);
        }
        chain.doFilter(request, response);
    }

    private boolean isLoginRequest(HttpServletRequest request) {
        return "/api/auth/login".equals(request.getRequestURI())
                && "POST".equalsIgnoreCase(request.getMethod());
    }

    private boolean isRateLimited(String ip) {
        Deque<Instant> windows = attempts.get(ip);
        if (windows == null) return false;

        Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);
        while (!windows.isEmpty() && windows.peekFirst().isBefore(cutoff)) {
            windows.pollFirst();
        }
        return windows.size() >= MAX_ATTEMPTS;
    }

    private void recordAttempt(String ip) {
        attempts.computeIfAbsent(ip, k -> new ArrayDeque<>()).addLast(Instant.now());
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
