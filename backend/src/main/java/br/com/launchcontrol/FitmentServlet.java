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

/**
 * Consulta referências de aplicação de peças e informa explicitamente o nível de verificação.
 */
@WebServlet("/api/compatibilidade-pecas")
public class FitmentServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String make = value(request.getParameter("marca"), 80);
        String model = value(request.getParameter("modelo"), 100);
        String engine = value(request.getParameter("motorizacao"), 80);
        String oem = value(request.getParameter("oem"), 80);
        Integer year = null;

        try {
            String yearValue = value(request.getParameter("ano"), 4);
            if (!yearValue.isBlank()) {
                year = Integer.valueOf(yearValue);
                if (year < 1950 || year > 2100) throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            Json.error(response, 400, "Ano do veículo inválido.");
            return;
        }

        if (make.length() < 2 || model.length() < 2) {
            Json.error(response, 400, "Informe marca e modelo para consultar a compatibilidade.");
            return;
        }

        String sql = "SELECT c.id,c.status,c.fonte,c.referencia,c.observacoes,c.codigo_oem,c.verificado_em," +
                "p.id AS peca_id,p.nome AS nome_peca,p.codigo AS codigo_peca " +
                "FROM compatibilidade_pecas c JOIN pecas p ON p.id=c.peca_id " +
                "WHERE p.ativo=TRUE AND LOWER(c.marca_veiculo)=LOWER(?) AND LOWER(c.modelo_veiculo)=LOWER(?) " +
                "AND (? IS NULL OR c.ano_inicio IS NULL OR ? >= c.ano_inicio) " +
                "AND (? IS NULL OR c.ano_fim IS NULL OR ? <= c.ano_fim) " +
                "AND (c.motorizacao IS NULL OR c.motorizacao='' OR (? IS NOT NULL AND LOWER(c.motorizacao)=LOWER(?))) " +
                "AND (? IS NULL OR LOWER(COALESCE(c.codigo_oem,''))=LOWER(?) OR LOWER(COALESCE(p.codigo,''))=LOWER(?)) " +
                "ORDER BY CASE WHEN c.status='CONFIRMADA' AND c.fonte IS NOT NULL AND c.verificado_em IS NOT NULL THEN 0 " +
                "WHEN c.status='INCOMPATIVEL' AND c.fonte IS NOT NULL AND c.verificado_em IS NOT NULL THEN 1 ELSE 2 END, p.nome " +
                "LIMIT 100";

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, make);
            statement.setString(2, model);
            statement.setObject(3, year);
            statement.setObject(4, year);
            statement.setObject(5, year);
            statement.setObject(6, year);
            statement.setString(7, engine.isBlank() ? null : engine);
            statement.setString(8, engine.isBlank() ? null : engine);
            statement.setString(9, oem.isBlank() ? null : oem);
            statement.setString(10, oem.isBlank() ? null : oem);
            statement.setString(11, oem.isBlank() ? null : oem);

            try (ResultSet result = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (result.next()) {
                    if (!first) json.append(',');
                    first = false;
                    String status = result.getString("status");
                    String source = result.getString("fonte");
                    String verifiedAt = result.getString("verificado_em");
                    boolean verified = source != null && !source.isBlank() && verifiedAt != null;
                    if (!verified) status = "NAO_VERIFICADA";

                    json.append("{\"id\":").append(result.getLong("id"))
                        .append(",\"pecaId\":").append(result.getLong("peca_id"))
                        .append(",\"peca\":").append(Json.quote(result.getString("nome_peca")))
                        .append(",\"codigoPeca\":").append(Json.quote(result.getString("codigo_peca")))
                        .append(",\"codigoOem\":").append(Json.quote(result.getString("codigo_oem")))
                        .append(",\"status\":").append(Json.quote(status))
                        .append(",\"fonte\":").append(Json.quote(source))
                        .append(",\"referencia\":").append(Json.quote(result.getString("referencia")))
                        .append(",\"observacoes\":").append(Json.quote(result.getString("observacoes")))
                        .append(",\"verificadoEm\":").append(Json.quote(verifiedAt)).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write(json.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar o catálogo de compatibilidade.");
        }
    }

    private static String value(String raw, int maxLength) {
        if (raw == null) return "";
        String clean = raw.trim();
        return clean.length() > maxLength ? clean.substring(0, maxLength) : clean;
    }
}
