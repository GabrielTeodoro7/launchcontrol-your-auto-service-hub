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
│   └── modelo.sql               Estrutura das tabelas MySQL
├── scripts/
│   └── verificar_projeto.py     Verificações automáticas
├── .github/workflows/           Compilação automática
└── README.md                    Este guia
```

## Tecnologias

Java 25 LTS, Jakarta Servlet 6.1, MySQL 8+, JDBC, HTML5, CSS3, JavaScript puro, Maven 3.10+ e Apache Tomcat 11.

## Executar no VS Code

1. Instale o JDK 25, Maven, MySQL Server 8 e Apache Tomcat 11. No VS Code, instale **Extension Pack for Java**.
2. Baixe o repositório:

```bash
git clone https://github.com/GabrielTeodoro7/launchcontrol-your-auto-service-hub.git
cd launchcontrol-your-auto-service-hub
code .
```

3. Abra `banco-de-dados/modelo.sql` no MySQL Workbench e execute o script para criar as tabelas.
4. Configure no ambiente em que o Tomcat será iniciado as variáveis `LC_DB_URL`, `LC_DB_USER` e `LC_DB_PASSWORD`. Não publique senhas no GitHub.
5. Na raiz do projeto, compile:

```bash
mvn -f backend/pom.xml clean package
```

8. Copie `backend/target/launchcontrol.war` para a pasta `webapps` do Tomcat 11, inicie o servidor e acesse `http://localhost:8080/launchcontrol/`.

O Tomcat precisa usar o JDK 25 e conseguir acessar o MySQL.

## Onde alterar cada parte

- **Cores e layout:** `frontend/css/estilo-principal.css`
- **Telas, textos e campos:** `frontend/index.html`
- **Botões e comportamento da interface:** `frontend/js/interacoes.js`
- **Regras e endpoints do servidor:** classes Java em `backend/src/main/java/br/com/launchcontrol/`
- **Esquema-base:** `banco-de-dados/modelo.sql`\n- **Novas tabelas do ecossistema:** `banco-de-dados/migracoes/001_ecossistema_automotivo.sql`\n- **Plano técnico e auditoria inicial:** `docs/PLANO-EVOLUCAO-LAUNCHCONTROL.md`

A aba **Actions** do GitHub mostra o resultado das verificações automáticas. O GitHub não inicia sozinho o Tomcat e o MySQL. O checkout é demonstrativo e não processa cobranças reais; a compilação não substitui testes com o servidor e o banco configurados.


## Recursos adicionados nesta linha de evolução

- **Launch AI:** o assistente existente oferece modo de pesquisa de peças e modo de triagem de sintomas. O modo de sintomas inclui orientações de segurança e um fallback guiado sem IA quando o provedor não está configurado ou indisponível. A triagem não substitui avaliação profissional.
- **CarPassport:** histórico de manutenções informado pelo cliente, com custos, quilometragem e lembretes registrados.
- **AutoCare Score:** indicadores explicáveis de planejamento (gastos registrados, itens vencidos e cobertura dos lembretes); não avalia a saúde mecânica do veículo.
- **Fitment Engine:** consulta o catálogo local de referências de compatibilidade e exibe fonte e estado de verificação. Sem registros de catálogo, não afirma compatibilidade nem incompatibilidade.
- **Oficina Transparente:** registra transições de status das ordens e apresenta a linha do tempo para o cliente. Eventos antigos não são inventados retroativamente.
- **SmartQuote:** separa peças/materiais, mão de obra e outros custos, calcula o total no backend e exibe garantia, validade e condição das peças quando informadas.

Esses recursos dependem da execução da migração SQL e da configuração do ambiente. A presença do código não comprova, por si só, funcionamento em produção; consulte o resultado de CI e valide a aplicação com MySQL e Tomcat configurados.
