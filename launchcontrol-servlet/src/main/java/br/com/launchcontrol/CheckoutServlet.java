package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@WebServlet("/api/checkout")
public class CheckoutServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var session = request.getSession(false);
        if (!"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente cliente autenticado pode iniciar checkout.");
            return;
        }
        long userId = (Long) session.getAttribute("userId");
        long quoteId;
        String method = request.getParameter("method");
        try { quoteId = Long.parseLong(request.getParameter("quoteId")); }
        catch (Exception ex) { Json.error(response, 400, "ID de orçamento inválido."); return; }
        if (!"PIX_DEMO".equals(method) && !"CARTAO_DEMO".equals(method)) {
            Json.error(response, 400, "Método de demonstração inválido."); return;
        }
        String idempotencyKey = request.getHeader("Idempotency-Key");
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{16,80}")) {
            Json.error(response, 400, "Chave de idempotência ausente ou inválida."); return;
        }

        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT pg.id,pg.valor,pg.status,pg.modo,pg.cliente_id,ped.id AS pedido_id,ped.orcamento_id " +
                    "FROM pagamentos pg JOIN pedidos ped ON ped.id=pg.pedido_id WHERE pg.chave_idempotencia=? FOR UPDATE")) {
                    ps.setString(1, idempotencyKey);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            if (rs.getLong("cliente_id") != userId || rs.getLong("orcamento_id") != quoteId) {
                                c.rollback(); Json.error(response, 409, "A chave de idempotência já foi usada em outra operação."); return;
                            }
                            long paymentId = rs.getLong("id");
                            long existingOrderId = rs.getLong("pedido_id");
                            java.math.BigDecimal existingAmount = rs.getBigDecimal("valor");
                            String existingStatus = rs.getString("status");
                            String existingMode = rs.getString("modo");
                            c.commit();
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"paymentId\":" + paymentId + ",\"pedidoId\":" + existingOrderId +
                                ",\"valor\":" + existingAmount.toPlainString() + ",\"status\":" + Json.quote(existingStatus) +
                                ",\"modo\":" + Json.quote(existingMode) + ",\"cobrado\":false}");
                            return;
                        }
                    }
                }

                java.math.BigDecimal amount;
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT o.valor FROM orcamentos o JOIN solicitacoes_orcamento s ON s.id=o.solicitacao_id " +
                    "WHERE o.id=? AND s.cliente_id=? AND o.status='ACEITO' FOR UPDATE")) {
                    ps.setLong(1, quoteId); ps.setLong(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); Json.error(response, 404, "Orçamento aceito não encontrado para este cliente."); return; }
                        amount = rs.getBigDecimal("valor");
                    }
                }
                long orderId;
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pedidos(cliente_id,orcamento_id,total) VALUES(?,?,?) " +
                    "ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, userId); ps.setLong(2, quoteId); ps.setBigDecimal(3, amount); ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) { if (!rs.next()) throw new SQLException("Pedido sem ID"); orderId = rs.getLong(1); }
                }
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id,valor,metodo,status,modo FROM pagamentos WHERE pedido_id=? AND status='PENDENTE' FOR UPDATE")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            if (!method.equals(rs.getString("metodo"))) {
                                c.rollback(); Json.error(response, 409, "Já existe um pagamento pendente para este pedido. Cancele-o antes de escolher outro método."); return;
                            }
                            long paymentId = rs.getLong("id");
                            java.math.BigDecimal existingAmount = rs.getBigDecimal("valor");
                            c.commit();
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"paymentId\":" + paymentId + ",\"pedidoId\":" + orderId +
                                ",\"valor\":" + existingAmount.toPlainString() + ",\"status\":\"PENDENTE\",\"modo\":\"DEMONSTRACAO\",\"cobrado\":false}");
                            return;
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO pagamentos(pedido_id,cliente_id,metodo,status,modo,chave_idempotencia,valor) " +
                    "VALUES(?,?,?,'PENDENTE','DEMONSTRACAO',?,?) ON DUPLICATE KEY UPDATE id=LAST_INSERT_ID(id)",
                    PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, orderId); ps.setLong(2, userId); ps.setString(3, method);
                    ps.setString(4, idempotencyKey); ps.setBigDecimal(5, amount); ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Pagamento sem ID");
                        long paymentId = rs.getLong(1);
                        c.commit();
                        response.setStatus(201); response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"paymentId\":" + paymentId + ",\"pedidoId\":" + orderId +
                          ",\"valor\":" + amount.toPlainString() + ",\"status\":\"PENDENTE\",\"modo\":\"DEMONSTRACAO\",\"cobrado\":false}");
                    }
                }
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível registrar o pagamento demonstrativo.");
        }
    }
}
