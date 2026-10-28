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
        StringBuilder sql = new StringBuilder(
          "SELECT os.id,os.codigo,os.status,os.inicio_em,os.previsao_em," +
          "u.nome AS cliente,CONCAT(COALESCE(v.marca,''),' ',COALESCE(v.modelo,'')) AS veiculo," +
          "COALESCE(sv.nome,so.descricao) AS servico,o.valor " +
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
                       .append(",\"codigo\":").append(Json.quote(rs.getString("codigo")))
                       .append(",\"status\":").append(Json.quote(rs.getString("status")))
                       .append(",\"cliente\":").append(Json.quote(rs.getString("cliente")))
                       .append(",\"veiculo\":").append(Json.quote(rs.getString("veiculo")))
                       .append(",\"servico\":").append(Json.quote(rs.getString("servico")))
                       .append(",\"valor\":").append(rs.getBigDecimal("valor") == null ? "null" : rs.getBigDecimal("valor").toPlainString())
                       .append(",\"inicio\":").append(Json.quote(rs.getString("inicio_em")))
                       .append(",\"previsao\":").append(Json.quote(rs.getString("previsao_em"))).append('}');
                }
                response.getWriter().write(out.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar as ordens de serviço.");
        }
    }
}
