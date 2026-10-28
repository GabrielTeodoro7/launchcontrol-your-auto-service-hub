package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

@WebServlet("/api/auth")
public class AuthServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        var session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            Json.error(response, 401, "Nenhuma sessão autenticada.");
            return;
        }
        response.getWriter().write("{\"id\":" + session.getAttribute("userId") +
                ",\"nome\":" + Json.quote((String)session.getAttribute("userName")) +
                ",\"email\":" + Json.quote((String)session.getAttribute("userEmail")) +
                ",\"perfil\":" + Json.quote((String)session.getAttribute("userRole")) + "}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        String action = value(request.getParameter("action"));
        if ("logout".equals(action)) {
            HttpSession session = request.getSession(false);
            if (session != null) session.invalidate();
            response.getWriter().write("{\"ok\":true}");
            return;
        }
        try {
            if ("register".equals(action)) register(request, response);
            else if ("login".equals(action)) login(request, response);
            else Json.error(response, 400, "Ação de autenticação inválida.");
        } catch (SQLException ex) {
            if ("23000".equals(ex.getSQLState())) Json.error(response, 409, "Já existe uma conta com esse e-mail.");
            else Json.error(response, 500, "Não foi possível concluir a operação de autenticação.");
        } catch (GeneralSecurityException ex) {
            Json.error(response, 500, "Não foi possível processar a credencial.");
        }
    }

    private void register(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, GeneralSecurityException, IOException {
        String name = value(request.getParameter("name")).trim();
        String email = value(request.getParameter("email")).trim().toLowerCase(Locale.ROOT);
        char[] password = value(request.getParameter("password")).toCharArray();
        if (name.length() < 2 || name.length() > 120 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || email.length() > 190 || password.length < 10 || password.length > 128) {
            java.util.Arrays.fill(password, '\0');
            Json.error(response, 400, "Informe nome, e-mail válido e senha entre 10 e 128 caracteres.");
            return;
        }
        String hash = PasswordUtil.hash(password);
        java.util.Arrays.fill(password, '\0');
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO usuarios(nome,email,senha_hash,perfil) VALUES(?,?,?,'CLIENTE')",
                     PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, email); ps.setString(3, hash); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) { Json.error(response, 500, "Conta criada sem identificador retornado."); return; }
                startSession(request, response, keys.getLong(1), name, "CLIENTE", email);
            }
        }
    }

    private void login(HttpServletRequest request, HttpServletResponse response)
            throws SQLException, GeneralSecurityException, IOException {
        String email = value(request.getParameter("email")).trim().toLowerCase(Locale.ROOT);
        char[] password = value(request.getParameter("password")).toCharArray();
        try (Connection c = Database.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id,nome,email,senha_hash,perfil FROM usuarios WHERE email=? AND ativo=TRUE")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !PasswordUtil.verify(password, rs.getString("senha_hash"))) {
                    Json.error(response, 401, "E-mail ou senha inválidos.");
                    return;
                }
                startSession(request, response, rs.getLong("id"), rs.getString("nome"),
                        rs.getString("perfil"), rs.getString("email"));
            }
        } finally { java.util.Arrays.fill(password, '\0'); }
    }

    private void startSession(HttpServletRequest request, HttpServletResponse response,
                              long id, String name, String role, String email) throws IOException {
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", id);
        session.setAttribute("userName", name);
        session.setAttribute("userRole", role);
        session.setAttribute("userEmail", email);
        session.setMaxInactiveInterval(30 * 60);
        response.setStatus(200);
        response.getWriter().write("{\"ok\":true,\"id\":" + id + ",\"nome\":" + Json.quote(name) +
                ",\"email\":" + Json.quote(email) + ",\"perfil\":" + Json.quote(role) + "}");
    }
    private static String value(String s) { return s == null ? "" : s; }
}
