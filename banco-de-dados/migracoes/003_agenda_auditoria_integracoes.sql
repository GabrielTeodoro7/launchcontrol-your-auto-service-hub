USE launchcontrol;

CREATE TABLE IF NOT EXISTS configuracao_agenda_oficina (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  oficina_id BIGINT NOT NULL,
  dia_semana TINYINT NOT NULL,
  hora_inicio TIME NOT NULL,
  hora_fim TIME NOT NULL,
  capacidade INT NOT NULL DEFAULT 1,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT uq_agenda_oficina_dia UNIQUE (oficina_id, dia_semana),
  CONSTRAINT chk_agenda_dia CHECK (dia_semana BETWEEN 1 AND 7),
  CONSTRAINT chk_agenda_capacidade CHECK (capacidade BETWEEN 1 AND 50),
  CONSTRAINT chk_agenda_horario CHECK (hora_fim > hora_inicio),
  FOREIGN KEY (oficina_id) REFERENCES oficinas(id)
);

CREATE TABLE IF NOT EXISTS agendamentos (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  codigo VARCHAR(30) NOT NULL UNIQUE,
  oficina_id BIGINT NOT NULL,
  cliente_id BIGINT NOT NULL,
  veiculo_id BIGINT NOT NULL,
  inicio_em DATETIME NOT NULL,
  fim_em DATETIME NOT NULL,
  status ENUM('AGENDADO','CONFIRMADO','CANCELADO','CONCLUIDO','NAO_COMPARECEU') NOT NULL DEFAULT 'AGENDADO',
  observacoes VARCHAR(500),
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT chk_agendamento_intervalo CHECK (fim_em > inicio_em),
  FOREIGN KEY (oficina_id) REFERENCES oficinas(id),
  FOREIGN KEY (cliente_id) REFERENCES usuarios(id),
  FOREIGN KEY (veiculo_id) REFERENCES veiculos(id),
  INDEX idx_agenda_oficina_inicio (oficina_id, inicio_em, fim_em, status),
  INDEX idx_agenda_cliente (cliente_id, inicio_em)
);

CREATE TABLE IF NOT EXISTS preferencias_notificacao (
  usuario_id BIGINT PRIMARY KEY,
  email_ativo BOOLEAN NOT NULL DEFAULT TRUE,
  whatsapp_ativo BOOLEAN NOT NULL DEFAULT FALSE,
  whatsapp_numero VARCHAR(20),
  atualizado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE IF NOT EXISTS notificacao_entregas (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  notificacao_id BIGINT NOT NULL,
  usuario_id BIGINT NOT NULL,
  canal ENUM('EMAIL','WHATSAPP') NOT NULL,
  status ENUM('PENDENTE','ENVIADA','AGUARDANDO_CONFIGURACAO','FALHA') NOT NULL DEFAULT 'PENDENTE',
  tentativas INT NOT NULL DEFAULT 0,
  proxima_tentativa_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ultima_resposta VARCHAR(500),
  criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  enviada_em DATETIME,
  FOREIGN KEY (notificacao_id) REFERENCES notificacoes(id),
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  UNIQUE KEY uq_entrega_notificacao_canal (notificacao_id, canal),
  INDEX idx_entrega_pendente (status, proxima_tentativa_em)
);
