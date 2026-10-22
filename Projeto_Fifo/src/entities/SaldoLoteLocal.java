package entities;

import java.math.BigDecimal;

// Quanto de cada lote existe em cada local (chave composta lote + local).
public class SaldoLoteLocal {

	private Integer loteId;
	private Integer localId;
	private BigDecimal quantidade = BigDecimal.ZERO;

	public SaldoLoteLocal() {
	}

	public SaldoLoteLocal(Integer loteId, Integer localId, BigDecimal quantidade) {
		this.loteId = loteId;
		this.localId = localId;
		this.quantidade = quantidade;
	}

	public Integer getLoteId() {
		return loteId;
	}

	public void setLoteId(Integer loteId) {
		this.loteId = loteId;
	}

	public Integer getLocalId() {
		return localId;
	}

	public void setLocalId(Integer localId) {
		this.localId = localId;
	}

	public BigDecimal getQuantidade() {
		return quantidade;
	}

	public void setQuantidade(BigDecimal quantidade) {
		this.quantidade = quantidade;
	}

}
