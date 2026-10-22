package entities;

import java.math.BigDecimal;

// Produto vendido/armazenado. O estoque mínimo define o alerta de reposição.
public class Produto {

	private Integer id;
	private String sku;
	private String nome;
	private String unidadeMedida;
	private BigDecimal estoqueMinimo = BigDecimal.ZERO;
	private boolean ativo = true;

	public Produto() {
	}

	public Produto(String sku, String nome, String unidadeMedida, BigDecimal estoqueMinimo) {
		this.sku = sku;
		this.nome = nome;
		this.unidadeMedida = unidadeMedida;
		this.estoqueMinimo = estoqueMinimo;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getSku() {
		return sku;
	}

	public void setSku(String sku) {
		this.sku = sku;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getUnidadeMedida() {
		return unidadeMedida;
	}

	public void setUnidadeMedida(String unidadeMedida) {
		this.unidadeMedida = unidadeMedida;
	}

	public BigDecimal getEstoqueMinimo() {
		return estoqueMinimo;
	}

	public void setEstoqueMinimo(BigDecimal estoqueMinimo) {
		this.estoqueMinimo = estoqueMinimo;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void setAtivo(boolean ativo) {
		this.ativo = ativo;
	}

}
