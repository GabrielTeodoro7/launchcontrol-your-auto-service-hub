from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
required = [
    "pom.xml", "database/schema.sql", "README.md",
    "src/main/java/br/com/launchcontrol/Database.java",
    "src/main/java/br/com/launchcontrol/Json.java",
    "src/main/java/br/com/launchcontrol/PasswordUtil.java",
    "src/main/java/br/com/launchcontrol/AuthServlet.java",
    "src/main/java/br/com/launchcontrol/AuthFilter.java",
    "src/main/java/br/com/launchcontrol/OrderServlet.java",
    "src/main/java/br/com/launchcontrol/VehicleServlet.java",
    "src/main/java/br/com/launchcontrol/RequestServlet.java",
    "src/main/java/br/com/launchcontrol/QuoteCreateServlet.java",
    "src/main/java/br/com/launchcontrol/QuoteDecisionServlet.java",
    "src/main/java/br/com/launchcontrol/QuoteServlet.java",
    "src/main/java/br/com/launchcontrol/DiagnosticServlet.java",
    "src/main/java/br/com/launchcontrol/CheckoutServlet.java",
    "src/main/java/br/com/launchcontrol/PaymentServlet.java",
    "src/main/webapp/index.html", "src/main/webapp/css/style.css",
    "src/main/webapp/js/app.js", "src/main/webapp/WEB-INF/web.xml",
]
missing = [item for item in required if not (ROOT / item).is_file()]
if missing:
    print("FALHA — arquivos ausentes:\n" + "\n".join(missing))
    sys.exit(1)

read = lambda item: (ROOT / item).read_text(encoding="utf-8")
pom = read("pom.xml")
schema = read("database/schema.sql")
quote = read("src/main/java/br/com/launchcontrol/QuoteServlet.java")
checkout = read("src/main/java/br/com/launchcontrol/CheckoutServlet.java")
payments = read("src/main/java/br/com/launchcontrol/PaymentServlet.java")
password = read("src/main/java/br/com/launchcontrol/PasswordUtil.java")
auth_filter = read("src/main/java/br/com/launchcontrol/AuthFilter.java")
html = read("src/main/webapp/index.html")
js = read("src/main/webapp/js/app.js")
css = read("src/main/webapp/css/style.css")

assert "jakarta.servlet-api" in pom and "mysql-connector-j" in pom
tables = ["usuarios", "oficinas", "fornecedores", "servicos", "pecas", "veiculos",
          "solicitacoes_orcamento", "orcamentos", "ordens_servico", "diagnosticos",
          "pedidos", "pagamentos", "auditoria"]
assert all("CREATE TABLE IF NOT EXISTS " + table in schema for table in tables), "Schema incompleto"
assert "PBKDF2WithHmacSHA256" in password, "Hash de senha não localizado"
assert "X-CSRF-Token" in auth_filter, "Proteção CSRF não localizada"
assert "CONCLUIDO" in quote and "não podem ser alterados" in quote, "Bloqueio de orçamento concluído não localizado"
assert "CONCLUIDO" in read("src/main/java/br/com/launchcontrol/OrderServlet.java"), "Bloqueio de reabertura de ordem concluída não localizado"
assert "cliente_id" in read("src/main/java/br/com/launchcontrol/VehicleServlet.java"), "Isolamento de veículos por cliente não localizado"
assert "FOR UPDATE" in read("src/main/java/br/com/launchcontrol/RequestServlet.java"), "Validação de propriedade de veículo na solicitação não localizada"
assert "CRIAR_ORCAMENTO" in read("src/main/java/br/com/launchcontrol/QuoteCreateServlet.java"), "Auditoria de criação de orçamento não localizada"
assert "ACEITAR" in read("src/main/java/br/com/launchcontrol/QuoteDecisionServlet.java"), "Decisão de orçamento não localizada"
assert "PENDENTE" in checkout and "DEMONSTRACAO" in checkout and "cobrado" in checkout, "Checkout demonstrativo precisa permanecer pendente"
assert "CANCELADO" in payments and "cobrancaRealizada" in payments, "Cancelamento seguro de pagamento demonstrativo não localizado"
assert 'order.status === "CONCLUIDO"' in js and "canEditQuote = !completed" in js, "Ação de edição deve ser omitida para serviços concluídos"
assert not any(token in (html + js + css).lower() for token in ["react", "typescript", "vite"]), "Tecnologia não autorizada detectada no frontend"
assert "localStorage" not in js, "O frontend não deve usar localStorage como persistência de domínio"
html_ids = set(re.findall(r'\bid="([^"]+)"', html))
js_ids = set(re.findall(r'querySelector\(["\']#([A-Za-z0-9_-]+)', js))
missing_selectors = sorted(js_ids - html_ids)
assert not missing_selectors, "Seletores JS sem elemento HTML: " + ", ".join(missing_selectors)
assert 'id="vehicle-form"' in html and 'id="request-form"' in html, "Formulários de veículo e solicitação ausentes"
assert 'id="quote-form"' in html and 'id="quote-list"' in html, "Interface de orçamento ausente"
assert "api/pagamentos" in js and "api/orcamentos/decisao" in js, "Fluxo de orçamento/pagamento não conectado"
assert "A solicitação já foi aceita" in read("src/main/java/br/com/launchcontrol/QuoteDecisionServlet.java"), "Proteção contra aceitar múltiplos orçamentos ausente"

print("OK: " + str(len(required)) + " arquivos essenciais presentes.")
print("OK: dependências Servlet/JDBC, " + str(len(tables)) + " tabelas, hash PBKDF2, CSRF, bloqueio de orçamento concluído e checkout demonstrativo.")
print("Limite: teste estrutural; não prova compilação, conexão MySQL, segurança completa ou funcionamento em navegador.")
