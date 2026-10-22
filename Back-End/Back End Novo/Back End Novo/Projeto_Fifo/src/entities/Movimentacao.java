package entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Registro de entrada, saída, transferência ou ajuste.
public class Movimentacao {

	private Long id;
	private String uuidCliente;
	private TipoMovimentacao tipo;
	private Integer loteId;
	private Integer localOrigemId;
	private Integer localDestinoId;
	private BigDecimal quantidade;
	private Integer usuarioId;
	private LocalDateTime dataHoraCliente;
	private boolean fifoIgnorado = false;
	private String justificativa;

	public Movimentacao() {
	}

	public Movimentacao(TipoMovimentacao tipo, Integer loteId, Integer localOrigemId, Integer localDestinoId, BigDecimal quantidade, Integer usuarioId) {
		this.tipo = tipo;
		this.loteId = loteId;
		this.localOrigemId = localOrigemId;
		this.localDestinoId = localDestinoId;
		this.quantidade = quantidade;
		this.usuarioId = usuarioId;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUuidCliente() {
		return uuidCliente;
	}

	public void setUuidCliente(String uuidCliente) {
		this.uuidCliente = uuidCliente;
	}

	public TipoMovimentacao getTipo() {
		return tipo;
	}

	public void setTipo(TipoMovimentacao tipo) {
		this.tipo = tipo;
	}

	public Integer getLoteId() {
		return loteId;
	}

	public void setLoteId(Integer loteId) {
		this.loteId = loteId;
	}

	public Integer getLocalOrigemId() {
		return localOrigemId;
	}

	public void setLocalOrigemId(Integer localOrigemId) {
		this.localOrigemId = localOrigemId;
	}

	public Integer getLocalDestinoId() {
		return localDestinoId;
	}

	public void setLocalDestinoId(Integer localDestinoId) {
		this.localDestinoId = localDestinoId;
	}

	public BigDecimal getQuantidade() {
		return quantidade;
	}

	public void setQuantidade(BigDecimal quantidade) {
		this.quantidade = quantidade;
	}

	public Integer getUsuarioId() {
		return usuarioId;
	}

	public void setUsuarioId(Integer usuarioId) {
		this.usuarioId = usuarioId;
	}

	public LocalDateTime getDataHoraCliente() {
		return dataHoraCliente;
	}

	public void setDataHoraCliente(LocalDateTime dataHoraCliente) {
		this.dataHoraCliente = dataHoraCliente;
	}

	public boolean isFifoIgnorado() {
		return fifoIgnorado;
	}

	public void setFifoIgnorado(boolean fifoIgnorado) {
		this.fifoIgnorado = fifoIgnorado;
	}

	public String getJustificativa() {
		return justificativa;
	}

	public void setJustificativa(String justificativa) {
		this.justificativa = justificativa;
	}

}
