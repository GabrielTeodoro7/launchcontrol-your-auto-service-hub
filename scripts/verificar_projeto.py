"""Verificação estrutural simples para encontrar arquivos ausentes.

Este script não substitui a compilação Java nem testes no servidor e no MySQL.
"""

from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
required = [
    "backend/pom.xml", "banco-de-dados/modelo.sql",
    "backend/src/main/java/br/com/launchcontrol/Database.java",
    "backend/src/main/java/br/com/launchcontrol/Json.java",
    "backend/src/main/java/br/com/launchcontrol/PasswordUtil.java",
    "backend/src/main/java/br/com/launchcontrol/AuthServlet.java",
    "backend/src/main/java/br/com/launchcontrol/AuthFilter.java",
    "backend/src/main/java/br/com/launchcontrol/OrderServlet.java",
    "backend/src/main/java/br/com/launchcontrol/VehicleServlet.java",
    "backend/src/main/java/br/com/launchcontrol/RequestServlet.java",
    "backend/src/main/java/br/com/launchcontrol/QuoteCreateServlet.java",
    "backend/src/main/java/br/com/launchcontrol/QuoteDecisionServlet.java",
    "backend/src/main/java/br/com/launchcontrol/QuoteServlet.java",
    "backend/src/main/java/br/com/launchcontrol/DiagnosticServlet.java",
    "backend/src/main/java/br/com/launchcontrol/PartsSearchServlet.java",
    "backend/src/main/java/br/com/launchcontrol/PartsAssistantServlet.java",
    "backend/src/main/java/br/com/launchcontrol/FitmentServlet.java",
    "backend/src/main/java/br/com/launchcontrol/MaintenanceServlet.java",
    "backend/src/main/java/br/com/launchcontrol/MaintenanceAttachmentServlet.java",
    "backend/src/main/java/br/com/launchcontrol/AdditionalServiceServlet.java",
    "backend/src/main/java/br/com/launchcontrol/NotificationServlet.java",
    "backend/src/main/java/br/com/launchcontrol/ReviewServlet.java",
    "backend/src/main/java/br/com/launchcontrol/InventoryServlet.java",
    "backend/src/main/java/br/com/launchcontrol/CheckoutServlet.java",
    "backend/src/main/java/br/com/launchcontrol/PaymentServlet.java",
    "frontend/index.html", "frontend/css/estilo-principal.css",
    "frontend/js/interacoes.js", "frontend/js/recursos-complementares.js",
    "backend/src/main/webapp/WEB-INF/web.xml",
    "banco-de-dados/migracoes/001_ecossistema_automotivo.sql",
    "banco-de-dados/migracoes/002_aprovacoes_notificacoes.sql",
    "banco-de-dados/migracoes/003_agenda_auditoria_integracoes.sql",
    "backend/src/main/java/br/com/launchcontrol/DashboardAnalyticsServlet.java",
    "backend/src/main/java/br/com/launchcontrol/AppointmentServlet.java",
    "backend/src/main/java/br/com/launchcontrol/FitmentImportServlet.java",
    "backend/src/main/java/br/com/launchcontrol/AuditTrailServlet.java",
    "backend/src/main/java/br/com/launchcontrol/NotificationPreferencesServlet.java",
    "backend/src/main/java/br/com/launchcontrol/NotificationDelivery.java",
    "backend/src/main/java/br/com/launchcontrol/NotificationOutboxListener.java",
    "frontend/js/launchcontrol-operacoes.js",
    "scripts/smoke_test_http.py",
    "scripts/seed_smoke_test.sql",
    ".github/workflows/launchcontrol-validation.yml",
    "docs/PLANO-EVOLUCAO-LAUNCHCONTROL.md",
]
missing = [item for item in required if not (ROOT / item).is_file()]
if missing:
    print("FALHA — arquivos ausentes:\n" + "\n".join(missing))
    sys.exit(1)

read = lambda item: (ROOT / item).read_text(encoding="utf-8")
pom = read("backend/pom.xml")
schema = read("banco-de-dados/modelo.sql")
migration = read("banco-de-dados/migracoes/001_ecossistema_automotivo.sql")
plan = read("docs/PLANO-EVOLUCAO-LAUNCHCONTROL.md")
quote = read("backend/src/main/java/br/com/launchcontrol/QuoteServlet.java")
checkout = read("backend/src/main/java/br/com/launchcontrol/CheckoutServlet.java")
payments = read("backend/src/main/java/br/com/launchcontrol/PaymentServlet.java")
password = read("backend/src/main/java/br/com/launchcontrol/PasswordUtil.java")
auth_filter = read("backend/src/main/java/br/com/launchcontrol/AuthFilter.java")
html = read("frontend/index.html")
js = read("frontend/js/interacoes.js")
supplemental_js = read("frontend/js/recursos-complementares.js")
css = read("frontend/css/estilo-principal.css")
migration_two = read("banco-de-dados/migracoes/002_aprovacoes_notificacoes.sql")

assert "<maven.compiler.release>25</maven.compiler.release>" in pom, "Compilação precisa usar Java 25"
assert "<version>6.1.0</version>" in pom and "web-app_6_1.xsd" in read("backend/src/main/webapp/WEB-INF/web.xml"), "Servlet 6.1 não está configurado"
assert "<version>9.7.0</version>" in pom, "Driver MySQL não está na versão aprovada para esta atualização"
assert "maven-compiler-plugin" in pom and "maven-war-plugin" in pom, "Plugins de compilação/empacotamento precisam estar explícitos"
assert "jakarta.servlet-api" in pom and "mysql-connector-j" in pom
tables = ["usuarios", "oficinas", "fornecedores", "servicos", "pecas", "veiculos",
          "solicitacoes_orcamento", "orcamentos", "ordens_servico", "diagnosticos",
          "pedidos", "pagamentos", "auditoria"]
assert all("CREATE TABLE IF NOT EXISTS " + table in schema for table in tables), "Schema incompleto"
assert "PBKDF2WithHmacSHA256" in password, "Hash de senha não localizado"
assert "X-CSRF-Token" in auth_filter, "Proteção CSRF não localizada"
assert "CONCLUIDO" in quote and "não podem ser alterados" in quote, "Bloqueio de orçamento concluído não localizado"
assert "CONCLUIDO" in read("backend/src/main/java/br/com/launchcontrol/OrderServlet.java"), "Bloqueio de reabertura de ordem concluída não localizado"
assert "cliente_id" in read("backend/src/main/java/br/com/launchcontrol/VehicleServlet.java"), "Isolamento de veículos por cliente não localizado"
assert "FOR UPDATE" in read("backend/src/main/java/br/com/launchcontrol/RequestServlet.java"), "Validação de propriedade de veículo na solicitação não localizada"
assert "CRIAR_ORCAMENTO" in read("backend/src/main/java/br/com/launchcontrol/QuoteCreateServlet.java"), "Auditoria de criação de orçamento não localizada"
assert "ACEITAR" in read("backend/src/main/java/br/com/launchcontrol/QuoteDecisionServlet.java"), "Decisão de orçamento não localizada"
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
assert 'id="parts-search-form"' in html and 'id="parts-results"' in html, "Interface LC Parts ausente"
assert 'id="garagem-inteligente"' in html and 'id="garage-timeline"' in html, "Interface Garagem Inteligente ausente"
assert "async function loadGarage()" in js and "api/diagnosticos" in js, "Histórico da garagem não conectado aos endpoints existentes"
assert "solicitacaoId" in read("backend/src/main/java/br/com/launchcontrol/OrderServlet.java") and "veiculoId" in read("backend/src/main/java/br/com/launchcontrol/RequestServlet.java"), "Relacionamentos para o histórico da garagem ausentes"
inventory = read("backend/src/main/java/br/com/launchcontrol/InventoryServlet.java")
assert "/api/estoque" in inventory and "FOR UPDATE" in inventory and "fornecedor_id" in inventory, "Controle de estoque por fornecedor ausente"
assert "estoque" in read("frontend/index.html") and "quote-sort" in read("frontend/index.html"), "Interface de estoque ou comparação de orçamentos ausente"
assert all(host in js for host in ["lista.mercadolivre.com.br", "shopee.com.br/search", "olx.com.br/brasil"]), "Pesquisas externas do LC Parts ausentes"
assert "noopener noreferrer" in js and "api/pecas/busca" in js, "Busca real e segurança dos links externos ausentes"
assert "api/assistente-pecas" in js and 'id="parts-assistant-form"' in html, "Assistente IA do LC Parts ausente"
assert '"/api/pecas/busca".equals(path)' in read("backend/src/main/java/br/com/launchcontrol/AuthFilter.java"), "Consulta pública de peças não liberada no filtro de autenticação"
assert "A solicitação já foi aceita" in read("backend/src/main/java/br/com/launchcontrol/QuoteDecisionServlet.java"), "Proteção contra aceitar múltiplos orçamentos ausente"
assert 'src="js/recursos-complementares.js"' in html, "Interface complementar não está carregada"
assert "@WebServlet(\"/api/servicos-adicionais\")" in read("backend/src/main/java/br/com/launchcontrol/AdditionalServiceServlet.java"), "Aprovação de serviços adicionais ausente"
assert "@WebServlet(\"/api/notificacoes\")" in read("backend/src/main/java/br/com/launchcontrol/NotificationServlet.java"), "API de notificações ausente"
assert "@WebServlet(\"/api/anexos-manutencao\")" in read("backend/src/main/java/br/com/launchcontrol/MaintenanceAttachmentServlet.java"), "API de anexos do passaporte ausente"
assert "LC_UPLOAD_DIR" in read("backend/src/main/java/br/com/launchcontrol/MaintenanceAttachmentServlet.java"), "Diretório seguro para anexos não configurável"
assert all("CREATE TABLE IF NOT EXISTS " + table in migration_two for table in ["servicos_adicionais", "notificacoes", "anexos_manutencao"]), "Migração de aprovações/anexos incompleta"
assert "api/anexos-manutencao" in supplemental_js and "Salvar como PDF" in supplemental_js, "Anexos ou exportação do passaporte não conectados"
assert "servicos-adicionais" in supplemental_js and "api/notificacoes" in supplemental_js, "Aprovações/notificações não conectadas à interface"
assert "api/avaliacoes" in supplemental_js and "loadServiceReviews" in supplemental_js, "Avaliações verificadas não conectadas à experiência do cliente"
assert "renderMaintenanceReminders" in supplemental_js and "proximaQuilometragem" in supplemental_js, "Lembretes informativos de manutenção ausentes"
assert "valor_adicionais_aceitos" in read("backend/src/main/java/br/com/launchcontrol/OrderServlet.java"), "Total de adicionais aceitos não está disponível na ordem"
assert "createNotification(c, workshopUserId" in read("backend/src/main/java/br/com/launchcontrol/QuoteDecisionServlet.java"), "Decisão de orçamento não notifica a oficina"


feature_tables = ["historico_manutencao", "avaliacoes_servico", "ordem_status_historico",
                  "compatibilidade_pecas", "orcamento_detalhes", "orcamento_itens"]
assert all("CREATE TABLE IF NOT EXISTS " + table in migration for table in feature_tables), "Migração do ecossistema incompleta"
assert "SYMPTOM_INSTRUCTIONS" in read("backend/src/main/java/br/com/launchcontrol/PartsAssistantServlet.java"), "Modo de triagem do Launch AI ausente"
assert "writeGuidedFallback" in read("backend/src/main/java/br/com/launchcontrol/PartsAssistantServlet.java"), "Fallback guiado sem IA ausente"
assert "INSERT INTO ordem_status_historico" in read("backend/src/main/java/br/com/launchcontrol/OrderServlet.java"), "Linha do tempo de ordens não persistida"
assert "isValidTransition" in read("backend/src/main/java/br/com/launchcontrol/OrderServlet.java"), "Validação de transições de status ausente"
assert "partsAmount.add(laborAmount).add(otherAmount)" in read("backend/src/main/java/br/com/launchcontrol/QuoteCreateServlet.java"), "Total de orçamento não calculado no backend"
assert "api/compatibilidade-pecas" in js and 'id="fitment-status"' in html, "Fitment Engine não conectado à interface"
assert 'id="fitment-admin-form"' in html and "Somente administradores" in read("backend/src/main/java/br/com/launchcontrol/FitmentServlet.java"), "Gestão administrativa do catálogo de compatibilidade ausente"
assert 'id="maintenance-form"' in html and "api/manutencoes" in js, "CarPassport não conectado à interface"
assert 'id="repair-timeline"' in html and "historicoVeiculoId" in js, "Oficina Transparente não conectado à interface"
assert "planningCoverage" in js and "não a saúde mecânica" in html, "AutoCare Score sem regra explicável ou aviso de escopo"
assert "preservar a identidade visual" in plan.lower() or "preservação visual" in plan.lower(), "Plano técnico sem diretriz de preservação visual"


assert all("CREATE TABLE IF NOT EXISTS " + table in read("banco-de-dados/migracoes/003_agenda_auditoria_integracoes.sql") for table in ["configuracao_agenda_oficina", "agendamentos", "preferencias_notificacao", "notificacao_entregas"]), "Migração de agenda e notificações externas incompleta"
assert '@WebServlet("/api/dashboard/analytics")' in read("backend/src/main/java/br/com/launchcontrol/DashboardAnalyticsServlet.java"), "Dashboard gerencial não conectado ao backend"
assert '@WebServlet("/api/agendamentos")' in read("backend/src/main/java/br/com/launchcontrol/AppointmentServlet.java") and "FOR UPDATE" in read("backend/src/main/java/br/com/launchcontrol/AppointmentServlet.java"), "Agenda sem endpoint ou bloqueio concorrente"
assert '@WebServlet("/api/compatibilidade-pecas/importacao")' in read("backend/src/main/java/br/com/launchcontrol/FitmentImportServlet.java") and "MAX_ROWS = 500" in read("backend/src/main/java/br/com/launchcontrol/FitmentImportServlet.java"), "Importação CSV de compatibilidade ausente"
assert '@WebServlet("/api/auditoria")' in read("backend/src/main/java/br/com/launchcontrol/AuditTrailServlet.java"), "Consulta de auditoria ausente"
assert "RESEND_API_KEY" in read("backend/src/main/java/br/com/launchcontrol/NotificationDelivery.java") and "WHATSAPP_ACCESS_TOKEN" in read("backend/src/main/java/br/com/launchcontrol/NotificationDelivery.java"), "Canais externos de notificação não configuráveis"
assert 'src="js/launchcontrol-operacoes.js"' in html and 'id="operations-metrics"' in html and 'id="fitment-import-form"' in html, "Interface de operações avançadas ausente"
assert "api/dashboard/analytics" in read("frontend/js/launchcontrol-operacoes.js") and "api/agendamentos" in read("frontend/js/launchcontrol-operacoes.js"), "Frontend de agenda ou dashboard não conectado"

# Verificações de segurança e integração que devem permanecer como contratos explícitos.
assert "safeMethod" in auth_filter and 'response.setHeader("X-Content-Type-Options", "nosniff")' in auth_filter, "Filtro HTTP deve proteger todos os métodos inseguros e aplicar cabeçalhos defensivos"
assert 'response.setHeader("Cache-Control", "no-store")' in auth_filter, "Respostas de API devem impedir cache de dados sensíveis"
assert "catch (IllegalArgumentException ex)" in password and 'Arrays.fill(derived, (byte) 0)' in password, "Hash de senha precisa tolerar dados inválidos e limpar buffers"
auth_source = read("backend/src/main/java/br/com/launchcontrol/AuthServlet.java")
assert 'allowAttempt("login|" + remoteAddress, 10)' in auth_source and 'allowAttempt("register|" + remoteAddress, 5)' in auth_source, "Limitação básica de tentativas de autenticação ausente"
assert 'response.setHeader("Retry-After", "900")' in auth_source, "Bloqueio temporário de autenticação sem Retry-After"
appointment_source = read("backend/src/main/java/br/com/launchcontrol/AppointmentServlet.java")
assert "!start.toLocalDate().equals(end.toLocalDate())" in appointment_source, "Agenda deve impedir reservas que cruzem a meia-noite"
delivery_source = read("backend/src/main/java/br/com/launchcontrol/NotificationDelivery.java")
assert "if((resend==null||from==null)&&(waToken==null||waPhoneId==null))return;" not in delivery_source, "Fila deve sinalizar quando os canais externos ainda não estão configurados"
assert 'src="js/launchcontrol-operacoes.js"' in html, "JavaScript operacional não está carregado"
workflow = read(".github/workflows/launchcontrol-validation.yml")
assert "mysql:8.4" in workflow and "Tomcat 11" in workflow and "smoke_test_http.py" in workflow, "CI precisa executar banco real, implantação e smoke test HTTP"
smoke = read("scripts/smoke_test_http.py")
for scenario in ["cadastro", "CSRF", "e-mail duplicado", "logout", "senha incorreta", "login com credencial correta"]:
    assert scenario.lower() in smoke.lower(), "Smoke test sem cenário: " + scenario
for table in ["configuracao_agenda_oficina", "agendamentos", "preferencias_notificacao", "notificacao_entregas"]:
    assert "CREATE TABLE IF NOT EXISTS " + table in read("banco-de-dados/migracoes/003_agenda_auditoria_integracoes.sql"), "Migração ausente: " + table

print("OK: " + str(len(required)) + " arquivos essenciais presentes.")
print("OK: dependências Servlet/JDBC, " + str(len(tables)) + " tabelas, hash PBKDF2, CSRF, bloqueio de orçamento concluído e checkout demonstrativo.")
print("Limite: teste estrutural; não prova compilação, conexão MySQL, segurança completa ou funcionamento em navegador.")
