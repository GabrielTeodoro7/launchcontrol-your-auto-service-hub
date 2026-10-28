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
import java.util.UUID;

/**
 * Processa a decisão do cliente de aceitar ou recusar um orçamento e aplica as regras necessárias.
 */
@WebServlet("/api/orcamentos/decisao")
public class QuoteDecisionServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (!"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente o cliente dono da solicitação pode responder ao orçamento.");
            return;
        }
        long userId = (Long)session.getAttribute("userId");
        long quoteId;
        String decision = request.getParameter("decisao");
        try { quoteId = Long.parseLong(request.getParameter("orcamentoId")); }
        catch (Exception ex) { Json.error(response, 400, "ID do orçamento inválido."); return; }
        if (!"ACEITAR".equals(decision) && !"RECUSAR".equals(decision)) {
            Json.error(response, 400, "Decisão deve ser ACEITAR ou RECUSAR.");
            return;
        }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long requestId;
                long workshopId;
                long workshopUserId;
                String status;
                String requestStatus;
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT o.status,s.id AS solicitacao_id,s.status AS solicitacao_status," +
                    "o.oficina_id,ofi.usuario_id AS oficina_usuario_id " +
                    "FROM orcamentos o JOIN solicitacoes_orcamento s ON s.id=o.solicitacao_id " +
                    "JOIN oficinas ofi ON ofi.id=o.oficina_id WHERE o.id=? AND s.cliente_id=? FOR UPDATE")) {
                    ps.setLong(1, quoteId); ps.setLong(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); Json.error(response, 404, "Orçamento não encontrado para este cliente."); return; }
                        status = rs.getString("status");
                        requestId = rs.getLong("solicitacao_id");
                        requestStatus = rs.getString("solicitacao_status");
                        workshopId = rs.getLong("oficina_id");
                        workshopUserId = rs.getLong("oficina_usuario_id");
                    }
                }
                if ("ACEITA".equals(requestStatus) || "CONCLUIDA".equals(requestStatus) || "CANCELADA".equals(requestStatus)) {
                    c.rollback(); Json.error(response, 409, "A solicitação já foi aceita, concluída ou cancelada."); return;
                }
                if (!"ENVIADO".equals(status) && !"PENDENTE".equals(status)) {
                    c.rollback(); Json.error(response, 409, "Este orçamento não aceita mais decisões."); return;
                }
                if ("ACEITAR".equals(decision)) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE orcamentos SET status='ACEITO' WHERE id=?")) {
                        ps.setLong(1, quoteId); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE solicitacoes_orcamento SET status='ACEITA' WHERE id=?")) {
                        ps.setLong(1, requestId); ps.executeUpdate();
                    }
                    long orderId;
                    try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ordens_servico(codigo,solicitacao_id,orcamento_id,status) VALUES(?,?,?,'PENDENTE')",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, "LC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT));
                        ps.setLong(2, requestId); ps.setLong(3, quoteId); ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Ordem sem identificador.");
                            orderId = keys.getLong(1);
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement(
                            "INSERT INTO ordem_status_historico(ordem_id,status,observacao,usuario_id) VALUES(?,'PENDENTE',?,?)")) {
                        ps.setLong(1, orderId);
                        ps.setString(2, "Ordem criada após aceite do orçamento pelo cliente.");
                        ps.setLong(3, userId);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE orcamentos SET status='RECUSADO' WHERE id=?")) {
                        ps.setLong(1, quoteId); ps.executeUpdate();
                    }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE solicitacoes_orcamento SET status='EM_ANALISE' WHERE id=?")) {
                        ps.setLong(1, requestId); ps.executeUpdate();
                    }
                }
                AdditionalServiceServlet.createNotification(c, workshopUserId,
                        "Decisão sobre orçamento",
                        "O cliente " + ("ACEITAR".equals(decision) ? "aceitou" : "recusou") +
                                " o orçamento #" + quoteId + ".",
                        "DECISAO_ORCAMENTO", quoteId);
                if ("ACEITAR".equals(decision)) {
                    AdditionalServiceServlet.createNotification(c, userId,
                            "Ordem de serviço criada",
                            "Seu orçamento foi aceito e uma ordem de serviço foi criada.",
                            "ORDEM_SERVICO", requestId);
                }
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)"))
                    ps.setLong(1, userId); ps.setString(2, "DECISAO_ORCAMENTO"); ps.setString(3, "orcamentos");
                    ps.setLong(4, quoteId); ps.setString(5, "Decisão do cliente: " + decision); ps.executeUpdate();
                }
                c.commit(); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":true,\"orcamentoId\":" + quoteId + ",\"status\":" + Json.quote("ACEITAR".equals(decision) ? "ACEITO" : "RECUSADO") + "}");
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível registrar decisão do orçamento."); }
    }
}
