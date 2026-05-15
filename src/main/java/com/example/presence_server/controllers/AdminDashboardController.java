package com.example.presence_server.controllers;

import com.example.presence_server.models.Etudiant;
import com.example.presence_server.models.Seance;
import com.example.presence_server.repositories.EtudiantRepository;
import com.example.presence_server.repositories.PresenceRepository;
import com.example.presence_server.repositories.ProfesseurRepository;
import com.example.presence_server.repositories.SeanceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/dashboard")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class AdminDashboardController {

    @Autowired private EtudiantRepository etudiantRepository;
    @Autowired private ProfesseurRepository professeurRepository;
    @Autowired private SeanceRepository seanceRepository;
    @Autowired private PresenceRepository presenceRepository;

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