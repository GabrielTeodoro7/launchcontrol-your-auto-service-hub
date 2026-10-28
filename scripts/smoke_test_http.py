#!/usr/bin/env python3
"""Smoke/integration tests HTTP against a deployed LaunchControl + MySQL test instance.

Uses only the Python standard library. All accounts, parts, workshops and services are
synthetic fixtures from scripts/seed_smoke_test.sql. Never point this at production.
"""

import datetime as dt
import http.cookiejar
import json
import os
import sys
import traceback
import urllib.error
import urllib.parse
import urllib.request
import uuid
from zoneinfo import ZoneInfo

BASE_URL = os.environ.get("LAUNCHCONTROL_BASE_URL", "http://127.0.0.1:8080/launchcontrol").rstrip("/")
TEST_PASSWORD = "LaunchControl-Smoke-25!"
FORM_HEADERS = {"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"}


def client():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def request(opener, path, method="GET", data=None, headers=None, expected=200):
    payload = urllib.parse.urlencode(data).encode("utf-8") if data is not None else None
    request_headers = dict(headers or {})
    if payload is not None:
        request_headers.setdefault("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
    req = urllib.request.Request(BASE_URL + path, data=payload, method=method, headers=request_headers)
    try:
        with opener.open(req, timeout=15) as response:
            code, body, response_headers = response.status, response.read().decode("utf-8"), response.headers
    except urllib.error.HTTPError as error:
        code, body, response_headers = error.code, error.read().decode("utf-8"), error.headers
    if expected is not None and code != expected:
        raise AssertionError(f"{method} {path}: esperado HTTP {expected}, recebido {code}; corpo={body[:500]}")
    return code, body, response_headers


def json_request(opener, path, method="GET", data=None, headers=None, expected=200):
    _, body, response_headers = request(opener, path, method, data, headers, expected)
    if not body:
        return {}, response_headers
    try:
        return json.loads(body), response_headers
    except json.JSONDecodeError as error:
        raise AssertionError(f"Resposta não é JSON em {method} {path}: {body[:300]}") from error



def multipart_request(opener, path, fields, file_field, filename, content_type, file_bytes, csrf, expected=200):
    boundary = "----LaunchControlSmoke" + uuid.uuid4().hex
    body = bytearray()
    for name, value in fields.items():
        body.extend(f"--{boundary}\r\n".encode())
        body.extend(f'Content-Disposition: form-data; name="{name}"\r\n\r\n'.encode())
        body.extend(str(value).encode("utf-8"))
        body.extend(b"\r\n")
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(f'Content-Disposition: form-data; name="{file_field}"; filename="{filename}"\r\n'.encode())
    body.extend(f"Content-Type: {content_type}\r\n\r\n".encode())
    body.extend(file_bytes)
    body.extend(f"\r\n--{boundary}--\r\n".encode())
    req = urllib.request.Request(
        BASE_URL + path,
        data=bytes(body),
        method="POST",
        headers={"Content-Type": f"multipart/form-data; boundary={boundary}", "X-CSRF-Token": csrf},
    )
    try:
        with opener.open(req, timeout=15) as response:
            code, payload = response.status, response.read()
    except urllib.error.HTTPError as error:
        code, payload = error.code, error.read()
    if expected is not None and code != expected:
        raise AssertionError(f"POST {path} multipart: esperado HTTP {expected}, recebido {code}; corpo={payload[:500]!r}")
    if not payload:
        return {}, b""
    try:
        return json.loads(payload.decode("utf-8")), payload
    except (UnicodeDecodeError, json.JSONDecodeError):
        return {}, payload

def login(opener, email):
    result, _ = json_request(opener, "/api/auth", method="POST",
                             data={"action": "login", "email": email, "password": TEST_PASSWORD})
    assert result.get("ok") is True and result.get("csrf"), f"Login não retornou sessão válida: {result}"
    return result["csrf"]


def logout(opener, csrf):
    result, _ = json_request(opener, "/api/auth", method="POST",
                             data={"action": "logout"}, headers={"X-CSRF-Token": csrf})
    assert result.get("ok") is True, result
    request(opener, "/api/auth", expected=401)


def post_csrf(opener, path, data, csrf, expected=200, extra_headers=None):
    headers = {"X-CSRF-Token": csrf}
    headers.update(extra_headers or {})
    return json_request(opener, path, method="POST", data=data, headers=headers, expected=expected)


def record(checks, label):
    checks.append(label)
    print("  ✓ " + label, flush=True)


def main():
    checks = []
    anonymous = client()
    _, html, _ = request(anonymous, "/", expected=200)
    assert "LAUNCHCONTROL" in html.upper(), "Página inicial ausente ou incorreta"
    checks.append("página inicial do WAR")

    health, headers = json_request(anonymous, "/api/health")
    assert health.get("status") == "ok" and health.get("database") == "connected", health
    assert headers.get("X-Content-Type-Options") == "nosniff", "Cabeçalho nosniff ausente"
    assert headers.get("X-Frame-Options") == "DENY", "Cabeçalho anti-frame ausente"
    assert headers.get("Cache-Control") == "no-store", "API não deve ser armazenada em cache"
    checks.append("Tomcat, API, MySQL e cabeçalhos defensivos")

    request(anonymous, "/api/ordens-servico", expected=401)
    checks.append("bloqueio de endpoint privado sem sessão")

    email = f"smoke-{uuid.uuid4().hex[:16]}@example.test"
    customer = client()
    registered, _ = json_request(customer, "/api/auth", method="POST",
                                 data={"action": "register", "name": "Cliente Smoke Test",
                                       "email": email, "password": TEST_PASSWORD})
    assert registered.get("ok") is True and registered.get("csrf"), registered
    csrf = registered["csrf"]
    session, _ = json_request(customer, "/api/auth")
    assert session.get("email") == email and session.get("perfil") == "CLIENTE", session
    checks.append("cadastro, hash de senha e sessão")

    post_csrf(customer, "/api/notificacoes/preferencias",
              {"emailAtivo": "true", "whatsappAtivo": "false", "whatsappNumero": ""},
              "token-invalido", expected=403)
    request(customer, "/api/auth", method="POST",
            data={"action": "register", "name": "Duplicado", "email": email, "password": TEST_PASSWORD},
            expected=409)
    request(customer, "/api/notificacoes/preferencias", method="PUT",
            data={"emailAtivo": "true"}, headers={"X-CSRF-Token": "token-invalido"}, expected=403)
    checks.append("CSRF inválido em POST/PUT e e-mail duplicado rejeitados")

    logout(customer, csrf)
    request(customer, "/api/auth", method="POST",
            data={"action": "login", "email": email, "password": "Senha-Incorreta-25!"},
            expected=401)
    csrf = login(customer, email)
    checks.append("logout, senha incorreta e login com credencial correta")

    vehicle, _ = post_csrf(customer, "/api/veiculos",
                           {"marca": "Renault", "modelo": "Sandero", "ano": "2016",
                            "placa": "SMOKE001", "quilometragem": "90000", "motorizacao": "1.0"},
                           csrf, expected=201)
    vehicle_id = vehicle["id"]
    vehicles, _ = json_request(customer, "/api/veiculos")
    assert any(v["id"] == vehicle_id for v in vehicles), vehicles
    ai_result, _ = post_csrf(customer, "/api/assistente-pecas",
                             {"modo": "sintomas", "mensagem": "O pedal do freio está muito duro e o carro demora para parar"},
                             csrf)
    assert ai_result.get("fallback") is True and "não uma resposta gerada por IA" in ai_result.get("aviso", ""), ai_result
    record(checks, "cadastro e consulta de veículo")
    record(checks, "Launch AI em modo de contingência sem credencial externa")

    today = dt.datetime.now(dt.timezone.utc).date()
    maintenance, _ = post_csrf(customer, "/api/manutencoes",
                                {"veiculoId": str(vehicle_id), "categoria": "Revisão",
                                 "descricao": "Revisão sintética de smoke test", "oficina": "Oficina Smoke Test",
                                 "quilometragem": "90000", "custo": "250.00",
                                 "dataServico": today.isoformat(),
                                 "proximaData": (today + dt.timedelta(days=90)).isoformat(),
                                 "proximaQuilometragem": "95000"},
                                csrf)
    maintenance_rows, _ = json_request(customer, f"/api/manutencoes?veiculoId={vehicle_id}")
    assert any(row.get("id") == maintenance.get("id") for row in maintenance_rows), maintenance_rows
    diagnostic, _ = post_csrf(customer, "/api/diagnosticos",
                              {"vehicle": "Renault Sandero 2016", "symptoms": "Ruído leve ao frear durante a manhã",
                               "priority": "NORMAL"}, csrf, expected=201)
    diagnostics, _ = json_request(customer, "/api/diagnosticos")
    assert any(row.get("id") == diagnostic["id"] for row in diagnostics), diagnostics
    pdf_bytes = b"%PDF-1.4\\n1 0 obj <<>> endobj\\ntrailer <<>>\\n%%EOF\\n"
    attachment, _ = multipart_request(customer, "/api/anexos-manutencao",
                                      {"historicoId": str(maintenance["id"])}, "arquivo",
                                      "relatorio-smoke.pdf", "application/pdf", pdf_bytes, csrf)
    attachment_id = attachment["id"]
    attachment_list, _ = json_request(customer, f"/api/anexos-manutencao?veiculoId={vehicle_id}")
    assert any(item.get("id") == attachment_id for item in attachment_list), attachment_list
    _, downloaded, download_headers = request(customer, f"/api/anexos-manutencao?download={attachment_id}")
    assert downloaded.startswith("%PDF-1.4") and download_headers.get("X-Content-Type-Options") == "nosniff"
    multipart_request(customer, "/api/anexos-manutencao",
                      {"historicoId": str(maintenance["id"])}, "arquivo",
                      "fake.pdf", "application/pdf", b"this is not a PDF", csrf, expected=415)
    checks.append("CarPassport, relato de diagnóstico e upload/download seguro de anexos")

    service_rows, _ = json_request(anonymous, "/api/servicos")
    assert any(row.get("id") == 900001 for row in service_rows), "Serviço de teste não foi semeado"
    request_one, _ = post_csrf(customer, "/api/solicitacoes",
                               {"veiculoId": str(vehicle_id), "servicoId": "900001",
                                "descricao": "Solicito orçamento para revisão preventiva"},
                               csrf, expected=201)
    request_two, _ = post_csrf(customer, "/api/solicitacoes",
                               {"veiculoId": str(vehicle_id), "servicoId": "900002",
                                "descricao": "Solicito serviço vinculado a outra oficina"},
                               csrf, expected=201)
    own_requests, _ = json_request(customer, "/api/solicitacoes")
    assert {request_one["id"], request_two["id"]}.issubset({row["id"] for row in own_requests}), own_requests
    checks.append("solicitações vinculadas ao veículo e serviço")

    # Agenda: valida horário local, capacidade, consulta e cancelamento.
    local_tz = ZoneInfo("America/Sao_Paulo")
    tomorrow = dt.datetime.now(local_tz).date() + dt.timedelta(days=1)
    appointment_start = dt.datetime.combine(tomorrow, dt.time(10, 0))
    invalid_start = dt.datetime.combine(tomorrow, dt.time(23, 30))
    post_csrf(customer, "/api/agendamentos",
              {"oficinaId": "900001", "veiculoId": str(vehicle_id),
               "inicio": invalid_start.strftime("%Y-%m-%dT%H:%M"), "duracaoMinutos": "60"},
              csrf, expected=400)
    appointment, _ = post_csrf(customer, "/api/agendamentos",
                               {"oficinaId": "900001", "veiculoId": str(vehicle_id),
                                "inicio": appointment_start.strftime("%Y-%m-%dT%H:%M"),
                                "duracaoMinutos": "60", "observacoes": "Reserva sintética de CI"},
                               csrf, expected=201)
    appointments, _ = json_request(customer, "/api/agendamentos")
    assert any(row.get("id") == appointment["id"] for row in appointments), appointments
    post_csrf(customer, "/api/agendamentos", {"acao": "cancelar", "id": str(appointment["id"])}, csrf)
    checks.append("agenda, expediente, limite de dia e cancelamento")

    post_csrf(customer, "/api/notificacoes/preferencias",
              {"emailAtivo": "true", "whatsappAtivo": "false", "whatsappNumero": ""}, csrf)
    preferences, _ = json_request(customer, "/api/notificacoes/preferencias")
    assert preferences.get("emailAtivo") is True and preferences.get("whatsappAtivo") is False, preferences
    dashboard, _ = json_request(customer, "/api/dashboard/analytics")
    assert "ordens" in dashboard and "agendamentos" in dashboard, dashboard
    audit, _ = json_request(customer, "/api/auditoria")
    assert isinstance(audit, list), audit
    parts_status, parts_body, _ = request(anonymous, "/api/pecas/busca?q=Smoke", expected=None)
    assert parts_status in {200, 502, 503, 504}, f"LC Parts retornou status inesperado {parts_status}: {parts_body[:300]}"
    checks.append("preferências, dashboard, auditoria e degradação segura do LC Parts")

    logout(customer, csrf)
    workshop = client()
    workshop_csrf = login(workshop, "smoke-workshop@example.test")
    workshop_requests, _ = json_request(workshop, "/api/solicitacoes")
    workshop_request_ids = {row["id"] for row in workshop_requests}
    assert request_one["id"] in workshop_request_ids, workshop_requests
    assert request_two["id"] not in workshop_request_ids, "Oficina recebeu solicitação vinculada a outra oficina"
    post_csrf(workshop, "/api/orcamentos",
              {"solicitacaoId": str(request_two["id"]), "prazoDias": "3",
               "valorPecas": "100.00", "valorMaoObra": "50.00", "valorOutro": "10.00"},
              workshop_csrf, expected=403)
    quote, _ = post_csrf(workshop, "/api/orcamentos",
                         {"solicitacaoId": str(request_one["id"]), "prazoDias": "3",
                          "valorPecas": "100.00", "valorMaoObra": "50.00", "valorOutro": "10.00",
                          "garantiaDias": "90", "validadeDias": "7", "condicaoPecas": "NOVAS",
                          "observacoes": "Orçamento sintético para teste"},
                         workshop_csrf, expected=201)
    assert float(quote["valor"]) == 160.0, quote
    logout(workshop, workshop_csrf)
    checks.append("isolamento entre oficinas e orçamento discriminado")

    csrf = login(customer, email)
    decision, _ = post_csrf(customer, "/api/orcamentos/decisao",
                            {"orcamentoId": str(quote["id"]), "decisao": "ACEITAR"}, csrf)
    assert decision.get("status") == "ACEITO", decision
    orders, _ = json_request(customer, "/api/ordens-servico")
    order = next((row for row in orders if row.get("orcamentoId") == quote["id"]), None)
    assert order is not None and order.get("status") == "PENDENTE", orders

    idempotency_key = "smoke-key-" + uuid.uuid4().hex[:20]
    payment, _ = post_csrf(customer, "/api/checkout",
                           {"quoteId": str(quote["id"]), "method": "PIX_DEMO"}, csrf,
                           extra_headers={"Idempotency-Key": idempotency_key}, expected=201)
    assert payment.get("cobrado") is False and payment.get("modo") == "DEMONSTRACAO", payment
    repeated_payment, _ = post_csrf(customer, "/api/checkout",
                                    {"quoteId": str(quote["id"]), "method": "PIX_DEMO"}, csrf,
                                    extra_headers={"Idempotency-Key": idempotency_key})
    assert repeated_payment.get("paymentId") == payment.get("paymentId"), repeated_payment
    post_csrf(customer, "/api/pagamentos", {"id": str(payment["paymentId"])}, csrf)
    payments, _ = json_request(customer, "/api/pagamentos")
    assert any(row.get("id") == payment["paymentId"] and row.get("status") == "CANCELADO" for row in payments), payments
    notifications, _ = json_request(customer, "/api/notificacoes")
    assert notifications, "A decisão do orçamento deveria gerar notificação interna"
    post_csrf(customer, "/api/notificacoes", {"id": str(notifications[0]["id"])}, csrf)
    checks.append("decisão de orçamento, ordem, checkout idempotente e pagamento demonstrativo")

    logout(customer, csrf)
    workshop_csrf = login(workshop, "smoke-workshop@example.test")
    additional, _ = post_csrf(workshop, "/api/servicos-adicionais",
                              {"acao": "propor", "ordemId": str(order["id"]),
                               "descricao": "Substituição adicional sintética", "valor": "25.00"},
                              workshop_csrf)
    post_csrf(workshop, "/api/ordens-servico",
              {"id": str(order["id"]), "status": "EM_ANDAMENTO"}, workshop_csrf)
    post_csrf(workshop, "/api/ordens-servico",
              {"id": str(order["id"]), "status": "CONCLUIDO"}, workshop_csrf, expected=409)
    logout(workshop, workshop_csrf)

    csrf = login(customer, email)
    additional_decision, _ = post_csrf(customer, "/api/servicos-adicionais",
                                       {"acao": "decidir", "id": str(additional["id"]), "decisao": "ACEITAR"}, csrf)
    assert additional_decision.get("status") == "ACEITO", additional_decision
    orders, _ = json_request(customer, "/api/ordens-servico")
    updated_order = next(row for row in orders if row.get("id") == order["id"])
    assert float(updated_order["valorTotalAtualizado"]) == 185.0, updated_order
    logout(customer, csrf)

    workshop_csrf = login(workshop, "smoke-workshop@example.test")
    post_csrf(workshop, "/api/ordens-servico",
              {"id": str(order["id"]), "status": "CONCLUIDO"}, workshop_csrf)
    orders, _ = json_request(workshop, "/api/ordens-servico")
    assert next(row for row in orders if row.get("id") == order["id"])["status"] == "CONCLUIDO", orders
    logout(workshop, workshop_csrf)
    checks.append("aprovação de adicionais, total atualizado e transições da ordem")

    csrf = login(customer, email)
    review, _ = post_csrf(customer, "/api/avaliacoes",
                          {"ordemId": str(order["id"]), "nota": "5",
                           "comentario": "Atendimento sintético validado pelo CI"}, csrf)
    post_csrf(customer, "/api/avaliacoes",
              {"ordemId": str(order["id"]), "nota": "5", "comentario": "Avaliação duplicada"},
              csrf, expected=409)
    reviews, _ = json_request(customer, "/api/avaliacoes")
    assert any(row.get("id") == review.get("id") for row in reviews), reviews
    logout(customer, csrf)
    checks.append("avaliação de ordem concluída e bloqueio de duplicidade")

    admin = client()
    admin_csrf = login(admin, "smoke-admin@example.test")
    csv_header = "pecaId,marcaVeiculo,modeloVeiculo,anoInicio,anoFim,motorizacao,codigoOem,status,fonte,referencia,observacoes"
    csv_row = "900001,Renault,Sandero,2015,2018,1.0,SMOKE-FILTER-001,NAO_VERIFICADA,,,Registro sintético"
    imported, _ = post_csrf(admin, "/api/compatibilidade-pecas/importacao",
                            {"csv": csv_header + "\n" + csv_row}, admin_csrf, expected=201)
    assert imported.get("importadas") == 1, imported
    query = urllib.parse.urlencode({"marca": "Renault", "modelo": "Sandero", "ano": "2016",
                                    "motorizacao": "1.0", "oem": "SMOKE-FILTER-001"})
    fitment, _ = json_request(anonymous, "/api/compatibilidade-pecas?" + query)
    assert any(row.get("status") == "NAO_VERIFICADA" for row in fitment), fitment
    matching_part_query = urllib.parse.urlencode({"marca": "Renault", "modelo": "Sandero", "ano": "2016",
                                                   "motorizacao": "1.0", "peca": "Filtro Smoke Test"})
    matching_parts, _ = json_request(anonymous, "/api/compatibilidade-pecas?" + matching_part_query)
    assert len(matching_parts) == 1 and matching_parts[0].get("peca") == "Filtro Smoke Test", matching_parts
    unrelated_part_query = urllib.parse.urlencode({"marca": "Renault", "modelo": "Sandero", "ano": "2016",
                                                    "motorizacao": "1.0", "peca": "Pastilha inexistente"})
    unrelated_parts, _ = json_request(anonymous, "/api/compatibilidade-pecas?" + unrelated_part_query)
    assert not unrelated_parts, unrelated_parts
    rollback_candidate = "900001,Honda,Civic,2015,2018,1.5,ROLLBACK-CANDIDATE,NAO_VERIFICADA,,,Linha que deve sofrer rollback"
    invalid_row = "999999,Renault,Sandero,2015,2018,1.0,UNKNOWN,NAO_VERIFICADA,,,Peça inexistente"
    post_csrf(admin, "/api/compatibilidade-pecas/importacao",
              {"csv": csv_header + "\n" + rollback_candidate + "\n" + invalid_row},
              admin_csrf, expected=400)
    fitment_after, _ = json_request(anonymous, "/api/compatibilidade-pecas?" + query)
    rollback_query = urllib.parse.urlencode({"marca": "Honda", "modelo": "Civic", "ano": "2016", "motorizacao": "1.5"})
    rollback_rows, _ = json_request(anonymous, "/api/compatibilidade-pecas?" + rollback_query)
    assert len(fitment_after) == 1 and not rollback_rows, {"fitment": fitment_after, "rollback": rollback_rows}
    logout(admin, admin_csrf)
    checks.append("importação CSV, compatibilidade e rollback de linha inválida")

    supplier = client()
    supplier_csrf = login(supplier, "smoke-supplier@example.test")
    stock, _ = json_request(supplier, "/api/estoque?q=Smoke&estoque=todos")
    assert any(row.get("id") == 900001 for row in stock), stock
    _, _, limited_headers = request(supplier, "/api/auth", method="POST",
                                     data={"action": "login", "email": "smoke-supplier@example.test",
                                           "password": TEST_PASSWORD}, expected=429)
    assert limited_headers.get("Retry-After") == "900", limited_headers
    logout(supplier, supplier_csrf)
    record(checks, "isolamento e consulta de estoque do fornecedor")
    record(checks, "limitação de tentativas de login e cabeçalho Retry-After")

    print(f"SMOKE TEST OK — {len(checks)} grupos de integração.")


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"SMOKE TEST FALHOU: {error}", file=sys.stderr)
        traceback.print_exc()
        sys.exit(1)
