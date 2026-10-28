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

/** Fornece indicadores operacionais com escopo limitado ao perfil autenticado. */
@WebServlet("/api/dashboard/analytics")
public class DashboardAnalyticsServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) { Json.error(response, 401, "Autenticação necessária."); return; }
        String role = (String) session.getAttribute("userRole");
        long userId = (Long) session.getAttribute("userId");
        if (!java.util.Set.of("CLIENTE", "OFICINA", "ADMINISTRADOR").contains(role)) {
            Json.error(response, 403, "Seu perfil não possui indicadores operacionais."); return;
        }
        String orderScope;
        String quoteScope;
        if ("OFICINA".equals(role)) {
            orderScope = "JOIN orcamentos q ON q.id=os.orcamento_id JOIN oficinas f ON f.id=q.oficina_id WHERE f.usuario_id=?";
            quoteScope = "JOIN oficinas f ON f.id=o.oficina_id WHERE f.usuario_id=?";
        } else if ("CLIENTE".equals(role)) {
            orderScope = "JOIN solicitacoes_orcamento s ON s.id=os.solicitacao_id WHERE s.cliente_id=?";
            quoteScope = "JOIN solicitacoes_orcamento s ON s.id=o.solicitacao_id WHERE s.cliente_id=?";
        } else {
            orderScope = "";
            quoteScope = "";
        }
        try (Connection c = Database.getConnection()) {
            long totalOrders = scalar(c, "SELECT COUNT(*) FROM ordens_servico os " + orderScope, role, userId);
            long openOrders = scalar(c, "SELECT COUNT(*) FROM ordens_servico os " + orderScope
                    + (orderScope.isBlank() ? " WHERE " : " AND ") + "os.status IN ('PENDENTE','EM_ANDAMENTO','AGUARDANDO_PECA')", role, userId);
            long completedOrders = scalar(c, "SELECT COUNT(*) FROM ordens_servico os " + orderScope
                    + (orderScope.isBlank() ? " WHERE " : " AND ") + "os.status='CONCLUIDO'", role, userId);
            long quotes = scalar(c, "SELECT COUNT(*) FROM orcamentos o " + quoteScope, role, userId);
            long pendingQuotes = scalar(c, "SELECT COUNT(*) FROM orcamentos o " + quoteScope
                    + (quoteScope.isBlank() ? " WHERE " : " AND ") + "o.status IN ('PENDENTE','ENVIADO')", role, userId);
            String revenueSql = "SELECT COALESCE(SUM(o.valor + COALESCE(x.total_adicionais,0)),0) FROM ordens_servico os "
                    + "JOIN orcamentos o ON o.id=os.orcamento_id "
                    + "LEFT JOIN (SELECT ordem_id,SUM(valor) total_adicionais FROM servicos_adicionais WHERE status='ACEITO' GROUP BY ordem_id) x ON x.ordem_id=os.id "
                    + orderScope + (orderScope.isBlank() ? " WHERE " : " AND ") + "os.status='CONCLUIDO'";
            String revenue = decimal(c, revenueSql, role, userId);
            long appointments = scalar(c, "SELECT COUNT(*) FROM agendamentos a "
                    + ("OFICINA".equals(role) ? "JOIN oficinas f ON f.id=a.oficina_id WHERE f.usuario_id=?" :
                       "CLIENTE".equals(role) ? "WHERE a.cliente_id=?" : ""), role, userId);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"perfil\":" + Json.quote(role)
                    + ",\"ordens\":" + totalOrders + ",\"ordensAbertas\":" + openOrders
                    + ",\"ordensConcluidas\":" + completedOrders + ",\"orcamentos\":" + quotes
                    + ",\"orcamentosPendentes\":" + pendingQuotes + ",\"receitaConcluida\":" + revenue
                    + ",\"agendamentos\":" + appointments + "}");
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível calcular os indicadores. Confirme se a migração 003 foi aplicada.");
        }
    }

    private static long scalar(Connection c, String sql, String role, long userId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            if (!"ADMINISTRADOR".equals(role)) p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
    private static String decimal(Connection c, String sql, String role, long userId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            if (!"ADMINISTRADOR".equals(role)) p.setLong(1, userId);
            try (ResultSet r = p.executeQuery()) { r.next(); java.math.BigDecimal n = r.getBigDecimal(1); return n == null ? "0.00" : n.toPlainString(); }
        }
    }
}
