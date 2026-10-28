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
 * Atualiza propostas editáveis somente pela oficina responsável ou administrador.
 */
@WebServlet("/api/orcamentos/editar")
public class QuoteServlet extends HttpServlet {
    private static final Set<String> PART_CONDITIONS =
            Set.of("NOVAS", "USADAS", "RECONDICIONADAS", "MISTAS", "NAO_INFORMADA");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        if (session == null) {
            Json.error(response, 401, "Autenticação necessária.");
            return;
        }
        String role = (String) session.getAttribute("userRole");
        if (!"OFICINA".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "Somente a oficina autorizada pode alterar o orçamento.");
            return;
        }

        long userId = (Long) session.getAttribute("userId");
        long quoteId;
        BigDecimal amount;
        BigDecimal partsAmount;
        BigDecimal laborAmount;
        BigDecimal otherAmount;
        int days;
        Integer warrantyDays;
        Integer validityDays;
        boolean itemized;
        String observations = value(request.getParameter("observacoes"));
        String condition = value(request.getParameter("condicaoPecas"));
        if (condition.isBlank()) condition = "NAO_INFORMADA";

        try {
            quoteId = Long.parseLong(value(request.getParameter("id")));
            days = Integer.parseInt(value(request.getParameter("prazoDias")));
            warrantyDays = optionalInteger(request.getParameter("garantiaDias"));
            validityDays = optionalInteger(request.getParameter("validadeDias"));
            itemized = hasValue(request.getParameter("valorPecas"))
                    || hasValue(request.getParameter("valorMaoObra"))
                    || hasValue(request.getParameter("valorOutro"));
            if (itemized) {
                partsAmount = parseAmount(request.getParameter("valorPecas"));
                laborAmount = parseAmount(request.getParameter("valorMaoObra"));
                otherAmount = parseAmount(request.getParameter("valorOutro"));
                amount = partsAmount.add(laborAmount).add(otherAmount).setScale(2, RoundingMode.UNNECESSARY);
            } else {
                amount = parseAmount(request.getParameter("valor"));
                partsAmount = BigDecimal.ZERO.setScale(2);
                laborAmount = BigDecimal.ZERO.setScale(2);
                otherAmount = amount;
            }
            if (quoteId < 1 || amount.signum() < 0 || amount.compareTo(new BigDecimal("99999999.99")) > 0
                    || days < 0 || days > 365 || observations.length() > 2000
                    || (warrantyDays != null && (warrantyDays < 0 || warrantyDays > 3650))
                    || (validityDays != null && (validityDays < 0 || validityDays > 365))
                    || !PART_CONDITIONS.contains(condition)) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            Json.error(response, 400, "Confira orçamento, custos, prazo, garantia, validade e condição das peças.");
            return;
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String query = "SELECT o.status,os.status AS ordem_status,ofi.usuario_id " +
                        "FROM orcamentos o JOIN oficinas ofi ON ofi.id=o.oficina_id " +
                        "LEFT JOIN ordens_servico os ON os.orcamento_id=o.id WHERE o.id=? FOR UPDATE";
                try (PreparedStatement statement = connection.prepareStatement(query)) {
                    statement.setLong(1, quoteId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            Json.error(response, 404, "Orçamento não encontrado.");
                            return;
                        }
                        if ("CONCLUIDO".equals(result.getString("ordem_status"))) {
                            connection.rollback();
                            Json.error(response, 409, "Orçamentos de serviços concluídos não podem ser alterados.");
                            return;
                        }
                        if ("OFICINA".equals(role) && result.getLong("usuario_id") != userId) {
                            connection.rollback();
                            Json.error(response, 403, "Este orçamento não pertence à sua oficina.");
                            return;
                        }
                        if (!Set.of("PENDENTE", "ENVIADO").contains(result.getString("status"))) {
                            connection.rollback();
                            Json.error(response, 409, "Este orçamento não está disponível para edição.");
                            return;
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE orcamentos SET valor=?,prazo_dias=?,observacoes=? WHERE id=?")) {
                    statement.setBigDecimal(1, amount);
                    statement.setInt(2, days);
                    statement.setString(3, observations.isEmpty() ? null : observations);
                    statement.setLong(4, quoteId);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM orcamento_itens WHERE orcamento_id=?")) {
                    statement.setLong(1, quoteId);
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
                        "INSERT INTO orcamento_detalhes(orcamento_id,garantia_dias,validade_dias,condicao_pecas) " +
                        "VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE garantia_dias=VALUES(garantia_dias)," +
                        "validade_dias=VALUES(validade_dias),condicao_pecas=VALUES(condicao_pecas)")) {
                    statement.setLong(1, quoteId);
                    setNullableInteger(statement, 2, warrantyDays);
                    setNullableInteger(statement, 3, validityDays);
                    statement.setString(4, condition);
                    statement.executeUpdate();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")) {
                    statement.setLong(1, userId);
                    statement.setString(2, "EDITAR_ORCAMENTO");
                    statement.setString(3, "orcamentos");
                    statement.setLong(4, quoteId);
                    statement.setString(5, "Custos, prazo e condições da proposta atualizados.");
                    statement.executeUpdate();
                }

                connection.commit();
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":true,\"id\":" + quoteId + ",\"valor\":" + amount.toPlainString() + "}");
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível atualizar o orçamento.");
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
        BigDecimal amount = new BigDecimal(raw.trim());
        if (amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException();
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
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
}
