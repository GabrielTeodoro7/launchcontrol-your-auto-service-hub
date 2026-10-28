USE launchcontrol;

-- Serviços adicionais exigem decisão explícita do cliente antes de serem executados.
CREATE TABLE IF NOT EXISTS servicos_adicionais (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  ordem_id BIGINT NOT NULL,
  oficina_id BIGINT NOT NULL,
  cliente_id BIGINT NOT NULL,
  descricao VARCHAR(500) NOT NULL,
  valor DECIMAL(10,2) NOT NULL,
  status ENUM('PENDENTE','ACEITO','RECUSADO') NOT NULL DEFAULT 'PENDENTE',
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  respondido_em DATETIME,
  FOREIGN KEY (ordem_id) REFERENCES ordens_servico(id),
  FOREIGN KEY (oficina_id) REFERENCES oficinas(id),
  FOREIGN KEY (cliente_id) REFERENCES usuarios(id),
  CONSTRAINT chk_servico_adicional_valor CHECK (valor >= 0),
  INDEX idx_adicional_cliente_status (cliente_id, status, criado_em),
  INDEX idx_adicional_ordem (ordem_id, status)
);

-- Notificações internas associadas ao usuário autenticado.
CREATE TABLE IF NOT EXISTS notificacoes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  usuario_id BIGINT NOT NULL,
  titulo VARCHAR(140) NOT NULL,
  mensagem VARCHAR(500) NOT NULL,
  tipo VARCHAR(50) NOT NULL DEFAULT 'GERAL',
  referencia_id BIGINT,
  lida_em DATETIME,
  criada_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  INDEX idx_notificacoes_usuario_data (usuario_id, criada_em),
  INDEX idx_notificacoes_usuario_nao_lida (usuario_id, lida_em, criada_em)
);
