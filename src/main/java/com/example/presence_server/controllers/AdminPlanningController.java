package com.example.presence_server.controllers;

import com.example.presence_server.models.*;
import com.example.presence_server.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/planning")
public class AdminPlanningController {

    @Autowired private SeanceRepository           seanceRepository;
    @Autowired private ProfesseurRepository       professeurRepository;
    @Autowired private CoursRepository            coursRepository;
    @Autowired private SalleRepository            salleRepository;
    @Autowired private CoursAssignationRepository coursAssignationRepository;
    @Autowired private EtudiantRepository         etudiantRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/planning/groupe/{groupe}
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/groupe/{groupe}")
    public ResponseEntity<?> getPlanningByGroup(@PathVariable String groupe) {
        List<Seance> seances = seanceRepository.findByGroupe(groupe);
        return ResponseEntity.ok(formatSeances(seances));
    }

    @GetMapping("/specialite/{specialite}")
    public ResponseEntity<?> getPlanningBySpecialite(@PathVariable String specialite) {
        List<Seance> seances = seanceRepository.findByCours_Specialite(specialite);
        return ResponseEntity.ok(formatSeances(seances));
    }

    private List<Map<String, Object>> formatSeances(List<Seance> seances) {
        return seances.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id",       s.getId());
            map.put("day",      s.getJour());
            map.put("time",     s.getHeurePlage());
       
            map.put("module",   s.getCours()      != null ? s.getCours().getLibelle()                                       : "N/A");
            map.put("moduleId", s.getCours()      != null ? s.getCours().getId()                     
                        : null);
            map.put("prof",     s.getProfesseur() != null ? s.getProfesseur().getNom() + " " + s.getProfesseur().getPrenom(): "N/A");
            map.put("profId",   s.getProfesseur() != null ? s.getProfesseur().getId()                              
          : null);
            map.put("room",     s.getSalle()      != null ? s.getSalle().getNom()                                           : "N/A");
            map.put("roomId",   s.getSalle()     
  != null ? s.getSalle().getId()                                            : null);
            map.put("type",     s.getTypeSeance());
            map.put("groupe",   s.getGroupe());
            return map;
        }).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/admin/planning/form-data
    // Retourne profs, cours, salles + assignations groupées par coursId
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/form-data")
    public ResponseEntity<?> getFormData() {
        // Profs (données légères)
        List<Map<String, Object>> profsDto = professeurRepository.findAll().stream().map(p -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id",     p.getId());
 
            dto.put("nom",    p.getNom());
            dto.put("prenom", p.getPrenom());
            return dto;
        }).collect(Collectors.toList());

        // Cours (données légères)
        List<Map<String, Object>> coursDto = coursRepository.findAll().stream().map(c -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id",         c.getId());
            dto.put("libelle",    c.getLibelle());
            dto.put("specialite", c.getSpecialite());
            return dto;
   
        }).collect(Collectors.toList());

        // Salles
        List<Map<String, Object>> sallesDto = salleRepository.findAll().stream().map(s -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id",  s.getId());
            dto.put("nom", s.getNom());
            return dto;
        }).collect(Collectors.toList());

        // Assignations groupées par coursId
        // Structure : { "1": [{profId, profNom, typeSeance}, ...], "2": [...] }
        List<CoursAssignation> allAssignations = coursAssignationRepository.findAll();
        Map<String, List<Map<String, Object>>> assignationsByCours = new HashMap<>();

        for (CoursAssignation ca : allAssignations) {
            String key = ca.getCours().getId().toString();
            assignationsByCours.computeIfAbsent(key, k -> new ArrayList<>()).add(Map.of(
                "profId",    ca.getProfesseur().getId(),
                "profNom",   ca.getProfesseur().getNom() + " " + ca.getProfesseur().getPrenom(),
                "typeSeance",ca.getTypeSeance()
            ));
        }

        Map<String, Object> response = new HashMap<>();
        response.put("profs",       profsDto);
        response.put("cours",       coursDto);
        response.put("salles",      sallesDto);
        response.put("assignations",assignationsByCours);

        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/admin/planning/add
    // PUT  /api/admin/planning/update/{id}
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/add")
    public ResponseEntity<?> addSeance(@RequestBody Map<String, Object> payload) {
        return saveOrUpdateSeance(null, payload);
    }

    @PutMapping("/update/{id}")
    public ResponseEntity<?> updateSeance(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        return saveOrUpdateSeance(id, payload);
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<?> deleteSeance(@PathVariable Long id) {
        seanceRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Séance supprimée"));
    }

    private ResponseEntity<?> saveOrUpdateSeance(Long id, Map<String, Object> payload) {
        try {
            String groupe     = String.valueOf(payload.get("groupe"));
            String jour       = String.valueOf(payload.get("day"));
            String heurePlage = String.valueOf(payload.get("timeSlot"));
            Long   coursId    = Long.valueOf(String.valueOf(payload.get("coursId")));
            Long   profId     = Long.valueOf(String.valueOf(payload.get("profId")));
            Long   salleId    = Long.valueOf(String.valueOf(payload.get("salleId")));
            String type       = String.valueOf(payload.get("type"));

            Professeur prof  = professeurRepository.findById(profId)
                .orElseThrow(() -> new RuntimeException("Professeur introuvable"));
            Cours      cours = coursRepository.findById(coursId)
                .orElseThrow(() -> new RuntimeException("Cours introuvable"));
            Salle      salle = salleRepository.findById(salleId)
                .orElseThrow(() -> new RuntimeException("Salle introuvable"));

            // Conflit prof
            List<Seance> conflitsProf = seanceRepository
                    .findByJourAndHeurePlageAndProfesseur_Id(jour, heurePlage, profId);
            if (conflitsProf.stream().anyMatch(s -> !s.getId().equals(id))) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Ce professeur est déjà occupé sur ce créneau."));
            }

            // Conflit salle
            List<Seance> conflitsSalle = seanceRepository
                    .findByJourAndHeurePlageAndSalle(jour, heurePlage, salle);
            if (conflitsSalle.stream().anyMatch(s -> !s.getId().equals(id))) {
                return ResponseEntity.badRequest()
                    .body(Map.of("message", "Cette salle est déjà réservée sur ce créneau."));
            }

            Seance seance = (id == null)
                ?
                new Seance()
                : seanceRepository.findById(id).orElseThrow();

            seance.setGroupe(groupe);
            seance.setJour(jour);
            seance.setHeurePlage(heurePlage);
            seance.setSalle(salle);
            seance.setTypeSeance(type);
            seance.setCours(cours);
            seance.setProfesseur(prof);
            seance.setTitre(cours.getLibelle() + " — " + type);

            LocalDateTime prochaineDate = calculerProchaineDate(jour, heurePlage);
            seance.setDateHeureDebut(prochaineDate);
            seance.setDateHeureFin(prochaineDate.plusMinutes(90));
            seance.setDatePlanifiee(prochaineDate); // ← AJOUTER ICI

            seanceRepository.save(seance);

            return ResponseEntity.ok(Map.of(
                "message",  id == null ? "Séance ajoutée" : "Séance modifiée",
                "seanceId", seance.getId()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("message", "Erreur : " + e.getMessage()));
        }
    }

    private LocalDateTime calculerProchaineDate(String jourStr, String heureStr) {
        DayOfWeek targetDay;
        switch (jourStr.toLowerCase()) {
            case "lundi":    targetDay = DayOfWeek.MONDAY;
            break;
            case "mardi":    targetDay = DayOfWeek.TUESDAY;   break;
            case "mercredi": targetDay = DayOfWeek.WEDNESDAY; break;
            case "jeudi":    targetDay = DayOfWeek.THURSDAY;  break;
            case "vendredi": targetDay = DayOfWeek.FRIDAY;    break;
            case "samedi":   targetDay = DayOfWeek.SATURDAY;  break;
            default:         targetDay = DayOfWeek.SUNDAY;
            break;
        }
        LocalTime      heure    = LocalTime.parse(heureStr);
        LocalDateTime  now      = LocalDateTime.now();
        LocalDateTime  nextDate = now.with(TemporalAdjusters.nextOrSame(targetDay)).with(heure);
        if (nextDate.isBefore(now)) nextDate = nextDate.plusWeeks(1);
        return nextDate;
    }
}