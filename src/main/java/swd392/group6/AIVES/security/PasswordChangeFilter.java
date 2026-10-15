package swd392.group6.AIVES.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import swd392.group6.AIVES.common.GlobalExceptionHandler;

import java.io.IOException;
import java.util.Set;

/**
 * D62: an account whose password was set by someone else (admin form, CSV import) may only read its profile,
 * change the password, change its UI language and sign out until the password is changed. Everything else answers
 * {@code 403 PASSWORD_CHANGE_REQUIRED}, so the rule holds even without the web app.
 */
@Component
public class PasswordChangeFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED = Set.of(
            "GET /api/v1/users/me", "PATCH /api/v1/users/me", "PUT /api/v1/users/me/password", "POST /api/v1/auth/logout");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (auth != null && auth.getPrincipal() instanceof PasswordChangeRequirement p && p.isMustChangePassword()
                && path.startsWith("/api/") && !path.startsWith("/api/v1/avatars/")
                && !ALLOWED.contains(request.getMethod() + " " + path)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"type\":\"" + GlobalExceptionHandler.TYPE_PREFIX + "PASSWORD_CHANGE_REQUIRED\","
                    + "\"status\":403,\"code\":\"PASSWORD_CHANGE_REQUIRED\","
                    + "\"detail\":\"Change the initial password before using AIVES\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
