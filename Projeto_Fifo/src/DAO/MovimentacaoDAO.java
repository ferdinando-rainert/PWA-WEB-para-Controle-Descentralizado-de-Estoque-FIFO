package DAO;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import Conexao.Conexao;
import entities.Movimentacao;
import entities.TipoMovimentacao;

public class MovimentacaoDAO {

	private Connection conn;

	// Construtor sem argumentos (operações simples/consultas isoladas)
	public MovimentacaoDAO() {
	}

	// Construtor com conexão (para operações dentro de uma mesma transação)
	public MovimentacaoDAO(Connection conn) {
		this.conn = conn;
	}

	private Connection getEffectiveConnection() throws SQLException {
		return (this.conn != null && !this.conn.isClosed()) ? this.conn : Conexao.getConexao();
	}

	// Registra a movimentação utilizando a conexão padrão
	public void registrar(Movimentacao m) throws SQLException {
		registrar(m, getEffectiveConnection());
	}

	// Registra a movimentação dentro de uma transação ativa
	public void registrar(Movimentacao m, Connection connection) throws SQLException {
		String sql = "INSERT INTO movimentacao (uuid_cliente, tipo, lote_id, local_origem_id, local_destino_id, "
				+ "quantidade, usuario_id, data_hora_cliente, fifo_ignorado, justificativa) "
				+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			String uuid = m.getUuidCliente() != null ? m.getUuidCliente() : UUID.randomUUID().toString();
			LocalDateTime quando = m.getDataHoraCliente() != null ? m.getDataHoraCliente() : LocalDateTime.now();

			stmt.setString(1, uuid);
			stmt.setString(2, m.getTipo().name());
			stmt.setInt(3, m.getLoteId());
			stmt.setObject(4, m.getLocalOrigemId());
			stmt.setObject(5, m.getLocalDestinoId());
			stmt.setBigDecimal(6, m.getQuantidade());
			stmt.setInt(7, m.getUsuarioId());
			stmt.setTimestamp(8, Timestamp.valueOf(quando));
			stmt.setBoolean(9, m.isFifoIgnorado());
			stmt.setString(10, m.getJustificativa());

			stmt.executeUpdate();
		}
	}

	// Busca lotes com saldo disponível para consumo FIFO aplicando a trava FOR UPDATE (bloqueio pessimista)
	public List<LoteEstoque> buscarLotesDisponiveisFIFO(int produtoId, Connection connection) throws SQLException {
		List<LoteEstoque> lotes = new ArrayList<>();
		String sql = "SELECT id, quantidade_saldo, preco_custo FROM lote "
				+ "WHERE produto_id = ? AND quantidade_saldo > 0 "
				+ "ORDER BY data_entrada ASC, id ASC FOR UPDATE";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, produtoId);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					lotes.add(new LoteEstoque(
						rs.getInt("id"),
						rs.getBigDecimal("quantidade_saldo"),
						rs.getBigDecimal("preco_custo")
					));
				}
			}
		}
		return lotes;
	}

	// Atualiza o saldo restante do lote que foi consumido pela saída FIFO
	public void atualizarSaldoLote(int loteId, BigDecimal novoSaldo, Connection connection) throws SQLException {
		String sql = "UPDATE lote SET quantidade_saldo = ? WHERE id = ?";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setBigDecimal(1, novoSaldo);
			stmt.setInt(2, loteId);
			stmt.executeUpdate();
		}
	}

	// Lista as últimas movimentações (histórico)
	public List<Movimentacao> listarUltimas(int limite) {
		List<Movimentacao> lista = new ArrayList<>();
		String sql = "SELECT * FROM movimentacao ORDER BY id DESC LIMIT ?";

		try (PreparedStatement stmt = getEffectiveConnection().prepareStatement(sql)) {
			stmt.setInt(1, limite);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					Movimentacao m = new Movimentacao(
							TipoMovimentacao.valueOf(rs.getString("tipo")),
							rs.getInt("lote_id"),
							(Integer) rs.getObject("local_origem_id"),
							(Integer) rs.getObject("local_destino_id"),
							rs.getBigDecimal("quantidade"),
							rs.getInt("usuario_id")
					);
					m.setId(rs.getLong("id"));
					m.setUuidCliente(rs.getString("uuid_cliente"));
					
					Timestamp ts = rs.getTimestamp("data_hora_cliente");
					if (ts != null) {
						m.setDataHoraCliente(ts.toLocalDateTime());
					}
					
					m.setFifoIgnorado(rs.getBoolean("fifo_ignorado"));
					m.setJustificativa(rs.getString("justificativa"));
					lista.add(m);
				}
			}
		} catch (SQLException e) {
			System.err.println("Erro ao listar as Movimentações: " + e.getMessage());
		}
		return lista;
	}

	// Classe auxiliar interna para mapear os lotes retornados no FIFO
	public static class LoteEstoque {
		private int id;
		private BigDecimal quantidadeSaldo;
		private BigDecimal precoCusto;

		public LoteEstoque(int id, BigDecimal quantidadeSaldo, BigDecimal precoCusto) {
			this.id = id;
			this.quantidadeSaldo = quantidadeSaldo;
			this.precoCusto = precoCusto;
		}

		public int getId() { return id; }
		public BigDecimal getQuantidadeSaldo() { return quantidadeSaldo; }
		public BigDecimal getPrecoCusto() { return precoCusto; }
	}
}