package com.example.presence_server.dto;

public class ProfStatsDTO {
    private String presenceMoyenne;
    private String justifsAttente;
    private String heuresAssurees;

    public ProfStatsDTO(String presenceMoyenne, String justifsAttente, String heuresAssurees) {
        this.presenceMoyenne = presenceMoyenne;
        this.justifsAttente = justifsAttente;
        this.heuresAssurees = heuresAssurees;
    }

    // Getters
    public String getPresenceMoyenne() { return presenceMoyenne; }
    public String getJustifsAttente() { return justifsAttente; }
    public String getHeuresAssurees() { return heuresAssurees; }
}