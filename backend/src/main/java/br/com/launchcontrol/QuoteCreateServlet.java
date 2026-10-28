package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;

/**
 * Consulta propostas autorizadas e registra orçamentos com custos detalhados no servidor.
 */
@WebServlet("/api/orcamentos")
public class QuoteCreateServlet extends HttpServlet {
    private static final Set<String> PART_CONDITIONS =
            Set.of("NOVAS", "USADAS", "RECONDICIONADAS", "MISTAS", "NAO_INFORMADA");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null) {
            Json.error(response, 401, "Autenticação necessária.");
            return;
        }
        String role = (String) session.getAttribute("userRole");
        long userId = (Long) session.getAttribute("userId");
        if (!"CLIENTE".equals(role) && !"OFICINA".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "Seu perfil não pode consultar orçamentos.");
            return;
        }

        StringBuilder sql = new StringBuilder(
            "SELECT o.id,o.solicitacao_id,o.valor,o.prazo_dias,o.observacoes,o.status,o.criado_em," +
            "ofi.nome_fantasia,s.descricao AS solicitacao_descricao,s.status AS solicitacao_status," +
            "d.garantia_dias,d.validade_dias,d.condicao_pecas," +
            "itens.custo_pecas,itens.custo_mao_obra,itens.custo_outros " +
            "FROM orcamentos o JOIN oficinas ofi ON ofi.id=o.oficina_id " +
            "JOIN solicitacoes_orcamento s ON s.id=o.solicitacao_id " +
            "LEFT JOIN orcamento_detalhes d ON d.orcamento_id=o.id " +
            "LEFT JOIN (SELECT orcamento_id," +
            "SUM(CASE WHEN tipo='PECA' THEN quantidade*valor_unitario ELSE 0 END) AS custo_pecas," +
            "SUM(CASE WHEN tipo='MAO_DE_OBRA' THEN quantidade*valor_unitario ELSE 0 END) AS custo_mao_obra," +
            "SUM(CASE WHEN tipo='OUTRO' THEN quantidade*valor_unitario ELSE 0 END) AS custo_outros " +
            "FROM orcamento_itens GROUP BY orcamento_id) itens ON itens.orcamento_id=o.id ");
        if ("CLIENTE".equals(role)) sql.append("WHERE s.cliente_id=? ");
        else if ("OFICINA".equals(role)) sql.append("WHERE ofi.usuario_id=? ");
        sql.append("ORDER BY o.criado_em DESC LIMIT 200");

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            if (!"ADMINISTRADOR".equals(role)) statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (result.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(result.getLong("id"))
                        .append(",\"solicitacaoId\":").append(result.getLong("solicitacao_id"))
                        .append(",\"valor\":").append(decimal(result, "valor"))
                        .append(",\"custoPecas\":").append(nullableDecimal(result, "custo_pecas"))
                        .append(",\"custoMaoObra\":").append(nullableDecimal(result, "custo_mao_obra"))
                        .append(",\"custoOutros\":").append(nullableDecimal(result, "custo_outros"))
                        .append(",\"garantiaDias\":").append(nullableInteger(result, "garantia_dias"))
                        .append(",\"validadeDias\":").append(nullableInteger(result, "validade_dias"))
                        .append(",\"condicaoPecas\":").append(Json.quote(result.getString("condicao_pecas")))
                        .append(",\"prazoDias\":").append(nullableInteger(result, "prazo_dias"))
                        .append(",\"observacoes\":").append(Json.quote(result.getString("observacoes")))
                        .append(",\"status\":").append(Json.quote(result.getString("status")))
                        .append(",\"criadoEm\":").append(Json.quote(result.getString("criado_em")))
                        .append(",\"oficina\":").append(Json.quote(result.getString("nome_fantasia")))
                        .append(",\"descricao\":").append(Json.quote(result.getString("solicitacao_descricao")))
                        .append(",\"solicitacaoStatus\":").append(Json.quote(result.getString("solicitacao_status"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write(json.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar orçamentos.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        if (session == null || !"OFICINA".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente uma conta de oficina pode enviar orçamento.");
            return;
        }

        long userId = (Long) session.getAttribute("userId");
        long requestId;
        int days;
        Integer warrantyDays;
        Integer validityDays;
        BigDecimal partsAmount;
        BigDecimal laborAmount;
        BigDecimal otherAmount;
        BigDecimal amount;
        boolean itemized;
        String observations = value(request.getParameter("observacoes"));
        String condition = value(request.getParameter("condicaoPecas"));
        if (condition.isBlank()) condition = "NAO_INFORMADA";

        try {
            requestId = Long.parseLong(value(request.getParameter("solicitacaoId")));
            days = Integer.parseInt(value(request.getParameter("prazoDias")));
            warrantyDays = optionalInteger(request.getParameter("garantiaDias"));
            validityDays = optionalInteger(request.getParameter("validadeDias"));
            itemized = hasValue(request.getParameter("valorPecas")) || hasValue(request.getParameter("valorMaoObra")) || hasValue(request.getParameter("valorOutro"));
            if (itemized) {
                partsAmount = parseAmount(request.getParameter("valorPecas"));
                laborAmount = parseAmount(request.getParameter("valorMaoObra"));
                otherAmount = parseAmount(request.getParameter("valorOutro"));
                amount = partsAmount.add(laborAmount).add(otherAmount).setScale(2, RoundingMode.UNNECESSARY);
            } else {
                amount = parseAmount(request.getParameter("valor"));
                partsAmount = BigDecimal.ZERO.setScale(2);
                laborAmount = BigDecimal.ZERO.setScale(2);
                otherAmount = BigDecimal.ZERO.setScale(2);
            }
            if (requestId < 1 || amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("99999999.99")) > 0 || days < 0 || days > 365
                    || observations.length() > 2000
                    || (warrantyDays != null && (warrantyDays < 0 || warrantyDays > 3650))
                    || (validityDays != null && (validityDays < 0 || validityDays > 365))
                    || !PART_CONDITIONS.contains(condition)) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            Json.error(response, 400, "Confira solicitação, valores, prazo, garantia, validade e condição das peças.");
            return;
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long workshopId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM oficinas WHERE usuario_id=? AND ativa=TRUE FOR UPDATE")) {
                    statement.setLong(1, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            Json.error(response, 403, "A conta não está vinculada a uma oficina ativa.");
                            return;
                        }
                        workshopId = result.getLong(1);
                    }
                }

                Long requestedServiceId = null;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT status,servico_id FROM solicitacoes_orcamento WHERE id=? FOR UPDATE")) {
                    statement.setLong(1, requestId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            Json.error(response, 404, "Solicitação não encontrada.");
                            return;
                        }
                        String status = result.getString("status");
                        Object rawServiceId = result.getObject("servico_id");
                        if (rawServiceId != null) requestedServiceId = result.getLong("servico_id");
                        if ("CANCELADA".equals(status) || "CONCLUIDA".equals(status) || "RECUSADA".equals(status)) {
                            connection.rollback();
                            Json.error(response, 409, "A solicitação não aceita novos orçamentos.");
                            return;
                        }
                    }
                }

                if (requestedServiceId != null) {
                    try (PreparedStatement service = connection.prepareStatement(
                            "SELECT id FROM servicos WHERE id=? AND oficina_id=? AND ativo=TRUE")) {
                        service.setLong(1, requestedServiceId);
                        service.setLong(2, workshopId);
                        try (ResultSet result = service.executeQuery()) {
                            if (!result.next()) {
                                connection.rollback();
                                Json.error(response, 403, "O serviço solicitado não pertence a esta oficina ou está inativo.");
                                return;
                            }
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM orcamentos WHERE solicitacao_id=? AND oficina_id=? " +
                        "AND status NOT IN ('RECUSADO','CANCELADO') FOR UPDATE")) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, workshopId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            connection.rollback();
                            Json.error(response, 409, "Esta oficina já possui um orçamento ativo para a solicitação.");
                            return;
                        }
                    }
                }

                long quoteId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO orcamentos(solicitacao_id,oficina_id,valor,prazo_dias,observacoes,status) " +
                        "VALUES(?,?,?,?,?,'ENVIADO')", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, requestId);
                    statement.setLong(2, workshopId);
                    statement.setBigDecimal(3, amount);
                    statement.setInt(4, days);
                    statement.setString(5, observations);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Orçamento sem identificador.");
                        quoteId = keys.getLong(1);
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO orcamento_detalhes(orcamento_id,garantia_dias,validade_dias,condicao_pecas) VALUES(?,?,?,?)")) {
                    statement.setLong(1, quoteId);
                    setNullableInteger(statement, 2, warrantyDays);
                    setNullableInteger(statement, 3, validityDays);
                    statement.setString(4, condition);
                    statement.executeUpdate();
                }

                if (itemized) {
                    insertItem(connection, quoteId, "PECA", "Peças e materiais", partsAmount);
                    insertItem(connection, quoteId, "MAO_DE_OBRA", "Mão de obra", laborAmount);
                    insertItem(connection, quoteId, "OUTRO", "Outros custos", otherAmount);
                } else {
                    insertItem(connection, quoteId, "OUTRO", "Valor global informado pela oficina", amount);
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE solicitacoes_orcamento SET status='ORCAMENTO_ENVIADO' WHERE id=?")) {
                    statement.setLong(1, requestId);
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")) {
                    statement.setLong(1, userId);
                    statement.setString(2, "CRIAR_ORCAMENTO");
                    statement.setString(3, "orcamentos");
                    statement.setLong(4, quoteId);
                    statement.setString(5, "Orçamento enviado pela oficina; total calculado no servidor.");
                    statement.executeUpdate();
                }

                connection.commit();
                response.setStatus(201);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"id\":" + quoteId + ",\"valor\":" + amount.toPlainString() + ",\"status\":\"ENVIADO\"}");
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível enviar orçamento.");
        }
    }

    private static void insertItem(Connection connection, long quoteId, String type, String description,
                                   BigDecimal amount) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO orcamento_itens(orcamento_id,tipo,descricao,quantidade,valor_unitario) VALUES(?,?,?,1,?)")) {
            statement.setLong(1, quoteId);
            statement.setString(2, type);
            statement.setString(3, description);
            statement.setBigDecimal(4, amount);
            statement.executeUpdate();
        }
    }

    private static BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO.setScale(2);
        BigDecimal value = new BigDecimal(raw.trim());
        if (value.signum() < 0 || value.scale() > 2) throw new IllegalArgumentException();
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static Integer optionalInteger(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return Integer.valueOf(raw.trim());
    }

    private static boolean hasValue(String raw) {
        return raw != null && !raw.isBlank();
    }

    private static String value(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.INTEGER);
        else statement.setInt(index, value);
    }

    private static String nullableInteger(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        return value == null ? "null" : String.valueOf(value);
    }

    private static String nullableDecimal(ResultSet result, String column) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        return value == null ? "null" : value.toPlainString();
    }

    private static String decimal(ResultSet result, String column) throws SQLException {
        BigDecimal value = result.getBigDecimal(column);
        return value == null ? "0.00" : value.toPlainString();
    }
}
