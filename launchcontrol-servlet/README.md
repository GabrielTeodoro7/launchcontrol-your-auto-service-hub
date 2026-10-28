# LaunchControl — implementação independente Java Servlet + MySQL

Esta pasta contém a nova implementação independente da plataforma. Ela segue a restrição tecnológica definida para esta entrega: **Java, Jakarta Servlets, HTML, CSS, JavaScript puro, MySQL/SQL e Python para verificações auxiliares**. Não usa React, TypeScript, Vite, TanStack Router, Node.js, Express ou Spring Boot.

O protótipo Lovable e os materiais acadêmicos originais são preservados como referências; não são o backend desta aplicação.

## Estado real desta entrega

Implementado nesta etapa:
- Interface responsiva com identidade escura, painéis, cartões e destaque laranja.
- Registro de conta de cliente e login por sessão no servidor.
- Hash de senha PBKDF2-HMAC-SHA256 com salt aleatório.
- Filtro de autenticação e validação de token CSRF para POSTs autenticados.
- Consulta de ordens persistidas no MySQL com filtro por perfil.
- Regra no backend que bloqueia edição de orçamento quando a ordem relacionada está CONCLUIDO.
- Relatos iniciais de diagnóstico, separados de laudo técnico.
- Checkout demonstrativo que cria registro PENDENTE; não realiza cobrança, não gera PIX válido e não aprova pagamentos.
- Schema MySQL inicial com usuários, veículos, serviços, oficinas, fornecedores, peças, solicitações, orçamentos, ordens, diagnósticos, pedidos, pagamentos e auditoria.

Ainda pendente:
- CRUD completo e autorização para todos os módulos e perfis.
- Tela de gerenciamento de orçamentos e ligação completa do fluxo da oficina.
- Integração com provedor de pagamentos, tokenização, QR Code PIX real e webhooks assinados.
- Regras de diagnóstico configuráveis, anexos, laudos e histórico completo.
- Testes de integração, segurança, execução em contêiner Servlet e validação visual em navegador.
- Revisão final dos diagramas UML e geração do DER/exports.
- Verificação da entrega final e ZIP acadêmico.

Não considere a lista acima como prova de que os itens pendentes estão concluídos.

## Requisitos para executar

- Java 17 ou superior.
- Maven 3.9+.
- MySQL 8.0+.
- Contêiner compatível com Jakarta Servlet 6.0, por exemplo Tomcat 10.1+.
- Navegador moderno.

## Configuração do banco

1. Crie um usuário de banco com permissões limitadas para a aplicação.
2. Execute database/schema.sql no MySQL.
3. Configure variáveis de ambiente no processo que executa o servidor:
   - LC_DB_URL=jdbc:mysql://localhost:3306/launchcontrol
   - LC_DB_USER=launchcontrol_app
   - LC_DB_PASSWORD com a senha definida localmente.
4. Não grave credenciais reais no repositório.

O schema não cria usuários fictícios nem credenciais de administrador padrão. Crie uma conta pela tela de registro (perfil inicial CLIENTE). A mudança para perfis privilegiados deve ser feita por um administrador autorizado diretamente no banco, com trilha de auditoria institucional apropriada.

## Compilar e implantar

Na pasta launchcontrol-servlet/, com Maven disponível, execute: mvn clean package

O WAR será gerado em target/launchcontrol.war. Implante-o em um contêiner compatível com Jakarta Servlet 6.0 e abra o contexto disponibilizado pelo servidor.

Endpoints implementados nesta etapa:
- GET /api/health — verifica conexão JDBC.
- POST /api/auth — action=register, action=login ou action=logout.
- GET /api/auth — informa a sessão atual.
- GET /api/servicos?q=... — pesquisa serviços ativos.
- GET /api/ordens-servico — lista ordens permitidas ao perfil autenticado.
- POST /api/orcamentos/editar — altera orçamento quando permitido e bloqueia ordens concluídas.
- POST /api/diagnosticos — registra relato inicial de cliente.
- POST /api/checkout — cria pagamento demonstrativo pendente para orçamento aceito.

O formulário de cadastro só cria perfil CLIENTE; não existe cadastro público de contas de oficina ou administrador. Isso evita que qualquer pessoa se atribua privilégios.

## Segurança e limitações

- Senhas são armazenadas como hash PBKDF2, não em texto puro.
- Consultas usam PreparedStatement.
- A API protegida exige sessão e token CSRF em POSTs autenticados.
- A aplicação não armazena número completo de cartão ou CVV.
- Checkout é somente demonstração e nunca representa cobrança aprovada.
- Para produção ainda são necessários rate limiting, política de recuperação de senha, cabeçalhos de segurança, gestão de sessões/cookies revisada no ambiente, testes de autorização e integração com provedor.
- O frontend mostra erro quando a API não está disponível; não inventa registros reais.

## Verificação auxiliar

Execute python3 scripts/check_project.py. Essa verificação é estrutural e não substitui compilação, teste no servidor ou conexão ao MySQL.
