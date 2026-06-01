package com.example.presence_server.controllers;
import com.example.presence_server.models.Utilisateur;
import com.example.presence_server.repositories.UserRepositorie;
import com.example.presence_server.models.Etudiant;
import com.example.presence_server.models.Seance;
import com.example.presence_server.repositories.EtudiantRepository;
import com.example.presence_server.repositories.PresenceRepository;
import com.example.presence_server.repositories.ProfesseurRepository;
import com.example.presence_server.repositories.SeanceRepository;
import com.example.presence_server.repositories.JustificatifRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
// Ajouter ces imports en haut :

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @Autowired private EtudiantRepository etudiantRepository;
    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private SeanceRepository seanceRepository;
    @Autowired private PresenceRepository presenceRepository;
    @Autowired private JustificatifRepository justificatifRepository;
// Ajouter ce @Autowired dans la classe :
@Autowired private UserRepositorie userRepositorie;

    // Helper réutilisé depuis SeanceController — copier la même logique
    private List<Etudiant> getEtudiantsConcerneParSeance(Seance seance) {
        String groupeSeance = seance.getGroupe();
        if (groupeSeance == null || groupeSeance.isBlank()) return new ArrayList<>();
        List<Etudiant> tousLesEtudiants = etudiantRepository.findAll();
        List<Etudiant> result = new ArrayList<>();
        String gl = groupeSeance.toLowerCase();
        for (Etudiant e : tousLesEtudiants) {
            String spe = e.getSpecialite() != null ? e.getSpecialite().toLowerCase() : "";
            String grp = e.getGroupe() != null ? e.getGroupe().toLowerCase() : "";
            if (!spe.isBlank() && (gl.contains(spe) || spe.contains(gl))) {
                boolean matchGrp = grp.isBlank() || gl.contains(grp);
                if (matchGrp) result.add(e);
            }
        }
        return result;
    }




// ─────────────────────────────────────────────────────────────────────────
// PUT /api/admin/dashboard/update-password
// ─────────────────────────────────────────────────────────────────────────
@PutMapping("/update-password")
public ResponseEntity<?> updatePassword(
        @RequestBody Map<String, String> body,
        jakarta.servlet.http.HttpServletRequest request) {

    String role = (String) request.getAttribute("role");
    if (!"admin".equals(role)) {
        return ResponseEntity.status(403).body(Map.of("success", false, "message", "Accès refusé."));
    }

    Object userIdAttr = request.getAttribute("userId");
    if (userIdAttr == null) {
        return ResponseEntity.status(401).body(Map.of("success", false, "message", "Non authentifié."));
    }

    Long userId = Long.valueOf(userIdAttr.toString());
    String newPassword = body.get("newPassword");

    if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Mot de passe trop court (min. 6 caractères)."));
    }

    Optional<Utilisateur> opt = userRepositorie.findById(userId);
    if (opt.isEmpty()) {
        return ResponseEntity.status(404).body(Map.of("success", false, "message", "Utilisateur introuvable."));
    }

    Utilisateur admin = opt.get();
    admin.setMotDePasse(newPassword);
    userRepositorie.save(admin);

    System.out.println("🔐 [ADMIN] Mot de passe mis à jour pour userId=" + userId);
    return ResponseEntity.ok(Map.of("success", true, "message", "Mot de passe mis à jour avec succès."));
}

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/dashboard/insights
    // Calcule les 3 types d'insights en temps réel
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/dashboard/insights")
    public ResponseEntity<List<Map<String, Object>>> getInsights() {
        List<Map<String, Object>> insights = new ArrayList<>();

        LocalDateTime now         = LocalDateTime.now();
        LocalDateTime debutSemaine = now.minusDays(7);
        LocalDateTime debutMois   = now.minusDays(30);

        // ── 1. GROUPES : détection hausse / baisse cette semaine ─────────────
        try {
            List<Seance> seancesSemaine = seanceRepository
                    .findByDateHeureDebutBetween(debutSemaine, now).stream()
                    .filter(Seance::isEstTerminee)
                    .collect(Collectors.toList());

            // Grouper les séances par groupe
            Map<String, List<Seance>> parGroupe = new LinkedHashMap<>();
            for (Seance s : seancesSemaine) {
                if (s.getGroupe() == null) continue;
                parGroupe.computeIfAbsent(s.getGroupe(), k -> new ArrayList<>()).add(s);
            }

            double globalMin = 100.0;
            double globalMax = 0.0;
            String groupeMin = null, groupeMax = null;

            for (Map.Entry<String, List<Seance>> entry : parGroupe.entrySet()) {
                List<Seance> seances = entry.getValue();
                if (seances.isEmpty()) continue;

                // Compter étudiants attendus pour ce groupe
                Seance firstS = seances.get(0);
                List<Etudiant> attendus = getEtudiantsConcerneParSeance(firstS);
                if (attendus.isEmpty()) continue;

                long totalAttendus = (long) attendus.size() * seances.size();
                long totalPresents = 0;
                for (Seance s : seances) {
                    totalPresents += presenceRepository.findBySeance_Id(s.getId())
                            .stream().filter(p -> "PRESENT".equals(p.getStatutPresence())).count();
                }

                double pct = totalAttendus > 0
                        ? Math.round(((double) totalPresents / totalAttendus) * 100.0) : 0;

                if (pct < globalMin) { globalMin = pct; groupeMin = entry.getKey(); }
                if (pct > globalMax) { globalMax = pct; groupeMax = entry.getKey(); }
            }

            // Insight baisse
            if (groupeMin != null) {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("type",   "baisse");
                i.put("icon",   "TrendingDown");
                i.put("titre",  "Baisse de présence — " + groupeMin);
                i.put("body",   "Le groupe " + groupeMin + " affiche " + (long) globalMin + "% de présence cette semaine.");
                i.put("pct",    (long) globalMin);
                insights.add(i);
            }

            // Insight hausse
            if (groupeMax != null) {
                Map<String, Object> i = new LinkedHashMap<>();
                i.put("type",   "hausse");
                i.put("icon",   "TrendingUp");
                i.put("titre",  "Meilleur groupe — " + groupeMax);
                i.put("body",   "Le groupe " + groupeMax + " atteint " + (long) globalMax + "% de présence cette semaine.");
                i.put("pct",    (long) globalMax);
                insights.add(i);
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Insights groupes : " + ex.getMessage());
        }

        // ── 2. SPÉCIALITÉS : ce mois ──────────────────────────────────────────
        try {
            List<Seance> seancesMois = seanceRepository
                    .findByDateHeureDebutBetween(debutMois, now).stream()
                    .filter(Seance::isEstTerminee)
                    .collect(Collectors.toList());

            Map<String, long[]> parSpe = new LinkedHashMap<>(); // [totalAttendus, totalPresents]

            for (Seance s : seancesMois) {
                if (s.getCours() == null || s.getCours().getSpecialite() == null) continue;
                String spe = s.getCours().getSpecialite();
                List<Etudiant> attendus = getEtudiantsConcerneParSeance(s);
                long presents = presenceRepository.findBySeance_Id(s.getId()).stream()
                        .filter(p -> "PRESENT".equals(p.getStatutPresence())).count();

                parSpe.computeIfAbsent(spe, k -> new long[]{0L, 0L});
                parSpe.get(spe)[0] += attendus.size();
                parSpe.get(spe)[1] += presents;
            }

            for (Map.Entry<String, long[]> entry : parSpe.entrySet()) {
                long totalA = entry.getValue()[0];
                long totalP = entry.getValue()[1];
                if (totalA == 0) continue;
                long pct = Math.round(((double) totalP / totalA) * 100);

                Map<String, Object> i = new LinkedHashMap<>();
                if (pct >= 80) {
                    i.put("type",  "hausse_spe");
                    i.put("icon",  "TrendingUp");
                    i.put("titre", entry.getKey() + " en hausse ce mois");
                    i.put("body",  "La spécialité " + entry.getKey() + " atteint " + pct + "% de présence ce mois-ci.");
                } else {
                    i.put("type",  "baisse_spe");
                    i.put("icon",  "TrendingDown");
                    i.put("titre", entry.getKey() + " — attention requise");
                    i.put("body",  "La spécialité " + entry.getKey() + " affiche seulement " + pct + "% ce mois-ci.");
                }
                i.put("pct", pct);
                insights.add(i);
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Insights spécialités : " + ex.getMessage());
        }

        // ── 3. JUSTIFICATIFS ──────────────────────────────────────────────────
        try {
            long enAttente = justificatifRepository.countByStatutValidation("EN_ATTENTE");
            long acceptes  = justificatifRepository.countByStatutValidation("ACCEPTE");
            long refuses   = justificatifRepository.countByStatutValidation("REFUSE");

            Map<String, Object> i = new LinkedHashMap<>();
            i.put("type",     "justificatifs");
            i.put("icon",     "FileText");
            i.put("titre",    enAttente + " justificatif(s) en attente");
            i.put("body",     acceptes + " accepté(s) · " + refuses + " refusé(s). " +
                              (enAttente > 0 ? "Des absences requièrent votre attention." : "Tout est traité."));
            i.put("enAttente", enAttente);
            i.put("acceptes",  acceptes);
            i.put("refuses",   refuses);
            insights.add(i);
        } catch (Exception ex) {
            System.err.println("⚠️ Insights justificatifs : " + ex.getMessage());
        }

        return ResponseEntity.ok(insights);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getGlobalStats() {
        Map<String, Object> stats = new HashMap<>();

        // 1. Totaux basiques
        long totalEtudiants = etudiantRepository.count();
        long totalProfesseurs = professeurRepository.count();
        
        List<Seance> toutesLesSeances = seanceRepository.findAll();
        long seancesEnCours = toutesLesSeances.stream().filter(Seance::isEstActive).count();

        // 2. Calcul du taux de présence global moyen
        List<Seance> seancesTerminees = toutesLesSeances.stream()
                .filter(Seance::isEstTerminee)
                .collect(Collectors.toList());
                
        double globalAttendance = 0;

        if (!seancesTerminees.isEmpty()) {
            double sumPercentages = 0;
            int validSessions = 0;
            List<Etudiant> tousLesEtudiants = etudiantRepository.findAll();

            for (Seance s : seancesTerminees) {
                // Trouver combien d'étudiants étaient attendus pour le groupe de cette séance
                long expected = 0;
                if (s.getGroupe() != null && !s.getGroupe().isBlank()) {
                    for (Etudiant e : tousLesEtudiants) {
                        if (e.getSpecialite() != null && 
                            (s.getGroupe().toLowerCase().contains(e.getSpecialite().toLowerCase()) || 
                             e.getSpecialite().toLowerCase().contains(s.getGroupe().toLowerCase()))) {
                            expected++;
                        }
                    }
                }

                // Si des étudiants étaient attendus, on calcule le % de la séance
                if (expected > 0) {
                    long presents = presenceRepository.findBySeance_Id(s.getId()).size();
                    sumPercentages += ((double) presents / expected) * 100.0;
                    validSessions++;
                }
            }

            // Moyenne de tous les pourcentages des séances
            if (validSessions > 0) {
                globalAttendance = Math.round(sumPercentages / validSessions);
            }
        }

        stats.put("totalEtudiants", totalEtudiants);
        stats.put("totalProfesseurs", totalProfesseurs);
        stats.put("seancesEnCours", seancesEnCours);
        stats.put("tauxPresenceGlobal", globalAttendance);

        return ResponseEntity.ok(stats);
    }
}