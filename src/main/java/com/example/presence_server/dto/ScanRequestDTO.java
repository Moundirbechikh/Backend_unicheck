package com.example.presence_server.dto;

public class ScanRequestDTO {
    private String token;
    private Double studentLat;
    private Double studentLng;
    private String deviceId;
    // 🚀 AJOUT : L'ID de l'étudiant envoyé par le frontend
    private Long studentId;

    // Getters et Setters
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Double getStudentLat() { return studentLat; }
    public void setStudentLat(Double studentLat) { this.studentLat = studentLat; }

    public Double getStudentLng() { return studentLng; }
    public void setStudentLng(Double studentLng) { this.studentLng = studentLng; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    // 🚀 AJOUT DES NOUVEAUX MÉTHODES
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
}