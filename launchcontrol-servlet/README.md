# LaunchControl — Java Servlet + MySQL

Projeto independente do LaunchControl usando somente as tecnologias autorizadas: **Java, Java Servlets, MySQL/SQL, HTML, CSS, JavaScript e Python para verificações auxiliares**. C e C++ permanecem autorizados, mas não são necessários para este sistema web.

## Stack
- Interface: HTML, CSS e JavaScript puro (sem React).
- Servidor: Java Servlet API.
- Persistência: MySQL com JDBC.
- Verificações auxiliares: Python 3, biblioteca padrão.
- Implantação: contêiner compatível com Jakarta Servlet 6.0.

Não usa React, TypeScript, Vite, TanStack Router, Spring ou outro framework de aplicação.

## Estrutura
- `src/main/java/br/com/launchcontrol/Database.java`: conexão JDBC.
- `src/main/java/br/com/launchcontrol/HealthServlet.java`: endpoint de diagnóstico.
- `src/main/java/br/com/launchcontrol/ServiceServlet.java`: consulta serviços ativos no MySQL.
- `src/main/webapp/index.html`, `css/style.css`, `js/app.js`: interface sem framework.
- `database/schema.sql`: tabelas iniciais.
- `scripts/check_project.py`: verificações estáticas simples.

## Configuração
1. Crie um banco MySQL e execute `database/schema.sql`.
2. Configure as variáveis de ambiente `LC_DB_URL`, `LC_DB_USER` e `LC_DB_PASSWORD`.
3. Gere o WAR com Maven instalado e implante em um contêiner Jakarta Servlet 6.
4. Abra a aplicação no endereço fornecido pelo contêiner.

Valores esperados de exemplo:
- `LC_DB_URL=jdbc:mysql://localhost:3306/launchcontrol`
- `LC_DB_USER=launchcontrol_app`
- `LC_DB_PASSWORD` deve ser definida localmente e não commitada.

## Limites desta entrega
Esta é a base inicial executável da aplicação, com conexão JDBC, diagnóstico e consulta de catálogo. Autenticação, autorização por perfil, fluxo completo de orçamento, upload de fotos e demais módulos precisam ser implementados e testados antes de declarar o sistema completo. Não são simulados como se já estivessem prontos.
