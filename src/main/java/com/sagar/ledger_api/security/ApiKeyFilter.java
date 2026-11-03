
package com.sagar.ledger_api.security;

import com.sagar.ledger_api.repository.ApiClientRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private final ApiClientRepository apiClientRepository;

    public ApiKeyFilter(ApiClientRepository apiClientRepository) {
        this.apiClientRepository = apiClientRepository;
    }

    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    apiKey.getBytes(StandardCharsets.UTF_8)
            );
            return java.util.HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    exception
            );
        }
    }

    
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String requestUri = request.getRequestURI();

        return requestUri.endsWith("/actuator/health")
                || requestUri.endsWith("/actuator/health/")
                || requestUri.endsWith("/swagger-ui.html")
                || requestUri.contains("/swagger-ui/")
                || requestUri.endsWith("/v3/api-docs")
                || requestUri.contains("/v3/api-docs/")
                || requestUri.endsWith("/v3/api-docs.yaml");
    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String providedApiKey = request.getHeader("X-API-KEY");

        if (providedApiKey == null || providedApiKey.isBlank()) {
            rejectRequest(response);
            return;
        }

        String apiKeyHash = hashApiKey(providedApiKey);

        var clientId = apiClientRepository
                .findActiveClientIdByApiKeyHash(apiKeyHash);

        if (clientId.isEmpty()) {
            rejectRequest(response);
            return;
        }

        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        clientId.get(),
                        null,
                        List.of()
                );

        authentication.setDetails(
                new WebAuthenticationDetailsSource().buildDetails(request)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
        
    }

    private void rejectRequest(HttpServletResponse response)
            throws IOException {

        SecurityContextHolder.clearContext();

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"error\":\"Invalid or missing API key\"}"
        );
    }
}
