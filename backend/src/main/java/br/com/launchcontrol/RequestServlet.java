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
 * Recebe e lista solicitações de orçamento feitas por clientes.
 */
@WebServlet("/api/solicitacoes")
public class RequestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        long userId = (Long)session.getAttribute("userId");
        String role = (String)session.getAttribute("userRole");
        if (!"CLIENTE".equals(role) && !"OFICINA".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "Seu perfil não pode consultar solicitações.");
            return;
        }
        StringBuilder sql = new StringBuilder(
          "SELECT s.id,s.veiculo_id,s.descricao,s.status,s.criada_em,u.nome AS cliente," +
          "CONCAT(COALESCE(v.marca,''),' ',COALESCE(v.modelo,'')) AS veiculo,sv.nome AS servico " +
          "FROM solicitacoes_orcamento s JOIN usuarios u ON u.id=s.cliente_id " +
          "LEFT JOIN veiculos v ON v.id=s.veiculo_id LEFT JOIN servicos sv ON sv.id=s.servico_id ");
        if ("CLIENTE".equals(role)) sql.append("WHERE s.cliente_id=? ");
        else if ("OFICINA".equals(role)) sql.append("WHERE s.status IN ('ENVIADA','EM_ANALISE','ORCAMENTO_ENVIADO') ");
        sql.append("ORDER BY s.criada_em DESC LIMIT 200");
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            if ("CLIENTE".equals(role)) ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder out = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(',');
                    first = false;
                    out.append("{\"id\":").append(rs.getLong("id"))
                       .append(",\"veiculoId\":").append(rs.getObject("veiculo_id") == null ? "null" : rs.getLong("veiculo_id"))
                       .append(",\"descricao\":").append(Json.quote(rs.getString("descricao")))
                       .append(",\"status\":").append(Json.quote(rs.getString("status")))
                       .append(",\"cliente\":").append(Json.quote(rs.getString("cliente")))
                       .append(",\"veiculo\":").append(Json.quote(rs.getString("veiculo")))
                       .append(",\"servico\":").append(Json.quote(rs.getString("servico")))
                       .append(",\"criadaEm\":").append(Json.quote(rs.getString("criada_em"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(out.append(']').toString());
            }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível consultar solicitações."); }
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        if (!"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente cliente autenticado pode abrir solicitação.");
            return;
        }
        long userId = (Long)session.getAttribute("userId");
        long vehicleId, serviceId = 0;
        String description = request.getParameter("descricao") == null ? "" : request.getParameter("descricao").trim();
        try {
            vehicleId = Long.parseLong(request.getParameter("veiculoId"));
            String serviceValue = request.getParameter("servicoId");
            if (serviceValue != null && !serviceValue.isBlank()) serviceId = Long.parseLong(serviceValue);
            if (vehicleId < 1 || description.length() < 5 || description.length() > 3000 || serviceId < 0)
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            Json.error(response, 400, "Informe veículo e descrição válida (5 a 3000 caracteres).");
            return;
        }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM veiculos WHERE id=? AND cliente_id=? FOR UPDATE")) {
                    ps.setLong(1, vehicleId); ps.setLong(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); Json.error(response, 404, "Veículo não encontrado para este cliente."); return; }
                    }
                }
                if (serviceId > 0) {
                    try (PreparedStatement ps = c.prepareStatement("SELECT id FROM servicos WHERE id=? AND ativo=TRUE")) {
                        ps.setLong(1, serviceId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) { c.rollback(); Json.error(response, 404, "Serviço ativo não encontrado."); return; }
                        }
                    }
                }
                String sql = "INSERT INTO solicitacoes_orcamento(cliente_id,veiculo_id,servico_id,descricao,status) VALUES(?,?,?,?, 'ENVIADA')";
                try (PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, userId); ps.setLong(2, vehicleId);
                    if (serviceId > 0) ps.setLong(3, serviceId); else ps.setNull(3, java.sql.Types.BIGINT);
                    ps.setString(4, description); ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Solicitação sem identificador.");
                        long id = rs.getLong(1);
                        try (PreparedStatement audit = c.prepareStatement(
                                "INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")) {
                            audit.setLong(1, userId); audit.setString(2, "CRIAR_SOLICITACAO");
                            audit.setString(3, "solicitacoes_orcamento"); audit.setLong(4, id);
                            audit.setString(5, "Solicitação enviada pelo cliente"); audit.executeUpdate();
                        }
                        c.commit(); response.setStatus(201); response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"id\":" + id + ",\"status\":\"ENVIADA\"}");
                    }
                }
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível registrar solicitação."); }
    }
}
