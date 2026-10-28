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
