package com.example.presence_server.controllers;

import com.example.presence_server.models.*;
import com.example.presence_server.repositories.*;
import com.example.presence_server.services.EmailService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/seances")
public class SeanceController {

    @Autowired private SeanceRepository      seanceRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EtudiantRepository    etudiantRepository;
    @Autowired private PresenceRepository    presenceRepository;
    @Autowired private EmailService          emailService;

    // ── Token aléatoire ──────────────────────────────────────────────────────
    private String generateRandomToken() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        Random rnd = new Random();
        for (int i = 0; i < 6; i++) {
            if (i == 3) sb.append("-");
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    // ── Gravité selon nombre d'absences ──────────────────────────────────────
    private String calculerGravite(long absences) {
        if (absences <= 1)  return "VERT";
        if (absences <= 3)  return "ORANGE";
        return "ROUGE";
    }

    // ── Envoyer une notification à un utilisateur ────────────────────────────
    private void envoyerNotif(Utilisateur user, Long seanceId, String type,
                               String titre, String message, String gravite) {
        Notification n = new Notification();
        n.setUtilisateur(user);
        n.setSeanceId(seanceId);
        n.setTypeNotification(type);
        n.setTitre(titre);
        n.setMessage(message);
        n.setGravite(gravite);
        n.setDateCreation(LocalDateTime.now());
        n.setEstLue(false);
        notificationRepository.save(n);
    }

    // ── HELPER : Trouver les étudiants (Corrigé pour matcher Spécialité ET Groupe) ────
    private List<Etudiant> getEtudiantsConcerneParSeance(Seance seance) {
        String groupeSeance = seance.getGroupe(); // ex: "SITW G1"
        if (groupeSeance == null || groupeSeance.isBlank()) {
            return new ArrayList<>();
        }
        
        List<Etudiant> tousLesEtudiants = etudiantRepository.findAll();
        List<Etudiant> etudiantsConcernes = new ArrayList<>();
        
        String groupeSeanceLower = groupeSeance.toLowerCase();

        for (Etudiant etudiant : tousLesEtudiants) {
            String spe = etudiant.getSpecialite() != null ? etudiant.getSpecialite().toLowerCase() : "";
            String grp = etudiant.getGroupe() != null ? etudiant.getGroupe().toLowerCase() : "";

            if (!spe.isBlank()) {
                // 1. On vérifie que la spécialité correspond (ex: "sitw" est dans "sitw g1")
                boolean matchSpe = groupeSeanceLower.contains(spe) || spe.contains(groupeSeanceLower);
                
                // 2. On vérifie que le groupe exact de l'étudiant est concerné (ex: "g1" est dans "sitw g1")
                // S'il n'a pas de groupe, on part du principe qu'il est concerné (matchGrp = true par défaut)
                boolean matchGrp = true;
                if (!grp.isBlank()) {
                    matchGrp = groupeSeanceLower.contains(grp);
                }
                
                // S'il coche les 2 conditions, on le compte !
                if (matchSpe && matchGrp) {
                    etudiantsConcernes.add(etudiant);
                }
            }
        }
        return etudiantsConcernes;
    }

// À ajouter dans SeanceController.java
@GetMapping("/{id}/capacite")
public ResponseEntity<Map<String, Object>> getCapaciteGroupe(@PathVariable Long id) {
    return seanceRepository.findById(id).map(seance -> {
        // On réutilise ton helper existant qui filtre les étudiants par spécialité
        List<Etudiant> etudiants = getEtudiantsConcerneParSeance(seance);
        
        Map<String, Object> response = new HashMap<>();
        response.put("seanceId", id);
        response.put("totalEtudiants", etudiants.size());
        response.put("groupeNom", seance.getGroupe() != null ? seance.getGroupe() : "N/A");

        return ResponseEntity.ok(response);
    }).orElse(ResponseEntity.notFound().build());
}


// ─────────────────────────────────────────────────────────────────────────
// GET /api/seances/prochain/{profId}
// Retourne la séance active OU la prochaine séance à venir du prof
// ─────────────────────────────────────────────────────────────────────────
@GetMapping("/prochain/{profId}")
public ResponseEntity<?> getProchainCours(@PathVariable Long profId) {

    // 1. D'abord vérifier si une séance est en cours (active)
    Optional<Seance> seanceActive = seanceRepository.findByProfesseur_IdAndEstActiveTrue(profId);
    if (seanceActive.isPresent()) {
        return ResponseEntity.ok(formatSeancePourDashboard(seanceActive.get()));
    }

    // 2. Chercher la prochaine séance non terminée, non active, dans le futur
    //    Avec une grace period de 30min pour les séances qui viennent de commencer
    LocalDateTime maintenant = LocalDateTime.now().minusMinutes(30);
    List<Seance> prochainesSeances = seanceRepository
            .findByProfesseur_IdAndEstTermineeFalseAndEstActiveFalseAndDateHeureDebutAfterOrderByDateHeureDebutAsc(
                    profId, maintenant);
    if (!prochainesSeances.isEmpty()) {
        return ResponseEntity.ok(formatSeancePourDashboard(prochainesSeances.get(0)));
    }

    // 3. Fallback : n'importe quelle séance non terminée (cas où dateHeureDebut n'est pas définie)
    List<Seance> toutesNonTerminees = seanceRepository
            .findByProfesseur_IdAndEstTermineeFalseOrderByDateHeureDebutAsc(profId);
    if (!toutesNonTerminees.isEmpty()) {
        return ResponseEntity.ok(formatSeancePourDashboard(toutesNonTerminees.get(0)));
    }

    return ResponseEntity.noContent().build();
}

// ── Helper : formater la séance pour le dashboard prof ───────────────────
private Map<String, Object> formatSeancePourDashboard(Seance s) {
    Map<String, Object> dto = new HashMap<>();
    dto.put("id",                 s.getId());
    dto.put("titre",              s.getTitre());
    dto.put("typeSeance",         s.getTypeSeance());
    dto.put("groupe",             s.getGroupe());
    dto.put("jour",               s.getJour());
    dto.put("heurePlage",         s.getHeurePlage());
    dto.put("dateHeureDebut",     s.getDateHeureDebut());
    dto.put("dateHeureFin",       s.getDateHeureFin());
    dto.put("estActive",          s.isEstActive());
    dto.put("estTerminee",        s.isEstTerminee());
    dto.put("numeroSeance",       s.getNumeroSeance());
    dto.put("totalAttendus",      getEtudiantsConcerneParSeance(s).size());

    // ← LIGNE MANQUANTE — timer second device
    dto.put("dateHeureLancement", s.getDateHeureLancement() != null
            ? s.getDateHeureLancement().toString() : null);

    if (s.getCours() != null) {
        Map<String, Object> coursDto = new HashMap<>();
        coursDto.put("id",      s.getCours().getId());
        coursDto.put("libelle", s.getCours().getLibelle());
        dto.put("cours", coursDto);
    }
    if (s.getProfesseur() != null) {
        Map<String, Object> profDto = new HashMap<>();
        profDto.put("id",     s.getProfesseur().getId());
        profDto.put("nom",    s.getProfesseur().getNom());
        profDto.put("prenom", s.getProfesseur().getPrenom());
        dto.put("professeur", profDto);
    }
    if (s.getSalle() != null) {
        Map<String, Object> salleDto = new HashMap<>();
        salleDto.put("id",  s.getSalle().getId());
        salleDto.put("nom", s.getSalle().getNom());
        dto.put("salle", salleDto);
    }
    return dto;
}

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/seances/{id}/lancer
    // ─────────────────────────────────────────────────────────────────────────
@PutMapping("/{id}/lancer")
@Transactional
public ResponseEntity<?> lancerSeance(
        @PathVariable Long id,
        @RequestBody Map<String, Double> localisation) {

    return seanceRepository.findById(id).map(seance -> {

        if (seance.isEstTerminee()) {
            return ResponseEntity.badRequest()
                  .body(Map.of("error", "Cette séance est déjà terminée."));
        }

        LocalDateTime now = LocalDateTime.now();

        seance.setEstActive(true);
        seance.setTimerPaused(false);
        seance.setDateHeureDebut(now);
        seance.setDateHeureLancement(now);
        seance.setCurrentToken(generateRandomToken());
        seance.setTokenLastRefreshedAt(now); 
        seance.setPausedElapsedMs(0L);

        if (localisation.containsKey("lat") && localisation.containsKey("lng")) {
            seance.setProfLat(localisation.get("lat"));
            seance.setProfLng(localisation.get("lng"));
        }

        Seance saved = seanceRepository.saveAndFlush(seance);
        try {
            String module  = seance.getCours()      != null ?
                seance.getCours().getLibelle() : "Module inconnu";
            String profNom = seance.getProfesseur() != null
                    ? "Prof. " + seance.getProfesseur().getPrenom() + " " + seance.getProfesseur().getNom()
                    : "votre professeur";
            String heure   = now.format(DateTimeFormatter.ofPattern("HH:mm"));
            String titre   = "📚 Séance lancée — " + module;
            String message = "La séance de " + module + " vient d'être lancée par " + profNom + " à " + heure + ".";
            
            List<Etudiant> etudiants = getEtudiantsConcerneParSeance(seance);
            for (Etudiant e : etudiants) {
                envoyerNotif(e, saved.getId(), "LANCEMENT", titre, message, "VERT");
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Notif lancement : " + ex.getMessage());
        }

        return ResponseEntity.ok(saved);

    }).orElse(ResponseEntity.notFound().build());
}

@GetMapping("/{id}/session-state")
public ResponseEntity<?> getSessionState(@PathVariable Long id) {
    return seanceRepository.findById(id).map(seance -> {
        LocalDateTime now = LocalDateTime.now();

        long elapsedMs;
        if (seance.isTimerPaused()) {
            elapsedMs = seance.getPausedElapsedMs() != null ? seance.getPausedElapsedMs() : 0L;
        } else if (seance.getTokenLastRefreshedAt() != null) 
        {
            elapsedMs = java.time.Duration.between(
                    seance.getTokenLastRefreshedAt(), now).toMillis();
        } else {
            elapsedMs = 0L;
        }

        Map<String, Object> state = new HashMap<>();
        state.put("token",         seance.getCurrentToken() != null ? seance.getCurrentToken() : "");
        state.put("isTimerPaused", seance.isTimerPaused());
        state.put("elapsedMs",     Math.min(elapsedMs, 10500L));
        state.put("estActive",     seance.isEstActive());
        state.put("estTerminee",   seance.isEstTerminee());
        
        // 💡 NOUVEAU : Injecté ici aussi pour la mise à jour en temps réel !
        state.put("totalAttendus", getEtudiantsConcerneParSeance(seance).size());

        return ResponseEntity.ok(state);
    }).orElse(ResponseEntity.notFound().build());
}

    // ─────────────────────────────────────────────────────────────────────────
// -------------------------------------------------------------------------
    // PUT /api/seances/{id}/terminer
    // -------------------------------------------------------------------------
    @PutMapping("/{id}/terminer")
    @Transactional
    public ResponseEntity<?> terminerSeance(@PathVariable Long id) {

        return seanceRepository.findById(id).map(seance -> {

            if (seance.isEstTerminee()) {
                return ResponseEntity.ok(Map.of("message", "Déjà terminée"));
            }

            // 1. Mettre à jour et sauvegarder la séance actuelle
            LocalDateTime maintenant = LocalDateTime.now();
            seance.setEstActive(false);
            seance.setEstTerminee(true);
            seance.setDateHeureFin(maintenant);
            seanceRepository.saveAndFlush(seance);

            // Formatage de l'heure de fin pour la notification
            String heureFin = maintenant.format(DateTimeFormatter.ofPattern("HH:mm"));
            
            // Captures des données nécessaires (Sécurité JPA)
            final Long seanceId = seance.getId();
            final String module = seance.getCours() != null ? seance.getCours().getLibelle() : "Module inconnu";
            final Long coursId = seance.getCours() != null ? seance.getCours().getId() : null;
            final String groupeSeance = seance.getGroupe();
            final String profNom = seance.getProfesseur() != null
                    ? "Prof. " + seance.getProfesseur().getPrenom() + " " + seance.getProfesseur().getNom()
                    : "votre professeur";
            final String jourSeance = seance.getJour();
            final String heurePlage = seance.getHeurePlage();

            // 2. Suppression de la notification de lancement
            try {
                notificationRepository.deleteBySeanceIdAndTypeNotification(seanceId, "LANCEMENT");
            } catch (Exception ex) {
                System.err.println("❌ Erreur suppression notif lancement : " + ex.getMessage());
            }

            // 3. RECYCLING : Création automatique de la séance J+7 (Fait de manière synchrone)
            try {
                if (jourSeance != null && heurePlage != null) {
                    LocalDateTime nextPlanifiee;
                    if (seance.getDatePlanifiee() != null) {
                        nextPlanifiee = seance.getDatePlanifiee().plusDays(7);
                    } else if (seance.getDateHeureDebut() != null) {
                        nextPlanifiee = seance.getDateHeureDebut().plusDays(7);
                    } else {
                        LocalTime heure = LocalTime.parse(heurePlage);
                        nextPlanifiee = LocalDateTime.now().plusDays(7).with(heure);
                    }

                    Seance prochaine = new Seance();
                    prochaine.setGroupe(seance.getGroupe());
                    prochaine.setJour(jourSeance);
                    prochaine.setHeurePlage(heurePlage);
                    prochaine.setSalle(seance.getSalle());
                    prochaine.setTypeSeance(seance.getTypeSeance());
                    prochaine.setCours(seance.getCours());
                    prochaine.setProfesseur(seance.getProfesseur());
                    prochaine.setTitre(seance.getTitre());
                    prochaine.setNumeroSeance(seance.getNumeroSeance() != null ? seance.getNumeroSeance() + 1 : 2);
                    prochaine.setDateHeureDebut(nextPlanifiee);
                    prochaine.setDateHeureFin(nextPlanifiee.plusMinutes(90));
                    prochaine.setDatePlanifiee(nextPlanifiee);
                    prochaine.setEstActive(false);
                    prochaine.setEstTerminee(false);
                    seanceRepository.save(prochaine);
                    System.out.println("🔄 [RECYCLING] Séance recréée pour le " + nextPlanifiee.toLocalDate() + " à " + heurePlage);
                }
            } catch (Exception ex) {
                System.err.println("❌ Erreur de recyclage de séance : " + ex.getMessage());
            }

            // 4. Traitement des étudiants et des nouvelles notifications de FIN
            List<Etudiant> etudiants = getEtudiantsConcerneParSeance(seance);
            List<Map<String, Object>> listeEmailsAsynchrones = new ArrayList<>();

            for (Etudiant e : etudiants) {
                try {
                    // Était-il présent aujourd'hui ?
                    boolean presentCeJour = presenceRepository
                            .findByEtudiant_IdAndSeance_Id(e.getId(), seanceId)
                            .isPresent();

                    long absences = 0;
                    long totalPresences = 0;

                    if (coursId != null && groupeSeance != null) {
                        long totalSeances = seanceRepository.countByCours_IdAndGroupeAndEstTermineeTrue(coursId, groupeSeance);
                        totalPresences = presenceRepository.countByEtudiant_IdAndSeance_Cours_IdAndStatutPresence(e.getId(), coursId, "PRESENT");
                        absences = Math.max(0, totalSeances - totalPresences);
                    }

                    String gravite;
                    String titre;
                    String message;

                    if (presentCeJour) {
                        if (absences == 0) {
                            gravite = "VERT";
                            titre = "✅ Cours terminé — " + module;
                            message = "La séance s'est terminée à " + heureFin + ". Vous étiez présent(e). "
                                    + "Statut global : " + totalPresences + " présence(s) et 0 absence. Excellent !";
                        } else if (absences <= 3) {
                            gravite = "ORANGE";
                            titre = "⚠️ Cours terminé — " + module;
                            message = "La séance s'est terminée à " + heureFin + ". Vous étiez présent(e) (Total présences: " + totalPresences + "). "
                                    + "Attention : vous cumulez " + absences + " absence(s) sur ce module.";
                        } else {
                            gravite = "ROUGE";
                            titre = "🚨 Alerte Assiduité — " + module;
                            message = "Séance terminée à " + heureFin + ". Vous étiez présent(e), mais votre nombre d'absences est critique (" 
                                    + absences + " absences). Contactez " + profNom + " rapidement.";
                        }
                    } else {
                        // Étudiant ABSENT aujourd'hui
                        if (absences <= 1) {
                            gravite = "VERT";
                            titre = "❌ Absence enregistrée — " + module;
                            message = "La séance s'est terminée à " + heureFin + ". Vous avez été marqué(e) absent(e). "
                                    + "C'est votre 1ère absence au compteur, pensez à la justifier.";
                        } else if (absences <= 3) {
                            gravite = "ORANGE";
                            titre = "⚠️ Absence accumulée — " + module;
                            message = "La séance s'est terminée à " + heureFin + ". Vous étiez absent(e). "
                                    + "Vous cumulez désormais " + absences + " absences. Ajustez votre assiduité !";
                        } else {
                            gravite = "ROUGE";
                            titre = "🚨 Taux d'absence critique — " + module;
                            message = "Séance terminée à " + heureFin + ". Absent(e). Vous atteignez "
                                    + absences + " absences en " + module + ". Contactez impérativement " + profNom + ".";

                            // Préparation de l'email d'alerte (si critique)
                            if (e.getEmail() != null && !e.getEmail().isBlank()) {
                                Map<String, Object> emailData = new HashMap<>();
                                emailData.put("email", e.getEmail());
                                emailData.put("nomEtudiant", e.getPrenom() + " " + e.getNom());
                                emailData.put("absences", absences);
                                listeEmailsAsynchrones.add(emailData);
                            }
                        }
                    }

                    // Envoi synchrone de la notification de FIN
                    envoyerNotif(e, seanceId, "FIN", titre, message, gravite);

                } catch (Exception ex) {
                    System.err.println("❌ Erreur traitement notif étudiant ID " + e.getId() + " : " + ex.getMessage());
                }
            }

            // 5. ENVOI DES EMAILS EN ASYNC (Seule partie externalisée pour préserver les performances HTTP)
            if (!listeEmailsAsynchrones.isEmpty()) {
                CompletableFuture.runAsync(() -> {
                    for (Map<String, Object> emailData : listeEmailsAsynchrones) {
                        try {
                            emailService.envoyerAlerteAbsence(
                                    (String) emailData.get("email"),
                                    (String) emailData.get("nomEtudiant"),
                                    module,
                                    profNom,
                                    (Long) emailData.get("absences")
                            );
                        } catch (Exception mailEx) {
                            System.err.println("❌ Erreur lors de l'envoi asynchrone de l'email d'alerte : " + mailEx.getMessage());
                        }
                    }
                });
            }

            return ResponseEntity.ok(Map.of("status", "success", "message", "Séance terminée et notifications mises à jour."));

        }).orElse(ResponseEntity.notFound().build());
    }

@PutMapping("/{id}/toggle-timer")
@Transactional
public ResponseEntity<Map<String, Object>> toggleTimer(@PathVariable Long id) {
    return seanceRepository.findById(id).map(seance -> {
        LocalDateTime now      = LocalDateTime.now();
        boolean       newPaused = !seance.isTimerPaused();

        if (newPaused) {
            long elapsed = seance.getTokenLastRefreshedAt() != null
                    ? java.time.Duration.between(seance.getTokenLastRefreshedAt(), now).toMillis()
                    : 0L;
            seance.setPausedElapsedMs(Math.min(elapsed, 10000L));
        } else {
            long pausedMs = seance.getPausedElapsedMs() != null ?
                seance.getPausedElapsedMs() : 0L;
            seance.setTokenLastRefreshedAt(now.minusNanos(pausedMs * 1_000_000L));
        }

        seance.setTimerPaused(newPaused);
        seanceRepository.saveAndFlush(seance);

        Map<String, Object> result = new HashMap<>();
        result.put("isPaused",  newPaused);
        result.put("elapsedMs", seance.getPausedElapsedMs());
        return ResponseEntity.ok(result);
    }).orElse(ResponseEntity.notFound().build());
}

    @GetMapping("/{id}/token")
    public ResponseEntity<Map<String, String>> getCurrentToken(@PathVariable Long id) {
        return seanceRepository.findById(id).map(seance ->
            ResponseEntity.ok(Map.of("token", seance.getCurrentToken()))
        ).orElse(ResponseEntity.notFound().build());
    }

@GetMapping("/prof/{profId}/date")
public ResponseEntity<List<Map<String, Object>>> getSeancesByDate(
        @PathVariable Long profId,
        @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

    List<Seance> toutes = seanceRepository.findByProfAndDatePlanifiee(profId, date);
    return ResponseEntity.ok(toutes.stream()
            .map(this::formatSeancePourAgenda)
            .collect(Collectors.toList()));
}

@PutMapping("/{id}/refresh-token")
@Transactional
public ResponseEntity<?> refreshToken(@PathVariable Long id) {
    return seanceRepository.findById(id).map(seance -> {
        if (seance.isTimerPaused())
            return ResponseEntity.badRequest().body(Map.of("error", "Timer en pause."));
        if (seance.isEstTerminee())
            return ResponseEntity.badRequest().body(Map.of("error", "Séance terminée."));

        if (seance.getTokenLastRefreshedAt() != null) {
            long elapsed = java.time.Duration.between(
                    seance.getTokenLastRefreshedAt(), LocalDateTime.now()).toMillis();
            if (elapsed < 8000) {
                return ResponseEntity.ok(Map.of("token", seance.getCurrentToken()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        seance.setCurrentToken(generateRandomToken());
        seance.setTokenLastRefreshedAt(now);
        seance.setPausedElapsedMs(0L);
        seanceRepository.saveAndFlush(seance);

        return ResponseEntity.ok(Map.of("token", seance.getCurrentToken()));
    }).orElse(ResponseEntity.notFound().build());
}
    
    @GetMapping("/etudiant/specialite/{specialite}/date")
    public ResponseEntity<List<Map<String, Object>>> getSeancesBySpecialite(
            @PathVariable String specialite,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end   = date.atTime(LocalTime.MAX);

        List<Seance> seances = seanceRepository
                .findByGroupeContainingIgnoreCaseAndDateHeureDebutBetweenOrderByDateHeureDebutAsc(
                        specialite, start, end);
        List<Map<String, Object>> result = seances.stream()
                .map(this::formatSeancePourAgenda)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

// ─────────────────────────────────────────────────────────────────────────
// Helper commun : DTO propre pour les agendas (dates en ISO string)
// ─────────────────────────────────────────────────────────────────────────
private Map<String, Object> formatSeancePourAgenda(Seance s) {
    Map<String, Object> dto = new HashMap<>();
    dto.put("id",          s.getId());
    dto.put("titre",       s.getTitre());
    dto.put("typeSeance",  s.getTypeSeance() != null ? s.getTypeSeance() : "Cours");
    dto.put("groupe",      s.getGroupe());
    dto.put("jour",        s.getJour());
    dto.put("heurePlage",  s.getHeurePlage());
    dto.put("estActive",   s.isEstActive());
    dto.put("estTerminee", s.isEstTerminee());
    dto.put("numeroSeance",s.getNumeroSeance());
    dto.put("datePlanifiee", s.getDatePlanifiee() != null
        ? s.getDatePlanifiee().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
    dto.put("dateHeureLancement", s.getDateHeureLancement() != null
        ? s.getDateHeureLancement().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
    dto.put("dateHeureDebut", s.getDateHeureDebut() != null
            ? s.getDateHeureDebut().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
    dto.put("dateHeureFin",   s.getDateHeureFin() != null
            ? s.getDateHeureFin().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)   : null);
    
    // Cours
    if (s.getCours() != null) {
        Map<String, Object> coursDto = new HashMap<>();
        coursDto.put("id",         s.getCours().getId());
        coursDto.put("libelle",    s.getCours().getLibelle());
        coursDto.put("specialite", s.getCours().getSpecialite());
        dto.put("cours", coursDto);
    }

    // Professeur
    if (s.getProfesseur() != null) {
        Map<String, Object> profDto = new HashMap<>();
        profDto.put("id",     s.getProfesseur().getId());
        profDto.put("nom",    s.getProfesseur().getNom());
        profDto.put("prenom", s.getProfesseur().getPrenom());
        dto.put("professeur", profDto);
    }

    // Salle
    if (s.getSalle() != null) {
        Map<String, Object> salleDto = new HashMap<>();
        salleDto.put("id",  s.getSalle().getId());
        salleDto.put("nom", s.getSalle().getNom());
        dto.put("salle", salleDto);
    }

    return dto;
}
}