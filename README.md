# LaunchControl: Your Auto Service Hub

Mas vc nn fez tudo isso que eu pediTe mandei um exemplo e todo meu projeto ai preciso que crie em png/pdf e depois para rodar no lucidchart crie um perfeito bem estruturado junte tudo issoCrie uma versão FINAL do DIAGRAMA DE CASO DE USO UML do projeto “LaunchControl – Plataforma de Serviços Automotivos”.

O objetivo é produzir um Você é um arquiteto de software sênior, analista UML, UX/UI designer e desenvolvedor full-stack.

Quero que você desenvolva um SISTEMA WEB FUNCIONAL E COMPLETO chamado:

LAUNCHCONTROL
Plataforma de Serviços Automotivos

IMPORTANTE:
Não faça apenas uma demonstração visual.
Não faça telas estáticas.
Não use botões falsos.
Não deixe funcionalidades como "TODO".
Tudo que aparecer na interface deve funcionar de verdade, com navegação, validações, estados, persistência de dados e regras de negócio.

Use o diagrama de casos de uso fornecido como referência principal e respeite sua estrutura de atores, casos de uso, include, extend e generalização.

==================================================
1. OBJETIVO DO SISTEMA
==================================================

O LaunchControl será uma plataforma que conecta:

- CLIENTES
- OFICINAS
- FORNECEDORES
- ADMINISTRADORES

O cliente poderá pesquisar peças e serviços, localizar oficinas e fornecedores, solicitar e comparar orçamentos, contratar serviços, cancelar contratações e acompanhar serviços.

A oficina poderá cadastrar e gerenciar serviços, consultar solicitações de orçamento, elaborar orçamentos e atualizar o status dos serviços.

O fornecedor poderá cadastrar e gerenciar peças e participar do fluxo de solicitações/orçamentos.

O administrador poderá gerenciar usuários, oficinas, fornecedores, peças, serviços e solicitações.

==================================================
2. ATORES E GENERALIZAÇÃO UML
==================================================

Crie corretamente a seguinte hierarquia:

USUÁRIO
│
├── CLIENTE
├── OFICINA
├── FORNECEDOR
└── ADMINISTRADOR

USUÁRIO deve ser o ator genérico.

Todos os atores especializados herdam as funcionalidades comuns do USUÁRIO.

Não duplique desnecessariamente funcionalidades herdadas.

Funcionalidades comuns do USUÁRIO:

- Cadastrar-se
- Fazer login
- Fazer logout
- Recuperar senha
- Alterar senha
- Visualizar perfil
- Editar perfil
- Atualizar dados pessoais
- Gerenciar sessão
- Receber notificações

==================================================
3. CLIENTE
==================================================

O CLIENTE deve possuir:

- Pesquisar peças
- Pesquisar serviços
- Pesquisar
- Localizar oficinas e fornecedores
- Solicitar orçamento
- Consultar orçamentos
- Comparar orçamentos
- Contratar serviço
- Cancelar contratação
- Acompanhar serviço

Adicionar também, para que o sistema seja realmente funcional:

- Cadastrar veículo
- Editar veículo
- Remover veículo
- Visualizar veículos
- Selecionar veículo para solicitação
- Visualizar detalhes do serviço
- Visualizar detalhes do orçamento
- Aceitar orçamento
- Recusar orçamento
- Histórico de serviços
- Histórico de orçamentos
- Histórico de contratações
- Avaliar oficina
- Avaliar serviço
- Enviar mensagem para oficina
- Visualizar notificações

==================================================
4. PESQUISA
==================================================

O caso de uso "Pesquisar" deve funcionar como caso de uso geral.

Criar generalização corretamente:

Pesquisar peças ───────▷ Pesquisar
Pesquisar serviços ────▷ Pesquisar

A seta de generalização deve apontar para o caso de uso mais genérico "Pesquisar".

A pesquisa deve permitir:

- texto livre
- categoria
- marca
- modelo do veículo
- preço
- localização
- distância
- avaliação
- disponibilidade
- tipo de serviço

Criar resultados separados para:

- Serviços
- Oficinas
- Peças
- Fornecedores

==================================================
5. SOLICITAR ORÇAMENTO
==================================================

O CLIENTE deve conseguir:

1. Selecionar veículo
2. Informar problema/serviço desejado
3. Selecionar serviço
4. Adicionar peças necessárias, quando aplicável
5. Adicionar descrição
6. Adicionar fotos
7. Informar localização
8. Escolher oficinas/fornecedores
9. Enviar solicitação
10. Acompanhar situação da solicitação

Estados da solicitação:

- Rascunho
- Enviada
- Recebida
- Em análise
- Orçamento enviado
- Aguardando cliente
- Aceita
- Recusada
- Cancelada
- Expirada
- Concluída

==================================================
6. OFICINA
==================================================

A OFICINA deve conseguir:

- Cadastrar serviços
- Editar serviços
- Excluir/desativar serviços
- Gerenciar serviços
- Consultar solicitações de orçamento
- Filtrar solicitações
- Visualizar detalhes da solicitação
- Elaborar orçamento
- Editar orçamento antes do envio
- Enviar orçamento
- Atualizar status do serviço
- Visualizar serviços contratados
- Gerenciar agenda
- Visualizar histórico
- Conversar com cliente
- Receber notificações
- Avaliar/acompanhar desempenho

==================================================
7. ELABORAR ORÇAMENTO
==================================================

O orçamento deve possuir:

- Serviço
- Mão de obra
- Peças
- Quantidade
- Valor unitário
- Desconto
- Subtotal
- Valor total
- Prazo estimado
- Data prevista
- Observações
- Garantia
- Validade do orçamento

Calcular automaticamente:

subtotal das peças
+
mão de obra
-
descontos
=
valor total

O orçamento deve possuir estados:

- Rascunho
- Enviado
- Visualizado
- Aceito
- Recusado
- Expirado
- Cancelado

==================================================
8. FORNECEDOR
==================================================

O FORNECEDOR deve conseguir:

- Cadastrar peças
- Editar peças
- Desativar peças
- Gerenciar peças
- Consultar solicitações
- Elaborar orçamento
- Informar estoque
- Informar disponibilidade
- Informar preço
- Informar prazo de entrega
- Visualizar histórico
- Receber notificações

Cada peça deve possuir:

- Nome
- Código/SKU
- Marca
- Categoria
- Descrição
- Compatibilidade
- Veículos compatíveis
- Preço
- Estoque
- Imagens
- Fornecedor
- Status

==================================================
9. CONTRATAR SERVIÇO
==================================================

Para contratar:

1. Cliente consulta orçamento
2. Visualiza detalhes
3. Compara com outros orçamentos
4. Escolhe orçamento
5. Confirma contratação
6. Sistema registra contratação
7. Oficina recebe confirmação
8. Serviço passa para status apropriado
9. Cliente pode acompanhar o andamento

A contratação deve guardar:

- Cliente
- Veículo
- Oficina
- Serviço
- Orçamento
- Valor
- Data
- Status
- Observações
- Histórico de alterações

==================================================
10. CANCELAR CONTRATAÇÃO
==================================================

"Cancelar contratação" deve ser um:

<<extend>>

de:

"Contratar serviço"

O cancelamento somente deve aparecer quando as condições de negócio permitirem.

Solicitar motivo do cancelamento.

Registrar:

- quem cancelou
- data/hora
- motivo
- contratação afetada
- status anterior
- novo status

==================================================
11. COMPARAR ORÇAMENTOS
==================================================

"Comparar orçamentos" deve ser:

<<extend>>

de:

"Consultar orçamentos"

O cliente deve poder selecionar dois ou mais orçamentos.

Comparar:

- Oficina
- Avaliação
- Distância
- Serviço
- Peças
- Mão de obra
- Valor total
- Prazo
- Garantia
- Validade
- Observações

Criar uma tabela visual de comparação.

==================================================
12. INCLUDE
==================================================

Respeitar corretamente as relações:

Solicitar orçamento
    <<include>>
Fazer login

Consultar orçamentos
    <<include>>
Fazer login

Contratar serviço
    <<include>>
Consultar orçamentos

Acompanhar serviço
    <<include>>
Fazer login

Consultar solicitações de orçamento
    <<include>>
Fazer login

Elaborar orçamento
    <<include>>
Consultar solicitações de orçamento

Atualizar status do serviço
    <<include>>
Fazer login

Gerenciar serviços
    <<include>>
Fazer login

Gerenciar peças
    <<include>>
Fazer login

Sempre que uma funcionalidade exigir autenticação, aplicar controle real de acesso.

==================================================
13. EXTEND
==================================================

Implementar corretamente:

Comparar orçamentos
    <<extend>>
Consultar orçamentos

Cancelar contratação
    <<extend>>
Contratar serviço

Não utilizar include quando a funcionalidade for opcional.
Não utilizar extend quando a funcionalidade for obrigatória.

==================================================
14. ADMINISTRADOR
==================================================

O ADMINISTRADOR deve possuir:

- Gerenciar usuários
- Gerenciar oficinas
- Gerenciar fornecedores
- Gerenciar peças
- Gerenciar serviços
- Gerenciar solicitações
- Visualizar dashboard
- Bloquear usuário
- Desbloquear usuário
- Ativar/desativar oficina
- Ativar/desativar fornecedor
- Moderar conteúdo
- Visualizar avaliações
- Visualizar denúncias
- Visualizar logs
- Visualizar métricas

Criar painel administrativo completo.

Dashboard com:

- total de usuários
- clientes
- oficinas
- fornecedores
- serviços
- peças
- solicitações
- contratações
- orçamentos
- serviços em andamento
- cancelamentos

==================================================
15. AUTENTICAÇÃO E AUTORIZAÇÃO
==================================================

Implementar autenticação real.

Cada usuário deve possuir uma função/role:

CLIENTE
OFICINA
FORNECEDOR
ADMINISTRADOR

Criar controle de acesso baseado em função.

Um cliente NÃO pode acessar painel administrativo.

Uma oficina NÃO pode acessar gerenciamento administrativo.

Um fornecedor NÃO pode editar dados de outro fornecedor.

O administrador possui acesso administrativo.

Proteger rotas no frontend e backend.

==================================================
16. INTERFACE
==================================================

Criar interface profissional, moderna e responsiva.

Desktop:
- sidebar
- header
- dashboard
- cards
- tabelas
- filtros
- modais
- formulários

Mobile:
- menu responsivo
- cards adaptados
- tabelas responsivas
- navegação adequada

Criar identidade visual profissional para o LaunchControl.

Não utilizar aparência genérica de template.

==================================================
17. TELAS
==================================================

Criar no mínimo:

Públicas:

- Landing page
- Login
- Cadastro
- Recuperação de senha
- Pesquisa
- Resultados
- Detalhes da oficina
- Detalhes do fornecedor
- Detalhes do serviço
- Detalhes da peça

Cliente:

- Dashboard
- Perfil
- Meus veículos
- Pesquisa
- Solicitar orçamento
- Minhas solicitações
- Meus orçamentos
- Comparar orçamentos
- Minhas contratações
- Serviço em andamento
- Histórico
- Notificações
- Avaliações

Oficina:

- Dashboard
- Perfil da oficina
- Serviços
- Solicitações
- Detalhes da solicitação
- Criar orçamento
- Meus orçamentos
- Serviços contratados
- Atualizar status
- Histórico
- Notificações

Fornecedor:

- Dashboard
- Perfil
- Peças
- Cadastrar peça
- Editar peça
- Solicitações
- Orçamentos
- Histórico
- Notificações

Administrador:

- Dashboard
- Usuários
- Oficinas
- Fornecedores
- Serviços
- Peças
- Solicitações
- Contratações
- Avaliações
- Logs
- Configurações

==================================================
18. BANCO DE DADOS
==================================================

Criar banco de dados relacional bem estruturado.

Entidades mínimas:

User
Role
Client
Workshop
Supplier
Administrator
Vehicle
Service
Part
BudgetRequest
Budget
BudgetItem
Contract
ServiceStatus
Notification
Review
Message
Address
Attachment
AuditLog

Criar relacionamentos corretamente.

Não duplicar informações desnecessariamente.

Usar IDs, chaves estrangeiras, timestamps e status.

==================================================
19. API
==================================================

Criar API real para:

/auth
/users
/clients
/workshops
/suppliers
/vehicles
/services
/parts
/search
/budget-requests
/budgets
/contracts
/notifications
/reviews
/messages
/admin

Todas as operações devem possuir validação.

Implementar:

GET
POST
PUT/PATCH
DELETE

quando aplicável.

==================================================
20. VALIDAÇÃO
==================================================

Validar:

- campos obrigatórios
- e-mail
- telefone
- senha
- valores monetários
- quantidade
- estoque
- datas
- permissões
- status
- IDs relacionados

Mostrar mensagens de erro claras.

Nunca permitir dados inválidos.

==================================================
21. NOTIFICAÇÕES
==================================================

Criar sistema de notificações para:

- nova solicitação
- novo orçamento
- orçamento aceito
- orçamento recusado
- contratação
- cancelamento
- alteração de status
- nova mensagem
- avaliação
- estoque/peça quando aplicável

==================================================
22. LOGS E AUDITORIA
==================================================

Registrar ações importantes:

- login
- logout
- cadastro
- alteração de dados
- criação
- edição
- exclusão
- contratação
- cancelamento
- alteração de status
- ações administrativas

Guardar usuário, ação, data/hora e entidade afetada.

==================================================
23. REGRAS IMPORTANTES
==================================================

Não permitir:

- contratação sem orçamento válido
- orçamento sem solicitação
- alteração de orçamento já finalizado sem regra apropriada
- usuário acessar dados de outro usuário sem permissão
- oficina editar orçamento de outra oficina
- fornecedor editar peças de outro fornecedor
- cliente alterar serviço já concluído
- cancelamento fora das condições permitidas

Criar máquina de estados para solicitações, orçamentos e contratações.

==================================================
24. UML
==================================================

Além do sistema, mantenha a modelagem UML coerente.

Utilizar:

- associação para comunicação ator/caso de uso
- generalização para especialização
- <<include>> para comportamento obrigatório/reutilizado
- <<extend>> para comportamento opcional/condicional

Não desenhar relações UML aleatórias apenas para preencher o diagrama.

A direção das setas deve estar correta.

Generalização:

CLIENTE ─────▷ USUÁRIO
OFICINA ─────▷ USUÁRIO
FORNECEDOR ─▷ USUÁRIO
ADMINISTRADOR ─▷ USUÁRIO

Generalização de casos de uso:

PESQUISAR PEÇAS ─────▷ PESQUISAR
PESQUISAR SERVIÇOS ──▷ PESQUISAR

Extensões:

COMPARAR ORÇAMENTOS ──<<extend>>──> CONSULTAR ORÇAMENTOS

CANCELAR CONTRATAÇÃO ──<<extend>>──> CONTRATAR SERVIÇO

Includes devem apontar para o caso de uso incluído.

==================================================
25. QUALIDADE
==================================================

O sistema deve ser:

- responsivo
- acessível
- seguro
- organizado
- escalável
- modular
- fácil de manter
- com tratamento de erros
- com loading states
- com empty states
- com confirmação de ações destrutivas
- com feedback visual
- sem links quebrados
- sem botões sem função
- sem dados fictícios escondendo funcionalidades

==================================================
26. DADOS DE DEMONSTRAÇÃO
==================================================

Criar seed inicial para demonstração:

- usuários de cada tipo
- oficinas
- fornecedores
- veículos
- serviços
- peças
- solicitações
- orçamentos
- contratações

Deixar credenciais de teste claramente documentadas.

==================================================
27. ENTREGA
==================================================

Antes de finalizar:

1. Verifique todos os casos de uso.
2. Verifique todos os atores.
3. Verifique todas as relações include.
4. Verifique todas as relações extend.
5. Verifique todas as generalizações.
6. Verifique todas as permissões.
7. Verifique todas as telas.
8. Verifique todas as rotas.
9. Verifique todas as APIs.
10. Verifique o banco.
11. Verifique os formulários.
12. Verifique os fluxos completos.
13. Teste cadastro.
14. Teste login.
15. Teste pesquisa.
16. Teste solicitação de orçamento.
17. Teste elaboração de orçamento.
18. Teste comparação.
19. Teste contratação.
20. Teste cancelamento.
21. Teste acompanhamento.
22. Teste gerenciamento.
23. Teste administrador.

NÃO entregue uma versão incompleta.

Se alguma funcionalidade do diagrama não estiver implementada, corrija antes de finalizar.

No final, apresente uma matriz:

CASO DE USO | ATOR | TELA | API | BANCO | STATUS

E o status de todos deve ser:

IMPLEMENTADO E FUNCIONAL.

O objetivo é que o LaunchControl seja um sistema funcional de ponta a ponta, e não apenas um protótipo visual.

This project was built with [Lovable](https://lovable.dev).

## Build with Lovable

Continue developing this project in the [Lovable editor](https://lovable.dev/projects/3a8356f4-1940-4dda-aa08-f3817f0a51b2).

- **Ship faster**: describe what you want to build and Lovable handles the code.
- **Stay in sync**: every change made in Lovable is committed straight to this repository.
- **Full ownership**: this code is yours. Push to `main` on GitHub and your changes sync back into Lovable, ready for your next prompt.

## Development

Prefer working locally? You need Node.js and npm — [install with nvm](https://github.com/nvm-sh/nvm#installing-and-updating).

```sh
git clone <this-repository-url>
cd <repository-name>
npm i
npm run dev
```
