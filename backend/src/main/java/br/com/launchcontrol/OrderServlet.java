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
 * Consulta ordens de serviço e permite que perfis autorizados atualizem o andamento respeitando as regras do sistema.
 */
@WebServlet("/api/ordens-servico")
public class OrderServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        Long userId = (Long) request.getSession(false).getAttribute("userId");
        String role = (String) request.getSession(false).getAttribute("userRole");
        if (!"CLIENTE".equals(role) && !"OFICINA".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "Seu perfil não pode consultar ordens de serviço.");
            return;
        }
        String historyVehicle = request.getParameter("historicoVeiculoId");
        if (historyVehicle != null && !historyVehicle.isBlank()) {
            if (!"CLIENTE".equals(role)) {
                Json.error(response, 403, "Somente clientes podem consultar a linha do tempo dos próprios veículos.");
                return;
            }
            long vehicleId;
            try {
                vehicleId = Long.parseLong(historyVehicle);
                if (vehicleId < 1) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                Json.error(response, 400, "Identificador de veículo inválido.");
                return;
            }
            try (Connection connection = Database.getConnection()) {
                try (PreparedStatement owner = connection.prepareStatement("SELECT id FROM veiculos WHERE id=? AND cliente_id=?")) {
                    owner.setLong(1, vehicleId);
                    owner.setLong(2, userId);
                    try (ResultSet result = owner.executeQuery()) {
                        if (!result.next()) {
                            Json.error(response, 404, "Veículo não encontrado na sua garagem.");
                            return;
                        }
                    }
                }
                String historySql = "SELECT h.id,h.ordem_id,h.status,h.observacao,h.criado_em,os.codigo " +
                        "FROM ordem_status_historico h JOIN ordens_servico os ON os.id=h.ordem_id " +
                        "JOIN solicitacoes_orcamento so ON so.id=os.solicitacao_id " +
                        "WHERE so.veiculo_id=? AND so.cliente_id=? ORDER BY h.criado_em DESC,h.id DESC LIMIT 200";
                try (PreparedStatement statement = connection.prepareStatement(historySql)) {
                    statement.setLong(1, vehicleId);
                    statement.setLong(2, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        StringBuilder out = new StringBuilder("[");
                        boolean first = true;
                        while (result.next()) {
                            if (!first) out.append(',');
                            first = false;
                            out.append("{\"id\":").append(result.getLong("id"))
                               .append(",\"ordemId\":").append(result.getLong("ordem_id"))
                               .append(",\"codigo\":").append(Json.quote(result.getString("codigo")))
                               .append(",\"status\":").append(Json.quote(result.getString("status")))
                               .append(",\"observacao\":").append(Json.quote(result.getString("observacao")))
                               .append(",\"criadoEm\":").append(Json.quote(result.getString("criado_em"))).append('}');
                        }
                        response.setContentType("application/json;charset=UTF-8");
                        response.setHeader("Cache-Control", "no-store");
                        response.getWriter().write(out.append(']').toString());
                        return;
                    }
                }
            } catch (SQLException ex) {
                Json.error(response, 500, "Não foi possível consultar a linha do tempo da ordem.");
                return;
            }
        }
        StringBuilder sql = new StringBuilder(
          "SELECT os.id,os.codigo,os.status,os.inicio_em,os.previsao_em,os.solicitacao_id," +
          "os.criado_em,os.concluido_em,so.veiculo_id," +
          "u.nome AS cliente,CONCAT(COALESCE(v.marca,''),' ',COALESCE(v.modelo,'')) AS veiculo," +
          "COALESCE(sv.nome,so.descricao) AS servico,o.valor,o.id AS orcamento_id," +
          "o.status AS orcamento_status,o.prazo_dias,o.observacoes " +
          "FROM ordens_servico os JOIN solicitacoes_orcamento so ON so.id=os.solicitacao_id " +
          "JOIN usuarios u ON u.id=so.cliente_id LEFT JOIN veiculos v ON v.id=so.veiculo_id " +
          "LEFT JOIN servicos sv ON sv.id=so.servico_id LEFT JOIN orcamentos o ON o.id=os.orcamento_id ");
        if ("CLIENTE".equals(role)) sql.append("WHERE so.cliente_id=? ");
        else if ("OFICINA".equals(role)) sql.append("JOIN oficinas ofi ON ofi.id=o.oficina_id WHERE ofi.usuario_id=? ");
        sql.append("ORDER BY os.criado_em DESC LIMIT 200");
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            if (!"ADMINISTRADOR".equals(role)) ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder out = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(',');
                    first = false;
                    out.append("{\"id\":").append(rs.getLong("id"))
                       .append(",\"solicitacaoId\":").append(rs.getLong("solicitacao_id"))
                       .append(",\"veiculoId\":").append(rs.getObject("veiculo_id") == null ? "null" : rs.getLong("veiculo_id"))
                       .append(",\"criadoEm\":").append(Json.quote(rs.getString("criado_em")))
                       .append(",\"concluidoEm\":").append(Json.quote(rs.getString("concluido_em")))
                       .append(",\"codigo\":").append(Json.quote(rs.getString("codigo")))
                       .append(",\"status\":").append(Json.quote(rs.getString("status")))
                       .append(",\"cliente\":").append(Json.quote(rs.getString("cliente")))
                       .append(",\"veiculo\":").append(Json.quote(rs.getString("veiculo")))
                       .append(",\"servico\":").append(Json.quote(rs.getString("servico")))
                       .append(",\"valor\":").append(rs.getBigDecimal("valor") == null ? "null" : rs.getBigDecimal("valor").toPlainString())
                       .append(",\"orcamentoId\":").append(rs.getObject("orcamento_id") == null ? "null" : rs.getLong("orcamento_id"))
                       .append(",\"orcamentoStatus\":").append(Json.quote(rs.getString("orcamento_status")))
                       .append(",\"prazoDias\":").append(rs.getObject("prazo_dias") == null ? "null" : rs.getInt("prazo_dias"))
                       .append(",\"observacoes\":").append(Json.quote(rs.getString("observacoes")))
                       .append(",\"inicio\":").append(Json.quote(rs.getString("inicio_em")))
                       .append(",\"previsao\":").append(Json.quote(rs.getString("previsao_em"))).append('}');
                }
                response.getWriter().write(out.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar as ordens de serviço.");
        }
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String role = (String)session.getAttribute("userRole");
        if (!"OFICINA".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "Somente a oficina responsável ou administrador pode atualizar a ordem.");
            return;
        }
        long userId = (Long)session.getAttribute("userId");
        long orderId;
        String nextStatus = request.getParameter("status");
        try { orderId = Long.parseLong(request.getParameter("id")); }
        catch (Exception ex) { Json.error(response, 400, "ID da ordem inválido."); return; }
        if (!java.util.Set.of("EM_ANDAMENTO", "AGUARDANDO_PECA", "CONCLUIDO").contains(nextStatus)) {
            Json.error(response, 400, "Status de destino inválido.");
            return;
        }
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                String previousStatus;
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT os.status,ofi.usuario_id FROM ordens_servico os " +
                    "LEFT JOIN orcamentos o ON o.id=os.orcamento_id " +
                    "LEFT JOIN oficinas ofi ON ofi.id=o.oficina_id WHERE os.id=? FOR UPDATE")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { c.rollback(); Json.error(response, 404, "Ordem não encontrada."); return; }
                        previousStatus = rs.getString("status");
                        if ("CONCLUIDO".equals(previousStatus)) {
                            c.rollback(); Json.error(response, 409, "Uma ordem concluída não pode ser reaberta ou alterada."); return;
                        }
                        if (!isValidTransition(previousStatus, nextStatus)) {
                            c.rollback(); Json.error(response, 409, "A transição solicitada não é permitida para o status atual."); return;
                        }
                        if ("OFICINA".equals(role) && rs.getLong("usuario_id") != userId) {
                            c.rollback(); Json.error(response, 403, "Esta ordem não pertence à sua oficina."); return;
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE ordens_servico SET status=?,inicio_em=COALESCE(inicio_em,NOW())," +
                    "concluido_em=CASE WHEN ?='CONCLUIDO' THEN NOW() ELSE concluido_em END WHERE id=?")) {
                    ps.setString(1, nextStatus); ps.setString(2, nextStatus); ps.setLong(3, orderId); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO ordem_status_historico(ordem_id,status,observacao,usuario_id) VALUES(?,?,?,?)")) {
                    ps.setLong(1, orderId);
                    ps.setString(2, nextStatus);
                    ps.setString(3, "Status alterado de " + previousStatus + " para " + nextStatus + ".");
                    ps.setLong(4, userId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")) {
                    ps.setLong(1, userId); ps.setString(2, "ATUALIZAR_ORDEM"); ps.setString(3, "ordens_servico");
                    ps.setLong(4, orderId); ps.setString(5, "Novo status: " + nextStatus); ps.executeUpdate();
                }
                c.commit(); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":true,\"id\":" + orderId + ",\"status\":" + Json.quote(nextStatus) + "}");
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível atualizar a ordem."); }
    }

    private static boolean isValidTransition(String current, String next) {
        return switch (current) {
            case "PENDENTE" -> "EM_ANDAMENTO".equals(next) || "AGUARDANDO_PECA".equals(next);
            case "EM_ANDAMENTO" -> "AGUARDANDO_PECA".equals(next) || "CONCLUIDO".equals(next);
            case "AGUARDANDO_PECA" -> "EM_ANDAMENTO".equals(next) || "CONCLUIDO".equals(next);
            default -> false;
        };
    }

}
