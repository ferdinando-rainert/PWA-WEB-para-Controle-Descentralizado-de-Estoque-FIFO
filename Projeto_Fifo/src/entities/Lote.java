package entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Entrada de um produto. A dataEntrada define a ordem FIFO.
public class Lote {

	private Integer id;
	private Integer produtoId;
	private String codigoLote;
	private LocalDateTime dataEntrada;
	private LocalDate dataValidade;
	private BigDecimal quantidadeInicial;

	public Lote() {
	}

	public Lote(Integer produtoId, String codigoLote, LocalDate dataValidade, BigDecimal quantidadeInicial) {
		this.produtoId = produtoId;
		this.codigoLote = codigoLote;
		this.dataValidade = dataValidade;
		this.quantidadeInicial = quantidadeInicial;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Integer getProdutoId() {
		return produtoId;
	}

	public void setProdutoId(Integer produtoId) {
		this.produtoId = produtoId;
	}

	public String getCodigoLote() {
		return codigoLote;
	}

	public void setCodigoLote(String codigoLote) {
		this.codigoLote = codigoLote;
	}

	public LocalDateTime getDataEntrada() {
		return dataEntrada;
	}

	public void setDataEntrada(LocalDateTime dataEntrada) {
		this.dataEntrada = dataEntrada;
	}

	public LocalDate getDataValidade() {
		return dataValidade;
	}

	public void setDataValidade(LocalDate dataValidade) {
		this.dataValidade = dataValidade;
	}

	public BigDecimal getQuantidadeInicial() {
		return quantidadeInicial;
	}

	public void setQuantidadeInicial(BigDecimal quantidadeInicial) {
		this.quantidadeInicial = quantidadeInicial;
	}

}
