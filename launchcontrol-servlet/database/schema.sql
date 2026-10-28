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
