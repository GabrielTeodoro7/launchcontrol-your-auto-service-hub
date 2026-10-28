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
 * Registra avaliações de clientes somente para ordens de serviço concluídas.
 */
@WebServlet("/api/avaliacoes")
public class ReviewServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente clientes podem consultar suas avaliações.");
            return;
        }
        String sql = "SELECT a.id,a.ordem_servico_id,a.nota,a.comentario,a.criado_em,os.codigo,of.nome_fantasia " +
                "FROM avaliacoes_servico a JOIN ordens_servico os ON os.id=a.ordem_servico_id " +
                "JOIN oficinas of ON of.id=a.oficina_id WHERE a.cliente_id=? ORDER BY a.criado_em DESC LIMIT 100";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, (Long) session.getAttribute("userId"));
            try (ResultSet result = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (result.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(result.getLong("id"))
                        .append(",\"ordemId\":").append(result.getLong("ordem_servico_id"))
                        .append(",\"codigo\":").append(Json.quote(result.getString("codigo")))
                        .append(",\"oficina\":").append(Json.quote(result.getString("nome_fantasia")))
                        .append(",\"nota\":").append(result.getInt("nota"))
                        .append(",\"comentario\":").append(Json.quote(result.getString("comentario")))
                        .append(",\"criadoEm\":").append(Json.quote(result.getString("criado_em"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(json.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar as avaliações.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        if (session == null || !"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente clientes podem avaliar serviços.");
            return;
        }

        long orderId;
        int rating;
        String comment = value(request.getParameter("comentario"));
        try {
            orderId = Long.parseLong(value(request.getParameter("ordemId")));
            rating = Integer.parseInt(value(request.getParameter("nota")));
            if (orderId < 1 || rating < 1 || rating > 5 || comment.length() > 1000) throw new IllegalArgumentException();
        } catch (Exception ex) {
            Json.error(response, 400, "Informe uma ordem válida, uma nota de 1 a 5 e um comentário de até 1.000 caracteres.");
            return;
        }

        try (Connection connection = Database.getConnection()) {
            long customerId = (Long) session.getAttribute("userId");
            long shopId;
            String check = "SELECT o.oficina_id FROM ordens_servico os " +
                    "JOIN solicitacoes_orcamento so ON so.id=os.solicitacao_id " +
                    "JOIN orcamentos o ON o.id=os.orcamento_id " +
                    "WHERE os.id=? AND so.cliente_id=? AND os.status='CONCLUIDO'";
            try (PreparedStatement statement = connection.prepareStatement(check)) {
                statement.setLong(1, orderId);
                statement.setLong(2, customerId);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) {
                        Json.error(response, 409, "Só é possível avaliar uma ordem concluída pertencente à sua conta.");
                        return;
                    }
                    shopId = result.getLong("oficina_id");
                }
            }

            String insert = "INSERT INTO avaliacoes_servico(ordem_servico_id,cliente_id,oficina_id,nota,comentario) VALUES(?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(insert, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, orderId);
                statement.setLong(2, customerId);
                statement.setLong(3, shopId);
                statement.setInt(4, rating);
                statement.setString(5, comment.isBlank() ? null : comment);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"id\":" + keys.getLong(1) + ",\"mensagem\":\"Avaliação registrada.\"}");
                        return;
                    }
                }
            }
            Json.error(response, 500, "Não foi possível registrar a avaliação.");
        } catch (SQLException ex) {
            if (ex.getSQLState() != null && ex.getSQLState().startsWith("23")) {
                Json.error(response, 409, "Esta ordem já recebeu uma avaliação.");
            } else {
                Json.error(response, 500, "Não foi possível salvar a avaliação.");
            }
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
