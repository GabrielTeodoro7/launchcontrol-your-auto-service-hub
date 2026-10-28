package br.com.launchcontrol;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

/** Armazena anexos permitidos fora da pasta pública e verifica a propriedade do histórico. */
@WebServlet("/api/anexos-manutencao")
@MultipartConfig(maxFileSize = 5 * 1024 * 1024, maxRequestSize = 6 * 1024 * 1024, fileSizeThreshold = 0)
public class MaintenanceAttachmentServlet extends HttpServlet {
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (!"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente clientes podem consultar anexos dos próprios veículos.");
            return;
        }
        long userId = (Long) session.getAttribute("userId");
        String download = request.getParameter("download");
        if (download != null && !download.isBlank()) {
            downloadAttachment(response, userId, download);
            return;
        }

        long vehicleId;
        try {
            vehicleId = Long.parseLong(request.getParameter("veiculoId"));
            if (vehicleId < 1) throw new NumberFormatException();
        } catch (Exception ex) {
            Json.error(response, 400, "Selecione um veículo válido para consultar anexos.");
            return;
        }
        String sql = "SELECT a.id,a.historico_id,a.nome_original,a.tipo_mime,a.tamanho_bytes,a.criado_em " +
                "FROM anexos_manutencao a JOIN historico_manutencao h ON h.id=a.historico_id " +
                "JOIN veiculos v ON v.id=h.veiculo_id WHERE a.cliente_id=? AND v.id=? " +
                "ORDER BY a.criado_em DESC,a.id DESC LIMIT 100";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setLong(2, vehicleId);
            try (ResultSet result = statement.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (result.next()) {
                    if (!first) json.append(',');
                    first = false;
                    json.append("{\"id\":").append(result.getLong("id"))
                        .append(",\"historicoId\":").append(result.getLong("historico_id"))
                        .append(",\"nome\":").append(Json.quote(result.getString("nome_original")))
                        .append(",\"tipo\":").append(Json.quote(result.getString("tipo_mime")))
                        .append(",\"tamanho\":").append(result.getLong("tamanho_bytes"))
                        .append(",\"criadoEm\":").append(Json.quote(result.getString("criado_em")))
                        .append(",\"url\":").append(Json.quote("api/anexos-manutencao?download=" + result.getLong("id"))).append('}');
                }
                response.setContentType("application/json;charset=UTF-8");
                response.setHeader("Cache-Control", "no-store");
                response.getWriter().write(json.append(']').toString());
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar os anexos do veículo.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (!"CLIENTE".equals(session.getAttribute("userRole"))) {
            Json.error(response, 403, "Somente clientes podem anexar documentos ao passaporte.");
            return;
        }
        String configuredDirectory = System.getenv("LC_UPLOAD_DIR");
        if (configuredDirectory == null || configuredDirectory.isBlank()) {
            Json.error(response, 503, "O armazenamento de anexos não está configurado. Defina LC_UPLOAD_DIR como um caminho absoluto fora da pasta pública do Tomcat.");
            return;
        }

        Path directory;
        try {
            Path configuredPath = Path.of(configuredDirectory);
            if (!configuredPath.isAbsolute()) {
                Json.error(response, 503, "LC_UPLOAD_DIR deve ser um caminho absoluto.");
                return;
            }
            directory = configuredPath.toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            Json.error(response, 503, "O diretório configurado para anexos é inválido.");
            return;
        }
        if (isInsidePublicWebRoot(directory)) {
            Json.error(response, 503, "LC_UPLOAD_DIR deve ficar fora da pasta pública do Tomcat.");
            return;
        }

        long userId = (Long) session.getAttribute("userId");
        long historyId;
        try {
            historyId = Long.parseLong(request.getParameter("historicoId"));
            if (historyId < 1) throw new NumberFormatException();
        } catch (Exception ex) {
            Json.error(response, 400, "Selecione um registro de manutenção válido.");
            return;
        }

        Part file;
        try {
            file = request.getPart("arquivo");
        } catch (ServletException | IllegalStateException | IOException ex) {
            Json.error(response, 400, "O arquivo excede o limite permitido ou não pôde ser recebido.");
            return;
        }
        if (file == null || file.getSize() < 1 || file.getSize() > MAX_BYTES) {
            Json.error(response, 400, "O arquivo deve ter entre 1 byte e 5 MB.");
            return;
        }

        String mime;
        String extension;
        try {
            byte[] header = new byte[8];
            try (var input = file.getInputStream()) {
                int read = input.read(header);
                if (read < 5) throw new IllegalArgumentException();
            }
            if (header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F' && header[4] == '-') {
                mime = "application/pdf"; extension = ".pdf";
            } else if ((header[0] & 255) == 0x89 && header[1] == 'P' && header[2] == 'N' && header[3] == 'G') {
                mime = "image/png"; extension = ".png";
            } else if ((header[0] & 255) == 0xFF && (header[1] & 255) == 0xD8 && (header[2] & 255) == 0xFF) {
                mime = "image/jpeg"; extension = ".jpg";
            } else {
                Json.error(response, 415, "Formato não permitido. Envie PDF, PNG ou JPEG válido.");
                return;
            }
        } catch (IOException | IllegalArgumentException ex) {
            Json.error(response, 400, "Não foi possível validar o conteúdo do arquivo.");
            return;
        }

        String originalName = sanitizeName(file.getSubmittedFileName());
        String storedName = UUID.randomUUID() + extension;
        Path destination = directory.resolve(storedName).normalize();
        if (!destination.getParent().equals(directory)) {
            Json.error(response, 400, "Destino de arquivo inválido.");
            return;
        }

        try (Connection connection = Database.getConnection()) {
            try (PreparedStatement owner = connection.prepareStatement(
                    "SELECT h.id FROM historico_manutencao h JOIN veiculos v ON v.id=h.veiculo_id " +
                    "WHERE h.id=? AND v.cliente_id=?")) {
                owner.setLong(1, historyId);
                owner.setLong(2, userId);
                try (ResultSet result = owner.executeQuery()) {
                    if (!result.next()) {
                        Json.error(response, 404, "Registro de manutenção não encontrado na sua garagem.");
                        return;
                    }
                }
            }
            Files.createDirectories(directory);
            Path realDirectory = directory.toRealPath();
            if (isInsidePublicWebRoot(realDirectory)) {
                Json.error(response, 503, "LC_UPLOAD_DIR deve resolver para um diretório fora da pasta pública do Tomcat.");
                return;
            }
            destination = realDirectory.resolve(storedName).normalize();
            if (!destination.getParent().equals(realDirectory)) {
                Json.error(response, 400, "Destino de arquivo inválido.");
                return;
            }
            try (var input = file.getInputStream()) {
                Files.copy(input, destination);
            }
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO anexos_manutencao(historico_id,cliente_id,nome_original,nome_armazenado,tipo_mime,tamanho_bytes) " +
                    "VALUES(?,?,?,?,?,?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, historyId);
                statement.setLong(2, userId);
                statement.setString(3, originalName);
                statement.setString(4, storedName);
                statement.setString(5, mime);
                statement.setLong(6, file.getSize());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("Identificador de anexo não gerado.");
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"ok\":true,\"id\":" + keys.getLong(1) + "}");
                }
            } catch (SQLException ex) {
                Files.deleteIfExists(destination);
                throw ex;
            }
        } catch (IOException | SQLException ex) {
            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupError) {
                getServletContext().log("Não foi possível remover um arquivo parcial de anexo.", cleanupError);
            }
            Json.error(response, 500, "Não foi possível armazenar o anexo. Confira a configuração do diretório.");
        }
    }

    private void downloadAttachment(HttpServletResponse response, long userId, String rawId) throws IOException {
        long attachmentId;
        try {
            attachmentId = Long.parseLong(rawId);
            if (attachmentId < 1) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            Json.error(response, 400, "Identificador de anexo inválido.");
            return;
        }
        String directory = System.getenv("LC_UPLOAD_DIR");
        if (directory == null || directory.isBlank()) {
            Json.error(response, 503, "O armazenamento de anexos não está configurado.");
            return;
        }
        try {
            if (!Path.of(directory).isAbsolute()) {
                Json.error(response, 503, "LC_UPLOAD_DIR deve ser um caminho absoluto.");
                return;
            }
        } catch (RuntimeException ex) {
            Json.error(response, 503, "O diretório configurado para anexos é inválido.");
            return;
        }
        String storedName;
        String originalName;
        String mime;
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT nome_armazenado,nome_original,tipo_mime FROM anexos_manutencao WHERE id=? AND cliente_id=?")) {
            statement.setLong(1, attachmentId);
            statement.setLong(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    Json.error(response, 404, "Anexo não encontrado na sua garagem.");
                    return;
                }
                storedName = result.getString("nome_armazenado");
                originalName = result.getString("nome_original");
                mime = result.getString("tipo_mime");
            }
        } catch (SQLException ex) {
            Json.error(response, 500, "Não foi possível consultar o anexo.");
            return;
        }
        Path root;
        try {
            root = Path.of(directory).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            Json.error(response, 503, "O diretório configurado para anexos é inválido.");
            return;
        }
        if (isInsidePublicWebRoot(root)) {
            Json.error(response, 503, "LC_UPLOAD_DIR deve ficar fora da pasta pública do Tomcat.");
            return;
        }
        Path file = root.resolve(storedName).normalize();
        if (!file.getParent().equals(root) || !Files.isRegularFile(file)) {
            Json.error(response, 404, "Arquivo não encontrado no armazenamento.");
            return;
        }
        response.setContentType(mime);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "private, no-store");
        String encodedName = URLEncoder.encode(originalName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"anexo\"; filename*=UTF-8''" + encodedName);
        response.setContentLengthLong(Files.size(file));
        Files.copy(file, response.getOutputStream());
    }

    private boolean isInsidePublicWebRoot(Path directory) {
        String webRoot = getServletContext().getRealPath("/");
        if (webRoot == null || webRoot.isBlank()) return true;
        try {
            Path publicRoot = Path.of(webRoot).toRealPath().normalize();
            Path candidate = Files.exists(directory) ? directory.toRealPath().normalize() : directory.toAbsolutePath().normalize();
            return candidate.startsWith(publicRoot);
        } catch (IOException | RuntimeException ex) {
            return true;
        }
    }

    private static String sanitizeName(String submittedName) {
        if (submittedName == null || submittedName.isBlank()) return "documento";
        String normalized = submittedName.replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}]", "").trim();
        if (normalized.isBlank()) return "documento";
        return normalized.length() > 180 ? normalized.substring(normalized.length() - 180) : normalized;
    }
}
