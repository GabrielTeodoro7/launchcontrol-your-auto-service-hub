package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@WebServlet("/api/orcamentos")
public class QuoteCreateServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var session = request.getSession(false);
        if (!"OFICINA".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente uma conta de oficina pode enviar orçamento.");
            return;
        }
        long userId = (Long)session.getAttribute("userId");
        long requestId; BigDecimal amount; int days;
        String observations = request.getParameter("observacoes") == null ? "" : request.getParameter("observacoes").trim();
        try {
            requestId = Long.parseLong(request.getParameter("solicitacaoId"));
            amount = new BigDecimal(request.getParameter("valor"));
            days = Integer.parseInt(request.getParameter("prazoDias"));
            if (requestId < 1 || amount.signum() < 0 || amount.scale() > 2 || days < 0 || days > 365 || observations.length() > 2000)
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            Json.error(response, 400, "Informe solicitação, valor e prazo válidos.");
            return;
        }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long workshopId;
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM oficinas WHERE usuario_id=? AND ativa=TRUE FOR UPDATE")) {
                    ps.setLong(1, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); Json.error(response, 403, "A conta não está vinculada a uma oficina ativa."); return; }
                        workshopId = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT status FROM solicitacoes_orcamento WHERE id=? FOR UPDATE")) {
                    ps.setLong(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); Json.error(response, 404, "Solicitação não encontrada."); return; }
                        String status = rs.getString("status");
                        if ("CANCELADA".equals(status) || "CONCLUIDA".equals(status) || "RECUSADA".equals(status)) {
                            c.rollback(); Json.error(response, 409, "A solicitação não aceita novos orçamentos."); return;
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT id FROM orcamentos WHERE solicitacao_id=? AND oficina_id=? AND status NOT IN ('RECUSADO','CANCELADO') FOR UPDATE")) {
                    ps.setLong(1, requestId); ps.setLong(2, workshopId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) { c.rollback(); Json.error(response, 409, "Esta oficina já possui um orçamento ativo para a solicitação."); return; }
                    }
                }
                long quoteId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO orcamentos(solicitacao_id,oficina_id,valor,prazo_dias,observacoes,status) VALUES(?,?,?,?,?,'ENVIADO')",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, requestId); ps.setLong(2, workshopId); ps.setBigDecimal(3, amount);
                    ps.setInt(4, days); ps.setString(5, observations); ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Orçamento sem identificador.");
                        quoteId = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE solicitacoes_orcamento SET status='ORCAMENTO_ENVIADO' WHERE id=?")) {
                    ps.setLong(1, requestId); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")) {
                    ps.setLong(1, userId); ps.setString(2, "CRIAR_ORCAMENTO"); ps.setString(3, "orcamentos");
                    ps.setLong(4, quoteId); ps.setString(5, "Orçamento enviado pela oficina"); ps.executeUpdate();
                }
                c.commit(); response.setStatus(201); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"id\":" + quoteId + ",\"status\":\"ENVIADO\"}");
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível enviar orçamento."); }
    }
}
