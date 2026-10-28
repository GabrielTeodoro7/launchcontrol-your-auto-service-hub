package br.com.launchcontrol;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@WebFilter(urlPatterns = "/api/*")
/**
 * Funciona como um porteiro: antes de deixar uma requisição chegar a certas rotas, verifica login, perfil e proteção contra envios forjados.
 *
 * Esta classe reúne a lógica de uma responsabilidade específica do sistema.
 */
public class AuthFilter implements Filter {
    @Override
    // Este filtro é executado antes dos endpoints /api/* e decide se a requisição pode continuar.
    public void doFilter(jakarta.servlet.ServletRequest rawRequest, jakarta.servlet.ServletResponse rawResponse,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) rawRequest;
        HttpServletResponse response = (HttpServletResponse) rawResponse;
        String path = request.getRequestURI().substring(request.getContextPath().length());

        // Cadastro, login, catálogo de serviços e verificação de saúde podem ser acessados sem sessão.
        if ("/api/auth".equals(path) || "/api/servicos".equals(path) || "/api/health".equals(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Não cria uma sessão nova: verifica se já existe uma sessão autenticada.
        var session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            Json.error(response, 401, "Autenticação necessária.");
            return;
        }

        // POST pode alterar dados, então também exige o token CSRF guardado na sessão.
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
