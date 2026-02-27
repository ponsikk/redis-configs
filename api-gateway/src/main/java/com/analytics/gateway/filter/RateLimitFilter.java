package com.analytics.gateway.filter;

import com.analytics.gateway.service.RateLimitService;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Rate limiting filter applied to all requests
 */
@Component
@Slf4j
public class RateLimitFilter implements Filter {

    @Autowired
    private RateLimitService rateLimitService;

    @Value("${ratelimit.default.requests}")
    private int defaultMaxRequests;

    @Value("${ratelimit.default.window}")
    private int defaultWindowSeconds;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Get identifier (userId or IP)
        String userId = httpRequest.getHeader("X-User-Id");
        String identifier = userId != null ? "user:" + userId : "ip:" + httpRequest.getRemoteAddr();

        // Check rate limit
        if (!rateLimitService.allowRequest(identifier, defaultMaxRequests, defaultWindowSeconds)) {
            httpResponse.setStatus(429); // Too Many Requests
            httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(defaultMaxRequests));
            httpResponse.setHeader("X-RateLimit-Retry-After", String.valueOf(defaultWindowSeconds));
            httpResponse.getWriter().write("Rate limit exceeded. Please try again later.");
            return;
        }

        // Add rate limit headers
        int remaining = rateLimitService.getRemainingRequests(identifier, defaultMaxRequests, defaultWindowSeconds);
        httpResponse.setHeader("X-RateLimit-Limit", String.valueOf(defaultMaxRequests));
        httpResponse.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

        chain.doFilter(request, response);
    }
}
