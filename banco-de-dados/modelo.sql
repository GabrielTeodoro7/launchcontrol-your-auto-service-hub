CREATE DATABASE IF NOT EXISTS launchcontrol
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE launchcontrol;

CREATE TABLE IF NOT EXISTS usuarios (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  nome VARCHAR(120) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  senha_hash VARCHAR(255) NOT NULL,
  perfil ENUM('CLIENTE','OFICINA','FORNECEDOR','ADMINISTRADOR') NOT NULL DEFAULT 'CLIENTE',
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Dados complementares das oficinas associadas a contas de usuário.
CREATE TABLE IF NOT EXISTS oficinas (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  usuario_id BIGINT NOT NULL UNIQUE,
  nome_fantasia VARCHAR(160) NOT NULL,
  descricao TEXT,
  telefone VARCHAR(30),
  endereco VARCHAR(255),
  cidade VARCHAR(100),
  estado CHAR(2),
  ativa BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- Cadastro de fornecedores de peças e componentes.
CREATE TABLE IF NOT EXISTS fornecedores (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  usuario_id BIGINT NOT NULL UNIQUE,
  nome_fantasia VARCHAR(160) NOT NULL,
  telefone VARCHAR(30),
  cidade VARCHAR(100),
  estado CHAR(2),
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

-- Catálogo de serviços disponíveis para solicitação de orçamento.
CREATE TABLE IF NOT EXISTS servicos (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  oficina_id BIGINT,
  nome VARCHAR(160) NOT NULL,
  descricao TEXT,
  preco_base DECIMAL(10,2),
  categoria VARCHAR(100),
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (oficina_id) REFERENCES oficinas(id)
);

-- Catálogo de peças, com preço, disponibilidade e fornecedor.
CREATE TABLE IF NOT EXISTS pecas (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  fornecedor_id BIGINT,
  nome VARCHAR(160) NOT NULL,
  marca VARCHAR(100),
  codigo VARCHAR(80),
  descricao TEXT,
  preco DECIMAL(10,2) NOT NULL DEFAULT 0,
  estoque INT NOT NULL DEFAULT 0,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  FOREIGN KEY (fornecedor_id) REFERENCES fornecedores(id)
);

-- Veículos vinculados ao cliente responsável pelo cadastro.
CREATE TABLE IF NOT EXISTS veiculos (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cliente_id BIGINT NOT NULL,
  marca VARCHAR(80) NOT NULL,
  modelo VARCHAR(100) NOT NULL,
  ano SMALLINT,
  placa VARCHAR(10),
  quilometragem INT,
  motorizacao VARCHAR(80),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cliente_id) REFERENCES usuarios(id)
);

-- Solicitações de serviço associadas a um cliente e a um veículo.
CREATE TABLE IF NOT EXISTS solicitacoes_orcamento (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cliente_id BIGINT NOT NULL,
  veiculo_id BIGINT,
  servico_id BIGINT,
  descricao TEXT NOT NULL,
  status ENUM('RASCUNHO','ENVIADA','EM_ANALISE','ORCAMENTO_ENVIADO','ACEITA','RECUSADA','CANCELADA','CONCLUIDA') NOT NULL DEFAULT 'ENVIADA',
  criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cliente_id) REFERENCES usuarios(id),
  FOREIGN KEY (veiculo_id) REFERENCES veiculos(id),
  FOREIGN KEY (servico_id) REFERENCES servicos(id),
  INDEX idx_solicitacoes_cliente (cliente_id, criada_em)
);

-- Propostas comerciais enviadas pelas oficinas em resposta às solicitações.
CREATE TABLE IF NOT EXISTS orcamentos (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  solicitacao_id BIGINT NOT NULL,
  oficina_id BIGINT NOT NULL,
  valor DECIMAL(10,2) NOT NULL,
  prazo_dias INT,
  observacoes TEXT,
  status ENUM('PENDENTE','ENVIADO','ACEITO','RECUSADO','CANCELADO') NOT NULL DEFAULT 'PENDENTE',
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_orcamento(id),
  FOREIGN KEY (oficina_id) REFERENCES oficinas(id),
  INDEX idx_orcamentos_solicitacao (solicitacao_id)
);

-- Ordens de serviço geradas a partir de orçamentos aceitos.
CREATE TABLE IF NOT EXISTS ordens_servico (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  codigo VARCHAR(30) NOT NULL UNIQUE,
  solicitacao_id BIGINT NOT NULL,
  orcamento_id BIGINT UNIQUE,
  status ENUM('PENDENTE','EM_ANDAMENTO','AGUARDANDO_PECA','CONCLUIDO') NOT NULL DEFAULT 'PENDENTE',
  inicio_em DATETIME,
  previsao_em DATETIME,
  concluido_em DATETIME,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_orcamento(id),
  FOREIGN KEY (orcamento_id) REFERENCES orcamentos(id),
  INDEX idx_ordens_status (status, criado_em)
);

-- Relatos de sintomas informados pelo cliente; não representam laudos técnicos.
CREATE TABLE IF NOT EXISTS diagnosticos (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cliente_id BIGINT NOT NULL,
  veiculo_descricao VARCHAR(120) NOT NULL,
  sintomas TEXT NOT NULL,
  prioridade ENUM('NORMAL','ALTA','URGENTE') NOT NULL DEFAULT 'NORMAL',
  status ENUM('RELATO_RECEBIDO','EM_ANALISE','LAUDO_VALIDADO','ENCERRADO') NOT NULL DEFAULT 'RELATO_RECEBIDO',
  hipoteses_preliminares TEXT,
  laudo_tecnico TEXT,
  responsavel_id BIGINT,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (cliente_id) REFERENCES usuarios(id),
  FOREIGN KEY (responsavel_id) REFERENCES usuarios(id),
  INDEX idx_diagnosticos_cliente (cliente_id, criado_em)
);

-- Registros auxiliares do processo de contratação.
CREATE TABLE IF NOT EXISTS pedidos (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  cliente_id BIGINT NOT NULL,
  orcamento_id BIGINT NOT NULL,
  total DECIMAL(10,2) NOT NULL,
  status ENUM('AGUARDANDO_PAGAMENTO','PAGO','CANCELADO','EXPIRADO') NOT NULL DEFAULT 'AGUARDANDO_PAGAMENTO',
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (cliente_id) REFERENCES usuarios(id),
  FOREIGN KEY (orcamento_id) REFERENCES orcamentos(id),
  UNIQUE KEY uq_pedido_orcamento_cliente (cliente_id, orcamento_id)
);

-- Registros de pagamento. O fluxo atual é demonstrativo e não processa cobranças.
CREATE TABLE IF NOT EXISTS pagamentos (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  pedido_id BIGINT NOT NULL,
  cliente_id BIGINT NOT NULL,
  metodo ENUM('PIX_DEMO','CARTAO_DEMO') NOT NULL,
  status ENUM('PENDENTE','APROVADO','EXPIRADO','CANCELADO','FALHA') NOT NULL DEFAULT 'PENDENTE',
  modo ENUM('DEMONSTRACAO','PROVEDOR') NOT NULL DEFAULT 'DEMONSTRACAO',
  referencia_provedor VARCHAR(190),
  chave_idempotencia VARCHAR(80) NOT NULL UNIQUE,
  valor DECIMAL(10,2) NOT NULL,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (pedido_id) REFERENCES pedidos(id),
  FOREIGN KEY (cliente_id) REFERENCES usuarios(id),
  INDEX idx_pagamentos_cliente (cliente_id, criado_em)
);

-- Eventos de auditoria para rastreabilidade das operações.
CREATE TABLE IF NOT EXISTS auditoria (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  usuario_id BIGINT,
  acao VARCHAR(100) NOT NULL,
  entidade VARCHAR(100) NOT NULL,
  entidade_id BIGINT,
  detalhes VARCHAR(500),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  INDEX idx_auditoria_entidade (entidade, entidade_id, criado_em)
);

CREATE INDEX idx_servicos_ativo_nome ON servicos(ativo, nome);
CREATE INDEX idx_pecas_ativo_nome ON pecas(ativo, nome);

-- Passaporte digital do veículo: registros informados pelo cliente, sem substituir notas fiscais ou laudos oficiais.
CREATE TABLE IF NOT EXISTS historico_manutencao (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  veiculo_id BIGINT NOT NULL,
  categoria VARCHAR(100) NOT NULL,
  descricao VARCHAR(2000) NOT NULL,
  data_servico DATE NOT NULL,
  quilometragem INT NOT NULL,
  custo DECIMAL(10,2) NOT NULL DEFAULT 0,
  proxima_data DATE,
  proxima_quilometragem INT,
  oficina VARCHAR(160),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (veiculo_id) REFERENCES veiculos(id),
  INDEX idx_historico_veiculo_data (veiculo_id, data_servico DESC),
  INDEX idx_historico_proxima_data (proxima_data)
);

-- Avaliações verificadas: cada cliente pode avaliar uma ordem concluída uma única vez.
CREATE TABLE IF NOT EXISTS avaliacoes_servico (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ordem_servico_id BIGINT NOT NULL UNIQUE,
  cliente_id BIGINT NOT NULL,
  oficina_id BIGINT NOT NULL,
  nota TINYINT NOT NULL,
  comentario VARCHAR(1000),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ordem_servico_id) REFERENCES ordens_servico(id),
  FOREIGN KEY (cliente_id) REFERENCES usuarios(id),
  FOREIGN KEY (oficina_id) REFERENCES oficinas(id),
  CONSTRAINT chk_avaliacao_nota CHECK (nota BETWEEN 1 AND 5),
  INDEX idx_avaliacoes_oficina (oficina_id, criado_em)
);

-- Histórico imutável das transições de status das ordens de serviço.
CREATE TABLE IF NOT EXISTS ordem_status_historico (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ordem_id BIGINT NOT NULL,
  status VARCHAR(40) NOT NULL,
  observacao VARCHAR(500),
  usuario_id BIGINT,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ordem_id) REFERENCES ordens_servico(id),
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  INDEX idx_ordem_historico_data (ordem_id, criado_em)
);

-- Referências de aplicação de peças. CONFIRMADA exige uma fonte verificável; ausência de correspondência não significa incompatibilidade.
CREATE TABLE IF NOT EXISTS compatibilidade_pecas (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  peca_id BIGINT NOT NULL,
  marca_veiculo VARCHAR(80) NOT NULL,
  modelo_veiculo VARCHAR(100) NOT NULL,
  ano_inicio SMALLINT,
  ano_fim SMALLINT,
  motorizacao VARCHAR(80),
  codigo_oem VARCHAR(80),
  status ENUM('CONFIRMADA','INCOMPATIVEL','NAO_VERIFICADA') NOT NULL DEFAULT 'NAO_VERIFICADA',
  fonte VARCHAR(180),
  referencia VARCHAR(500),
  observacoes VARCHAR(500),
  verificado_por BIGINT,
  verificado_em DATETIME,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (peca_id) REFERENCES pecas(id),
  FOREIGN KEY (verificado_por) REFERENCES usuarios(id),
  CONSTRAINT chk_compatibilidade_ano CHECK (
    (ano_inicio IS NULL OR ano_inicio BETWEEN 1950 AND 2100) AND
    (ano_fim IS NULL OR ano_fim BETWEEN 1950 AND 2100) AND
    (ano_inicio IS NULL OR ano_fim IS NULL OR ano_fim >= ano_inicio)
  ),
  INDEX idx_fitment_vehicle (marca_veiculo, modelo_veiculo, ano_inicio, ano_fim),
  INDEX idx_fitment_oem (codigo_oem),
  INDEX idx_fitment_part (peca_id, status)
);
