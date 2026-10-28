package br.com.launchcontrol;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebFilter(urlPatterns = "/api/*")
public class AuthFilter implements Filter {
    @Override
    public void doFilter(jakarta.servlet.ServletRequest rawRequest, jakarta.servlet.ServletResponse rawResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) rawRequest;
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if ("/api/auth".equals(path) || "/api/servicos".equals(path) || "/api/health".equals(path)) {
            if ("POST".equalsIgnoreCase(request.getMethod())) {
            String expected = (String) session.getAttribute("csrfToken");
            String supplied = request.getHeader("X-CSRF-Token");
            if (expected == null || supplied == null || !java.security.MessageDigest.isEqual(
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                Json.error(response, 403, "Token CSRF ausente ou inválido.");
                return;
            }
        }
        chain.doFilter(request, response);
            return;
        }
        var session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            Json.error(response, 401, "Autenticação necessária.");
            return;
        }
        chain.doFilter(request, response);
    }
}
