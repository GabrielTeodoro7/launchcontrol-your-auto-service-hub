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

/** Disponibiliza notificações exclusivamente ao usuário autenticado. */
@WebServlet("/api/notificacoes")
public class NotificationServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        long userId = (Long) session.getAttribute("userId");
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id,titulo,mensagem,tipo,referencia_id,lida_em,criada_em " +
                     "FROM notificacoes WHERE usuario_id=? ORDER BY criada_em DESC,id DESC LIMIT 100")) {
            statement.setLong(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (result.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(result.getLong("id"))
                        .append(",\"titulo\":").append(Json.quote(result.getString("titulo")))
                        .append(",\"mensagem\":").append(Json.quote(result.getString("mensagem")))
                        .append(",\"tipo\":").append(Json.quote(result.getString("tipo")))
                        .append(",\"referenciaId\":").append(result.getObject("referencia_id") == null ? "null" : result.getLong("referencia_id"))
                        .append(",\"lidaEm\":").append(Json.quote(result.getString("lida_em")))
                        .append(",\"criadaEm\":").append(Json.quote(result.getString("criada_em"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write(json.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar as notificações.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        long userId = (Long) session.getAttribute("userId");
        long notificationId;
        try { notificationId = Long.parseLong(request.getParameter("id")); }
        catch (Exception ex) { Json.error(response, 400, "Identificador de notificação inválido."); return; }
        if (notificationId < 1) { Json.error(response, 400, "Identificador de notificação inválido."); return; }

        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE notificacoes SET lida_em=COALESCE(lida_em,NOW()) WHERE id=? AND usuario_id=?")) {
            statement.setLong(1, notificationId);
            statement.setLong(2, userId);
            if (statement.executeUpdate() != 1) {
                Json.error(response, 404, "Notificação não encontrada para este usuário.");
                return;
            }
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"ok\":true}");
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível marcar a notificação como lida.");
        }
    }
}
