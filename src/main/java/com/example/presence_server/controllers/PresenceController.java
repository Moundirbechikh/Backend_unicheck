package com.example.presence_server.controllers;

import com.example.presence_server.dto.ScanRequestDTO;
import com.example.presence_server.models.*;
import com.example.presence_server.repositories.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate; // Import ajouté pour corriger l'erreur
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/presences")
public class PresenceController {

    @Autowired private PresenceRepository  presenceRepository;
    @Autowired private SeanceRepository    seanceRepository;
    @Autowired private EtudiantRepository  etudiantRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/presences/scan-carte
    // Le professeur scanne la carte étudiant (QR code fixe).
    // Body : { "codeQrFixe": "...", "seanceId": 123 }
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/scan-carte")
    public ResponseEntity<Map<String, Object>> scanCarteEtudiant(
            @RequestBody Map<String, Object> body) {

        Map<String, Object> response = new HashMap<>();
        try {
            String codeQrFixe = body.get("codeQrFixe") != null
                    ?
            body.get("codeQrFixe").toString().trim() : null;
            Long seanceId = body.get("seanceId") != null
                    ?
            Long.valueOf(body.get("seanceId").toString()) : null;

            // ── Validations de base ──────────────────────────────────────────
            if (codeQrFixe == null || codeQrFixe.isBlank()) {
                response.put("success", false);
                response.put("message", "Code QR vide ou invalide.");
                return ResponseEntity.badRequest().body(response);
            }

            if (seanceId == null) {
                response.put("success", false);
                response.put("message", "ID de séance manquant.");
                return ResponseEntity.badRequest().body(response);
            }

            // ── Recherche de la séance ───────────────────────────────────────
            Optional<Seance> seanceOpt = seanceRepository.findById(seanceId);
            if (seanceOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Séance introuvable.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Seance seance = seanceOpt.get();
            if (!seance.isEstActive() || seance.isEstTerminee()) {
                response.put("success", false);
                response.put("message", "La séance n'est pas active.");
                return ResponseEntity.badRequest().body(response);
            }

            // ── Recherche de l'étudiant par codeQrFixe ───────────────────────
            Optional<Etudiant> etudiantOpt = etudiantRepository.findByCodeQrFixe(codeQrFixe);
            if (etudiantOpt.isEmpty()) {
                System.out.println("⚠️ [SCAN-CARTE] Code QR non reconnu : " + codeQrFixe);
                response.put("success", false);
                response.put("message", "Aucun étudiant trouvé pour ce code carte.");
                response.put("codeScanne", codeQrFixe);
                // Pour debug frontend
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            Etudiant etudiant = etudiantOpt.get();
            System.out.println("✅ [SCAN-CARTE] Étudiant trouvé : "
                    + etudiant.getPrenom() + " " + etudiant.getNom()
                    + " (ID: " + etudiant.getId() + ")");
            // ── Anti-doublon ─────────────────────────────────────────────────
            if (presenceRepository.findByEtudiant_IdAndSeance_Id(
                    etudiant.getId(), seanceId).isPresent()) {
                response.put("success", false);
                response.put("message", etudiant.getPrenom() + " " + etudiant.getNom()
                        + " est déjà marqué présent.");
                response.put("etudiantNom", etudiant.getPrenom() + " " + etudiant.getNom());
                return ResponseEntity.ok(response);
            }

            // ── Enregistrement de la présence ────────────────────────────────
            Presence presence = new Presence();
            presence.setEtudiant(etudiant);
            presence.setSeance(seance);
            presence.setHeurePointage(LocalDateTime.now());
            presence.setMethodeScan("CARTE_QR");
            presence.setStatutPresence("PRESENT");
            presenceRepository.save(presence);

            response.put("success",      true);
            response.put("message",      "Présence de " + etudiant.getPrenom()
                                         + " " + etudiant.getNom() + " validée !");
            response.put("etudiantNom",  etudiant.getPrenom() + " " + etudiant.getNom());
            response.put("etudiantId",   etudiant.getId());
            response.put("heure",        LocalDateTime.now().format(
                    java.time.format.DateTimeFormatter.ofPattern("HH:mm")));
            System.out.println("✅ [SCAN-CARTE] Présence enregistrée pour "
                    + etudiant.getPrenom() + " " + etudiant.getNom());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ [SCAN-CARTE] Erreur : " + e.getMessage());
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Erreur serveur : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tous les autres endpoints inchangés
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/dashboard-stats/{etudiantId}")
    public ResponseEntity<Map<String, Object>> getDashboardStats(@PathVariable Long etudiantId) {
        Optional<Etudiant> etudiantOpt = etudiantRepository.findById(etudiantId);
        if (etudiantOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

        Etudiant etudiant = etudiantOpt.get();
        String specialite = etudiant.getSpecialite();
        if (specialite == null || specialite.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                "pourcentagePresence", 0, "totalAbsences", 0,
                "specialite", "Non définie", "rang", 0, "totalEtudiants", 0
            ));
        }

        List<Seance> seancesTerminees = seanceRepository
                .findByGroupeContainingIgnoreCaseAndEstTermineeTrue(specialite);
        if (seancesTerminees.isEmpty())
            seancesTerminees = seanceRepository.findByCours_SpecialiteAndEstTermineeTrue(specialite);

        long totalSeancesGlobal = seancesTerminees.size();
        List<Etudiant> tousLesEtudiants = etudiantRepository.findBySpecialite(specialite);
        int totalEtudiants = tousLesEtudiants.size();

        Map<Long, Long> scoresPresences = new HashMap<>();
        for (Etudiant e : tousLesEtudiants) scoresPresences.put(e.getId(), 0L);

        for (Seance s : seancesTerminees) {
            for (Presence p : presenceRepository.findBySeance_Id(s.getId())) {
                if ("PRESENT".equals(p.getStatutPresence())
                        && scoresPresences.containsKey(p.getEtudiant().getId())) {
                    scoresPresences.merge(p.getEtudiant().getId(), 1L, Long::sum);
                }
            }
        }

        long mesPresences = scoresPresences.getOrDefault(etudiantId, 0L);
        long mesAbsences  = totalSeancesGlobal - mesPresences;
        long pourcentage  = totalSeancesGlobal > 0
                ?
        Math.round(((double) mesPresences / totalSeancesGlobal) * 100) : 0;

        List<Long> scoresTries = scoresPresences.values().stream()
                .sorted(Comparator.reverseOrder()).collect(Collectors.toList());
        int rang = scoresTries.indexOf(mesPresences) + 1;

        return ResponseEntity.ok(Map.of(
            "pourcentagePresence", pourcentage,
            "totalAbsences",       Math.max(0, mesAbsences),
            "specialite",          specialite,
            "rang",                rang,
          
            "totalEtudiants",      totalEtudiants
        ));
    }

// ─────────────────────────────────────────────────────────────────────────
// POST /api/presences/scan
// Validation en 3 conditions : Token + GPS (≤ 20m) + Device ID
// ─────────────────────────────────────────────────────────────────────────
@PostMapping("/scan")
public ResponseEntity<Map<String, Object>> validerScan(
        @RequestBody ScanRequestDTO request, HttpServletRequest httpRequest) {

    Map<String, Object> response = new HashMap<>();
    try {
        // ── 1. Résoudre etudiantId ──────────────────────────────────────────
        Long etudiantId = request.getStudentId();
        if (etudiantId == null) {
            Integer uid = (Integer) httpRequest.getAttribute("userId");
            if (uid != null) etudiantId = uid.longValue();
            else {
                response.put("success", false);
                response.put("message", "ID étudiant manquant.");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
        }

        Optional<Etudiant> etudiantOpt = etudiantRepository.findById(etudiantId);
        if (etudiantOpt.isEmpty()) {
            response.put("success", false);
            response.put("message", "Étudiant introuvable.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        Etudiant etudiant = etudiantOpt.get();

        // ── 2. Chercher la séance active par token EXACT (Code Mis à Jour) ──
        // Nettoyage et passage en majuscules pour éviter les erreurs de saisie
        String tokenRecu = request.getToken() != null 
                ? request.getToken().trim().toUpperCase() : "";

        List<Seance> seancesActives = seanceRepository
                .findByCurrentTokenAndEstActiveTrue(tokenRecu);

        if (seancesActives.isEmpty()) {
            response.put("success", false);
            response.put("message",
                "Code expiré ou invalide. Le code change toutes les 10 secondes — " +
                "attendez le prochain code affiché et réessayez.");
            return ResponseEntity.ok(response);
        }

        Seance seance = seancesActives.get(0);

        // ── 3. Anti-doublon ─────────────────────────────────────────────────
        if (presenceRepository.findByEtudiant_IdAndSeance_Id(
                etudiantId, seance.getId()).isPresent()) {
            response.put("success", false);
            response.put("message", "Vous avez déjà pointé pour cette séance.");
            return ResponseEntity.ok(response);
        }

        // ── 4. GPS : Seuil strict (100m) ───────────────────────────────────
        double distanceCalculee = 0;

        boolean etudiantAGps = request.getStudentLat() != null
                && request.getStudentLng() != null
                && !(request.getStudentLat() == 0.0 && request.getStudentLng() == 0.0);

        boolean profAGps = seance.getProfLat() != null && seance.getProfLng() != null
                && !(seance.getProfLat() == 0.0 && seance.getProfLng() == 0.0);

        if (etudiantAGps && profAGps) {
            distanceCalculee = calculerDistance(
                    request.getStudentLat(), request.getStudentLng(),
                    seance.getProfLat(),     seance.getProfLng()
            );

            // Seuil mis à jour à 100m (idéal pour couvrir le bâtiment et bloquer les fraudes)
            if (distanceCalculee > 100.0) {
                response.put("success", false);
                response.put("message", String.format(
                    "Vous êtes trop loin du cours (%.0fm détectés, max 100m). " +
                    "Assurez-vous d'être dans le bâtiment et réessayez.",
                    distanceCalculee));
                response.put("distanceM", Math.round(distanceCalculee));
                return ResponseEntity.ok(response);
            }
        }
        // GPS absent d'un côté → mode dégradé, on laisse passer pour éviter de bloquer la classe

        // ── 5. Device ID ────────────────────────────────────────────────────
        String deviceIdRecu = request.getDeviceId();
        if (deviceIdRecu != null && !deviceIdRecu.isBlank()
                && !"TEST_MODE".equals(deviceIdRecu)) {

            if (etudiant.getDeviceId() == null || etudiant.getDeviceId().isBlank()) {
                // Premier pointage : on lie définitivement l'appareil à l'étudiant
                etudiant.setDeviceId(deviceIdRecu);
                etudiantRepository.save(etudiant);
            } else if (!etudiant.getDeviceId().equals(deviceIdRecu)) {
                response.put("success", false);
                response.put("message",
                    "Appareil non reconnu. Utilisez l'appareil avec lequel " +
                    "vous avez effectué votre premier pointage.");
                return ResponseEntity.ok(response);
            }
        }

        // ── 6. Enregistrement de la présence ───────────────────────────────
        Presence presence = new Presence();
        presence.setEtudiant(etudiant);
        presence.setSeance(seance);
        presence.setHeurePointage(LocalDateTime.now());
        presence.setMethodeScan("DYNAMIQUE");
        presence.setStatutPresence("PRESENT");

        if (etudiantAGps) {
            presence.setStudentLat(request.getStudentLat());
            presence.setStudentLng(request.getStudentLng());
        }
        if (deviceIdRecu != null && !deviceIdRecu.isBlank()) {
            presence.setDeviceUsed(deviceIdRecu);
        }

        presenceRepository.save(presence);

        String heure = LocalDateTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("HH:mm"));

        // Préparation de la réponse de succès
        response.put("success", true);
        response.put("message", "Présence validée !");
        response.put("heure", heure);
        if (distanceCalculee > 0) {
            response.put("distanceM", Math.round(distanceCalculee));
        }

        // Log de suivi dans la console du serveur Render
        System.out.printf("✅ [SCAN] %s %s | Séance %d | %.1fm%n",
                etudiant.getPrenom(), etudiant.getNom(),
                seance.getId(), distanceCalculee);

        return ResponseEntity.ok(response);

    } catch (Exception e) {
        e.printStackTrace();
        response.put("success", false);
        response.put("message", "Erreur serveur : " + e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Helper indispensable : Calcul de distance (formule de Haversine) en mètres
// ─────────────────────────────────────────────────────────────────────────
private double calculerDistance(double lat1, double lng1, double lat2, double lng2) {
    final double R = 6371000.0; // Rayon de la Terre en mètres
    double dLat    = Math.toRadians(lat2 - lat1);
    double dLng    = Math.toRadians(lng2 - lng1);
    double a       = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                   + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                   * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    double c       = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c;
}
@GetMapping("/seance/{seanceId}")
public ResponseEntity<List<Map<String, Object>>> getPresencesBySeance(
        @PathVariable Long seanceId) {
    try {
        List<Presence> presences = presenceRepository.findBySeance_Id(seanceId);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Presence p : presences) {
            Etudiant e = p.getEtudiant();
            Map<String, Object> map = new HashMap<>();

            // ── Identité ─────────────────────────────────────────────────
            map.put("id",         e.getId());
            map.put("nom",        e.getNom());
            map.put("prenom",     e.getPrenom());
            map.put("name",       e.getPrenom() + " " + e.getNom());
            map.put("email",      e.getEmail() != null ? e.getEmail() : "—");
            map.put("matricule",  e.getMatricule() != null ? e.getMatricule() : "—");
            map.put("specialite", e.getSpecialite() != null ? e.getSpecialite() : "—");
            map.put("groupe",     e.getGroupe() != null ? e.getGroupe() : "—");
            map.put("deviceId",   e.getDeviceId() != null ? e.getDeviceId() : "—");
            // Initiales pour l'avatar
            String initials = "";
            if (e.getPrenom() != null && e.getNom() != null
                    && !e.getPrenom().isBlank() && !e.getNom().isBlank()) {
                initials = (e.getPrenom().substring(0, 1)
                          + e.getNom().substring(0, 1)).toUpperCase();
            }
            map.put("initials", initials);
            // ── Pointage ─────────────────────────────────────────────────
            map.put("time", p.getHeurePointage() != null
                    ? String.format("%02d:%02d",
                        p.getHeurePointage().getHour(),
                        p.getHeurePointage().getMinute())
             
                    : "--:--");
            map.put("methodeScan",    p.getMethodeScan());
            map.put("statutPresence", p.getStatutPresence());

            result.add(map);
        }
        return ResponseEntity.ok(result);
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ArrayList<>());
    }
}

   // ─────────────────────────────────────────────────────────────────────────
    // NOUVEAU : Obtenir les étudiants d'un professeur avec leurs absences
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/prof/{profId}/mes-etudiants")
    public ResponseEntity<List<Map<String, Object>>> getMesEtudiants(@PathVariable Long profId) {
        try {
            // 1. Récupérer toutes les séances du professeur
            List<Seance> seancesDuProf = seanceRepository.findAll().stream()
                    
                    .filter(s -> s.getProfesseur() != null && s.getProfesseur().getId().equals(profId))
                    .collect(Collectors.toList());
            // 2. Extraire les mots-clés des groupes (ex: "SITW", "1ère Ingénieur") enseignés
            Set<String> motsClesEnseignes = seancesDuProf.stream()
                    .map(Seance::getGroupe)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
              
                    .collect(Collectors.toSet());

            // 3. Récupérer tous les étudiants pour filtrage
            List<Etudiant> tousEtudiants = etudiantRepository.findAll();
            List<Map<String, Object>> result = new ArrayList<>();

            for (Etudiant e : tousEtudiants) {
                String spe = e.getSpecialite() != null ?
                e.getSpecialite() : "";
                String grp = e.getGroupe() != null ? e.getGroupe() : "";
                String classeEtudiant = spe + (grp.isEmpty() ? "" : " - " + grp);
                // L'étudiant est concerné si sa spécialité apparaît dans les groupes enseignés par le prof
                boolean isMyStudent = motsClesEnseignes.stream().anyMatch(g -> 
                    g.contains(spe.toLowerCase()) || spe.toLowerCase().contains(g)
                );
                if (isMyStudent && !spe.isBlank()) {
                    // Calcul des absences : on prend les séances TERMINÉES de CE prof pour CE groupe
                    List<Seance> seancesConcernees = seancesDuProf.stream()
                            .filter(Seance::isEstTerminee)
          
                            .filter(s -> s.getGroupe() != null && s.getGroupe().toLowerCase().contains(spe.toLowerCase()))
                            .collect(Collectors.toList());
                    long totalSeances = seancesConcernees.size();
                    long presences = 0;

                    for (Seance s : seancesConcernees) {
                        if (presenceRepository.findByEtudiant_IdAndSeance_Id(e.getId(), s.getId()).isPresent()) {
                            presences++;
                        }
                    }

                    long absences = Math.max(0, totalSeances - presences);
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", e.getId());
                    
                    // --- AJOUTS ICI POUR LE FRONTEND ---
                    map.put("nom", e.getNom());
                    map.put("prenom", e.getPrenom());
                    map.put("email", e.getEmail());
                    map.put("deviceId", e.getDeviceId());
                    // -----------------------------------

                    map.put("name", e.getPrenom() + " " + e.getNom());
                    map.put("class", classeEtudiant);
                    map.put("specialite", spe);
                    map.put("groupe", grp);
                    map.put("absences", absences);
                    
                    String initials = "";
                    if (e.getPrenom() != null && e.getNom() != null && !e.getPrenom().isBlank() && !e.getNom().isBlank()) {
                         initials = (e.getPrenom().substring(0, 1) + e.getNom().substring(0, 1)).toUpperCase();
                    }
                    map.put("initials", initials);
                    // Attribution de la couleur selon l'assiduité
                    if (absences >= 3) {
                        map.put("color", "bg-red-100 text-red-600");
                    } else if (absences > 0) {
                        map.put("color", "bg-orange-100 text-orange-600");
                    } else {
                        map.put("color", "bg-[#d1f4e0] text-[#006c49]");
                    }

                    result.add(map);
                }
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(new ArrayList<>());
        }
    }
    
    @PostMapping("/manuel")
    public ResponseEntity<Map<String, Object>> pointerManuel(
            @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            Long etudiantId = Long.valueOf(request.get("etudiantId").toString());
            Long seanceId   = Long.valueOf(request.get("seanceId").toString());

            Optional<Etudiant> etudiantOpt = etudiantRepository.findById(etudiantId);
            Optional<Seance>   seanceOpt   = seanceRepository.findById(seanceId);
            if (etudiantOpt.isEmpty() || seanceOpt.isEmpty()) {
                response.put("success", false);
                response.put("message", "Étudiant ou séance introuvable.");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            if (presenceRepository.findByEtudiant_IdAndSeance_Id(
                    etudiantId, seanceId).isPresent()) {
                response.put("success", false);
                response.put("message", "L'étudiant est déjà marqué présent.");
                return ResponseEntity.ok(response);
            }

            Presence presence = new Presence();
            presence.setEtudiant(etudiantOpt.get());
            presence.setSeance(seanceOpt.get());
            presence.setHeurePointage(LocalDateTime.now());
            presence.setMethodeScan("MANUELLE");
            presence.setStatutPresence("PRESENT");
            presenceRepository.save(presence);

            response.put("success", true);
            response.put("message", "Présence manuelle enregistrée avec succès.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Erreur lors de l'enregistrement manuel.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

   // ─────────────────────────────────────────────────────────────────────────
// GET /api/presences/stats/etudiant/{etudiantId}
// Stats par (module + typeSeance + professeur) — logique crédible
// ─────────────────────────────────────────────────────────────────────────
@GetMapping("/stats/etudiant/{etudiantId}")
public ResponseEntity<List<Map<String, Object>>> getStatsParCours(
        @PathVariable Long etudiantId) {

    Optional<Etudiant> etudiantOpt = etudiantRepository.findById(etudiantId);
    if (etudiantOpt.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

    Etudiant etudiant   = etudiantOpt.get();
    String maSpecialite = etudiant.getSpecialite();
    String monGroupe    = etudiant.getGroupe(); // ex: "G1"

    if (maSpecialite == null || maSpecialite.isEmpty())
        return ResponseEntity.ok(new ArrayList<>());

    // Toutes les séances terminées de la spécialité (pour cross-group check)
    List<Seance> toutesSeancesTerminees = seanceRepository
            .findByGroupeContainingIgnoreCaseAndEstTermineeTrue(maSpecialite);
    if (toutesSeancesTerminees.isEmpty())
        toutesSeancesTerminees = seanceRepository
                .findByCours_SpecialiteAndEstTermineeTrue(maSpecialite);
    if (toutesSeancesTerminees.isEmpty()) return ResponseEntity.ok(new ArrayList<>());

    // ── Filtrer les séances du GROUPE de l'étudiant uniquement ───────────────────
    List<Seance> mesSeancesTerminees;
    if (monGroupe != null && !monGroupe.isBlank() && maSpecialite != null) {
        final String grpNum = monGroupe.trim();    // ex: "G1"
        final String spe    = maSpecialite.trim(); // ex: "SITW"
        mesSeancesTerminees = toutesSeancesTerminees.stream()
                .filter(s -> {
                    String sg = s.getGroupe();
                    if (sg == null) return false;
                    // "G1 SITW" → premier token = "G1", reste contient "SITW"
                    String[] parts = sg.split("\\s+", 2);
                    return parts.length >= 1
                            && parts[0].equalsIgnoreCase(grpNum)
                            && sg.toLowerCase().contains(spe.toLowerCase());
                })
                .collect(Collectors.toList());

        // Fallback si filtre trop strict (groupe non renseigné)
        if (mesSeancesTerminees.isEmpty()) {
            mesSeancesTerminees = toutesSeancesTerminees;
        }
    } else {
        mesSeancesTerminees = toutesSeancesTerminees;
    }

    // ── Grouper par (coursId + typeSeance + profId) sur les séances du groupe ───
    Map<String, List<Seance>> seancesParGroupe = new LinkedHashMap<>();
    for (Seance s : mesSeancesTerminees) {
        if (s.getCours() == null) continue;
        String type   = s.getTypeSeance() != null ? s.getTypeSeance() : "COURS";
        String profId = s.getProfesseur() != null ? s.getProfesseur().getId().toString() : "0";
        String key    = s.getCours().getId() + "_" + type + "_" + profId;
        seancesParGroupe.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    }

    List<Map<String, Object>> stats = new ArrayList<>();

    for (Map.Entry<String, List<Seance>> entry : seancesParGroupe.entrySet()) {
        List<Seance> seancesDuGroupe = entry.getValue();
        if (seancesDuGroupe.isEmpty()) continue;

        Seance     firstSeance = seancesDuGroupe.get(0);
        Cours      cours       = firstSeance.getCours();
        String     typeSeance  = firstSeance.getTypeSeance() != null
                                 ? firstSeance.getTypeSeance() : "COURS";
        Professeur prof        = firstSeance.getProfesseur();

        long totalSeancesFaites = seancesDuGroupe.size();
        long totalPresences     = 0;

        for (Seance s : seancesDuGroupe) {

            // 1. Présence directe
            if (presenceRepository.findByEtudiant_IdAndSeance_Id(
                    etudiantId, s.getId()).isPresent()) {
                totalPresences++;
            } else if (cours != null && prof != null) {
                // 2. Présence cross-group : même jour + même cours + même prof + même type
                LocalDateTime refDate = s.getDatePlanifiee() != null
                        ? s.getDatePlanifiee() : s.getDateHeureDebut();
                if (refDate != null) {
                    final LocalDate jourSeance  = refDate.toLocalDate();
                    final Long      coursIdFinal = cours.getId();
                    final Long      profIdFinal  = prof.getId();
                    boolean crossPresent = toutesSeancesTerminees.stream()
                            .filter(other -> !other.getId().equals(s.getId()))
                            .filter(other -> other.getCours() != null
                                    && other.getCours().getId().equals(coursIdFinal))
                            .filter(other -> other.getProfesseur() != null
                                    && other.getProfesseur().getId().equals(profIdFinal))
                            .filter(other -> typeSeance.equals(other.getTypeSeance()))
                            .filter(other -> {
                                LocalDateTime d = other.getDatePlanifiee() != null
                                        ? other.getDatePlanifiee() : other.getDateHeureDebut();
                                return d != null && d.toLocalDate().equals(jourSeance);
                            })
                            .anyMatch(other -> presenceRepository
                                    .findByEtudiant_IdAndSeance_Id(etudiantId, other.getId())
                                    .isPresent());
                    if (crossPresent) totalPresences++;
                }
            }
        }

        double pourcentage = totalSeancesFaites > 0
                ? ((double) totalPresences / totalSeancesFaites) * 100 : 0;

        Map<String, Object> stat = new HashMap<>();
        stat.put("coursId",           cours.getId());
        stat.put("coursNom",          cours.getLibelle());
        stat.put("typeSeance",        typeSeance);
        stat.put("totalSeancesFaites",totalSeancesFaites);
        stat.put("presencesEtudiant", totalPresences);
        stat.put("absencesEtudiant",  Math.max(0, totalSeancesFaites - totalPresences));
        stat.put("pourcentage",       Math.round(pourcentage * 100.0) / 100.0);
        stat.put("professeurNom",     prof != null ? prof.getPrenom() + " " + prof.getNom() : "Inconnu");
        stat.put("professeurId",      prof != null ? prof.getId() : null);
        stats.add(stat);
    }

    Map<String, Integer> typeOrder = Map.of("COURS", 0, "TD", 1, "TP", 2);
    stats.sort((a, b) -> {
        int cmp = a.get("coursNom").toString()
                   .compareToIgnoreCase(b.get("coursNom").toString());
        if (cmp != 0) return cmp;
        return Integer.compare(
            typeOrder.getOrDefault(a.get("typeSeance").toString(), 9),
            typeOrder.getOrDefault(b.get("typeSeance").toString(), 9)
        );
    });

    return ResponseEntity.ok(stats);
}
// ─────────────────────────────────────────────────────────────────────────
// Helper : étudiant est-il présent pour un slot ?
// (inclut les groupes croisés)
// ─────────────────────────────────────────────────────────────────────────
private boolean estPresentPourSeance(Long etudiantId, Seance seance,
                                     List<Seance> toutesSeancesTerminees) {
    // Présence directe
    if (presenceRepository.findByEtudiant_IdAndSeance_Id(
            etudiantId, seance.getId()).isPresent()) {
        return true;
    }
    if (seance.getCours() == null || seance.getProfesseur() == null) return false;
    // Séance de référence : date planifiée ou dateHeureDebut
    LocalDateTime refDate = seance.getDatePlanifiee() != null
            ?
    seance.getDatePlanifiee() : seance.getDateHeureDebut();
    if (refDate == null) return false;

    final LocalDate jourSeance = refDate.toLocalDate();
    final Long      coursId    = seance.getCours().getId();
    final Long      profId     = seance.getProfesseur().getId();
    final String    type       = seance.getTypeSeance();
    // Cherche présence dans séances équivalentes (même cours + prof + type, même jour)
    return toutesSeancesTerminees.stream()
        .filter(s -> !s.getId().equals(seance.getId()))
        .filter(s -> s.getCours() != null && s.getCours().getId().equals(coursId))
        .filter(s -> s.getProfesseur() != null && s.getProfesseur().getId().equals(profId))
        .filter(s -> type != null && type.equals(s.getTypeSeance()))
        .filter(s -> {
            LocalDateTime d = s.getDatePlanifiee() != null
   
                 ? s.getDatePlanifiee() : s.getDateHeureDebut();
            return d != null && d.toLocalDate().equals(jourSeance);
        })
        .anyMatch(s -> presenceRepository
                .findByEtudiant_IdAndSeance_Id(etudiantId, s.getId()).isPresent());
    }

// ─────────────────────────────────────────────────────────────────────────
// GET /api/presences/prof/{profId}/stats-modules
// Retourne les stats par (module + type + groupe) pour un prof
// ─────────────────────────────────────────────────────────────────────────
@GetMapping("/prof/{profId}/stats-modules")
public ResponseEntity<List<Map<String, Object>>> getStatsByModule(
        @PathVariable Long profId) {
    try {
        List<Seance> toutesTerminees = seanceRepository
                .findByProfesseur_IdAndEstTermineeTrue(profId);
        // Grouper par (coursId + typeSeance + groupe)
        Map<String, List<Seance>> parGroupe = new LinkedHashMap<>();
        for (Seance s : toutesTerminees) {
            if (s.getCours() == null) continue;
            String type   = s.getTypeSeance() != null ? s.getTypeSeance() : "COURS";
            String groupe = s.getGroupe()     != null ? s.getGroupe()     : "Inconnu";
            String key    = s.getCours().getId() + "_" + type + "_" + groupe;
            parGroupe.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        List<Etudiant> tousEtudiants = etudiantRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<Seance>> entry : parGroupe.entrySet()) {
            List<Seance> seances = entry.getValue();
            if (seances.isEmpty()) continue;

            Seance firstSeance = seances.get(0);
            Cours  cours       = firstSeance.getCours();
            String type        = firstSeance.getTypeSeance() != null ? firstSeance.getTypeSeance() : "COURS";
            String groupe      = firstSeance.getGroupe()     != null ?
            firstSeance.getGroupe()     : "Inconnu";

            // Étudiants dont la spécialité est dans ce groupe
            List<Map<String, Object>> statsEtudiants = new ArrayList<>();
            for (Etudiant e : tousEtudiants) {
                String spe = e.getSpecialite();
                if (spe == null || spe.isBlank()) continue;
                if (!groupe.toLowerCase().contains(spe.toLowerCase())) continue;

                long totalSeances = seances.size();
                long presences    = 0;
                for (Seance s : seances) {
                    if (estPresentPourSeance(e.getId(), s, toutesTerminees)) presences++;
                }
                long absences = Math.max(0, totalSeances - presences);
                long pct      = totalSeances > 0
                        ?
                Math.round((double) presences / totalSeances * 100) : 0;

                String initials = (e.getPrenom() != null && e.getNom() != null)
                        ?
                (e.getPrenom().substring(0, 1) + e.getNom().substring(0, 1)).toUpperCase()
                        : "?";
                String color = absences >= 3
                        ?
                "bg-red-100 text-red-600"
                        : absences > 0 ?
                "bg-orange-100 text-orange-600"
                        : "bg-[#d1f4e0] text-[#006c49]";
                Map<String, Object> em = new HashMap<>();
                em.put("id",          e.getId());
                em.put("nom",         e.getNom());
                em.put("prenom",      e.getPrenom());
                em.put("name",        e.getPrenom() + " " + e.getNom());
                em.put("email",       e.getEmail()    != null ? e.getEmail()    : "—");
                em.put("matricule",   e.getMatricule()!= null ? e.getMatricule():"—");
                em.put("specialite",  spe);
                em.put("groupe",      e.getGroupe()   != null ? e.getGroupe()   : "—");
                em.put("initials",    initials);
                em.put("color",       color);
                em.put("presences",   presences);
                em.put("absences",    absences);
                em.put("totalSeances",totalSeances);
                em.put("pourcentage", pct);
                statsEtudiants.add(em);
            }

            // Trier par absences décroissantes
            statsEtudiants.sort((a, b) ->
                    Long.compare((Long) b.get("absences"), (Long) a.get("absences")));
            long atRisk = statsEtudiants.stream()
                    .filter(em -> (Long) em.get("absences") >= 3).count();
            Map<String, Object> moduleMap = new HashMap<>();
            moduleMap.put("coursId",     cours.getId());
            moduleMap.put("coursNom",    cours.getLibelle());
            moduleMap.put("typeSeance",  type);
            moduleMap.put("groupe",      groupe);
            moduleMap.put("totalSeances",(long) seances.size());
            moduleMap.put("atRiskCount", atRisk);
            moduleMap.put("etudiants",   statsEtudiants);
            result.add(moduleMap);
        }

        // Trier par nom de cours puis type
        Map<String, Integer> typeOrder = Map.of("COURS", 0, "TD", 1, "TP", 2);
        result.sort((a, b) -> {
            int cmp = a.get("coursNom").toString()
                       .compareToIgnoreCase(b.get("coursNom").toString());
            if (cmp != 0) return cmp;
            return Integer.compare(
                typeOrder.getOrDefault(a.get("typeSeance").toString(), 9),
            
                typeOrder.getOrDefault(b.get("typeSeance").toString(), 9));
        });

        return ResponseEntity.ok(result);
    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(500).body(new ArrayList<>());
    }
}
}