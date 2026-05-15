package com.example.presence_server.controllers;

import com.example.presence_server.models.*;
import com.example.presence_server.repositories.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/etudiants")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class EtudiantController {

    @Autowired private EtudiantRepository  etudiantRepository;
    @Autowired private SeanceRepository    seanceRepository;
    @Autowired private PresenceRepository  presenceRepository;

    @GetMapping
    public List<Etudiant> getAllEtudiants() {
        return etudiantRepository.findAll();
    }

    @GetMapping("/me/{id}")
    public ResponseEntity<Etudiant> getProfile(@PathVariable Long id) {
        return etudiantRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/specialite/{nom}")
    public List<Etudiant> getBySpecialite(@PathVariable String nom) {
        return etudiantRepository.findBySpecialite(nom);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/etudiants/admin/tous-avec-stats
    // Retourne tous les étudiants avec leur % de présence calculé en temps réel
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/admin/tous-avec-stats")
    public ResponseEntity<List<Map<String, Object>>> getTousAvecStats() {
        List<Etudiant> tous = etudiantRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Etudiant e : tous) {
            // Ignorer les étudiants sans nom (données incomplètes)
            if (e.getNom() == null || e.getPrenom() == null) continue;

            String specialite = e.getSpecialite();
            double attendance = 0;
            long   absences   = 0;

            if (specialite != null && !specialite.isBlank()) {
                // Séances terminées de la spécialité
                List<Seance> seancesTerminees = seanceRepository
                        .findByGroupeContainingIgnoreCaseAndEstTermineeTrue(specialite);

                long totalSeances = seancesTerminees.size();

                if (totalSeances > 0) {
                    long presences = 0;
                    for (Seance s : seancesTerminees) {
                        if (presenceRepository.findByEtudiant_IdAndSeance_Id(
                                e.getId(), s.getId()).isPresent()) {
                            presences++;
                        }
                    }
                    attendance = Math.round(((double) presences / totalSeances) * 100.0);
                    absences   = Math.max(0, totalSeances - presences);
                }
            }

            Map<String, Object> dto = new HashMap<>();
            dto.put("id",          e.getId());
            dto.put("nom",         e.getNom());
            dto.put("prenom",      e.getPrenom());
            dto.put("name",        e.getPrenom() + " " + e.getNom());
            dto.put("email",       e.getEmail() != null ? e.getEmail() : "—");
            dto.put("matricule",   e.getMatricule() != null ? e.getMatricule() : "—");
            dto.put("specialite",  specialite != null ? specialite : "—");
            dto.put("groupe",      e.getGroupe() != null ? e.getGroupe() : "—");
            dto.put("deviceId",    e.getDeviceId() != null ? e.getDeviceId() : "—");
            dto.put("attendance",  (long) attendance);
            dto.put("absences",    absences);
            dto.put("compteActif", e.getMotDePasse() != null && !e.getMotDePasse().isBlank());
            result.add(dto);
        }

        // Trier par nom
        result.sort(Comparator.comparing(m -> m.get("name").toString()));
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/etudiants/{id}  — admin uniquement
    // ─────────────────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEtudiantByAdmin(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            HttpServletRequest request) {

        String role = (String) request.getAttribute("role");
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "message", "Accès refusé."
            ));
        }

        Optional<Etudiant> opt = etudiantRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("success", false, "message", "Introuvable."));

        Etudiant e = opt.get();
        if (payload.containsKey("nom"))       e.setNom(payload.get("nom"));
        if (payload.containsKey("prenom"))    e.setPrenom(payload.get("prenom"));
        if (payload.containsKey("specialite")) e.setSpecialite(payload.get("specialite"));
        if (payload.containsKey("groupe"))    e.setGroupe(payload.get("groupe"));
        if (payload.containsKey("email"))     e.setEmail(payload.get("email"));
        if (payload.containsKey("deviceId"))  e.setDeviceId(payload.get("deviceId"));
        if (payload.containsKey("matricule")) e.setMatricule(payload.get("matricule"));
        etudiantRepository.save(e);

        return ResponseEntity.ok(Map.of("success", true, "message", "Étudiant mis à jour."));
    }

    @PutMapping("/{id}/update-password")
    public ResponseEntity<?> updatePassword(@PathVariable Long id, @RequestBody Map<String, String> passwords) {
        Optional<Etudiant> opt = etudiantRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        String newPassword = passwords.get("newPassword");
        if (newPassword == null || newPassword.isBlank())
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Mot de passe requis."));

        Etudiant e = opt.get();
        e.setMotDePasse(newPassword);
        etudiantRepository.save(e);
        return ResponseEntity.ok(Map.of("success", true, "message", "Mot de passe mis à jour."));
    }
}