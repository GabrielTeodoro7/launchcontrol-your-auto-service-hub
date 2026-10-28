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
 * Permite que um cliente consulte e cadastre seus próprios veículos, respeitando a sessão autenticada.
 */
@WebServlet("/api/veiculos")
public class VehicleServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String role = (String) session.getAttribute("userRole");
        if (!"CLIENTE".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "Seu perfil não pode consultar veículos.");
            return;
        }
        String sql = "SELECT v.id,v.cliente_id,v.marca,v.modelo,v.ano,v.placa,v.quilometragem,v.motorizacao " +
                "FROM veiculos v " + ("ADMINISTRADOR".equals(role) ? "" : "WHERE v.cliente_id=? ") +
                "ORDER BY v.id DESC LIMIT 200";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (!"ADMINISTRADOR".equals(role)) ps.setLong(1, (Long)session.getAttribute("userId"));
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder out = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(',');
                    first = false;
                    out.append("{\"id\":").append(rs.getLong("id"))
                       .append(",\"marca\":").append(Json.quote(rs.getString("marca")))
                       .append(",\"modelo\":").append(Json.quote(rs.getString("modelo")))
                       .append(",\"ano\":").append(rs.getObject("ano") == null ? "null" : rs.getInt("ano"))
                       .append(",\"placa\":").append(Json.quote(rs.getString("placa")))
                       .append(",\"quilometragem\":").append(rs.getObject("quilometragem") == null ? "null" : rs.getInt("quilometragem"))
                       .append(",\"motorizacao\":").append(Json.quote(rs.getString("motorizacao"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(out.append(']').toString());
            }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível consultar veículos."); }
    }
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        if (!"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente cliente pode cadastrar veículo.");
            return;
        }
        String brand = value(request.getParameter("marca"));
        String model = value(request.getParameter("modelo"));
        String plate = value(request.getParameter("placa")).toUpperCase(java.util.Locale.ROOT);
        String engine = value(request.getParameter("motorizacao"));
        int year, km;
        try {
            year = Integer.parseInt(request.getParameter("ano"));
            km = Integer.parseInt(request.getParameter("quilometragem"));
            if (brand.length() < 2 || brand.length() > 80 || model.length() < 1 || model.length() > 100
                    || year < 1950 || year > java.time.Year.now().getValue() + 1 || km < 0 || km > 2_000_000
                    || (!plate.isEmpty() && !plate.matches("[A-Z0-9-]{7,10}")) || engine.length() > 80)
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            Json.error(response, 400, "Confira marca, modelo, ano, placa e quilometragem.");
            return;
        }
        String sql = "INSERT INTO veiculos(cliente_id,marca,modelo,ano,placa,quilometragem,motorizacao) VALUES(?,?,?,?,?,?,?)";
        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, (Long)session.getAttribute("userId")); ps.setString(2, brand); ps.setString(3, model);
            ps.setInt(4, year); ps.setString(5, plate.isEmpty() ? null : plate); ps.setInt(6, km);
            ps.setString(7, engine.isEmpty() ? null : engine); ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (!rs.next()) { Json.error(response, 500, "Veículo cadastrado sem identificador."); return; }
                response.setStatus(201); response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"id\":" + rs.getLong(1) + ",\"ok\":true}");
            }
        } catch (SQLException ex) { Json.error(response, 500, "Não foi possível cadastrar veículo."); }
    }
    private static String value(String s) { return s == null ? "" : s.trim(); }
}
