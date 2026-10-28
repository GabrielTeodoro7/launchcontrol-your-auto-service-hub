# LaunchControl — Plataforma de Serviços Automotivos

O LaunchControl tem como objetivo aproximar clientes, oficinas e fornecedores, facilitando a consulta de serviços, o cadastro de veículos e a troca de solicitações e orçamentos.

## Implementação independente

A aplicação Java está na pasta `launchcontrol-servlet/`. Ela utiliza Java 17, Jakarta Servlets, JDBC, MySQL, HTML, CSS e JavaScript puro. Python é usado apenas para uma verificação estrutural auxiliar.

### Estrutura principal

- `launchcontrol-servlet/src/main/java/`: regras de negócio e endpoints HTTP implementados em Java.
- `launchcontrol-servlet/src/main/webapp/`: páginas HTML, estilos CSS e JavaScript executado no navegador.
- `launchcontrol-servlet/database/schema.sql`: criação do banco e das tabelas.
- `launchcontrol-servlet/scripts/check_project.py`: verifica se arquivos importantes e seletores esperados existem.
- `launchcontrol-servlet/README.md`: instruções detalhadas para configurar, compilar e executar a aplicação.

## Tecnologias

- Java 17 e Jakarta Servlet 6
- MySQL 8 e JDBC
- HTML5, CSS3 e JavaScript sem frameworks no frontend independente
- Maven para compilar e empacotar a aplicação como arquivo WAR

## Começar

Leia primeiro o arquivo `launchcontrol-servlet/README.md`. Ele explica os requisitos, como configurar as variáveis de ambiente do banco, como executar o script SQL e como compilar o projeto.

## Estado e limitações

A implementação independente já possui uma base de autenticação, veículos, solicitações, orçamentos, ordens de serviço, relatos de diagnóstico e pagamentos demonstrativos. Isso não significa que todos os módulos descritos no escopo acadêmico estejam completos ou prontos para produção.

O checkout é demonstrativo e não realiza cobranças reais. A aplicação ainda precisa de testes integrados em um servidor Servlet conectado a uma instância MySQL, além da revisão dos fluxos e permissões restantes. Consulte a documentação dentro de `launchcontrol-servlet/` para o estado detalhado.

## Diagramas e documentação acadêmica

Os materiais de apoio e diagramas estão em `docs/`. Antes de apresentar uma funcionalidade como concluída, confira se ela está implementada e testada, não apenas descrita em documentos.
