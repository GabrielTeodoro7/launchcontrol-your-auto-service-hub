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
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import jakarta.servlet.http.HttpSession;

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
        String partQuery = value(request.getParameter("peca"), 140);
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
                "AND (? IS NULL OR LOWER(p.nome) LIKE LOWER(?) OR LOWER(COALESCE(p.codigo,'')) LIKE LOWER(?) " +
                "OR LOWER(COALESCE(c.codigo_oem,'')) LIKE LOWER(?)) " +
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
            String partPattern = partQuery.isBlank() ? null : "%" + partQuery + "%";
            statement.setString(12, partPattern);
            statement.setString(13, partPattern);
            statement.setString(14, partPattern);

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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        if (session == null || !"ADMINISTRADOR".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente administradores podem cadastrar referências de compatibilidade.");
            return;
        }

        long partId;
        Integer yearStart;
        Integer yearEnd;
        String make = value(request.getParameter("marcaVeiculo"), 80);
        String model = value(request.getParameter("modeloVeiculo"), 100);
        String engine = value(request.getParameter("motorizacao"), 80);
        String oem = value(request.getParameter("codigoOem"), 80);
        String status = value(request.getParameter("status"), 30);
        String source = value(request.getParameter("fonte"), 180);
        String reference = value(request.getParameter("referencia"), 500);
        String notes = value(request.getParameter("observacoes"), 500);

        try {
            partId = Long.parseLong(value(request.getParameter("pecaId"), 20));
            yearStart = optionalYear(request.getParameter("anoInicio"));
            yearEnd = optionalYear(request.getParameter("anoFim"));
            if (partId < 1 || make.length() < 2 || model.length() < 2
                    || (yearStart != null && yearEnd != null && yearEnd < yearStart)
                    || !java.util.Set.of("CONFIRMADA", "INCOMPATIVEL", "NAO_VERIFICADA").contains(status)
                    || (("CONFIRMADA".equals(status) || "INCOMPATIVEL".equals(status))
                        && (source.isBlank() || reference.isBlank()))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            Json.error(response, 400, "Confira a peça, veículo, anos, status e fonte. Estados verificados exigem fonte e referência.");
            return;
        }

        boolean verified = "CONFIRMADA".equals(status) || "INCOMPATIVEL".equals(status);
        String sql = "INSERT INTO compatibilidade_pecas(peca_id,marca_veiculo,modelo_veiculo,ano_inicio,ano_fim," +
                "motorizacao,codigo_oem,status,fonte,referencia,observacoes,verificado_por,verificado_em) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement part = connection.prepareStatement("SELECT id FROM pecas WHERE id=? AND ativo=TRUE")) {
                part.setLong(1, partId);
                try (ResultSet result = part.executeQuery()) {
                    if (!result.next()) {
                        Json.error(response, 404, "Peça ativa não encontrada no inventário.");
                        return;
                    }
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, partId);
                statement.setString(2, make);
                statement.setString(3, model);
                setNullableInteger(statement, 4, yearStart);
                setNullableInteger(statement, 5, yearEnd);
                statement.setString(6, engine.isBlank() ? null : engine);
                statement.setString(7, oem.isBlank() ? null : oem);
                statement.setString(8, status);
                statement.setString(9, source.isBlank() ? null : source);
                statement.setString(10, reference.isBlank() ? null : reference);
                statement.setString(11, notes.isBlank() ? null : notes);
                if (verified) {
                    statement.setLong(12, (Long) session.getAttribute("userId"));
                    statement.setTimestamp(13, Timestamp.from(Instant.now()));
                } else {
                    statement.setNull(12, Types.BIGINT);
                    statement.setNull(13, Types.TIMESTAMP);
                }
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        Json.error(response, 500, "A referência não recebeu identificador.");
                        return;
                    }
                    response.setStatus(201);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"id\":" + keys.getLong(1)
                            + ",\"status\":" + Json.quote(verified ? status : "NAO_VERIFICADA")
                            + ",\"mensagem\":\"Referência de aplicação registrada.\"}");
                }
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível registrar a referência de compatibilidade.");
        }
    }

    private static Integer optionalYear(String raw) {
        if (raw == null || raw.isBlank()) return null;
        int year = Integer.parseInt(raw.trim());
        if (year < 1950 || year > 2100) throw new IllegalArgumentException();
        return year;
    }

    private static void setNullableInteger(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) statement.setNull(index, Types.SMALLINT);
        else statement.setInt(index, value);
    }


}
