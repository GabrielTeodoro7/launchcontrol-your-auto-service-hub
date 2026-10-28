package br.com.launchcontrol;

/**
 * Reúne funções simples para montar respostas JSON com segurança. JSON é um formato de texto usado para trocar dados entre o navegador e o servidor.
 *
 * Uma classe é como uma caixa que reúne dados e ações relacionados a uma tarefa.
 */
public final class Json {
    private Json() {}
    // Escapa caracteres especiais para que um texto possa ser incluído com segurança em JSON.
    public static String quote(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> { if (c < 0x20) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
            }
        }
        return out.append('"').toString();
    }
    // Devolve uma resposta de erro em JSON e define o código HTTP correspondente.
    public static void error(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"erro\":" + quote(message) + "}");
    }
}
