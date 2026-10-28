# LaunchControl

Sistema de gestão de serviços automotivos, organizado por função para facilitar a navegação no VS Code.

## Organização das pastas

```text
LaunchControl/
├── backend/                    Código Java e configuração Maven
│   └── src/main/
│       ├── java/br/com/launchcontrol/  Regras e endpoints
│       └── webapp/WEB-INF/             Configuração do servidor
├── frontend/                   Interface do navegador
│   ├── index.html               Estrutura das telas
│   ├── css/estilo-principal.css Aparência, cores e layout
│   └── js/interacoes.js         Botões, formulários e chamadas à API
├── banco-de-dados/
│   ├── modelo.sql               Estrutura-base das tabelas MySQL
│   └── migracoes/               Migrações aditivas versionadas
├── docs/                       Auditoria e plano técnico
├── scripts/                    Verificações existentes do projeto
├── .github/workflows/           Compilação automática
└── README.md                    Este guia
```

## Tecnologias

Java 25 LTS, Jakarta Servlet 6.1, MySQL 8+, JDBC, HTML5, CSS3, JavaScript puro, Maven e Apache Tomcat 11. As versões efetivas devem ser confirmadas no `backend/pom.xml`.

## Executar no VS Code

1. Instale o JDK configurado no Maven, Maven, MySQL Server 8 e Apache Tomcat compatível com Jakarta Servlet 6.1. No VS Code, instale **Extension Pack for Java**.
2. Baixe o repositório:

```bash
git clone https://github.com/GabrielTeodoro7/launchcontrol-your-auto-service-hub.git
cd launchcontrol-your-auto-service-hub
code .
```

3. Abra `banco-de-dados/modelo.sql` no MySQL Workbench e execute o script para criar o esquema-base.
4. Execute `banco-de-dados/migracoes/001_ecossistema_automotivo.sql` no mesmo banco. A migração é aditiva e usa `CREATE TABLE IF NOT EXISTS`; faça backup antes de aplicá-la em um ambiente com dados importantes.
5. Execute `banco-de-dados/migracoes/002_aprovacoes_notificacoes.sql` para habilitar aprovações de serviços adicionais e notificações internas. Faça backup antes de executar migrações em bancos com dados importantes.
6. Configure no ambiente em que o Tomcat será iniciado as variáveis `LC_DB_URL`, `LC_DB_USER` e `LC_DB_PASSWORD`. Não publique senhas no GitHub. Para anexos do passaporte, configure também `LC_UPLOAD_DIR` com um caminho absoluto gravável fora da pasta pública do Tomcat. O upload aceita PDF, PNG e JPEG de até 5 MB; sem essa variável, o endpoint de anexos permanece desabilitado.
7. Para habilitar a resposta de IA real, configure `OPENAI_API_KEY` no ambiente do Tomcat. `OPENAI_MODEL` é opcional e usa `gpt-5-mini` como padrão no código atual. Sem a chave, o modo de sintomas oferece orientação guiada explicitamente identificada como sem IA; o modo de pesquisa de peças depende do provedor. Não coloque a chave no frontend ou no repositório.
8. Na raiz do projeto, compile:

```bash
mvn -f backend/pom.xml clean package
```

9. Copie `backend/target/launchcontrol.war` para a pasta `webapps` do Tomcat, inicie o servidor e acesse `http://localhost:8080/launchcontrol/`.

O servidor precisa usar uma versão do JDK compatível com o `pom.xml` e conseguir acessar o MySQL.

## Onde alterar cada parte

- **Cores e layout:** `frontend/css/estilo-principal.css`
- **Telas, textos e campos:** `frontend/index.html`
- **Botões e comportamento da interface:** `frontend/js/interacoes.js`
- **Regras e endpoints do servidor:** classes Java em `backend/src/main/java/br/com/launchcontrol/`
- **Esquema-base:** `banco-de-dados/modelo.sql`
- **Novas tabelas do ecossistema:** `banco-de-dados/migracoes/001_ecossistema_automotivo.sql`
- **Plano técnico e auditoria inicial:** `docs/PLANO-EVOLUCAO-LAUNCHCONTROL.md`

## Recursos nesta linha de evolução

- **Launch AI:** o assistente existente oferece modo de pesquisa de peças e modo de triagem de sintomas. A triagem inclui orientações de segurança e um fallback guiado explicitamente identificado como sem IA quando o provedor está indisponível. Não substitui avaliação profissional.
- **SmartQuote:** separa peças e materiais, mão de obra e outros custos; o backend calcula o total e registra garantia, validade e condição das peças quando informadas.
- **CarPassport:** registra manutenções informadas pelo cliente, com custos, quilometragem e próximas datas/quilometragens. Os registros declarados não são apresentados como laudos verificados.
- **Fitment Engine:** consulta o catálogo local de aplicação de peças e mostra fonte e estado de verificação. Ausência de registros não significa incompatibilidade.
- **Oficina Transparente:** registra transições de status das ordens e disponibiliza uma linha do tempo por veículo. Notifica cliente e oficina sobre decisões de orçamento e mudanças de status, sem inventar eventos históricos retroativos.
- **Serviços adicionais:** a oficina envia propostas com descrição e valor; o cliente aceita ou recusa explicitamente. A conclusão da ordem é bloqueada enquanto houver propostas pendentes.
- **Totais atualizados:** ordens exibem o valor-base somado aos serviços adicionais aceitos, sem sobrescrever o orçamento original.
- **Avaliações verificadas:** clientes podem avaliar uma única vez cada ordem concluída que lhes pertence; a interface mostra notas e comentários registrados.
- **CarPassport complementar:** exporta o histórico por meio da impressão do navegador e permite anexar PDF/PNG/JPEG de até 5 MB. Configure `LC_UPLOAD_DIR` fora da pasta pública do Tomcat. Lembretes usam apenas a próxima data e quilometragem informadas pelo usuário.
- **AutoCare Score:** mostra indicadores explicáveis de planejamento, gastos registrados e cobertura dos lembretes; não avalia a saúde mecânica do veículo.

O catálogo de compatibilidade depende de referências confiáveis cadastradas no banco. As integrações de IA dependem da configuração do provedor. A presença do código não comprova, por si só, funcionamento em produção.

## Testes e limitações

A aba **Actions** do GitHub mostra os resultados das verificações automáticas disponíveis. O GitHub não inicia sozinho o Tomcat e o MySQL. O checkout permanece demonstrativo e não processa cobranças reais. A compilação não substitui testes de integração com banco, servidor e credenciais configurados. Não declare testes aprovados sem consultar seus resultados.
