package DAO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

import Conexao.Conexao;
import entities.Lote;

public class LoteDAO {

	// Cadastra o Lote e devolve o ID gerado.
	// Lança SQLException de propósito: é usado dentro de uma transação do EstoqueService.
	public int cadastrarLote(Lote lote) throws SQLException {

		String sql = "INSERT INTO lote (produto_id, codigo_lote, data_entrada, data_validade, quantidade_inicial) VALUES (?, ?, ?, ?, ?)";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

			LocalDateTime entrada = lote.getDataEntrada() != null ? lote.getDataEntrada() : LocalDateTime.now();

			stmt.setInt(1, lote.getProdutoId());
			stmt.setString(2, lote.getCodigoLote());
			stmt.setTimestamp(3, Timestamp.valueOf(entrada));
			stmt.setObject(4, lote.getDataValidade());
			stmt.setBigDecimal(5, lote.getQuantidadeInicial());

			stmt.executeUpdate();

			try (ResultSet rs = stmt.getGeneratedKeys()) {
				rs.next();
				return rs.getInt(1);
			}
		}
	}

	// Método para buscar Lote pelo ID (retorna null se não existir)
	public Lote buscarPorId(int id) {

		String sql = "SELECT * FROM lote WHERE id = ?";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setInt(1, id);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					Lote l = new Lote(rs.getInt("produto_id"), rs.getString("codigo_lote"),
							rs.getObject("data_validade", LocalDate.class), rs.getBigDecimal("quantidade_inicial"));
					l.setId(rs.getInt("id"));
					l.setDataEntrada(rs.getTimestamp("data_entrada").toLocalDateTime());
					return l;
				}
			}

		} catch (SQLException e) {
			System.err.println("Erro ao buscar o Lote: " + e.getMessage());
		}
		return null;
	}

}
