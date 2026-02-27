package com.analytics.gateway.controller;

import com.analytics.gateway.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication Controller
 */
@RestController
@RequestMapping("/api/auth")
@Slf4j
public class AuthController {

    @Autowired
    private SessionService sessionService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        // In real app, verify credentials
        String userId = request.getUsername(); // Simplified

        String sessionId = sessionService.createSession(userId, httpRequest.getRemoteAddr());

        LoginResponse response = new LoginResponse();
        response.setSessionId(sessionId);
        response.setUserId(userId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-Session-Id") String sessionId) {
        sessionService.invalidateSession(sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    public ResponseEntity<SessionService.UserSession> getSession(@RequestHeader("X-Session-Id") String sessionId) {
        SessionService.UserSession session = sessionService.getSession(sessionId);
        if (session != null) {
            return ResponseEntity.ok(session);
        }
        return ResponseEntity.status(401).build();
    }

    @Data
    public static class LoginRequest {
        private String username;
        private String password;
    }

    @Data
    public static class LoginResponse {
        private String sessionId;
        private String userId;
    }
}
