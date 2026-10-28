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
 * Recebe pedidos do navegador para consultar os serviços cadastrados no banco.
 */
@WebServlet("/api/servicos")
public class ServiceServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        String search = request.getParameter("q");
        if (search == null) search = "";
        search = search.trim();
        if (search.length() > 100) {
            Json.error(response, 400, "A pesquisa pode ter no máximo 100 caracteres.");
            return;
        }

        String sql = "SELECT id, nome, descricao, preco_base, categoria FROM servicos " +
                     "WHERE ativo = TRUE AND (nome LIKE ? OR COALESCE(descricao, '') LIKE ?) " +
                     "ORDER BY nome LIMIT 100";

        try (Connection connection = Database.getConnection();
             // PreparedStatement separa o comando SQL dos valores recebidos, reduzindo o risco de injeção de SQL.
             PreparedStatement statement = connection.prepareStatement(sql)) {
            String term = "%" + search + "%";
            statement.setString(1, term);
            statement.setString(2, term);
            try (ResultSet results = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (results.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(results.getLong("id"))
                        .append(",\"nome\":").append(Json.quote(results.getString("nome")))
                        .append(",\"descricao\":").append(Json.quote(results.getString("descricao")))
                        .append(",\"categoria\":").append(Json.quote(results.getString("categoria")))
                        .append(",\"precoBase\":");
                    java.math.BigDecimal price = results.getBigDecimal("preco_base");
                    json.append(price == null ? "null" : price.toPlainString()).append('}');
                }
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(json.append(']').toString());
            }
        } catch (SQLException exception) {
            Json.error(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Não foi possível consultar o catálogo de serviços.");
        }
    }
}
