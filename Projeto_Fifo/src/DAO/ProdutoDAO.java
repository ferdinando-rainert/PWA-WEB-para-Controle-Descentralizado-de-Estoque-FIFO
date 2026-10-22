package DAO;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import Conexao.Conexao;
import entities.Produto;

public class ProdutoDAO {

	private Connection conn;

	// Construtor sem parâmetros (para operações simples/consultas isoladas)
	public ProdutoDAO() {
	}

	// Construtor com conexão (para operações dentro de uma mesma transação)
	public ProdutoDAO(Connection conn) {
		this.conn = conn;
	}

	private Connection getEffectiveConnection() throws SQLException {
		return (this.conn != null && !this.conn.isClosed()) ? this.conn : Conexao.getConexao();
	}

	// Cadastrar produto na conexão padrão
	public void cadastrarProduto(Produto p) throws SQLException {
		cadastrarProduto(p, getEffectiveConnection());
	}

	// Cadastrar produto dentro de uma transação
	public void cadastrarProduto(Produto p, Connection connection) throws SQLException {
		if (p.getEstoqueMinimo() != null && p.getEstoqueMinimo().compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Erro ao cadastrar: O estoque mínimo deve ser maior ou igual a 0.");
		}

		String sql = "INSERT INTO produto (sku, nome, unidade_medida, estoque_minimo) VALUES (?, ?, ?, ?)";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setString(1, p.getSku());
			stmt.setString(2, p.getNome());
			stmt.setString(3, p.getUnidadeMedida());
			stmt.setBigDecimal(4, p.getEstoqueMinimo());
			stmt.executeUpdate();
		}
	}

	// Atualizar produto na conexão padrão
	public void atualizarProduto(Produto p) throws SQLException {
		atualizarProduto(p, getEffectiveConnection());
	}

	// Atualizar produto dentro de uma transação
	public void atualizarProduto(Produto p, Connection connection) throws SQLException {
		if (p.getEstoqueMinimo() != null && p.getEstoqueMinimo().compareTo(BigDecimal.ZERO) < 0) {
			throw new IllegalArgumentException("Erro ao atualizar: O estoque mínimo deve ser maior ou igual a 0.");
		}

		String sql = "UPDATE produto SET sku = ?, nome = ?, unidade_medida = ?, estoque_minimo = ? WHERE id = ? AND ativo = TRUE";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setString(1, p.getSku());
			stmt.setString(2, p.getNome());
			stmt.setString(3, p.getUnidadeMedida());
			stmt.setBigDecimal(4, p.getEstoqueMinimo());
			stmt.setInt(5, p.getId());

			int linhas = stmt.executeUpdate();
			if (linhas == 0) {
				throw new SQLException("Erro: Produto inativo ou não encontrado. Alteração não permitida.");
			}
		}
	}

	// Inativar produto na conexão padrão
	public void inativarProduto(int id) throws SQLException {
		inativarProduto(id, getEffectiveConnection());
	}

	// Inativar produto dentro de uma transação
	public void inativarProduto(int id, Connection connection) throws SQLException {
		String sql = "UPDATE produto SET ativo = FALSE WHERE id = ?";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, id);
			int linhas = stmt.executeUpdate();
			if (linhas == 0) {
				throw new SQLException("Erro ao inativar: Produto não encontrado.");
			}
		}
	}

	// Buscar produto por ID na conexão padrão
	public Produto buscarPorId(int id) throws SQLException {
		return buscarPorId(id, getEffectiveConnection());
	}

	// Buscar produto por ID dentro de uma transação
	public Produto buscarPorId(int id, Connection connection) throws SQLException {
		String sql = "SELECT * FROM produto WHERE id = ?";

		try (PreparedStatement stmt = connection.prepareStatement(sql)) {
			stmt.setInt(1, id);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return mapear(rs);
				}
			}
		}
		return null;
	}

	// Listar todos os produtos
	public List<Produto> listarTodos() throws SQLException {
		return listarTodos(getEffectiveConnection());
	}

	// Listar todos os produtos dentro de uma transação
	public List<Produto> listarTodos(Connection connection) throws SQLException {
		List<Produto> lista = new ArrayList<>();
		String sql = "SELECT * FROM produto ORDER BY nome";

		try (PreparedStatement stmt = connection.prepareStatement(sql);
				ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				lista.add(mapear(rs));
			}
		}
		return lista;
	}

	private Produto mapear(ResultSet rs) throws SQLException {
		Produto p = new Produto(
			rs.getString("sku"),
			rs.getString("nome"),
			rs.getString("unidade_medida"),
			rs.getBigDecimal("estoque_minimo")
		);
		p.setId(rs.getInt("id"));
		p.setAtivo(rs.getBoolean("ativo"));
		return p;
	}
}