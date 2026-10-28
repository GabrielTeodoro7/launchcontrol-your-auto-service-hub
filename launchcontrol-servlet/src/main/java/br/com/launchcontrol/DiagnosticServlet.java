package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Guarda o relato inicial de sintomas informado pelo cliente. Esse relato não substitui uma avaliação feita por um profissional.
 */
@WebServlet("/api/diagnosticos")
public class DiagnosticServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (!"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente o cliente pode consultar seus próprios relatos.");
            return;
        }

        String sql = "SELECT id,veiculo_descricao,sintomas,prioridade,status,hipoteses_preliminares," +
                "laudo_tecnico,criado_em,atualizado_em FROM diagnosticos " +
                "WHERE cliente_id=? ORDER BY criado_em DESC LIMIT 200";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, (Long) session.getAttribute("userId"));
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder out = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(',');
                    first = false;
                    out.append("{\"id\":").append(rs.getLong("id"))
                       .append(",\"veiculoDescricao\":").append(Json.quote(rs.getString("veiculo_descricao")))
                       .append(",\"sintomas\":").append(Json.quote(rs.getString("sintomas")))
                       .append(",\"prioridade\":").append(Json.quote(rs.getString("prioridade")))
                       .append(",\"status\":").append(Json.quote(rs.getString("status")))
                       .append(",\"hipotesesPreliminares\":").append(Json.quote(rs.getString("hipoteses_preliminares")))
                       .append(",\"laudoTecnico\":").append(Json.quote(rs.getString("laudo_tecnico")))
                       .append(",\"criadoEm\":").append(Json.quote(rs.getString("criado_em")))
                       .append(",\"atualizadoEm\":").append(Json.quote(rs.getString("atualizado_em"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(out.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar os relatos de diagnóstico.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
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
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) { Json.error(response, 500, "Relato não recebeu identificador."); return; }
                response.setStatus(201); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"id\":" + keys.getLong(1) + ",\"status\":\"RELATO_RECEBIDO\",\"aviso\":\"Relato inicial; não é laudo técnico.\"}");
            }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível registrar o relato."); }
    }
    private static String clean(String s) { return s == null ? "" : s.trim(); }
}
