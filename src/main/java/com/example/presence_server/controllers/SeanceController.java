package com.example.presence_server.controllers;

import com.example.presence_server.models.*;
import com.example.presence_server.repositories.*;
import com.example.presence_server.services.EmailService; // <--- L'IMPORT QUI MANQUAIT EST ICI

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
    @Autowired private EmailService          emailService; // L'injection de ton service d'email

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

    // ── HELPER : Trouver les étudiants (La même logique que le Dashboard) ────
    // Le dashboard affiche les séances où le groupe de la séance CONTIENT la spécialité de l'étudiant.
    // Donc, pour notifier les bons étudiants, on récupère TOUS les étudiants,
    // et on filtre ceux dont la spécialité est contenue dans le champ 'groupe' de la séance.
    private List<Etudiant> getEtudiantsConcerneParSeance(Seance seance) {
        String groupeSeance = seance.getGroupe();
        if (groupeSeance == null || groupeSeance.isBlank()) {
            return new ArrayList<>();
        }
        
        List<Etudiant> tousLesEtudiants = etudiantRepository.findAll();
        List<Etudiant> etudiantsConcernes = new ArrayList<>();
        
        for (Etudiant etudiant : tousLesEtudiants) {
            String specialiteEtudiant = etudiant.getSpecialite();
            if (specialiteEtudiant != null && !specialiteEtudiant.isBlank()) {
                // Si la spécialité de l'étudiant est dans le nom du groupe de la séance (ex: "SITW" dans "SITW G1")
                // OU si le groupe de la séance est dans la spécialité de l'étudiant (plus rare)
                if (groupeSeance.toLowerCase().contains(specialiteEtudiant.toLowerCase()) || 
                    specialiteEtudiant.toLowerCase().contains(groupeSeance.toLowerCase())) {
                    etudiantsConcernes.add(etudiant);
                }
            }
        }
        return etudiantsConcernes;
    }

// ─────────────────────────────────────────────────────────────────────────
// GET /api/seances/prochain/{profId}
// Retourne la séance active OU la prochaine séance à venir du prof
// Retourne aussi les infos cours et salle pour le frontend
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
    dto.put("id",             s.getId());
    dto.put("titre",          s.getTitre());
    dto.put("typeSeance",     s.getTypeSeance());
    dto.put("groupe",         s.getGroupe());
    dto.put("jour",           s.getJour());
    dto.put("heurePlage",     s.getHeurePlage());
    dto.put("dateHeureDebut", s.getDateHeureDebut());
    dto.put("dateHeureFin",   s.getDateHeureFin());
    dto.put("estActive",      s.isEstActive());
    dto.put("estTerminee",    s.isEstTerminee());
    dto.put("numeroSeance",   s.getNumeroSeance());

    // Cours
    if (s.getCours() != null) {
        Map<String, Object> coursDto = new HashMap<>();
        coursDto.put("id",      s.getCours().getId());
        coursDto.put("libelle", s.getCours().getLibelle());
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

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/seances/{id}/lancer
    // Lance la séance + envoie une notification
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
        seance.setTokenLastRefreshedAt(now);   // ← SYNC TIMER
        seance.setPausedElapsedMs(0L);          // ← RESET ELAPSED

        if (localisation.containsKey("lat") && localisation.containsKey("lng")) {
            seance.setProfLat(localisation.get("lat"));
            seance.setProfLng(localisation.get("lng"));
        }

        Seance saved = seanceRepository.saveAndFlush(seance);

        try {
            String module  = seance.getCours()      != null ? seance.getCours().getLibelle() : "Module inconnu";
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
            // En pause : retourner le temps figé au moment de la pause
            elapsedMs = seance.getPausedElapsedMs() != null ? seance.getPausedElapsedMs() : 0L;
        } else if (seance.getTokenLastRefreshedAt() != null) {
            // En cours : calculer depuis le dernier refresh token
            elapsedMs = java.time.Duration.between(
                    seance.getTokenLastRefreshedAt(), now).toMillis();
        } else {
            elapsedMs = 0L;
        }

        Map<String, Object> state = new HashMap<>();
        state.put("token",         seance.getCurrentToken() != null ? seance.getCurrentToken() : "");
        state.put("isTimerPaused", seance.isTimerPaused());
        state.put("elapsedMs",     Math.min(elapsedMs, 10500L)); // cap à 10.5s
        state.put("estActive",     seance.isEstActive());
        state.put("estTerminee",   seance.isEstTerminee());

        return ResponseEntity.ok(state);
    }).orElse(ResponseEntity.notFound().build());
}


    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/seances/{id}/terminer
    // Termine la séance + supprime notifs lancement + envoie notifs de fin
    // ─────────────────────────────────────────────────────────────────────────
  @PutMapping("/{id}/terminer")
@Transactional
public ResponseEntity<?> terminerSeance(@PathVariable Long id) {

    return seanceRepository.findById(id).map(seance -> {

        if (seance.isEstTerminee()) {
            return ResponseEntity.ok(Map.of("message", "Déjà terminée"));
        }

        seance.setEstActive(false);
        seance.setEstTerminee(true);
        seance.setDateHeureFin(LocalDateTime.now());
        seanceRepository.saveAndFlush(seance);

        // ── RECYCLING : +7 jours EXACT sur datePlanifiee ─────────────────────
        try {
            if (seance.getJour() != null && seance.getHeurePlage() != null) {

                // La prochaine occurrence = datePlanifiee + 7 jours
                // (jamais basé sur "aujourd'hui", toujours sur la date prévue)
                LocalDateTime nextPlanifiee;
                if (seance.getDatePlanifiee() != null) {
                    nextPlanifiee = seance.getDatePlanifiee().plusDays(7);
                } else if (seance.getDateHeureDebut() != null) {
                    nextPlanifiee = seance.getDateHeureDebut().plusDays(7);
                } else {
                    LocalTime heure = LocalTime.parse(seance.getHeurePlage());
                    nextPlanifiee   = LocalDateTime.now().plusDays(7).with(heure);
                }

                Seance prochaine = new Seance();
                prochaine.setGroupe(seance.getGroupe());
                prochaine.setJour(seance.getJour());
                prochaine.setHeurePlage(seance.getHeurePlage());
                prochaine.setSalle(seance.getSalle());
                prochaine.setTypeSeance(seance.getTypeSeance());
                prochaine.setCours(seance.getCours());
                prochaine.setProfesseur(seance.getProfesseur());
                prochaine.setTitre(seance.getTitre());
                prochaine.setNumeroSeance(
                        seance.getNumeroSeance() != null ? seance.getNumeroSeance() + 1 : 2);
                prochaine.setDateHeureDebut(nextPlanifiee);
                prochaine.setDateHeureFin(nextPlanifiee.plusMinutes(90));
                prochaine.setDatePlanifiee(nextPlanifiee);
                prochaine.setEstActive(false);
                prochaine.setEstTerminee(false);
                seanceRepository.save(prochaine);

                System.out.println("🔄 [RECYCLING] Séance recréée le "
                        + nextPlanifiee.toLocalDate() + " à " + seance.getHeurePlage());
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Recycling : " + ex.getMessage());
        }

        // ── Notifications de fin ─────────────────────────────────────────────
        try {
            String module  = seance.getCours() != null ? seance.getCours().getLibelle() : "Module inconnu";
            Long   coursId = seance.getCours() != null ? seance.getCours().getId()       : null;
            String profNom = seance.getProfesseur() != null
                    ? "Prof. " + seance.getProfesseur().getPrenom() + " " + seance.getProfesseur().getNom()
                    : "votre professeur";

            notificationRepository.deleteBySeanceIdAndTypeNotification(seance.getId(), "LANCEMENT");

            List<Etudiant> etudiants = getEtudiantsConcerneParSeance(seance);
            for (Etudiant e : etudiants) {
                long absences = 0;
                if (coursId != null) {
                    long total = seanceRepository
                            .countByCours_IdAndGroupeAndEstTermineeTrue(coursId, seance.getGroupe());
                    long presences = presenceRepository
                            .countByEtudiant_IdAndSeance_Cours_IdAndStatutPresence(
                                    e.getId(), coursId, "PRESENT");
                    absences = Math.max(0, total - presences);
                }

                String gravite = calculerGravite(absences);
                String titre, message;

                if (absences == 0) {
                    titre   = "✅ Séance terminée — " + module;
                    message = "La séance de " + module + " est terminée. Félicitations, vous êtes présent(e) à toutes les séances !";
                } else {
                    titre   = (absences >= 4 ? "🔴" : absences >= 2 ? "🟠" : "🟡") + " Séance terminée — " + module;
                    message = "La séance de " + module + " est terminée. Vous avez "
                            + absences + " absence(s)."
                            + (absences >= 4 ? " Taux critique." : absences >= 2 ? " Régularisez votre assiduité." : " Justificatif si nécessaire.");

                    if (absences == 4 && e.getEmail() != null && !e.getEmail().isBlank()) {
                        emailService.envoyerAlerteAbsence(
                                e.getEmail(), e.getPrenom() + " " + e.getNom(), module, profNom, absences);
                    }
                }
                envoyerNotif(e, seance.getId(), "FIN", titre, message, gravite);
            }
        } catch (Exception ex) {
            System.err.println("⚠️ Notif fin : " + ex.getMessage());
        }

        return ResponseEntity.ok(Map.of("status", "success", "message", "Séance terminée."));

    }).orElse(ResponseEntity.notFound().build());
}
    // ─────────────────────────────────────────────────────────────────────────
    // Autres endpoints
    // ─────────────────────────────────────────────────────────────────────────

@PutMapping("/{id}/toggle-timer")
@Transactional
public ResponseEntity<Map<String, Object>> toggleTimer(@PathVariable Long id) {
    return seanceRepository.findById(id).map(seance -> {
        LocalDateTime now      = LocalDateTime.now();
        boolean       newPaused = !seance.isTimerPaused();

        if (newPaused) {
            // ── Mise en PAUSE : sauvegarder l'elapsed actuel ─────────────────
            long elapsed = seance.getTokenLastRefreshedAt() != null
                    ? java.time.Duration.between(seance.getTokenLastRefreshedAt(), now).toMillis()
                    : 0L;
            seance.setPausedElapsedMs(Math.min(elapsed, 10000L));
        } else {
            // ── REPRISE : recalculer tokenLastRefreshedAt pour que l'elapsed
            //    continue depuis le bon endroit ──────────────────────────────
            long pausedMs = seance.getPausedElapsedMs() != null ? seance.getPausedElapsedMs() : 0L;
            // tokenLastRefreshedAt = now - pausedElapsedMs
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

    // Utilisation de la nouvelle méthode du repository qui filtre directement par date
    List<Seance> toutes = seanceRepository.findByProfAndDatePlanifiee(profId, date);

    // Transformation en DTO pour l'affichage dans l'agenda
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

        // ── Protection race condition multi-device ────────────────────────────
        // Si le token vient d'être rafraîchi il y a moins de 8s → renvoyer le token actuel
        if (seance.getTokenLastRefreshedAt() != null) {
            long elapsed = java.time.Duration.between(
                    seance.getTokenLastRefreshedAt(), LocalDateTime.now()).toMillis();
            if (elapsed < 8000) {
                return ResponseEntity.ok(Map.of("token", seance.getCurrentToken()));
            }
        }

        // ── Générer et sauvegarder le nouveau token ───────────────────────────
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
// Helper commun : DTO propre pour les agendas (dates en ISO string, pas de ref circulaire)
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

    // Dates en ISO string pour que JavaScript les parse correctement
    dto.put("dateHeureDebut", s.getDateHeureDebut() != null
            ? s.getDateHeureDebut().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
    dto.put("dateHeureFin",   s.getDateHeureFin() != null
            ? s.getDateHeureFin().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)   : null);

    // Cours (sans relations circulaires)
    if (s.getCours() != null) {
        Map<String, Object> coursDto = new HashMap<>();
        coursDto.put("id",         s.getCours().getId());
        coursDto.put("libelle",    s.getCours().getLibelle());
        coursDto.put("specialite", s.getCours().getSpecialite());
        dto.put("cours", coursDto);
    }

    // Professeur (sans coursEnseignes)
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