package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * Consulta e mantém o estoque já modelado no banco, sempre limitado ao fornecedor autenticado.
 */
@WebServlet("/api/estoque")
public class InventoryServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        String role = (String) session.getAttribute("userRole");
        if (!"FORNECEDOR".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "A área de estoque é exclusiva para fornecedores e administradores.");
            return;
        }
        String term = request.getParameter("q");
        term = term == null ? "" : term.trim();
        if (term.length() > 120) { Json.error(response, 400, "A pesquisa deve ter até 120 caracteres."); return; }
        String stock = request.getParameter("estoque");
        if (stock == null || !java.util.Set.of("todos", "baixo", "zerado").contains(stock)) stock = "todos";

        StringBuilder sql = new StringBuilder(
            "SELECT p.id,p.nome,p.marca,p.codigo,p.descricao,p.preco,p.estoque,p.ativo,f.nome_fantasia AS fornecedor " +
            "FROM pecas p LEFT JOIN fornecedores f ON f.id=p.fornecedor_id WHERE ");
        if ("FORNECEDOR".equals(role)) sql.append("f.usuario_id=? AND ");
        sql.append("(p.nome LIKE ? OR COALESCE(p.marca,'') LIKE ? OR COALESCE(p.codigo,'') LIKE ?) ");
        if ("baixo".equals(stock)) sql.append("AND p.estoque BETWEEN 1 AND 5 ");
        if ("zerado".equals(stock)) sql.append("AND p.estoque=0 ");
        sql.append("ORDER BY (p.estoque=0) DESC,p.estoque ASC,p.nome ASC LIMIT 300");

        try (Connection c = Database.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            if ("FORNECEDOR".equals(role)) ps.setLong(i++, (Long) session.getAttribute("userId"));
            String pattern = "%" + term + "%";
            ps.setString(i++, pattern); ps.setString(i++, pattern); ps.setString(i, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder out = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(',');
                    first = false;
                    int quantity = rs.getInt("estoque");
                    out.append("{\"id\":").append(rs.getLong("id"))
                        .append(",\"nome\":").append(Json.quote(rs.getString("nome")))
                        .append(",\"marca\":").append(Json.quote(rs.getString("marca")))
                        .append(",\"codigo\":").append(Json.quote(rs.getString("codigo")))
                        .append(",\"descricao\":").append(Json.quote(rs.getString("descricao")))
                        .append(",\"preco\":").append(rs.getBigDecimal("preco").toPlainString())
                        .append(",\"estoque\":").append(quantity)
                        .append(",\"ativo\":").append(rs.getBoolean("ativo"))
                        .append(",\"fornecedor\":").append(Json.quote(rs.getString("fornecedor")))
                        .append(",\"situacao\":").append(Json.quote(quantity == 0 ? "ZERADO" : quantity <= 5 ? "BAIXO" : "DISPONIVEL"))
                        .append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write(out.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar o estoque.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        String role = (String) session.getAttribute("userRole");
        if (!"FORNECEDOR".equals(role) && !"ADMINISTRADOR".equals(role)) {
            Json.error(response, 403, "Somente fornecedor ou administrador pode alterar o estoque.");
            return;
        }

        long itemId = 0;
        String name = value(request.getParameter("nome"));
        String brand = value(request.getParameter("marca"));
        String code = value(request.getParameter("codigo"));
        String description = value(request.getParameter("descricao"));
        BigDecimal price;
        int quantity;
        boolean active = !"false".equalsIgnoreCase(request.getParameter("ativo"));
        try {
            String rawId = request.getParameter("id");
            if (rawId != null && !rawId.isBlank()) itemId = Long.parseLong(rawId);
            price = new BigDecimal(request.getParameter("preco"));
            quantity = Integer.parseInt(request.getParameter("estoque"));
            if (itemId < 0 || name.length() < 2 || name.length() > 160 || brand.length() > 100 ||
                code.length() > 80 || description.length() > 2000 || price.signum() < 0 ||
                price.scale() > 2 || price.compareTo(new BigDecimal("99999999.99")) > 0 ||
                quantity < 0 || quantity > 2_000_000) throw new IllegalArgumentException();
        } catch (Exception ex) {
            Json.error(response, 400, "Confira nome, preço, quantidade e os limites dos campos.");
            return;
        }

        long userId = (Long) session.getAttribute("userId");
        boolean creating = itemId == 0;
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                Long supplierId = null;
                if ("FORNECEDOR".equals(role)) {
                    try (PreparedStatement ps = c.prepareStatement("SELECT id FROM fornecedores WHERE usuario_id=? AND ativo=TRUE FOR UPDATE")) {
                        ps.setLong(1, userId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) { c.rollback(); Json.error(response, 403, "Sua conta ainda não está vinculada a um fornecedor ativo."); return; }
                            supplierId = rs.getLong(1);
                        }
                    }
                }
                if (itemId > 0) {
                    String ownership = "SELECT fornecedor_id FROM pecas WHERE id=? FOR UPDATE";
                    try (PreparedStatement ps = c.prepareStatement(ownership)) {
                        ps.setLong(1, itemId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) { c.rollback(); Json.error(response, 404, "Peça não encontrada."); return; }
                            if ("FORNECEDOR".equals(role) && (supplierId == null || rs.getLong("fornecedor_id") != supplierId)) {
                                c.rollback(); Json.error(response, 403, "Você só pode alterar peças do seu próprio estoque."); return;
                            }
                        }
                    }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE pecas SET nome=?,marca=?,codigo=?,descricao=?,preco=?,estoque=?,ativo=? WHERE id=?")) {
                        ps.setString(1, name); ps.setString(2, blankNull(brand)); ps.setString(3, blankNull(code));
                        ps.setString(4, blankNull(description)); ps.setBigDecimal(5, price); ps.setInt(6, quantity);
                        ps.setBoolean(7, active); ps.setLong(8, itemId); ps.executeUpdate();
                    }
                } else {
                    if (supplierId == null) { c.rollback(); Json.error(response, 400, "Para cadastrar uma peça, a conta deve estar vinculada a um fornecedor."); return; }
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO pecas(fornecedor_id,nome,marca,codigo,descricao,preco,estoque,ativo) VALUES(?,?,?,?,?,?,?,?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, supplierId); ps.setString(2, name); ps.setString(3, blankNull(brand));
                        ps.setString(4, blankNull(code)); ps.setString(5, blankNull(description));
                        ps.setBigDecimal(6, price); ps.setInt(7, quantity); ps.setBoolean(8, active); ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Peça sem identificador.");
                            itemId = keys.getLong(1);
                        }
                    }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO auditoria(usuario_id,acao,entidade,entidade_id,detalhes) VALUES(?,?,?,?,?)")) {
                    ps.setLong(1, userId); ps.setString(2, creating ? "CRIAR_ESTOQUE_PECA" : "ATUALIZAR_ESTOQUE_PECA");
                    ps.setString(3, "pecas"); ps.setLong(4, itemId);
                    ps.setString(5, "Cadastro de estoque atualizado"); ps.executeUpdate();
                }
                c.commit();
                response.setStatus(creating ? 201 : 200);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"ok\":true,\"id\":" + itemId + "}");
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            } finally { c.setAutoCommit(true); }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível salvar a peça no estoque.");
        }
    }

    private static String value(String input) { return input == null ? "" : input.trim(); }
    private static String blankNull(String input) { return input.isBlank() ? null : input; }
}
