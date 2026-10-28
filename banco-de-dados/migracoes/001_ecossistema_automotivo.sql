-- Migração 001: recursos do ecossistema automotivo LaunchControl.
-- Executar depois de banco-de-dados/modelo.sql; todos os CREATE TABLE são aditivos e idempotentes.
USE launchcontrol;

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

-- Detalhes adicionais de proposta comercial sem alterar a tabela base de orçamentos.
CREATE TABLE IF NOT EXISTS orcamento_detalhes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  orcamento_id BIGINT NOT NULL UNIQUE,
  garantia_dias INT,
  validade_dias INT,
  condicao_pecas ENUM('NOVAS','USADAS','RECONDICIONADAS','MISTAS','NAO_INFORMADA') NOT NULL DEFAULT 'NAO_INFORMADA',
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (orcamento_id) REFERENCES orcamentos(id),
  CONSTRAINT chk_orcamento_garantia CHECK (garantia_dias IS NULL OR garantia_dias BETWEEN 0 AND 3650),
  CONSTRAINT chk_orcamento_validade CHECK (validade_dias IS NULL OR validade_dias BETWEEN 0 AND 365)
);

-- Itens normalizados para comparação de peças, mão de obra e outros custos.
CREATE TABLE IF NOT EXISTS orcamento_itens (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  orcamento_id BIGINT NOT NULL,
  tipo ENUM('PECA','MAO_DE_OBRA','OUTRO') NOT NULL,
  descricao VARCHAR(180) NOT NULL,
  quantidade DECIMAL(10,2) NOT NULL DEFAULT 1,
  valor_unitario DECIMAL(10,2) NOT NULL,
  criado_em TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (orcamento_id) REFERENCES orcamentos(id),
  CONSTRAINT chk_orcamento_item_quantidade CHECK (quantidade > 0),
  CONSTRAINT chk_orcamento_item_valor CHECK (valor_unitario >= 0),
  INDEX idx_orcamento_itens_quote (orcamento_id, tipo)
);
