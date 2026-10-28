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

@WebServlet("/api/orcamentos/editar")
public class QuoteServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!"OFICINA".equals(request.getSession(false).getAttribute("userRole"))
                && !"ADMINISTRADOR".equals(request.getSession(false).getAttribute("userRole"))) {
            Json.error(response, 403, "Somente a oficina autorizada pode alterar o orçamento.");
            return;
        }
        long userId = (Long) request.getSession(false).getAttribute("userId");
        long quoteId;
        BigDecimal amount;
        int days;
        try {
            quoteId = Long.parseLong(request.getParameter("id"));
            amount = new BigDecimal(request.getParameter("valor"));
            days = Integer.parseInt(request.getParameter("prazoDias"));
            if (quoteId < 1 || amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("99999999.99")) > 0 || days < 0 || days > 365) throw new IllegalArgumentException();
        } catch (Exception ex) {
            Json.error(response, 400, "Informe orçamento, valor e prazo válidos.");
            return;
        }
        String role = (String) request.getSession(false).getAttribute("userRole");
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String query = "SELECT o.status,os.status AS ordem_status,o.oficina_id,ofi.usuario_id " +
                    "FROM orcamentos o JOIN oficinas ofi ON ofi.id=o.oficina_id " +
                    "LEFT JOIN ordens_servico os ON os.orcamento_id=o.id WHERE o.id=? FOR UPDATE";
                try (PreparedStatement ps = c.prepareStatement(query)) {
                    ps.setLong(1, quoteId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); Json.error(response, 404, "Orçamento não encontrado."); return; }
                        if ("CONCLUIDO".equals(rs.getString("ordem_status"))) {
                            c.rollback(); Json.error(response, 409, "Orçamentos de serviços concluídos não podem ser alterados."); return;
                        }
                        if ("OFICINA".equals(role) && rs.getLong("usuario_id") != userId) {
                            c.rollback(); Json.error(response, 403, "Este orçamento não pertence à sua oficina."); return;
                        }
                        if (!java.util.Set.of("PENDENTE", "ENVIADO").contains(rs.getString("status"))) {
                            c.rollback(); Json.error(response, 409, "Este orçamento não está disponível para edição; orçamentos aceitos não podem ser alterados."); return;
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE orcamentos SET valor=?,prazo_dias=?,observacoes=? WHERE id=?")) {
                    ps.setBigDecimal(1, amount); ps.setInt(2, days);
                    ps.setString(3, request.getParameter("observacoes"));
                    ps.setLong(4, quoteId); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")) {
                    ps.setLong(1, userId); ps.setString(2, "EDITAR_ORCAMENTO"); ps.setString(3, "orcamentos");
                    ps.setLong(4, quoteId); ps.setString(5, "Valor e prazo atualizados"); ps.executeUpdate();
                }
                c.commit();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":true,\"id\":" + quoteId + "}");
            } catch (SQLException | RuntimeException ex) {
                c.rollback(); throw ex;
            } finally { c.setAutoCommit(true); }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível atualizar o orçamento.");
        }
    }
}
