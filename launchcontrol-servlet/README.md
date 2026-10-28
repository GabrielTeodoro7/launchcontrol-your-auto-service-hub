# LaunchControl — implementação independente Java Servlet + MySQL

Esta pasta contém a nova implementação independente da plataforma. Ela segue a restrição tecnológica definida para esta entrega: **Java, Jakarta Servlets, HTML, CSS, JavaScript puro, MySQL/SQL e Python para verificações auxiliares**. Não usa React, TypeScript, Vite, TanStack Router, Node.js, Express ou Spring Boot.

O frontend web original e os materiais acadêmicos são mantidos separados; esta pasta contém a implementação independente em Java.

## Estado real desta entrega

Implementado nesta etapa:
- Interface responsiva com identidade escura, painéis, cartões e destaque laranja.
- Registro de conta de cliente e login por sessão no servidor.
- Hash de senha PBKDF2-HMAC-SHA256 com salt aleatório.
- Filtro de autenticação e validação de token CSRF para POSTs autenticados.
- Consulta de ordens persistidas no MySQL com filtro por perfil.
- Cadastro de veículos por cliente e abertura/listagem de solicitações de orçamento.
- Painel inicial de propostas para clientes e oficinas, com envio, aceite/recusa e geração de ordem de serviço após aceite.
- Edição de orçamentos pendentes/enviados pela oficina proprietária, com validação de valor, prazo e limite de observações.
- Atualização de andamento da ordem por oficina autorizada ou administrador; ordem concluída fica bloqueada para reabertura.
- Regra no backend que bloqueia edição de orçamento quando a ordem relacionada está CONCLUIDO.
- Relatos iniciais de diagnóstico, separados de laudo técnico.
- Checkout demonstrativo que cria registro PENDENTE; não realiza cobrança, não gera PIX válido e não aprova pagamentos.
- Schema MySQL inicial com usuários, veículos, serviços, oficinas, fornecedores, peças, solicitações, orçamentos, ordens, diagnósticos, pedidos, pagamentos e auditoria.

Ainda pendente:
- CRUD completo para todos os módulos e perfis; esta entrega cobre apenas operações iniciais de veículos, solicitações, orçamentos e ordens.
- Fluxo de oficina ainda precisa de testes integrados com contas e dados reais de homologação.
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
- POST /api/ordens-servico — atualiza andamento e registra conclusão, com bloqueio para reabrir ordem concluída.
- GET/POST /api/veiculos — lista/cadastra veículos do cliente autenticado.
- GET/POST /api/solicitacoes — lista e abre solicitações de orçamento.
- POST /api/orcamentos — cria orçamento pela oficina vinculada.
- POST /api/orcamentos/decisao — permite ao cliente aceitar ou recusar orçamento; a aceitação gera uma ordem de serviço.
- POST /api/orcamentos/editar — altera orçamento quando permitido e bloqueia ordens concluídas.
- POST /api/diagnosticos — registra relato inicial de cliente.
- POST /api/checkout — cria pagamento demonstrativo pendente para orçamento aceito.
- GET /api/pagamentos — consulta histórico de pagamentos do cliente autenticado.
- POST /api/pagamentos — cancela pagamento demonstrativo pendente do próprio cliente.

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


## Guia rápido de programação para iniciantes

- **HTML** descreve os elementos da página: títulos, campos, botões, tabelas e janelas.
- **CSS** define a aparência: cores, tamanhos, espaçamento e adaptação para celular.
- **JavaScript** roda no navegador. Ele reage aos cliques, verifica formulários e envia pedidos ao servidor.
- **Java Servlet** é uma classe Java que recebe requisições HTTP e devolve uma resposta ao navegador.
- **Endpoint** é o endereço da API que executa uma tarefa, como `/api/veiculos`.
- **MySQL** é o banco que guarda informações de forma persistente.
- **SQL** é a linguagem usada para criar tabelas e consultar ou alterar dados.
- **JDBC** é a ponte usada pelo Java para conversar com o banco.
- **Sessão** é a forma de o servidor reconhecer que a pessoa já entrou no sistema.
- **Token CSRF** é um código adicional usado para dificultar ações autenticadas não autorizadas.
- **PreparedStatement** envia a consulta SQL e os valores separadamente, ajudando a evitar injeção de SQL.
- **WAR** é o pacote que contém a aplicação web Java para instalação em um servidor Servlet.

### Como acompanhar uma ação do início ao fim

Por exemplo, ao cadastrar um veículo: o formulário está em `index.html`; o evento e o pedido HTTP estão em `js/app.js`; o endpoint recebe e valida os dados em `VehicleServlet.java`; `Database.java` abre a conexão; e o SQL grava os dados na tabela `veiculos`. A resposta volta ao JavaScript, que mostra a mensagem para a pessoa.

Os comentários no código explicam as responsabilidades de cada arquivo e os trechos mais importantes. Eles são material de estudo; a fonte da verdade sobre o que está implementado continua sendo o código e os testes executados.

## LC Parts — estado desta implementação

A área LC Parts permite informar marca, modelo, ano, versão, motorização, peça, código OEM, cor opcional e condição. Quando a pessoa está autenticada como cliente, também pode preencher os dados a partir de um veículo cadastrado.

Nesta etapa, a pesquisa abre páginas de busca do Mercado Livre, Shopee, OLX e uma busca geral na web para localizar lojas e fabricantes. **Não há integração de API com esses serviços**: o LaunchControl não recupera os anúncios, preços, frete, avaliações, estoque ou compatibilidade, e não deve ser apresentado como se fizesse isso. A conferência acontece na fonte externa. Nenhuma tabela, coluna, dependência ou endpoint foi adicionado para esta área.


## Garagem Inteligente — estado desta implementação

A Garagem Inteligente reúne, por veículo cadastrado, solicitações, orçamentos e ordens de serviço que podem ser relacionados pelos identificadores já existentes. Também lista os relatos de diagnóstico do próprio cliente e exibe o laudo técnico somente quando há um texto registrado no banco.

Para isso, os endpoints existentes passaram a devolver campos adicionais de relacionamento e data; não foram criadas tabelas, colunas, dependências ou novas rotas. A consulta de diagnósticos por GET é restrita ao cliente autenticado e filtra pelo identificador da própria conta.

A tela informa que o histórico é limitado aos registros existentes no LaunchControl e não representa necessariamente o histórico completo ou oficial do veículo. A validação ponta a ponta ainda depende de executar a aplicação em um contêiner Jakarta Servlet conectado ao MySQL e testar com contas e registros de homologação.

## LC Parts — consulta real e assistente de IA

O LC Parts consulta anúncios públicos do Mercado Livre pelo endpoint de busca do backend (/api/pecas/busca). Os cartões mostram dados retornados pela fonte e abrem o anúncio original; preço anunciado não é tratado como preço final, frete desconhecido é sinalizado e a compatibilidade nunca é marcada como confirmada automaticamente.

Shopee, OLX e buscas por lojas oficiais são oferecidas como links para pesquisar diretamente na plataforma de origem. A aplicação não afirma que consultou APIs dessas fontes.

O assistente usa a API Responses da OpenAI no servidor Java, sem expor a chave no navegador e sem adicionar dependências. Para ativá-lo, configure no ambiente do servidor:

- OPENAI_API_KEY: chave privada da API OpenAI.
- OPENAI_MODEL (opcional): modelo habilitado na sua conta; o padrão é gpt-5-mini.

Não coloque a chave no JavaScript, no repositório ou em mensagens de chat. O uso da API pode gerar custos na conta do provedor. Se a chave não estiver configurada, a busca de peças continua funcionando e o assistente informa a configuração ausente.

O assistente ajuda a formular a pesquisa e pode pedir detalhes como versão, motorização ou código OEM. Ele não confirma compatibilidade nem substitui a conferência do anúncio com o vendedor.
