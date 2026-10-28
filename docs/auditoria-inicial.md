# Auditoria inicial — LaunchControl

Data da auditoria: 2026-10-27
Branch de trabalho: `work/launchcontrol-reconstruction`

## Escopo e método
Auditoria estática do conteúdo publicado no repositório GitHub. Não houve execução local do projeto, conexão com MySQL, nem testes de integração nesta etapa. A existência de um arquivo não é considerada prova de que uma funcionalidade está operacional.

## Inventário observado
- Frontend: React 19, TypeScript, Vite e TanStack Start/Router.
- UI: conjunto de componentes reutilizáveis em `src/components/ui/`, Tailwind CSS 4 e Lucide React.
- Scripts declarados: `dev`, `build`, `lint`, `test` e `test:watch`.
- Teste existente: `src/test/app-routing.test.tsx`, que verifica apenas se a rota `/` corresponde a uma página.
- Entrada visual: `src/routes/index.tsx` continha somente um placeholder de página em branco do Lovable.
- Metadados: `src/routes/__root.tsx` ainda usava título/descrição genéricos do Lovable e idioma HTML inglês.
- Não foram encontrados, no inventário do repositório publicado, diretórios/arquivos de backend Java Servlet, configuração JDBC, scripts SQL do LaunchControl, diagramas UML ou integração de pagamentos.
- `roadmap.md` registra uma entrega standalone separada (23 tabelas MySQL, API Java Servlet, telas HTML/CSS/JS e verificações anteriores) como trabalho salvo fora deste repositório; as etapas seguintes continuam marcadas como pendentes. Esses registros não comprovam que os artefatos externos estejam acessíveis nesta sessão nem que possam ser executados agora.
- `AGENTS.md` explicita que o app gerenciado pelo Lovable deve permanecer em TanStack Start e que a entrega Java Servlet/MySQL deve ser mantida como projeto standalone separado, pois o runtime hospedado não executa um container Servlet.

## Estado por requisito
| Área | Evidência encontrada | Estado da auditoria |
| --- | --- | --- |
| Página inicial | Placeholder genérico | Ausente no código publicado antes desta branch |
| Navegação e componentes | Infraestrutura TanStack e componentes UI presentes | Parcial; fluxo de negócio não demonstrado |
| Cadastro/login e perfis | Nenhuma rota/implementação de negócio localizada no inventário | Não comprovado |
| Veículos, busca, solicitações e orçamentos | Nenhuma implementação de negócio localizada no inventário | Não comprovado |
| Diagnóstico e laudos | Nenhuma implementação localizada no inventário | Não comprovado |
| Checkout e pagamentos | Nenhuma integração localizada no inventário | Não comprovado |
| Bloqueio de edição de serviço concluído | Nenhuma regra de domínio/API localizada no inventário | Não comprovado |
| Backend Java Servlet | Não consta no inventário do repositório | Separado/pendente de localizar |
| Banco MySQL | Não constam scripts SQL no inventário do repositório | Separado/pendente de localizar |
| UML e exports | Não constam diagramas no inventário do repositório | Pendente de localizar |
| Testes | Há teste de roteamento, sem evidência de testes de domínio/integrados | Parcial |
| Build/lint | Scripts existem, mas não foram executados neste ambiente | Pendente |

## Riscos e decisões
1. Não afirmar que o sistema completo está implementado a partir do README ou do roadmap.
2. Preservar o app TanStack/Lovable e não introduzir Java Servlet no runtime hospedado.
3. Manter backend Java Servlet + MySQL em entrega standalone, com instruções de execução e configuração externa.
4. Não simular pagamento aprovado nem persistência real sem provedor/backend disponível.
5. Criar mudanças nesta branch de trabalho, sem sobrescrever diretamente a branch principal.
6. Obter os ZIPs, PDF de design e artefatos standalone citados no prompt antes de alegar preservação fiel do design ou reaproveitamento dos 23 objetos de banco.

## Próximos passos
- Remover o placeholder e criar uma entrada LaunchControl responsiva e honesta sobre o estado demonstrativo.
- Atualizar metadados/idioma.
- Localizar e integrar os artefatos standalone anteriores quando estiverem disponíveis.
- Implementar os fluxos de domínio e documentar claramente o que for real, demonstrativo ou bloqueado.
- Executar build, lint e testes quando houver ambiente de execução; registrar os resultados sem inferi-los.
