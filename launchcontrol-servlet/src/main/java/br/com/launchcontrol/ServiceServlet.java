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

@WebServlet("/api/servicos")
public class ServiceServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        String search = request.getParameter("q");
        if (search == null) search = "";
        search = search.trim();

        String sql = "SELECT id, nome, descricao, preco_base, categoria FROM servicos " +
                     "WHERE ativo = TRUE AND (nome LIKE ? OR COALESCE(descricao, '') LIKE ?) " +
                     "ORDER BY nome LIMIT 100";

        try (Connection connection = Database.getConnection();
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
                        .append(",\"nome\":\"").append(escape(results.getString("nome"))).append("\"")
                        .append(",\"descricao\":\"").append(escape(results.getString("descricao"))).append("\"")
                        .append(",\"categoria\":\"").append(escape(results.getString("categoria"))).append("\"")
                        .append(",\"precoBase\":");
                    java.math.BigDecimal price = results.getBigDecimal("preco_base");
                    json.append(price == null ? "null" : price.toPlainString()).append('}');
                }
                json.append(']');
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(json.toString());
            }
        } catch (SQLException exception) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"erro\":\"nao_foi_possivel_consultar_servicos\"}");
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
