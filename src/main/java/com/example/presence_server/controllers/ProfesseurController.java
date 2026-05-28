package com.example.presence_server.controllers;

import com.example.presence_server.dto.ProfStatsDTO;
import com.example.presence_server.models.*;
import com.example.presence_server.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/professeurs")
public class ProfesseurController {

    @Autowired private SeanceRepository       seanceRepository;
    @Autowired private JustificatifRepository  justificatifRepository;
    @Autowired private ProfesseurRepository    professeurRepository;
    @Autowired private PresenceRepository      presenceRepository;
    @Autowired private EtudiantRepository      etudiantRepository; // ← AJOUTER

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/professeurs/{id}/stats  — dashboard prof avec vraies valeurs
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/stats")
    public ResponseEntity<ProfStatsDTO> getStats(@PathVariable Long id) {

        // ── Justificatifs en attente ──────────────────────────────────────────
        long justifsCount  = justificatifRepository
                .countBySeance_Professeur_IdAndStatutValidation(id, "EN_ATTENTE");
        String justifsFormat = String.format("%02d", justifsCount);

        // ── Séances terminées du prof ─────────────────────────────────────────
        List<Seance> seancesTerminees = seanceRepository
                .findByProfesseur_IdAndDateHeureFinBefore(id, LocalDateTime.now())
                .stream()
                .filter(Seance::isEstTerminee)
                .collect(Collectors.toList());

        // ── Heures assurées ───────────────────────────────────────────────────
        long totalMinutes = 0;
        for (Seance s : seancesTerminees) {
            if (s.getDateHeureDebut() != null && s.getDateHeureFin() != null) {
                totalMinutes += Duration.between(
                        s.getDateHeureDebut(), s.getDateHeureFin()).toMinutes();
            }
        }
        long heures = totalMinutes / 60;

        // ── Présence moyenne RÉELLE ───────────────────────────────────────────
        // Pour chaque séance terminée :
        //   - on compte les étudiants attendus (spécialité dans groupe)
        //   - on compte les présents enregistrés
        long totalPresents = 0;
        long totalAttendus = 0;

        List<Etudiant> tousEtudiants = etudiantRepository.findAll();

        for (Seance s : seancesTerminees) {
            String groupeSeance = s.getGroupe();
            if (groupeSeance == null || groupeSeance.isBlank()) continue;

            // Nombre d'étudiants attendus pour ce groupe
            long nbAttendus = tousEtudiants.stream()
                    .filter(e -> e.getSpecialite() != null && !e.getSpecialite().isBlank())
                    .filter(e -> groupeSeance.toLowerCase()
                                             .contains(e.getSpecialite().toLowerCase()))
                    .count();
            totalAttendus += nbAttendus;

            // Nombre de présents pour cette séance
            long presents = presenceRepository.findBySeance_Id(s.getId()).stream()
                    .filter(p -> "PRESENT".equals(p.getStatutPresence()))
                    .count();
            totalPresents += presents;
        }

        long moyennePct = totalAttendus > 0
                ? Math.round(((double) totalPresents / totalAttendus) * 100)
                : 0;
        // Plafonner à 100% (cas edge)
        moyennePct = Math.min(moyennePct, 100);
        String presenceMoyenne = moyennePct + "%";

        ProfStatsDTO stats = new ProfStatsDTO(presenceMoyenne, justifsFormat, heures + "h");
        return ResponseEntity.ok(stats);
    }

// ─────────────────────────────────────────────────────────────────────────
    // GET /api/professeurs/admin/tous-avec-stats
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/admin/tous-avec-stats")
    public ResponseEntity<List<Map<String, Object>>> getTousAvecStats() {

        List<Professeur> tous = professeurRepository.findAll();
        List<Etudiant> tousEtudiants = etudiantRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();

        for (Professeur p : tous) {
            if (p.getNom() == null || p.getPrenom() == null) continue;

            List<Seance> seancesTerminees = seanceRepository
                    .findByProfesseur_IdAndDateHeureFinBefore(p.getId(), LocalDateTime.now())
                    .stream().filter(Seance::isEstTerminee).collect(Collectors.toList());

            long totalMinutes = seancesTerminees.stream()
                    .filter(s -> s.getDateHeureDebut() != null && s.getDateHeureFin() != null)
                    .mapToLong(s -> Duration.between(s.getDateHeureDebut(), s.getDateHeureFin()).toMinutes())
                    .sum();
            long heures  = totalMinutes / 60;
            long minutes = totalMinutes % 60;

            long totalSeances = seanceRepository
                    .findByProfesseur_IdAndEstTermineeFalseOrderByDateHeureDebutAsc(p.getId())
                    .size() + seancesTerminees.size();

            long justifsAttente = justificatifRepository
                    .countBySeance_Professeur_IdAndStatutValidation(p.getId(), "EN_ATTENTE");

            List<String> modules = p.getCoursEnseignes() != null
                    ? p.getCoursEnseignes().stream().filter(Objects::nonNull).map(Cours::getLibelle)
                        .filter(Objects::nonNull).distinct().collect(Collectors.toList())
                    : new ArrayList<>();

            // ── CALCUL DES POURCENTAGES (Global + Par Module) ──
            long globalPresents = 0;
            long globalAttendus = 0;
            Map<String, long[]> parModule = new HashMap<>(); // libelle -> [presents, attendus]

            for (Seance s : seancesTerminees) {
                String module = s.getCours() != null ? s.getCours().getLibelle() : "Inconnu";
                String groupeSeance = s.getGroupe();
                if (groupeSeance == null || groupeSeance.isBlank()) continue;

                long nbAttendus = tousEtudiants.stream()
                        .filter(e -> e.getSpecialite() != null && !e.getSpecialite().isBlank())
                        .filter(e -> groupeSeance.toLowerCase().contains(e.getSpecialite().toLowerCase()))
                        .count();

                long nbPresents = presenceRepository.findBySeance_Id(s.getId()).stream()
                        .filter(pr -> "PRESENT".equals(pr.getStatutPresence())).count();

                globalAttendus += nbAttendus;
                globalPresents += nbPresents;

                parModule.putIfAbsent(module, new long[]{0, 0});
                parModule.get(module)[0] += nbPresents;
                parModule.get(module)[1] += nbAttendus;
            }

            long tauxGlobal = globalAttendus > 0 ? Math.round(((double) globalPresents / globalAttendus) * 100) : 0;
            
            List<Map<String, Object>> modulesStats = new ArrayList<>();
            for (Map.Entry<String, long[]> entry : parModule.entrySet()) {
                long p_pres = entry.getValue()[0];
                long p_att = entry.getValue()[1];
                long pct = p_att > 0 ? Math.round(((double) p_pres / p_att) * 100) : 0;
                Map<String, Object> mStat = new HashMap<>();
                mStat.put("libelle", entry.getKey());
                mStat.put("taux", Math.min(pct, 100)); // Plafond à 100%
                modulesStats.add(mStat);
            }

            Map<String, Object> dto = new HashMap<>();
            dto.put("id",               p.getId());
            dto.put("nom",              p.getNom());
            dto.put("prenom",           p.getPrenom());
            dto.put("name",             p.getPrenom() + " " + p.getNom());
            dto.put("email",            p.getEmail() != null ? p.getEmail() : "—");
            dto.put("heuresFormat",     heures + "h" + (minutes > 0 ? String.format("%02d", minutes) : ""));
            dto.put("seancesTotal",     totalSeances);
            dto.put("seancesTerminees", seancesTerminees.size());
            dto.put("justifsAttente",   justifsAttente);
            dto.put("modules",          modules);
            
            // Nouvelles données de statistiques
            dto.put("tauxPresenceGlobal", Math.min(tauxGlobal, 100));
            dto.put("modulesStats",       modulesStats);

            result.add(dto);
        }

        result.sort(Comparator.comparing(m -> m.get("name").toString()));
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/professeurs/{id}/detail — inchangé
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/{id}/detail")
    public ResponseEntity<Map<String, Object>> getDetail(@PathVariable Long id) {
        Optional<Professeur> opt = professeurRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Professeur p = opt.get();
        LocalDateTime now   = LocalDateTime.now();
        LocalDateTime debut = now.minusMonths(11).withDayOfMonth(1).toLocalDate().atStartOfDay();

        List<Seance> seancesTerminees = seanceRepository
                .findByProfesseur_IdAndDateHeureFinBefore(p.getId(), now)
                .stream().filter(Seance::isEstTerminee).collect(Collectors.toList());

        Map<String, Long> parMois = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            LocalDateTime moisDebut = now.minusMonths(i).withDayOfMonth(1).toLocalDate().atStartOfDay();
            String label = moisDebut.getMonth()
                    .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRENCH)
                    + " " + moisDebut.getYear();
            parMois.put(label, 0L);
        }
        for (Seance s : seancesTerminees) {
            if (s.getDateHeureDebut() != null && s.getDateHeureDebut().isAfter(debut)) {
                String label = s.getDateHeureDebut().getMonth()
                        .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.FRENCH)
                        + " " + s.getDateHeureDebut().getYear();
                parMois.merge(label, 1L, Long::sum);
            }
        }

        long justifsAcceptes = justificatifRepository
                .countBySeance_Professeur_IdAndStatutValidation(p.getId(), "ACCEPTE");
        long justifsRefuses  = justificatifRepository
                .countBySeance_Professeur_IdAndStatutValidation(p.getId(), "REFUSE");
        long justifsAttente  = justificatifRepository
                .countBySeance_Professeur_IdAndStatutValidation(p.getId(), "EN_ATTENTE");

        List<String> modules = p.getCoursEnseignes() != null
                ? p.getCoursEnseignes().stream().filter(Objects::nonNull)
                    .map(Cours::getLibelle).filter(Objects::nonNull)
                    .distinct().collect(Collectors.toList())
                : new ArrayList<>();

        Map<String, Object> detail = new HashMap<>();
        detail.put("id",              p.getId());
        detail.put("nom",             p.getNom());
        detail.put("prenom",          p.getPrenom());
        detail.put("email",           p.getEmail() != null ? p.getEmail() : "—");
        detail.put("modules",         modules);
        detail.put("seancesParMois",  parMois);
        detail.put("justifsAcceptes", justifsAcceptes);
        detail.put("justifsRefuses",  justifsRefuses);
        detail.put("justifsAttente",  justifsAttente);
        detail.put("seancesTotal",    seancesTerminees.size());
        return ResponseEntity.ok(detail);
    }
}