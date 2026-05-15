package com.example.presence_server.controllers;

import com.example.presence_server.models.*;
import com.example.presence_server.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/cours")
public class CoursController {

    @Autowired private CoursRepository            coursRepository;
    @Autowired private ProfesseurRepository        professeurRepository;
    @Autowired private CoursAssignationRepository  coursAssignationRepository;

    @GetMapping
    public List<Cours> getAllCours() {
        return coursRepository.findAll();
    }

    @GetMapping("/professeur/{profId}")
    public List<Cours> getCoursByProfesseur(@PathVariable Long profId) {
        return coursRepository.findByProfesseurs_Id(profId);
    }

    @GetMapping("/specialite/{nomSpecialite}")
    public List<Cours> getCoursBySpecialite(@PathVariable String nomSpecialite) {
        return coursRepository.findBySpecialite(nomSpecialite);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/cours/{id}/assignations
    // Retourne la liste des prof+type pour un cours donné
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/assignations")
    public ResponseEntity<List<Map<String, Object>>> getAssignations(@PathVariable Long id) {
        List<CoursAssignation> assignations = coursAssignationRepository.findByCours_Id(id);
        List<Map<String, Object>> result = new ArrayList<>();

        for (CoursAssignation ca : assignations) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id",        ca.getId());
            dto.put("profId",    ca.getProfesseur().getId());
            dto.put("profNom",   ca.getProfesseur().getNom() + " " + ca.getProfesseur().getPrenom());
            dto.put("typeSeance",ca.getTypeSeance());
            result.add(dto);
        }
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/cours/add
    // Crée un cours + ses assignations prof/type
    // Body :
    // {
    //   "libelle": "Base de données",
    //   "specialite": "SITW",
    //   "assignations": [
    //     { "profId": 1, "typeSeance": "COURS" },
    //     { "profId": 2, "typeSeance": "TD" },
    //     { "profId": 3, "typeSeance": "TD" },
    //     { "profId": 4, "typeSeance": "TP" }
    //   ]
    // }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/add")
    @Transactional
    public ResponseEntity<?> createCours(@RequestBody Map<String, Object> payload) {
        try {
            String libelle    = (String) payload.get("libelle");
            String specialite = (String) payload.getOrDefault("specialite", "");

            if (libelle == null || libelle.isBlank()) {
                return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "Le libellé est requis."));
            }

            // ── Créer le cours ────────────────────────────────────────────────
            Cours cours = new Cours();
            cours.setLibelle(libelle.trim());
            cours.setSpecialite(specialite.trim());
            Cours saved = coursRepository.save(cours);

            // ── Sauvegarder les assignations ──────────────────────────────────
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> assignations =
                (List<Map<String, Object>>) payload.getOrDefault("assignations", new ArrayList<>());

            for (Map<String, Object> a : assignations) {
                Long   profId = Long.valueOf(a.get("profId").toString());
                String type   = (String) a.get("typeSeance");
                if (type == null || type.isBlank()) continue;

                Optional<Professeur> profOpt = professeurRepository.findById(profId);
                if (profOpt.isEmpty()) continue;

                Professeur prof = profOpt.get();

                CoursAssignation ca = new CoursAssignation();
                ca.setCours(saved);
                ca.setProfesseur(prof);
                ca.setTypeSeance(type.toUpperCase());
                coursAssignationRepository.save(ca);

                // Maintenir le lien ManyToMany existant
                if (prof.getCoursEnseignes() == null) {
                    prof.setCoursEnseignes(new ArrayList<>());
                }
                boolean alreadyLinked = prof.getCoursEnseignes()
                        .stream().anyMatch(c -> c.getId().equals(saved.getId()));
                if (!alreadyLinked) {
                    prof.getCoursEnseignes().add(saved);
                    professeurRepository.save(prof);
                }
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "id",      saved.getId(),
                "message", "Module créé avec " + assignations.size() + " assignation(s)."
            ));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(
                Map.of("success", false, "message", "Erreur : " + e.getMessage()));
        }
    }

    @DeleteMapping("/delete/{id}")
    @Transactional
    public ResponseEntity<?> deleteCours(@PathVariable Long id) {
        coursAssignationRepository.deleteByCours_Id(id);
        coursRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Cours supprimé"));
    }
}