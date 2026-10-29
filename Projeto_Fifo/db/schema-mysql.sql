CREATE DATABASE IF NOT EXISTS estoque_fifo
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE estoque_fifo;

CREATE TABLE IF NOT EXISTS produto (
  id INT NOT NULL AUTO_INCREMENT,
  sku VARCHAR(80) NOT NULL,
  nome VARCHAR(180) NOT NULL,
  unidade_medida VARCHAR(20) NOT NULL,
  estoque_minimo DECIMAL(15,3) NOT NULL DEFAULT 0,
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uq_produto_sku (sku),
  CHECK (estoque_minimo >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS local_estoque (
  id INT NOT NULL AUTO_INCREMENT,
  nome VARCHAR(120) NOT NULL,
  descricao VARCHAR(500) NOT NULL DEFAULT '',
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uq_local_estoque_nome (nome)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS usuario (
  id INT NOT NULL AUTO_INCREMENT,
  nome VARCHAR(160) NOT NULL,
  email VARCHAR(254) NOT NULL,
  senha_hash VARCHAR(255) NOT NULL,
  perfil VARCHAR(20) NOT NULL DEFAULT 'OPERADOR',
  ativo BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uq_usuario_email (email),
  CHECK (perfil IN ('ADMIN', 'GESTOR', 'OPERADOR'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS lote (
  id INT NOT NULL AUTO_INCREMENT,
  produto_id INT NOT NULL,
  codigo_lote VARCHAR(100) NOT NULL,
  data_entrada DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  data_validade DATE NULL,
  quantidade_inicial DECIMAL(15,3) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_lote_fifo (produto_id, data_entrada, id),
  CONSTRAINT fk_lote_produto FOREIGN KEY (produto_id) REFERENCES produto (id),
  CHECK (quantidade_inicial > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS saldo_lote_local (
  lote_id INT NOT NULL,
  local_id INT NOT NULL,
  quantidade DECIMAL(15,3) NOT NULL DEFAULT 0,
  PRIMARY KEY (lote_id, local_id),
  KEY ix_saldo_local (local_id, lote_id),
  CONSTRAINT fk_saldo_lote FOREIGN KEY (lote_id) REFERENCES lote (id),
  CONSTRAINT fk_saldo_local FOREIGN KEY (local_id) REFERENCES local_estoque (id),
  CHECK (quantidade >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS movimentacao (
  id BIGINT NOT NULL AUTO_INCREMENT,
  uuid_cliente CHAR(36) NOT NULL,
  tipo VARCHAR(20) NOT NULL,
  lote_id INT NOT NULL,
  local_origem_id INT NULL,
  local_destino_id INT NULL,
  quantidade DECIMAL(15,3) NOT NULL,
  usuario_id INT NOT NULL,
  data_hora_cliente DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fifo_ignorado BOOLEAN NOT NULL DEFAULT FALSE,
  justificativa VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_movimentacao_uuid_cliente (uuid_cliente),
  KEY ix_movimentacao_data (id),
  CONSTRAINT fk_movimentacao_lote FOREIGN KEY (lote_id) REFERENCES lote (id),
  CONSTRAINT fk_movimentacao_origem FOREIGN KEY (local_origem_id) REFERENCES local_estoque (id),
  CONSTRAINT fk_movimentacao_destino FOREIGN KEY (local_destino_id) REFERENCES local_estoque (id),
  CONSTRAINT fk_movimentacao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id),
  CHECK (tipo IN ('ENTRADA', 'SAIDA', 'TRANSFERENCIA', 'AJUSTE')),
  CHECK (quantidade > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sessao_usuario (
  token_hash CHAR(64) NOT NULL,
  usuario_id INT NOT NULL,
  csrf_token CHAR(64) NOT NULL,
  criado_em DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expira_em DATETIME NOT NULL,
  PRIMARY KEY (token_hash),
  KEY ix_sessao_usuario_expira (expira_em),
  CONSTRAINT fk_sessao_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE
) ENGINE=InnoDB;
