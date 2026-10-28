package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

@WebServlet("/api/diagnosticos")
/**
 * Guarda o relato inicial de sintomas informado pelo cliente. Esse relato não substitui uma avaliação feita por um profissional.
 *
 * Esta classe reúne a lógica de uma responsabilidade específica do sistema.
 */
public class DiagnosticServlet extends HttpServlet {
    // POST recebe informações para criar ou alterar registros.
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        var session = request.getSession(false);
        String role = (String) session.getAttribute("userRole");
        if (!"CLIENTE".equals(role)) { Json.error(response, 403, "Somente cliente autenticado pode registrar relato inicial."); return; }
        String vehicle = clean(request.getParameter("vehicle"));
        String symptoms = clean(request.getParameter("symptoms"));
        String priority = clean(request.getParameter("priority"));
        if (vehicle.length() < 2 || vehicle.length() > 120 || symptoms.length() < 5 || symptoms.length() > 2000
                || !java.util.Set.of("NORMAL","ALTA","URGENTE").contains(priority)) {
            Json.error(response, 400, "Revise veículo, sintomas e prioridade."); return;
        }
        String sql = "INSERT INTO diagnosticos(cliente_id,veiculo_descricao,sintomas,prioridade) VALUES(?,?,?,?)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, (Long)session.getAttribute("userId")); ps.setString(2, vehicle);
            ps.setString(3, symptoms); ps.setString(4, priority); ps.executeUpdate();
            try (var keys = ps.getGeneratedKeys()) {
                if (!keys.next()) { Json.error(response, 500, "Relato não recebeu identificador."); return; }
                response.setStatus(201); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"id\":" + keys.getLong(1) + ",\"status\":\"RELATO_RECEBIDO\",\"aviso\":\"Relato inicial; não é laudo técnico.\"}");
            }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível registrar o relato."); }
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
