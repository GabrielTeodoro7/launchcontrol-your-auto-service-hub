# LaunchControl — auditoria e plano de evolução

**Status:** auditoria estática inicial e plano de execução. Este documento não declara funcionalidades concluídas nem testes aprovados.

## 1. Escopo e restrições

Evoluir as seis capacidades do LaunchControl — Launch AI, SmartQuote, CarPassport, Fitment Engine, Oficina Transparente e AutoCare Score — reutilizando a aplicação existente. Preservar a identidade visual escura, os tokens CSS atuais, a navegação e os formulários. Não introduzir linguagens, frameworks ou infraestrutura novos.

Stack observada nos arquivos consultados:
- Java com release 25 configurado no Maven.
- Jakarta Servlet 6.1.
- JDBC e MySQL Connector/J 9.7.0.
- HTML5, CSS3 e JavaScript sem framework no frontend.
- Maven com empacotamento WAR.
- Integração HTTP existente com a API Responses da OpenAI em Java.

As versões e a configuração devem ser revalidadas antes de cada mudança que as possa afetar.

## 2. Inventário inicial de código

| Área | Componentes encontrados | Evidência e lacunas a validar |
|---|---|---|
| Assistente de peças/IA | `PartsAssistantServlet`, interface LC Parts e JavaScript associado | Já existe chamada HTTP real à API Responses, leitura de `OPENAI_API_KEY`, modelo configurável por `OPENAI_MODEL`, timeout e tratamento de algumas falhas. O prompt atual foca pesquisa de peças, não um copiloto completo de sintomas. |
| Relatos de sintomas | `DiagnosticServlet`, tabela `diagnosticos`, formulário de diagnóstico e Garagem Inteligente | Há persistência de relatos e campos para hipóteses preliminares/laudo técnico. O relato não deve ser tratado como laudo confirmado. |
| Orçamentos | `QuoteCreateServlet`, `QuoteDecisionServlet`, painel de orçamentos | O fluxo base existe. O modelo e a interface devem ser verificados para detalhamento por item, garantia, validade, histórico e comparação sem dados inventados. |
| Veículos | `VehicleServlet`, cadastro e seletor de veículos | Existe cadastro persistido; o passaporte cronológico de manutenção e a origem/verificação dos registros precisam ser validados. |
| Peças/estoque | `PartsSearchServlet`, `InventoryServlet`, LC Parts | Há pesquisa e estoque. A pesquisa comercial não confirma automaticamente aplicação técnica; falta verificar fonte e nível de confiança da compatibilidade. |
| Ordens | `OrderServlet`, listagem de ordens e estados | Existe ciclo de ordens. Precisam ser auditados transições permitidas, trilha de auditoria e aprovações de adicionais. |
| Segurança | `AuthFilter`, `PasswordUtil`, `Database`, `Json` | Há filtro de sessão/perfil e validação de CSRF para POST no filtro. Revisar todas as rotas, verificações de propriedade, cabeçalhos e mensagens de erro. |
| Banco | `banco-de-dados/modelo.sql` | Schema SQL versionado no repositório; mudanças devem ser aditivas, reproduzíveis e não destrutivas. |
| Frontend | `frontend/index.html`, `frontend/css/estilo-principal.css`, `frontend/js/interacoes.js` | Identidade visual atual usa tokens CSS escuros e destaque laranja; novos fluxos devem reutilizar esses padrões. |

## 3. Classificação preliminar

Esta classificação representa evidência estática de código, não um teste de ponta a ponta.

| Funcionalidade | Estado preliminar | Próxima verificação |
|---|---|---|
| Launch AI | Parcial | Validar entrada/saída, limites, segurança, UX, tratamento de falha e conversão em relato/solicitação. |
| SmartQuote | Parcial | Conferir schema e endpoints de criação/decisão, total calculado no servidor, validade, garantia e histórico. |
| CarPassport | Parcial | Separar dados declarados de eventos confirmados por ordens concluídas; conferir propriedade e duplicidade. |
| Fitment Engine | Parcial | Rastrear origem dos dados de aplicação e nunca converter resultado de busca em compatibilidade confirmada. |
| Oficina Transparente | Parcial | Validar transições de status, autorizações, trilha temporal e isolamento entre cliente e oficina. |
| AutoCare Score | Não verificado como funcionalidade completa | Definir métricas explicáveis usando apenas despesas e manutenções persistidas; marcar estimativas como estimativas. |

## 4. Arquitetura-alvo compatível com a stack

### Launch AI

```text
Frontend existente (HTML/CSS/JavaScript)
  -> endpoint Java autenticado + CSRF
    -> validação de tamanho, perfil e limite de uso
      -> serviço de aplicação Java
        -> regras de segurança e dados mínimos do veículo
        -> provedor IA por HTTP (configuração do servidor)
        -> validação da resposta estruturada
      -> resposta tipada e útil para a interface
  -> relato de sintomas / solicitação de orçamento existente
```

Reutilizar primeiro `PartsAssistantServlet` e `DiagnosticServlet` quando os contratos forem compatíveis. Não criar um segundo chat independente sem necessidade. A integração deve continuar utilizável quando a chave externa não estiver configurada, com um fluxo guiado explicitamente identificado como tal.

### Fluxos compartilhados

- SmartQuote usa a solicitação existente e persiste valores e decisões no servidor.
- Oficina Transparente evolui a ordem existente e registra mudanças autorizadas.
- CarPassport agrega eventos do veículo sem afirmar que registros declarados são verificados.
- Fitment Engine retorna estados de compatibilidade explícitos: confirmada por fonte identificada, incompatível segundo fonte identificada ou não verificada.
- AutoCare Score consome os dados persistidos de CarPassport e ordens; não cria históricos artificiais.

## 5. Plano de implementação

### Fase 0 — baseline e segurança
1. Conferir branch e arquivos existentes antes de cada edição.
2. Identificar comandos de build e verificações já disponíveis.
3. Auditar autenticação, CSRF, autorização por perfil e propriedade de cada registro.
4. Registrar limitações de ambiente (MySQL, credenciais externas, JDK/Maven).
5. Não remover alterações anteriores nem recriar o banco.

### Fase 1 — Launch AI
1. Definir contrato de entrada/saída estruturado para intenção, perguntas em falta, explicação preliminar, alertas e próximos passos.
2. Usar provedor HTTP Java existente, com segredo exclusivamente no ambiente.
3. Limitar tamanho da mensagem/histórico, timeout e frequência; não enviar placa, contato ou histórico inteiro por padrão.
4. Detectar sinais de risco por regras explícitas e conservadoras; recomendar parar em local seguro e buscar assistência quando apropriado, sem dar diagnóstico definitivo.
5. Persistir o relato pelo fluxo existente e permitir iniciar solicitação de orçamento com confirmação do cliente.
6. Tratar resposta inválida, timeout, indisponibilidade, limite de uso e ausência de chave.
7. Testar parsing, validação, erros, permissões e integração de ponta a ponta quando ambiente estiver disponível.

### Fase 2 — SmartQuote e Oficina Transparente
1. Confirmar modelo de dados antes de adicionar campos/tabelas.
2. Calcular subtotais e totais no servidor com `BigDecimal`.
3. Registrar validade, garantia e escopo apenas quando fornecidos pela oficina.
4. Validar transições de estado no backend e restringir cada transição por perfil.
5. Guardar eventos de decisão/status sem sobrescrever o histórico relevante.
6. Exigir aceite explícito para serviços adicionais antes de sua execução.
7. Comparar apenas critérios com dados reais, distinguindo ausência de informação.

### Fase 3 — CarPassport
1. Usar veículos e ordens existentes como fontes.
2. Criar migrações aditivas apenas para campos ausentes.
3. Registrar data, quilometragem, custo, serviço e origem/verificação.
4. Evitar duplicar ordens concluídas ao montar o histórico.
5. Autorizar leitura e escrita pela propriedade do veículo.
6. Implementar filtros e lembretes com base em datas/quilometragem reais.

### Fase 4 — Fitment Engine
1. Inventariar campos existentes de peças, estoque e referências.
2. Modelar origem da compatibilidade, referência de catálogo e data de atualização.
3. Só exibir confirmação quando houver fonte verificável; caso contrário, usar “não verificada”.
4. Usar SQL parametrizado, filtros limitados, paginação e índices justificados.
5. Não inferir estoque, preço ou equivalência técnica com IA.

### Fase 5 — AutoCare Score
1. Definir métricas explicáveis e versão da regra usada.
2. Separar gastos efetivamente registrados de estimativas.
3. Indicar cobertura e qualidade do histórico.
4. Não classificar segurança mecânica a partir de completude de dados.
5. Permitir planejamento por recomendações identificáveis do fabricante, quando disponíveis.

### Fase 6 — integração e qualidade
1. Conferir todos os fluxos de ponta a ponta.
2. Adicionar testes de regras, permissões, estados e persistência usando recursos existentes.
3. Executar build Maven e verificações frontend disponíveis no ambiente.
4. Documentar dependências externas e migrações.
5. Corrigir regressões antes de declarar uma etapa concluída.

## 6. Diretrizes de segurança

- Segredos apenas em variáveis de ambiente ou configuração segura do servidor.
- Nunca registrar prompts completos, credenciais, tokens ou dados pessoais em logs.
- Validar sessão, perfil e propriedade no backend, mesmo que a interface oculte ações.
- Manter CSRF em operações que alteram dados.
- Usar consultas parametrizadas e limitar entradas/saídas.
- Não permitir que a IA altere estados, aprove orçamentos ou execute ações de negócio sem confirmação e autorização explícitas.
- Tratar conteúdo fornecido pelo usuário como dado não confiável.
- Não armazenar dados externos em massa sem necessidade e consentimento adequados.

## 7. Preservação visual

Manter `--bg`, `--sidebar`, `--panel`, `--panel2`, `--line`, `--text`, `--muted`, `--orange`, `--blue`, `--green`, `--purple` e `--red` definidos em `estilo-principal.css`. Reutilizar `panel`, `panel-heading`, `stack-form`, `button`, `form-status`, `muted` e o padrão de formulários. Qualquer mudança deve ser incremental e responsiva.

## 8. Critérios de conclusão por funcionalidade

Uma funcionalidade só será marcada como concluída depois de haver evidência de:
- UI integrada ao endpoint correto.
- Regra de negócio executada no servidor.
- Persistência correta quando necessária.
- Validação, autorização e isolamento de dados.
- Tratamento de erros e indisponibilidade.
- Testes executados com resultado registrado.
- Ausência de regressões conhecidas sem documentação.

Se o ambiente de execução ou credenciais estiverem indisponíveis, registrar exatamente o que não foi possível verificar. Não afirmar build, testes, integração externa ou publicação sem evidência.

## 9. Estado desta auditoria

- **Inspecionado estaticamente:** README, `pom.xml`, interface principal, CSS, JavaScript principal, schema SQL e classes centrais de IA, diagnóstico, autenticação, orçamento, ordem, busca e estoque.
- **Ainda necessário:** execução de build/testes, integração real com MySQL, teste de navegador e validação com credenciais do provedor externo.
- **Sem alegação de sucesso de testes:** nenhuma execução de teste é afirmada neste documento.
