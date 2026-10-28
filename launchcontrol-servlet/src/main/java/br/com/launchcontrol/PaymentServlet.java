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

@WebServlet("/api/pagamentos")
/**
 * Consulta ou cancela registros de pagamento demonstrativos pertencentes ao próprio cliente.
 *
 * Esta classe reúne a lógica de uma responsabilidade específica do sistema.
 */
public class PaymentServlet extends HttpServlet {
    // GET é usado principalmente para consultar informações já existentes.
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var session = request.getSession(false);
        long userId = (Long) session.getAttribute("userId");
        String role = (String) session.getAttribute("userRole");
        if (!"CLIENTE".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "Seu perfil não pode consultar pagamentos.");
            return;
        }
        String sql = "SELECT pg.id,pg.pedido_id,pg.valor,pg.metodo,pg.status,pg.modo,pg.criado_em " +
                     "FROM pagamentos pg WHERE " + ("ADMINISTRADOR".equals(role) ? "1=1 " : "pg.cliente_id=? ") +
                     "ORDER BY pg.criado_em DESC LIMIT 100";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (!"ADMINISTRADOR".equals(role)) ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(rs.getLong("id"))
                        .append(",\"pedidoId\":").append(rs.getLong("pedido_id"))
                        .append(",\"valor\":").append(rs.getBigDecimal("valor").toPlainString())
                        .append(",\"metodo\":").append(Json.quote(rs.getString("metodo")))
                        .append(",\"status\":").append(Json.quote(rs.getString("status")))
                        .append(",\"modo\":").append(Json.quote(rs.getString("modo")))
                        .append(",\"criadoEm\":").append(Json.quote(rs.getString("criado_em"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(json.append(']').toString());
            }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível consultar pagamentos."); }
    }
    // POST recebe informações para criar ou alterar registros.
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        var session = request.getSession(false);
        if (!"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente o cliente pode cancelar o próprio pagamento pendente.");
            return;
        }
        long userId = (Long) session.getAttribute("userId");
        long paymentId;
        try { paymentId = Long.parseLong(request.getParameter("id")); }
        catch (Exception ex) { Json.error(response, 400, "ID de pagamento inválido."); return; }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pagamentos SET status='CANCELADO' WHERE id=? AND cliente_id=? AND status='PENDENTE' AND modo='DEMONSTRACAO'")) {
                    ps.setLong(1, paymentId); ps.setLong(2, userId); changed = ps.executeUpdate();
                }
                if (changed == 0) {
                    c.rollback();
                    Json.error(response, 409, "Pagamento não encontrado, não pertence a este cliente ou não está pendente.");
                    return;
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pedidos p JOIN pagamentos pg ON pg.pedido_id=p.id SET p.status='CANCELADO' " +
                        "WHERE pg.id=? AND p.status='AGUARDANDO_PAGAMENTO'")) {
                    ps.setLong(1, paymentId); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")) {
                    ps.setLong(1, userId); ps.setString(2, "CANCELAR_PAGAMENTO_DEMO");
                    ps.setString(3, "pagamentos"); ps.setLong(4, paymentId);
                    ps.setString(5, "Cancelamento de pagamento demonstrativo pendente"); ps.executeUpdate();
                }
                c.commit();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":true,\"id\":" + paymentId + ",\"status\":\"CANCELADO\",\"cobrancaRealizada\":false}");
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível cancelar o pagamento."); }
    }
}
