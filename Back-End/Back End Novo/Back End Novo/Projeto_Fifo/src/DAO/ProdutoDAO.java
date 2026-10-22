package DAO;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import Conexao.Conexao;
import entities.Produto;

public class ProdutoDAO {

	// Método para cadastrar Produto com validação da RN P03
	public void cadastrarProduto(Produto p) {

		// RN P03: Valida se o estoque mínimo é negativo
		if (p.getEstoqueMinimo() != null && p.getEstoqueMinimo().compareTo(BigDecimal.ZERO) < 0) {
			System.err.println("Erro ao cadastrar: O estoque mínimo deve ser maior ou igual a 0.");
			return;
		}

		String sql = "INSERT INTO produto (sku, nome, unidade_medida, estoque_minimo) VALUES (?, ?, ?, ?)";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setString(1, p.getSku());
			stmt.setString(2, p.getNome());
			stmt.setString(3, p.getUnidadeMedida());
			stmt.setBigDecimal(4, p.getEstoqueMinimo());

			stmt.executeUpdate();
			System.out.println("Produto cadastrado!");

		} catch (SQLException e) {
			System.err.println("Erro ao cadastrar o Produto: " + e.getMessage());
		}
	}

	// Método para atualizar Produto pelo ID com validação da RN P03
	public void atualizarProduto(Produto p) {

		if (p.getEstoqueMinimo() != null && p.getEstoqueMinimo().compareTo(BigDecimal.ZERO) < 0) {
			System.err.println("Erro ao atualizar: O estoque mínimo deve ser maior ou igual a 0.");
			return;
		}

		// A instrução só afetará linhas onde id bate E ativo seja TRUE
		String sql = "UPDATE produto SET sku = ?, nome = ?, unidade_medida = ?, estoque_minimo = ? WHERE id = ? AND ativo = TRUE";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setString(1, p.getSku());
			stmt.setString(2, p.getNome());
			stmt.setString(3, p.getUnidadeMedida());
			stmt.setBigDecimal(4, p.getEstoqueMinimo());
			stmt.setInt(5, p.getId());

			int linhas = stmt.executeUpdate();
			if (linhas > 0) {
				System.out.println("Produto atualizado!");
			} else {
				System.err.println("Erro: Produto inativo ou não encontrado. Alteração não permitida.");
			}

		} catch (SQLException e) {
			System.err.println("Erro ao atualizar o Produto: " + e.getMessage());
		}
	}

	// Método para inativar Produto (o histórico não é apagado)
	public void inativarProduto(int id) {

		String sql = "UPDATE produto SET ativo = FALSE WHERE id = ?";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setInt(1, id);
			int linhas = stmt.executeUpdate();
			System.out.println(linhas > 0 ? "Produto inativado!" : "Produto não encontrado.");

		} catch (SQLException e) {
			System.err.println("Erro ao inativar o Produto: " + e.getMessage());
		}
	}

	// Método para buscar Produto pelo ID (retorna null se não existir)
	public Produto buscarPorId(int id) {

		String sql = "SELECT * FROM produto WHERE id = ?";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql)) {

			stmt.setInt(1, id);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return mapear(rs);
				}
			}

		} catch (SQLException e) {
			System.err.println("Erro ao buscar o Produto: " + e.getMessage());
		}
		return null;
	}

	// Método para listar todos os Produtos
	public List<Produto> listarTodos() {

		List<Produto> lista = new ArrayList<>();
		String sql = "SELECT * FROM produto ORDER BY nome";

		try (PreparedStatement stmt = Conexao.getConexao().prepareStatement(sql);
				ResultSet rs = stmt.executeQuery()) {

			while (rs.next()) {
				lista.add(mapear(rs));
			}

		} catch (SQLException e) {
			System.err.println("Erro ao listar os Produtos: " + e.getMessage());
		}
		return lista;
	}

	private Produto mapear(ResultSet rs) throws SQLException {
		Produto p = new Produto(rs.getString("sku"), rs.getString("nome"), rs.getString("unidade_medida"),
				rs.getBigDecimal("estoque_minimo"));
		p.setId(rs.getInt("id"));
		p.setAtivo(rs.getBoolean("ativo"));
		return p;
	}

}