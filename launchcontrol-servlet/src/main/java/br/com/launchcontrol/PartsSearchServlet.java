package br.com.launchcontrol;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Consulta anúncios públicos do Mercado Livre e devolve os dados da fonte sem inventar resultados.
 */
@WebServlet("/api/pecas/busca")
public class PartsSearchServlet extends HttpServlet {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ConcurrentMap<String, RequestWindow> REQUESTS = new ConcurrentHashMap<>();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String query = request.getParameter("q");
        query = query == null ? "" : query.trim();
        if (query.length() < 3 || query.length() > 180 || query.chars().anyMatch(Character::isISOControl)) {
            Json.error(response, 400, "Informe uma pesquisa entre 3 e 180 caracteres.");
            return;
        }
        if (!withinLimit(request.getRemoteAddr())) {
            Json.error(response, 429, "Muitas pesquisas em pouco tempo. Aguarde um minuto e tente novamente.");
            return;
        }

        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        URI uri = URI.create("https://api.mercadolibre.com/sites/MLB/search?q=" + encodedQuery + "&limit=12");
        HttpRequest upstreamRequest = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "LaunchControl-LCParts/1.0")
                .GET()
                .build();

        try {
            HttpResponse<String> upstream = HTTP.send(upstreamRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (upstream.statusCode() != 200) {
                Json.error(response, 502, "O Mercado Livre não disponibilizou resultados nesta tentativa. Você ainda pode abrir a pesquisa diretamente na plataforma.");
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "public, max-age=45");
            response.getWriter().write(upstream.body());
        } catch (java.net.http.HttpTimeoutException ex) {
            Json.error(response, 504, "A consulta ao Mercado Livre demorou demais. Tente novamente.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            Json.error(response, 503, "A consulta foi interrompida. Tente novamente.");
        } catch (IOException ex) {
            Json.error(response, 502, "Não foi possível consultar o Mercado Livre agora. Você ainda pode abrir a pesquisa diretamente na plataforma.");
        }
    }

    // Limita a quantidade de consultas por endereço para reduzir o uso abusivo do endpoint público.
    private static boolean withinLimit(String address) {
        long now = System.currentTimeMillis();
        String key = address == null ? "unknown" : address;
        RequestWindow window = REQUESTS.compute(key, (ignored, current) ->
                current == null || now - current.startedAt >= 60_000 ? new RequestWindow(now) : current);
        if (REQUESTS.size() > 2_000) {
            REQUESTS.entrySet().removeIf(entry -> now - entry.getValue().startedAt >= 60_000);
        }
        return window.count.incrementAndGet() <= 30;
    }

    private static final class RequestWindow {
        private final long startedAt;
        private final AtomicInteger count = new AtomicInteger();

        private RequestWindow(long startedAt) {
            this.startedAt = startedAt;
        }
    }
}
