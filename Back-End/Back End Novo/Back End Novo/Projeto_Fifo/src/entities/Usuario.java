package entities;

// Usuário responsável pelas movimentações.
public class Usuario {

	private Integer id;
	private String nome;
	private String email;
	private String senhaHash;
	private Perfil perfil = Perfil.OPERADOR;
	private boolean ativo = true;

	public Usuario() {
	}

	public Usuario(String nome, String email, String senhaHash, Perfil perfil) {
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
		this.perfil = perfil;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public void setSenhaHash(String senhaHash) {
		this.senhaHash = senhaHash;
	}

	public Perfil getPerfil() {
		return perfil;
	}

	public void setPerfil(Perfil perfil) {
		this.perfil = perfil;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void setAtivo(boolean ativo) {
		this.ativo = ativo;
	}

}
