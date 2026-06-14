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
        if (payload.containsKey("nom"))        e.setNom(payload.get("nom"));
        if (payload.containsKey("prenom"))     e.setPrenom(payload.get("prenom"));
        if (payload.containsKey("specialite")) e.setSpecialite(payload.get("specialite"));
        if (payload.containsKey("groupe"))     e.setGroupe(payload.get("groupe"));
        if (payload.containsKey("email"))      e.setEmail(payload.get("email"));
        if (payload.containsKey("deviceId"))   e.setDeviceId(payload.get("deviceId"));
        if (payload.containsKey("matricule"))  e.setMatricule(payload.get("matricule"));
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

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/etudiants/{id}  — admin uniquement
    //
    // STRATÉGIE "SOFT ANONYMISATION" :
    // On ne supprime PAS physiquement l'étudiant de la base de données.
    // Pourquoi ? Parce que cet étudiant peut avoir des présences liées
    // à des séances passées. Si on le supprime, les enregistrements de
    // présence perdent leur référence (erreur de clé étrangère) ou
    // deviennent incohérents dans les statistiques.
    //
    // À la place, on :
    //   1. Remplace nom/prénom par "Inconnu" pour anonymiser
    //   2. Efface toutes les données personnelles (email, matricule, etc.)
    //   3. Désactive le compte (mot de passe null)
    //   4. Conserve l'enregistrement en base → les stats restent cohérentes
    //
    // Résultat côté frontend : l'étudiant n'apparaît plus dans les listes
    // car getTousAvecStats() filtre les étudiants sans nom valide.
    // ─────────────────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<?> supprimerEtudiant(
            @PathVariable Long id,
            HttpServletRequest request) {

        // Vérification du rôle admin
        String role = (String) request.getAttribute("role");
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "message", "Accès refusé. Seul un administrateur peut supprimer un étudiant."
            ));
        }

        // Vérifier que l'étudiant existe
        Optional<Etudiant> opt = etudiantRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "message", "Étudiant introuvable (ID: " + id + ")."
            ));
        }

        Etudiant e = opt.get();

        // ── Combien de présences cet étudiant a-t-il ? (pour le log) ─────────
        // On compte pour le message de confirmation dans les logs serveur
        long nbPresences = presenceRepository.findAll()
                .stream()
                .filter(p -> p.getEtudiant() != null && p.getEtudiant().getId().equals(id))
                .count();

        System.out.println("🗑️ [DELETE] Anonymisation de l'étudiant ID=" + id
                + " (" + e.getPrenom() + " " + e.getNom() + ")"
                + " — " + nbPresences + " présence(s) conservée(s) en base.");

        // ── Anonymisation : remplacement des données personnelles ─────────────
        e.setNom("Inconnu");
        e.setPrenom("Inconnu");
        e.setEmail(null);
        e.setMatricule("SUPPRIME-" + id);   // matricule unique pour éviter les doublons de contrainte unique
        e.setSpecialite(null);
        e.setGroupe(null);
        e.setDeviceId(null);
        e.setMotDePasse(null);              // désactive le compte → plus de connexion possible
        e.setCodeQrFixe(null);              // efface le QR code personnel

        etudiantRepository.save(e);

        System.out.println("✅ [DELETE] Étudiant ID=" + id + " anonymisé avec succès.");

        return ResponseEntity.ok(Map.of(
            "success", true,
            "message", "Étudiant supprimé avec succès. Ses présences passées ont été conservées pour l'intégrité des statistiques."
        ));
    }
}