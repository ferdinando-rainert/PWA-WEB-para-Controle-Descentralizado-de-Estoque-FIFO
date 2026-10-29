USE estoque_fifo;

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
