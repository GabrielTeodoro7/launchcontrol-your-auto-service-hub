package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Mantém o passaporte de manutenção por veículo e valida a propriedade dos registros.
 */
@WebServlet("/api/manutencoes")
public class MaintenanceServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente clientes podem consultar o passaporte dos próprios veículos.");
            return;
        }

        String vehicleParam = request.getParameter("veiculoId");
        StringBuilder sql = new StringBuilder(
                "SELECT h.id,h.veiculo_id,h.categoria,h.descricao,h.data_servico,h.quilometragem," +
                "h.custo,h.proxima_data,h.proxima_quilometragem,h.oficina,h.criado_em,v.quilometragem AS quilometragem_atual " +
                "FROM historico_manutencao h JOIN veiculos v ON v.id=h.veiculo_id " +
                "WHERE v.cliente_id=? ");
        if (vehicleParam != null && !vehicleParam.isBlank()) sql.append("AND h.veiculo_id=? ");
        sql.append("ORDER BY h.data_servico DESC,h.id DESC LIMIT 300");

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setLong(1, (Long) session.getAttribute("userId"));
            if (vehicleParam != null && !vehicleParam.isBlank()) {
                long vehicleId = Long.parseLong(vehicleParam);
                if (vehicleId < 1) {
                    Json.error(response, 400, "Veículo inválido.");
                    return;
                }
                statement.setLong(2, vehicleId);
            }
            try (ResultSet result = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (result.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(result.getLong("id"))
                        .append(",\"veiculoId\":").append(result.getLong("veiculo_id"))
                        .append(",\"categoria\":").append(Json.quote(result.getString("categoria")))
                        .append(",\"descricao\":").append(Json.quote(result.getString("descricao")))
                        .append(",\"dataServico\":").append(Json.quote(result.getString("data_servico")))
                        .append(",\"quilometragem\":").append(nullableNumber(result, "quilometragem"))
                        .append(",\"quilometragemAtual\":").append(nullableNumber(result, "quilometragem_atual"))
                        .append(",\"custo\":").append(result.getBigDecimal("custo") == null ? "null" : result.getBigDecimal("custo").toPlainString())
                        .append(",\"proximaData\":").append(Json.quote(result.getString("proxima_data")))
                        .append(",\"proximaQuilometragem\":").append(nullableNumber(result, "proxima_quilometragem"))
                        .append(",\"oficina\":").append(Json.quote(result.getString("oficina")))
                        .append(",\"criadoEm\":").append(Json.quote(result.getString("criado_em"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(json.append(']').toString());
            }
        } catch (NumberFormatException ex) {
            Json.error(response, 400, "Identificador de veículo inválido.");
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar o histórico de manutenção.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        if (session == null || !"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente clientes podem registrar manutenções.");
            return;
        }

        long vehicleId;
        int mileage;
        BigDecimal cost;
        Date serviceDate;
        Date nextDate = null;
        Integer nextMileage = null;
        String category = value(request.getParameter("categoria"));
        String description = value(request.getParameter("descricao"));
        String shop = value(request.getParameter("oficina"));
        try {
            vehicleId = Long.parseLong(value(request.getParameter("veiculoId")));
            mileage = Integer.parseInt(value(request.getParameter("quilometragem")));
            cost = new BigDecimal(value(request.getParameter("custo")));
            serviceDate = Date.valueOf(LocalDate.parse(value(request.getParameter("dataServico"))));
            String nextDateValue = value(request.getParameter("proximaData"));
            if (!nextDateValue.isBlank()) nextDate = Date.valueOf(LocalDate.parse(nextDateValue));
            String nextMileageValue = value(request.getParameter("proximaQuilometragem"));
            if (!nextMileageValue.isBlank()) nextMileage = Integer.valueOf(nextMileageValue);
            if (vehicleId < 1 || mileage < 0 || mileage > 2_000_000 || cost.signum() < 0
                    || category.length() < 2 || category.length() > 100
                    || description.length() < 3 || description.length() > 2000
                    || shop.length() > 160 || (nextMileage != null && (nextMileage < mileage || nextMileage > 2_000_000))
                    || (nextDate != null && nextDate.before(serviceDate))) {
                throw new IllegalArgumentException();
            }
        } catch (Exception ex) {
            Json.error(response, 400, "Confira veículo, categoria, descrição, data, quilometragem e custo.");
            return;
        }

        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement ownership = connection.prepareStatement(
                    "SELECT id FROM veiculos WHERE id=? AND cliente_id=?")) {
                ownership.setLong(1, vehicleId);
                ownership.setLong(2, (Long) session.getAttribute("userId"));
                try (ResultSet result = ownership.executeQuery()) {
                    if (!result.next()) {
                        Json.error(response, 404, "Veículo não encontrado na sua garagem.");
                        return;
                    }
                }
            }

            String sql = "INSERT INTO historico_manutencao " +
                    "(veiculo_id,categoria,descricao,data_servico,quilometragem,custo,proxima_data,proxima_quilometragem,oficina) " +
                    "VALUES(?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, vehicleId);
                statement.setString(2, category);
                statement.setString(3, description);
                statement.setDate(4, serviceDate);
                statement.setInt(5, mileage);
                statement.setBigDecimal(6, cost);
                statement.setDate(7, nextDate);
                if (nextMileage == null) statement.setNull(8, java.sql.Types.INTEGER);
                else statement.setInt(8, nextMileage);
                statement.setString(9, shop.isBlank() ? null : shop);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"id\":" + keys.getLong(1) + ",\"mensagem\":\"Manutenção registrada.\"}");
                        return;
                    }
                }
            }
            Json.error(response, 500, "Não foi possível registrar a manutenção.");
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível salvar o histórico de manutenção.");
        }
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullableNumber(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        return value == null ? "null" : String.valueOf(value);
    }
}
