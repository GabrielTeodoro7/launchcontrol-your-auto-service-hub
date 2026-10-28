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
  FOREIGN KEY (servico_id) REFERENCES servicos(id)
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
  FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_orcamento(id),
  FOREIGN KEY (oficina_id) REFERENCES oficinas(id)
);

CREATE INDEX idx_servicos_ativo_nome ON servicos(ativo, nome);
CREATE INDEX idx_pecas_ativo_nome ON pecas(ativo, nome);
CREATE INDEX idx_solicitacoes_cliente ON solicitacoes_orcamento(cliente_id, criada_em);
