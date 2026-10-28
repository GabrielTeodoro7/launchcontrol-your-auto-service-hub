package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

@WebServlet("/api/health")
/**
 * Oferece uma rota de verificação para descobrir se a aplicação e a conexão com o banco estão respondendo.
 *
 * Uma classe é como uma caixa que reúne dados e ações relacionados a uma tarefa.
 */
public class HealthServlet extends HttpServlet {
    // Executado quando o navegador faz uma requisição HTTP GET, normalmente para consultar dados.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        try (Connection ignored = Database.getConnection()) {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"status\":\"ok\",\"database\":\"connected\"}");
        } catch (SQLException exception) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.getWriter().write("{\"status\":\"error\",\"message\":\"database_unavailable\"}");
        }
    }
}
