package com.example.presence_server.controllers;

import com.example.presence_server.models.Etudiant;
import com.example.presence_server.models.Professeur;
import com.example.presence_server.repositories.EtudiantRepository;
import com.example.presence_server.repositories.UserRepositorie;
import com.example.presence_server.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/inscription")
public class InscriptionController {

    @Autowired
    private EtudiantRepository etudiantRepository;

    @Autowired
    private UserRepositorie userRepositorie;

    @Autowired
    private EmailService emailService;

    private static final String CODE_PROF_SECRET = "PROF2024";

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/inscription/etudiants-sans-compte
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/etudiants-sans-compte")
    public ResponseEntity<List<Map<String, Object>>> getEtudiantsSansCompte() {
        List<Etudiant> tous = etudiantRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Etudiant e : tous) {
            boolean sansMdp  = e.getMotDePasse() == null;
            boolean aUnNom   = e.getNom()    != null && !e.getNom().isBlank();
            boolean aUnPrenom = e.getPrenom() != null && !e.getPrenom().isBlank();

            if (sansMdp && aUnNom && aUnPrenom) {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id",     e.getId());
                dto.put("nom",    e.getNom());
                dto.put("prenom", e.getPrenom());
                result.add(dto);
            }
        }
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. POST /api/inscription/verifier-code-prof
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/verifier-code-prof")
    public ResponseEntity<Map<String, Object>> verifierCodeProf(
            @RequestBody Map<String, String> body) {

        Map<String, Object> result = new HashMap<>();
        String code = body.getOrDefault("code", "").trim();
        boolean valide = CODE_PROF_SECRET.equals(code);

        result.put("valide",  valide);
        result.put("message", valide ? "Code accepté." : "Code incorrect.");
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. POST /api/inscription/verifier-identite
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/verifier-identite")
    public ResponseEntity<Map<String, Object>> verifierIdentite(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> result = new HashMap<>();

        try {
            Long   id        = Long.valueOf(body.get("etudiantId").toString());
            String matricule = body.get("matricule").toString().trim();

            Optional<Etudiant> opt = etudiantRepository.findById(id);
            if (opt.isEmpty()) {
                result.put("valide",  false);
                result.put("message", "Étudiant introuvable.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
            }

            boolean valide = matricule.equals(opt.get().getMatricule());
            result.put("valide",  valide);
            result.put("message", valide ? "Identité confirmée." : "Code incorrect.");
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("valide",  false);
            result.put("message", "Requête invalide.");
            return ResponseEntity.badRequest().body(result);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. PUT /api/inscription/etudiant/{id}/finaliser
    // ─────────────────────────────────────────────────────────────────────────
    @PutMapping("/etudiant/{id}/finaliser")
    public ResponseEntity<Map<String, Object>> finaliserCompteEtudiant(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Map<String, Object> result = new HashMap<>();

        Optional<Etudiant> opt = etudiantRepository.findById(id);
        if (opt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Étudiant introuvable.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }

        Etudiant etudiant = opt.get();

        if (etudiant.getMotDePasse() != null && !etudiant.getMotDePasse().isBlank()) {
            result.put("success", false);
            result.put("message", "Ce compte a déjà été créé.");
            return ResponseEntity.badRequest().body(result);
        }

        String email      = body.get("email");
        String password   = body.get("password");
        String specialite = body.get("specialite");
        String groupe     = body.get("groupe");

        if (email == null || email.isBlank()) {
            result.put("success", false);
            result.put("message", "L'email est requis.");
            return ResponseEntity.badRequest().body(result);
        }
        if (password == null || password.length() < 6) {
            result.put("success", false);
            result.put("message", "Le mot de passe doit contenir au moins 6 caractères.");
            return ResponseEntity.badRequest().body(result);
        }
        if (userRepositorie.existsByEmail(email)) {
            result.put("success", false);
            result.put("message", "Cet email est déjà utilisé.");
            return ResponseEntity.badRequest().body(result);
        }

        etudiant.setEmail(email);
        etudiant.setMotDePasse(password);
        etudiant.setSpecialite(specialite);
        etudiant.setGroupe(groupe);
        etudiantRepository.save(etudiant);

        // 🚀 CORRECTION : ENVOI DU MAIL EN ASYNCHRONE (Ne bloque plus le frontend)
        String nomComplet = etudiant.getPrenom() + " " + etudiant.getNom();
        String mailDestinataire = etudiant.getEmail();
        
        CompletableFuture.runAsync(() -> {
            try {
                emailService.envoyerMailBienvenue(mailDestinataire, nomComplet, "Étudiant");
            } catch (Exception e) {
                System.err.println("❌ Erreur asynchrone lors de l'envoi du mail (Etudiant) : " + e.getMessage());
            }
        });

        result.put("success", true);
        result.put("message", "Compte créé avec succès.");
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. POST /api/inscription/professeur
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/professeur")
    public ResponseEntity<Map<String, Object>> inscrireProf(
            @RequestBody Map<String, String> body) {

        Map<String, Object> result = new HashMap<>();

        String nom      = body.get("nom");
        String prenom   = body.get("prenom");
        String email    = body.get("email");
        String password = body.get("password");

        if (nom == null || prenom == null || email == null || password == null
                || nom.isBlank() || prenom.isBlank() || email.isBlank() || password.isBlank()) {
            result.put("success", false);
            result.put("message", "Tous les champs sont obligatoires.");
            return ResponseEntity.badRequest().body(result);
        }

        if (userRepositorie.existsByEmail(email)) {
            result.put("success", false);
            result.put("message", "Cet email est déjà utilisé.");
            return ResponseEntity.badRequest().body(result);
        }

        Professeur prof = new Professeur();
        prof.setNom(nom);
        prof.setPrenom(prenom);
        prof.setEmail(email);
        prof.setMotDePasse(password);
        userRepositorie.save(prof);

        // 🚀 CORRECTION : ENVOI DU MAIL EN ASYNCHRONE (Ne bloque plus le frontend)
        String nomComplet = prof.getPrenom() + " " + prof.getNom();
        String mailDestinataire = prof.getEmail();
        
        CompletableFuture.runAsync(() -> {
            try {
                emailService.envoyerMailBienvenue(mailDestinataire, nomComplet, "Enseignant");
            } catch (Exception e) {
                System.err.println("❌ Erreur asynchrone lors de l'envoi du mail (Professeur) : " + e.getMessage());
            }
        });

        result.put("success", true);
        result.put("message", "Compte professeur créé avec succès.");
        return ResponseEntity.ok(result);
    }
}