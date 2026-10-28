# Matriz de rastreabilidade — LaunchControl

Esta matriz relaciona os requisitos do prompt mestre aos artefatos atualmente encontrados ou criados. "Parcial" significa que existe uma primeira implementação, não que o fluxo completo esteja concluído.

| Requisito | Artefato atual | Estado |
|---|---|---|
| Interface escura, cards, status e responsividade | launchcontrol-servlet/src/main/webapp/index.html e css/style.css | Parcial; ainda precisa validação em navegador nos breakpoints exigidos |
| Cadastro e login de cliente | AuthServlet.java, PasswordUtil.java | Parcial; sem recuperação de senha, rate limit e cadastro de perfis privilegiados |
| Sessão e autorização no servidor | AuthFilter.java | Parcial; há autenticação e CSRF para POSTs protegidos, mas faltam políticas completas por módulo |
| Pesquisa de serviços | ServiceServlet.java | Inicial; consulta catálogo ativo com PreparedStatement |
| Consulta de ordens por perfil | OrderServlet.java | Inicial; cliente, oficina e administrador com filtros por perfil |
| Editar orçamento | QuoteServlet.java | Inicial; exige oficina/admin e bloqueia ordem CONCLUIDO |
| Remover ação de edição para concluídos | src/main/webapp/js/app.js | Implementado no render da lista; ação backend também bloqueia |
| Cadastro de veículos | schema tem tabela veiculos | CRUD/endpoints e tela pendentes |
| Cadastro e gestão de peças | schema tem tabela pecas | CRUD/endpoints e tela pendentes |
| Solicitação e comparação de orçamentos | schema e relações iniciais | Fluxo completo pendente |
| Diagnóstico automotivo | DiagnosticServlet.java | Apenas relato inicial; regras, anexos, laudo e histórico pendentes |
| Checkout | CheckoutServlet.java | Demonstrativo; orçamento aceito gera pedido e pagamento pendente |
| Histórico/cancelamento de pagamento demonstrativo | PaymentServlet.java | Inicial; consulta e cancelamento do próprio pagamento pendente de demonstração |
| Pagamento real por cartão/PIX | Nenhum provedor conectado | Pendente; não há cobrança, tokenização, QR Code válido ou webhook |
| Evitar pagamento duplicado | CheckoutServlet.java | Verificação inicial por chave de idempotência e pagamento pendente; precisa teste concorrente |
| Banco MySQL | database/schema.sql | Schema inicial com 13 tabelas; ainda precisa execução/importação em MySQL |
| DER editável | docs/diagramas/der-launchcontrol.drawio | Criado com base no schema inicial; precisa revisão e exportação PNG/PDF |
| Casos de uso — visão geral | docs/diagramas/casos-de-uso-launchcontrol.drawio | Criado como escopo-alvo; ainda precisa validação com requisitos do professor |
| Diagrama de classes existente | projeto-integrador-main/docs/diagramas/diagrama-classes.drawio no ZIP fornecido | Encontrado nos materiais; revisão e sincronização com implementação pendentes |
| Casos de uso detalhados por perfil | Não localizados nos ZIPs inspecionados | Pendentes; há apenas uma visão geral inicial |
| Testes automatizados | scripts/check_project.py | Verificação estrutural; não substitui teste funcional/integrado |
| Compilação WAR | pom.xml e workflow GitHub Actions | Configurado; resultado de execução ainda não confirmado |
| ZIP final acadêmico | Não gerado | Pendente até execução e revisão final |

## Regra de segurança sobre pagamentos

Enquanto um provedor de pagamento não estiver configurado, todo pagamento criado por este projeto fica marcado como DEMONSTRACAO e PENDENTE. Nenhuma interface deve informar que a cobrança foi aprovada. O número completo do cartão e o CVV não são armazenados.

## Critério para marcar como concluído

Um item só deve passar para "concluído" depois de implementar o fluxo, executar os testes correspondentes e registrar o resultado. A existência de tabela, tela ou servlet isolado não comprova integração funcional.
