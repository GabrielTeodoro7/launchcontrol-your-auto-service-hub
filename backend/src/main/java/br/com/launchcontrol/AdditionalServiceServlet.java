package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/** Gerencia propostas de serviços adicionais com aceite explícito do cliente. */
@WebServlet("/api/servicos-adicionais")
public class AdditionalServiceServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        long userId = (Long) session.getAttribute("userId");
        String role = (String) session.getAttribute("userRole");
        if (!Set.of("CLIENTE", "OFICINA", "ADMINISTRADOR").contains(role)) {
            Json.error(response, 403, "Seu perfil não pode consultar serviços adicionais.");
            return;
        }

        String sql = "SELECT sa.id,sa.ordem_id,os.codigo,sa.descricao,sa.valor,sa.status," +
                "sa.criado_em,sa.respondido_em,sa.cliente_id,sa.oficina_id " +
                "FROM servicos_adicionais sa JOIN ordens_servico os ON os.id=sa.ordem_id ";
        if ("CLIENTE".equals(role)) sql += "WHERE sa.cliente_id=? ";
        else if ("OFICINA".equals(role)) sql += "JOIN oficinas ofi ON ofi.id=sa.oficina_id WHERE ofi.usuario_id=? ";
        sql += "ORDER BY sa.criado_em DESC LIMIT 200";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (!"ADMINISTRADOR".equals(role)) statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (result.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(result.getLong("id"))
                        .append(",\"ordemId\":").append(result.getLong("ordem_id"))
                        .append(",\"codigoOrdem\":").append(Json.quote(result.getString("codigo")))
                        .append(",\"descricao\":").append(Json.quote(result.getString("descricao")))
                        .append(",\"valor\":").append(result.getBigDecimal("valor").toPlainString())
                        .append(",\"status\":").append(Json.quote(result.getString("status")))
                        .append(",\"criadoEm\":").append(Json.quote(result.getString("criado_em")))
                        .append(",\"respondidoEm\":").append(Json.quote(result.getString("respondido_em"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write(json.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar os serviços adicionais.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        long userId = (Long) session.getAttribute("userId");
        String role = (String) session.getAttribute("userRole");
        String action = request.getParameter("acao");

        if ("propor".equals(action)) {
            if (!"OFICINA".equals(role) && !"ADMINISTRADOR".equals(role)) {
                Json.error(response, 403, "Somente a oficina responsável pode propor um serviço adicional.");
                return;
            }
            long orderId;
            BigDecimal amount;
            String description = request.getParameter("descricao");
            try {
                orderId = Long.parseLong(request.getParameter("ordemId"));
                amount = new BigDecimal(request.getParameter("valor"));
                if (orderId < 1 || amount.signum() < 0 || amount.scale() > 2 || amount.precision() > 10) throw new NumberFormatException();
            } catch (Exception ex) {
                Json.error(response, 400, "Informe uma ordem e um valor válido com até duas casas decimais.");
                return;
            }
            if (description == null || description.isBlank() || description.trim().length() > 500) {
                Json.error(response, 400, "A descrição deve conter entre 1 e 500 caracteres.");
                return;
            }
            propose(session, response, userId, role, orderId, description.trim(), amount);
            return;
        }

        if ("decidir".equals(action)) {
            if (!"CLIENTE".equals(role)) {
                Json.error(response, 403, "Somente o cliente responsável pode decidir sobre o serviço adicional.");
                return;
            }
            long additionalId;
            String decision = request.getParameter("decisao");
            try { additionalId = Long.parseLong(request.getParameter("id")); }
            catch (Exception ex) { Json.error(response, 400, "Identificador de serviço adicional inválido."); return; }
            if (additionalId < 1 || (!"ACEITAR".equals(decision) && !"RECUSAR".equals(decision))) {
                Json.error(response, 400, "Decisão inválida.");
                return;
            }
            decide(response, userId, additionalId, decision);
            return;
        }

        Json.error(response, 400, "Ação inválida.");
    }

    private void propose(HttpSession session, HttpServletResponse response, long userId, String role,
                         long orderId, String description, BigDecimal amount) throws IOException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long customerId;
                long workshopId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT so.cliente_id,o.oficina_id,os.status,ofi.usuario_id " +
                        "FROM ordens_servico os JOIN solicitacoes_orcamento so ON so.id=os.solicitacao_id " +
                        "JOIN orcamentos o ON o.id=os.orcamento_id JOIN oficinas ofi ON ofi.id=o.oficina_id " +
                        "WHERE os.id=? FOR UPDATE")) {
                    statement.setLong(1, orderId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) { connection.rollback(); Json.error(response, 404, "Ordem não encontrada."); return; }
                        if ("OFICINA".equals(role) && result.getLong("usuario_id") != userId) {
                            connection.rollback(); Json.error(response, 403, "Esta ordem não pertence à sua oficina."); return;
                        }
                        if ("CONCLUIDO".equals(result.getString("status"))) {
                            connection.rollback(); Json.error(response, 409, "Não é possível propor adicionais para uma ordem concluída."); return;
                        }
                        customerId = result.getLong("cliente_id");
                        workshopId = result.getLong("oficina_id");
                    }
                }
                long additionalId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO servicos_adicionais(ordem_id,oficina_id,cliente_id,descricao,valor) VALUES(?,?,?,?,?)",
                        PreparedStatement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, orderId);
                    statement.setLong(2, workshopId);
                    statement.setLong(3, customerId);
                    statement.setString(4, description);
                    statement.setBigDecimal(5, amount);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Não foi possível gerar o identificador.");
                        additionalId = keys.getLong(1);
                    }
                }
                createNotification(connection, customerId, "Aprovação necessária",
                        "A oficina propôs um serviço adicional: " + description, "SERVICO_ADICIONAL", additionalId);
                connection.commit();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":true,\"id\":" + additionalId + ",\"status\":\"PENDENTE\"}");
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível registrar a proposta de serviço adicional.");
        }
    }

    private void decide(HttpServletResponse response, long userId, long additionalId, String decision) throws IOException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long workshopId;
                String description;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT oficina_id,descricao,status FROM servicos_adicionais WHERE id=? AND cliente_id=? FOR UPDATE")) {
                    statement.setLong(1, additionalId);
                    statement.setLong(2, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) { connection.rollback(); Json.error(response, 404, "Serviço adicional não encontrado para este cliente."); return; }
                        if (!"PENDENTE".equals(result.getString("status"))) {
                            connection.rollback(); Json.error(response, 409, "Este serviço adicional já recebeu uma decisão."); return;
                        }
                        workshopId = result.getLong("oficina_id");
                        description = result.getString("descricao");
                    }
                }
                String nextStatus = "ACEITAR".equals(decision) ? "ACEITO" : "RECUSADO";
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE servicos_adicionais SET status=?,respondido_em=NOW() WHERE id=? AND status='PENDENTE'")) {
                    statement.setString(1, nextStatus);
                    statement.setLong(2, additionalId);
                    if (statement.executeUpdate() != 1) throw new SQLException("O serviço já foi decidido por outra requisição.");
                }
                try (PreparedStatement statement = connection.prepareStatement("SELECT usuario_id FROM oficinas WHERE id=?")) {
                    statement.setLong(1, workshopId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) createNotification(connection, result.getLong(1),
                                "Decisão sobre serviço adicional",
                                "O cliente " + ("ACEITO".equals(nextStatus) ? "aceitou" : "recusou") + ": " + description,
                                "SERVICO_ADICIONAL", additionalId);
                    }
                }
                connection.commit();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":true,\"id\":" + additionalId + ",\"status\":" + Json.quote(nextStatus) + "}");
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível registrar a decisão do cliente.");
        }
    }

    static void createNotification(Connection connection, long userId, String title, String message,
                                   String type, long referenceId) throws SQLException {
        long notificationId;
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO notificacoes(usuario_id,titulo,mensagem,tipo,referencia_id) VALUES(?,?,?,?,?)",
                PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, userId);
            statement.setString(2, title);
            statement.setString(3, message.length() > 500 ? message.substring(0, 500) : message);
            statement.setString(4, type);
            statement.setLong(5, referenceId);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Notificação sem identificador.");
                notificationId = keys.getLong(1);
            }
        }
        try (PreparedStatement delivery = connection.prepareStatement(
                "INSERT INTO notificacao_entregas(notificacao_id,usuario_id,canal) VALUES(?,?,'EMAIL'),(?,?,'WHATSAPP')")) {
            delivery.setLong(1, notificationId);
            delivery.setLong(2, userId);
            delivery.setLong(3, notificationId);
            delivery.setLong(4, userId);
            delivery.executeUpdate();
        }
    }
}
