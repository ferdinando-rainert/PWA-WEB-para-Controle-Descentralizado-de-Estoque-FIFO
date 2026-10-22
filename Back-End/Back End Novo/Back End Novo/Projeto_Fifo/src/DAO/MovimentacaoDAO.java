package DAO;

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

	// Registra a movimentação. Lança SQLException de propósito (usado dentro de transação).
	public void registrar(Movimentacao m) throws SQLException {

		String sql = "INSERT INTO movimentacao (uuid_cliente, tipo, lote_id, local_origem_id, local_destino_id, "
				+ "quantidade, usuario_id, data_hora_cliente, fifo_ignorado, justificativa) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

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

	// Lista as últimas movimentações (histórico)
	public List<Movimentacao> listarUltimas(int limite) {

		List<Movimentacao> lista = new ArrayList<>();
		String sql = "SELECT * FROM movimentacao ORDER BY id DESC LIMIT ?";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setInt(1, limite);

			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					Movimentacao m = new Movimentacao(TipoMovimentacao.valueOf(rs.getString("tipo")),
							rs.getInt("lote_id"), (Integer) rs.getObject("local_origem_id"),
							(Integer) rs.getObject("local_destino_id"), rs.getBigDecimal("quantidade"),
							rs.getInt("usuario_id"));
					m.setId(rs.getLong("id"));
					m.setUuidCliente(rs.getString("uuid_cliente"));
					m.setDataHoraCliente(rs.getTimestamp("data_hora_cliente").toLocalDateTime());
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

}
