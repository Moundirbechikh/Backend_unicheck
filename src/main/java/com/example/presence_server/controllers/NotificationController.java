package com.example.presence_server.controllers;

import com.example.presence_server.models.Notification;
import com.example.presence_server.models.Utilisateur;
import com.example.presence_server.repositories.NotificationRepository;
import com.example.presence_server.repositories.UserRepositorie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class NotificationController {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepositorie userRepositorie;

    // ── Helper : formater la date en "Il y a X min/h/j" ─────────────────────
    private String formatTemps(LocalDateTime date) {
        if (date == null) return "";
        LocalDateTime now = LocalDateTime.now();
        long minutes = ChronoUnit.MINUTES.between(date, now);
        long heures  = ChronoUnit.HOURS.between(date, now);
        long jours   = ChronoUnit.DAYS.between(date, now);

        if (minutes < 1)   return "À l'instant";
        if (minutes < 60)  return "Il y a " + minutes + " min";
        if (heures < 24)   return "Il y a " + heures + "h";
        if (jours == 1)    return "Hier";
        return "Il y a " + jours + " jours";
    }

    // ── Helper : construire le DTO notif ─────────────────────────────────────
    private Map<String, Object> toDto(Notification n) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",      n.getId());
        dto.put("titre",   n.getTitre() != null ? n.getTitre() : "Notification");
        dto.put("message", n.getMessage());
        dto.put("gravite", n.getGravite()); // VERT, ORANGE, ROUGE
        dto.put("estLue",  n.isEstLue());
        dto.put("time",    formatTemps(n.getDateCreation()));
        return dto;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/notifications/user/{userId}
    // Retourne toutes les notifications d'un utilisateur + son nom
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getNotificationsUser(@PathVariable Long userId) {

        Optional<Utilisateur> userOpt = userRepositorie.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Utilisateur introuvable."));
        }

        Utilisateur user = userOpt.get();
        List<Notification> notifs = notificationRepository
                .findByUtilisateurOrderByDateCreationDesc(user);

        long nonLues = notificationRepository.countByUtilisateurAndEstLueFalse(user);

        List<Map<String, Object>> liste = new ArrayList<>();
        for (Notification n : notifs) {
            liste.add(toDto(n));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("prenom",       user.getPrenom());
        result.put("nom",          user.getNom());
        result.put("notifications", liste);
        result.put("totalNonLues", nonLues);

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/notifications/{id}/lire
    // Marque une notification comme lue
    // ─────────────────────────────────────────────────────────────────────────
    @PutMapping("/{id}/lire")
    public ResponseEntity<Map<String, Object>> marquerLue(@PathVariable Long id) {
        Optional<Notification> opt = notificationRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Notification introuvable."));
        }
        Notification n = opt.get();
        n.setEstLue(true);
        notificationRepository.save(n);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT /api/notifications/user/{userId}/tout-lire
    // Marque toutes les notifications d'un user comme lues
    // ─────────────────────────────────────────────────────────────────────────
    @PutMapping("/user/{userId}/tout-lire")
    public ResponseEntity<Map<String, Object>> marquerToutLu(@PathVariable Long userId) {
        Optional<Utilisateur> userOpt = userRepositorie.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "Utilisateur introuvable."));
        }
        List<Notification> nonLues = notificationRepository
                .findByUtilisateurAndEstLueFalse(userOpt.get());
        for (Notification n : nonLues) {
            n.setEstLue(true);
        }
        notificationRepository.saveAll(nonLues);
        return ResponseEntity.ok(Map.of("success", true, "marquees", nonLues.size()));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/notifications/envoyer
    // Crée une notification pour un utilisateur (usage interne / admin)
    // Body : { userId, titre, message, gravite }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/envoyer")
    public ResponseEntity<Map<String, Object>> envoyerNotification(
            @RequestBody Map<String, Object> body) {

        try {
            Long userId   = Long.valueOf(body.get("userId").toString());
            String titre   = body.getOrDefault("titre",   "Notification").toString();
            String message = body.get("message").toString();
            String gravite = body.getOrDefault("gravite", "VERT").toString();

            Optional<Utilisateur> userOpt = userRepositorie.findById(userId);
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Utilisateur introuvable."));
            }

            Notification n = new Notification();
            n.setUtilisateur(userOpt.get());
            n.setTitre(titre);
            n.setMessage(message);
            n.setGravite(gravite);
            n.setDateCreation(LocalDateTime.now());
            n.setEstLue(false);
            notificationRepository.save(n);

            return ResponseEntity.ok(Map.of("success", true, "message", "Notification envoyée."));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", "Requête invalide : " + e.getMessage()));
        }
    }
}