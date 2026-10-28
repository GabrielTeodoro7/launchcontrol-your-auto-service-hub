# Auditoria técnica inicial — LaunchControl

Data da inspeção: 2026-10-27  
Escopo: os dois arquivos ZIP e o PDF enviados nesta conversa, além da estrutura pública do repositório GitHub. Esta auditoria distingue o que existe nos arquivos do que ainda precisa ser implementado.

## 1. Materiais inspecionados

- `launchcontrol-pro-main.zip`: aplicação frontend baseada em React 19, TypeScript, Vite e TanStack Start/Router. Contém componentes reutilizáveis, rotas, estilos, tipos de domínio e uma store de demonstração.
- `projeto-integrador-main.zip`: protótipo em HTML, CSS e JavaScript puro. Contém `index.html`, `style.css` e `script.js`; o script tem dados de exemplo e persiste parte do estado em `localStorage`.
- `Sem título_compressed.pdf`: 26 páginas de capturas de telas do sistema, incluindo login, dashboard, solicitações, orçamentos, serviços, clientes, veículos e configurações.
- `projeto-integrador-main/docs/diagramas/diagrama-classes.drawio` e `diagrama-classes.png`: diagrama de classes editável e exportação PNG.

## 2. Tecnologias encontradas

| Material | Tecnologias observadas | Situação |
|---|---|---|
| Frontend Lovable | React, TypeScript, Vite, TanStack Start/Router, componentes UI | A interface está estruturada, mas a store inclui comportamento de demonstração |
| Protótipo acadêmico | HTML, CSS, JavaScript, localStorage | Útil como referência de fluxos; não tem persistência no servidor |
| Backend | Nenhum backend Java Servlet identificado nos ZIPs | Precisa ser criado |
| Banco de dados | Nenhum schema SQL/MySQL identificado nos ZIPs | Precisa ser criado |
| UML | Diagrama de classes Draw.io e PNG | Não foi localizada uma suíte completa de casos de uso nem DER nesses ZIPs |

## 3. Funcionalidades existentes nos materiais

O protótipo em JavaScript inclui telas/áreas para home, solicitação, dashboard da oficina, solicitações, orçamentos, andamento, concluídos, clientes, veículos e configurações, além de áreas de cliente para dashboard, veículos, solicitações, orçamentos, serviços e perfil. Há formulários de solicitação, login, cadastro e orçamento.

O frontend React inclui componentes reutilizáveis de layout, tabela, badges de status, navegação e uma store de estado com dados de exemplo. O PDF confirma uma identidade visual escura com destaque laranja, sidebar, cartões de indicadores, tabelas e estados coloridos.

## 4. Problemas e lacunas confirmados

1. O JavaScript do protótipo declara explicitamente que usa dados mockados e `localStorage`, sem backend/API real.
2. O frontend React possui stack que o usuário pediu para evitar na implementação principal. A nova entrega independente utiliza HTML/CSS/JavaScript puro.
3. Não foi encontrado código Java Servlet, DAOs, filtros de autenticação/autorização nem conexão JDBC nos ZIPs fornecidos.
4. Não foi encontrado schema MySQL pronto nos ZIPs.
5. O PDF contém credenciais de demonstração. Elas não devem ser tratadas como usuários reais nem reutilizadas em produção.
6. Não foi encontrada integração real com provedor de pagamentos; pagamentos reais não podem ser marcados como aprovados sem confirmação do provedor.
7. O diagrama de classes não substitui o DER nem os diagramas de casos de uso. A consistência semântica completa da UML permanece pendente.
8. A versão Java/Servlet precisa ser executada em contêiner compatível; uma hospedagem estática comum não executa Servlets.

## 5. Direção técnica adotada

Para obedecer à restrição tecnológica mais recente, a nova implementação usa:
- Frontend: HTML, CSS e JavaScript puro.
- Backend: Java com Jakarta Servlet.
- Banco: MySQL/SQL via JDBC.
- Testes auxiliares: Python.
- C e C++: não serão introduzidos sem necessidade técnica concreta.
- Sem React, TypeScript, Vite, TanStack Router, Node.js, Express ou Spring Boot na aplicação independente.

A pasta `launchcontrol-servlet/` é independente do frontend Lovable existente. Isso permite manter o material de referência e não quebrar a integração atual do Lovable, que depende de sua stack original.

## 6. Próximas entregas e estado real

- [x] Inspecionar estrutura e tecnologias dos ZIPs.
- [x] Identificar telas, mock data e ausência de backend/SQL.
- [x] Criar uma base Java Servlet + JDBC + MySQL.
- [x] Implementar primeira etapa de cadastro/login, consulta de ordens, bloqueio de orçamento concluído, relato inicial de diagnóstico e checkout demonstrativo com status pendente.
- [x] Implementar a primeira etapa de cadastro/login de cliente, sessão, hash PBKDF2 e filtro de autenticação/CSRF. Autorização completa de todos os módulos ainda pendente.
- [ ] Completar CRUD e regras de negócio por perfil.
- [x] Criar endpoint inicial de relato de diagnóstico e checkout demonstrativo pendente. Fluxos completos, provedor real e demais estados ainda pendentes.
- [x] Criar DER editável inicial em docs/diagramas/der-launchcontrol.drawio e visão geral de casos de uso em docs/diagramas/casos-de-uso-launchcontrol.drawio. Revisão semântica detalhada por perfil e exportações PNG/PDF ainda pendentes.
- [ ] Compilar e executar com servidor Servlet e MySQL.
- [ ] Executar testes de integração e responsividade em navegador. Foram executadas verificações estáticas dos arquivos via GitHub; não substituem testes de execução.
- [ ] Gerar pacote final depois das verificações.

## 7. Limitações desta auditoria

Não foi possível confirmar execução ponta a ponta porque o ambiente desta etapa não tem Maven nem servidor Servlet/MySQL configurado e o clone do GitHub não pôde ser baixado no ambiente local. Foram verificadas estaticamente 11 condições usando os arquivos acessíveis no branch. Isso não equivale a executar o script Python, compilar Java, conectar ao banco ou testar segurança. Nenhuma cobrança real deve ser realizada nesta entrega.
