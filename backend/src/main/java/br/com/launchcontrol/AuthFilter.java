package br.com.launchcontrol;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Aplica as regras de acesso às rotas, incluindo autenticação, autorização por perfil e validação CSRF.
 */
@WebFilter(urlPatterns = "/api/*")
public class AuthFilter implements Filter {
    @Override
    public void doFilter(jakarta.servlet.ServletRequest rawRequest, jakarta.servlet.ServletResponse rawResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) rawRequest;
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        String path = request.getRequestURI().substring(request.getContextPath().length());

        // Rotas públicas que não exigem sessão autenticada.
        if ("/api/auth".equals(path) || "/api/servicos".equals(path) || "/api/health".equals(path)
                || ("/api/pecas/busca".equals(path) && "GET".equalsIgnoreCase(request.getMethod()))) {
            chain.doFilter(request, response);
            return;
        }

        // Consulta a sessão existente sem criar uma nova.
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            Json.error(response, 401, "Autenticação necessária.");
            return;
        }

        // Requisições de alteração exigem o token CSRF associado à sessão.
        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String expected = (String) session.getAttribute("csrfToken");
            String supplied = request.getHeader("X-CSRF-Token");
            if (expected == null || supplied == null || !MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
                Json.error(response, 403, "Token CSRF ausente ou inválido.");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
