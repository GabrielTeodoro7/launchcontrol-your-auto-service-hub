#!/usr/bin/env python3
"""Smoke test HTTP da aplicação implantada em Tomcat com MySQL real."""

import http.cookiejar
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE_URL = os.environ.get("LAUNCHCONTROL_BASE_URL", "http://127.0.0.1:8080/launchcontrol").rstrip("/")


def client():
    jar = http.cookiejar.CookieJar()
    return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))


def request(opener, path, method="GET", data=None, headers=None, expected=None):
    payload = urllib.parse.urlencode(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(BASE_URL + path, data=payload, method=method, headers=headers or {})
    try:
        with opener.open(req, timeout=8) as response:
            code, body, response_headers = response.status, response.read().decode("utf-8"), response.headers
    except urllib.error.HTTPError as error:
        code, body, response_headers = error.code, error.read().decode("utf-8"), error.headers
    if expected is not None and code != expected:
        raise AssertionError(f"{method} {path}: esperado HTTP {expected}, recebido {code}; corpo={body[:400]}")
    return code, body, response_headers


def main():
    checks = []
    anonymous = client()
    code, body, headers = request(anonymous, "/", expected=200)
    assert "LAUNCHCONTROL" in body.upper(), "A página inicial não contém a identidade LaunchControl"
    checks.append("página inicial implantada")

    code, body, headers = request(anonymous, "/api/health", expected=200)
    assert headers.get("X-Content-Type-Options") == "nosniff", "Cabeçalho nosniff ausente"
    assert headers.get("X-Frame-Options") == "DENY", "Cabeçalho anti-frame ausente"
    assert headers.get("Cache-Control") == "no-store", "Resposta de API não deve ser armazenada em cache"
    health = json.loads(body)
    assert health.get("status") == "ok" and health.get("database") == "connected", health
    checks.append("API e conexão MySQL")

    request(anonymous, "/api/ordens-servico", expected=401)
    checks.append("bloqueio de API sem sessão")

    email = f"smoke-{uuid.uuid4().hex[:16]}@example.test"
    password = "LaunchControl-Teste-25!"
    authenticated = client()
    _, body, _ = request(authenticated, "/api/auth", method="POST",
                         data={"action": "register", "name": "Usuário de Smoke Test",
                               "email": email, "password": password}, expected=200)
    registered = json.loads(body)
    assert registered.get("ok") is True and registered.get("csrf"), registered
    csrf = registered["csrf"]
    checks.append("cadastro e criação de sessão")

    _, body, _ = request(authenticated, "/api/auth", expected=200)
    assert json.loads(body).get("email") == email, body
    checks.append("consulta da sessão autenticada")

    request(authenticated, "/api/notificacoes/preferencias", method="POST",
            data={"emailAtivo": "true", "whatsappAtivo": "false", "whatsappNumero": ""},
            headers={"X-CSRF-Token": "token-invalido"}, expected=403)
    checks.append("bloqueio de CSRF inválido")

    request(authenticated, "/api/auth", method="POST",
            data={"action": "register", "name": "Usuário Duplicado",
                  "email": email, "password": password}, expected=409)
    checks.append("bloqueio de e-mail duplicado")

    _, body, _ = request(authenticated, "/api/auth", method="POST",
                         data={"action": "logout"}, headers={"X-CSRF-Token": csrf}, expected=200)
    assert json.loads(body).get("ok") is True, body
    request(authenticated, "/api/auth", expected=401)
    checks.append("logout e invalidação de sessão")

    request(authenticated, "/api/auth", method="POST",
            data={"action": "login", "email": email, "password": "Senha-errada-25!"}, expected=401)
    checks.append("rejeição de senha incorreta")

    _, body, _ = request(authenticated, "/api/auth", method="POST",
                         data={"action": "login", "email": email, "password": password}, expected=200)
    login = json.loads(body)
    assert login.get("ok") is True and login.get("csrf"), login
    checks.append("login com credencial correta")

    request(authenticated, "/api/auth", method="POST",
            data={"action": "logout"}, headers={"X-CSRF-Token": login["csrf"]}, expected=200)
    checks.append("logout após login")

    print("SMOKE TEST OK — " + str(len(checks)) + " verificações:")
    for check in checks:
        print("  ✓ " + check)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"SMOKE TEST FALHOU: {error}", file=sys.stderr)
        sys.exit(1)
