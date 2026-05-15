package com.example.presence_server.controllers;

import com.example.presence_server.models.*;
import com.example.presence_server.repositories.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/justificatifs")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class JustificatifController {

    @Autowired private JustificatifRepository  justificatifRepository;
    @Autowired private EtudiantRepository      etudiantRepository;
    @Autowired private SeanceRepository        seanceRepository;
    @Autowired private PresenceRepository      presenceRepository;
    @Autowired private UserRepositorie         userRepositorie;
    @Autowired private NotificationRepository  notificationRepository;

    // Dossier de stockage des fichiers
    private static final String UPLOAD_DIR = "./uploads/justificatifs/";

    // ─────────────────────────────────────────────────────────────────────────
    // Helper : envoyer une notification
    // ─────────────────────────────────────────────────────────────────────────
    private void envoyerNotif(Utilisateur user, String titre,
                               String message, String gravite) {
        Notification n = new Notification();
        n.setUtilisateur(user);
        n.setTitre(titre);
        n.setMessage(message);
        n.setGravite(gravite);
        n.setDateCreation(LocalDateTime.now());
        n.setEstLue(false);
        notificationRepository.save(n);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GET /api/justificatifs/etudiant/{id}/seances-absentes
    //    Retourne les séances terminées où l'étudiant était absent
    //    ET n'a pas encore soumis de justificatif
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/etudiant/{etudiantId}/seances-absentes")
    public ResponseEntity<List<Map<String, Object>>> getSeancesAbsentes(
            @PathVariable Long etudiantId) {

        Optional<Etudiant> etudiantOpt = etudiantRepository.findById(etudiantId);
        if (etudiantOpt.isEmpty()) return ResponseEntity.notFound().build();

        Etudiant etudiant = etudiantOpt.get();
        String specialite = etudiant.getSpecialite();
        if (specialite == null || specialite.isBlank())
            return ResponseEntity.ok(new ArrayList<>());

        // Toutes les séances terminées de la spécialité
        List<Seance> seancesTerminees = seanceRepository
                .findByGroupeContainingIgnoreCaseAndEstTermineeTrue(specialite);

        List<Map<String, Object>> result = new ArrayList<>();

        for (Seance s : seancesTerminees) {
            // L'étudiant était-il présent ?
            boolean estPresent = presenceRepository
                    .findByEtudiant_IdAndSeance_Id(etudiantId, s.getId())
                    .isPresent();

            if (estPresent) continue;

            // A-t-il déjà soumis un justificatif pour cette séance ?
            boolean dejaJustifie = justificatifRepository
                    .existsByEtudiantAndSeance(etudiant, s);

            Map<String, Object> dto = new HashMap<>();
            dto.put("id",          s.getId());
            dto.put("module",      s.getCours() != null ? s.getCours().getLibelle() : "Module inconnu");
            dto.put("typeSeance",  s.getTypeSeance() != null ? s.getTypeSeance() : "Cours");
            dto.put("groupe",      s.getGroupe());
            dto.put("date",        s.getDateHeureDebut() != null
                    ? s.getDateHeureDebut().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "—");
            dto.put("heure",       s.getDateHeureDebut() != null
                    ? s.getDateHeureDebut().format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "—");
            dto.put("profNom",     s.getProfesseur() != null
                    ? s.getProfesseur().getPrenom() + " " + s.getProfesseur().getNom()
                    : "Inconnu");
            dto.put("dejaJustifie", dejaJustifie);

            result.add(dto);
        }

        // Trier par date décroissante
        result.sort((a, b) -> ((String) b.get("date")).compareTo((String) a.get("date")));

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. GET /api/justificatifs/etudiant/{id}/historique
    //    Historique des justificatifs soumis par l'étudiant
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/etudiant/{etudiantId}/historique")
    public ResponseEntity<List<Map<String, Object>>> getHistoriqueEtudiant(
            @PathVariable Long etudiantId) {

        Optional<Etudiant> etudiantOpt = etudiantRepository.findById(etudiantId);
        if (etudiantOpt.isEmpty()) return ResponseEntity.notFound().build();

        List<Justificatif> justifs = justificatifRepository
                .findByEtudiantOrderByDateSoumissionDesc(etudiantOpt.get());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Justificatif j : justifs) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id",          j.getId());
            dto.put("module",      j.getSeance() != null && j.getSeance().getCours() != null
                    ? j.getSeance().getCours().getLibelle() : "Module inconnu");
            dto.put("typeSeance",  j.getSeance() != null && j.getSeance().getTypeSeance() != null
                    ? j.getSeance().getTypeSeance() : "Cours");
            dto.put("date",        j.getDateSoumission() != null
                    ? j.getDateSoumission().format(DateTimeFormatter.ofPattern("dd MMM"))
                    : "—");
            dto.put("typeMotif",   j.getTypeMotif());
            dto.put("statut",      j.getStatutValidation());
            dto.put("commentaire", j.getCommentaireProf());
            result.add(dto);
        }

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. POST /api/justificatifs/soumettre (multipart/form-data)
    //    Soumet un justificatif avec fichier PDF
    //    Params : etudiantId, seanceId, typeMotif, motif, file
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping(value = "/soumettre", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> soumettre(
            @RequestParam("etudiantId") Long etudiantId,
            @RequestParam("seanceId")   Long seanceId,
            @RequestParam("typeMotif")  String typeMotif,
            @RequestParam(value = "motif", required = false) String motif,
            @RequestParam("file")       MultipartFile file) {

        Map<String, Object> result = new HashMap<>();

        // ── Validations ──────────────────────────────────────────────────────
        Optional<Etudiant> etudiantOpt = etudiantRepository.findById(etudiantId);
        Optional<Seance>   seanceOpt   = seanceRepository.findById(seanceId);

        if (etudiantOpt.isEmpty() || seanceOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Étudiant ou séance introuvable.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }

        Etudiant etudiant = etudiantOpt.get();
        Seance   seance   = seanceOpt.get();

        // Anti-doublon
        if (justificatifRepository.existsByEtudiantAndSeance(etudiant, seance)) {
            result.put("success", false);
            result.put("message", "Vous avez déjà soumis un justificatif pour cette séance.");
            return ResponseEntity.badRequest().body(result);
        }

        // Vérification type fichier
        String contentType = file.getContentType();
        if (contentType == null ||
            (!contentType.equals("application/pdf") &&
             !contentType.startsWith("image/"))) {
            result.put("success", false);
            result.put("message", "Seuls les fichiers PDF et images sont acceptés.");
            return ResponseEntity.badRequest().body(result);
        }

        // ── Sauvegarde du fichier ────────────────────────────────────────────
        String fichierUrl;
        String nomOriginal;
        try {
            Path uploadPath = Paths.get(UPLOAD_DIR);
            if (!Files.exists(uploadPath)) Files.createDirectories(uploadPath);

            nomOriginal = file.getOriginalFilename();
            String extension = (nomOriginal != null && nomOriginal.contains("."))
                    ? nomOriginal.substring(nomOriginal.lastIndexOf('.'))
                    : ".pdf";
            String nomFichier = "justif_" + etudiantId + "_" + seanceId
                    + "_" + System.currentTimeMillis() + extension;

            Path filePath = uploadPath.resolve(nomFichier);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            fichierUrl = nomFichier;

        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "Erreur lors de l'enregistrement du fichier.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        // ── Création du justificatif ─────────────────────────────────────────
        Justificatif j = new Justificatif();
        j.setEtudiant(etudiant);
        j.setSeance(seance);
        j.setFichierPdfUrl(fichierUrl);
        j.setNomFichierOriginal(nomOriginal);
        j.setTypeMotif(typeMotif);
        j.setMotif(motif);
        j.setDateSoumission(LocalDateTime.now());
        j.setStatutValidation("EN_ATTENTE");
        justificatifRepository.save(j);

        // ── Notification au professeur ───────────────────────────────────────
        if (seance.getProfesseur() != null) {
            String module  = seance.getCours() != null
                    ? seance.getCours().getLibelle() : "Module inconnu";
            String dateFmt = seance.getDateHeureDebut() != null
                    ? seance.getDateHeureDebut().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "—";

            envoyerNotif(
                seance.getProfesseur(),
                "📄 Justificatif reçu — " + module,
                etudiant.getPrenom() + " " + etudiant.getNom()
                    + " a soumis un justificatif (" + typeMotif + ") pour la séance du "
                    + dateFmt + " (" + module + "). En attente de votre décision.",
                "ORANGE"
            );
        }

        result.put("success", true);
        result.put("message", "Justificatif soumis avec succès.");
        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. GET /api/justificatifs/prof/{profId}/en-attente
    //    Liste des justificatifs EN_ATTENTE pour les séances d'un prof
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/prof/{profId}/en-attente")
    public ResponseEntity<List<Map<String, Object>>> getJustificatifsProf(
            @PathVariable Long profId) {

        List<Justificatif> justifs = justificatifRepository
                .findBySeance_Professeur_IdAndStatutValidation(profId, "EN_ATTENTE");

        List<Map<String, Object>> result = new ArrayList<>();
        for (Justificatif j : justifs) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id",          j.getId());
            dto.put("etudiantNom", j.getEtudiant().getPrenom() + " " + j.getEtudiant().getNom());
            dto.put("module",      j.getSeance().getCours() != null
                    ? j.getSeance().getCours().getLibelle() : "Module inconnu");
            dto.put("typeSeance",  j.getSeance().getTypeSeance() != null
                    ? j.getSeance().getTypeSeance() : "Cours");
            dto.put("dateSeance",  j.getSeance().getDateHeureDebut() != null
                    ? j.getSeance().getDateHeureDebut()
                       .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "—");
            dto.put("typeMotif",   j.getTypeMotif());
            dto.put("motif",       j.getMotif());
            dto.put("dateSoumission", j.getDateSoumission() != null
                    ? j.getDateSoumission().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "—");
            dto.put("fichierNom",  j.getNomFichierOriginal());
            // URL pour voir le fichier dans le navigateur
            dto.put("fichierUrl",  "/api/justificatifs/fichier/" + j.getId());
            result.add(dto);
        }

        return ResponseEntity.ok(result);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. GET /api/justificatifs/fichier/{id}
    //    Sert le fichier inline (pour visualisation dans le navigateur)
    //    Ajouter ?download=true pour forcer le téléchargement
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/fichier/{id}")
    public ResponseEntity<Resource> getFichier(
            @PathVariable Long id,
            @RequestParam(value = "download", defaultValue = "false") boolean download) {

        Optional<Justificatif> opt = justificatifRepository.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        Justificatif j = opt.get();
        if (j.getFichierPdfUrl() == null) return ResponseEntity.notFound().build();

        try {
            Path filePath = Paths.get(UPLOAD_DIR).resolve(j.getFichierPdfUrl());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) return ResponseEntity.notFound().build();

            // Détecter le type
            String contentType = "application/pdf";
            String filename    = j.getNomFichierOriginal() != null
                                 ? j.getNomFichierOriginal() : j.getFichierPdfUrl();
            if (filename != null && (filename.endsWith(".jpg") || filename.endsWith(".jpeg")))
                contentType = "image/jpeg";
            else if (filename != null && filename.endsWith(".png"))
                contentType = "image/png";

            // inline = voir dans navigateur, attachment = forcer download
            ContentDisposition disposition = download
                    ? ContentDisposition.attachment().filename(filename).build()
                    : ContentDisposition.inline().filename(filename).build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);

        } catch (MalformedURLException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────
// GET /api/justificatifs/etudiant/{etudiantId}/seance/{seanceId}/statut
// Retourne le statut du justificatif d'un étudiant pour une séance donnée
// ─────────────────────────────────────────────────────────────────────────
@GetMapping("/etudiant/{etudiantId}/seance/{seanceId}/statut")
public ResponseEntity<Map<String, Object>> getStatutJustificatif(
        @PathVariable Long etudiantId,
        @PathVariable Long seanceId) {

    Optional<Etudiant> etudiantOpt = etudiantRepository.findById(etudiantId);
    Optional<Seance>   seanceOpt   = seanceRepository.findById(seanceId);

    if (etudiantOpt.isEmpty() || seanceOpt.isEmpty()) {
        return ResponseEntity.ok(Map.of("statut", "AUCUN"));
    }

    Optional<Justificatif> justifOpt = justificatifRepository
            .findByEtudiantAndSeance(etudiantOpt.get(), seanceOpt.get());

    if (justifOpt.isEmpty()) {
        return ResponseEntity.ok(Map.of("statut", "AUCUN"));
    }

    return ResponseEntity.ok(Map.of(
        "statut", justifOpt.get().getStatutValidation()   // EN_ATTENTE | ACCEPTE | REFUSE
    ));
}

    // ─────────────────────────────────────────────────────────────────────────
    // 6. PUT /api/justificatifs/{id}/traiter
    //    Le prof accepte ou refuse
    //    Body : { "decision": "ACCEPTE" | "REFUSE", "commentaire": "..." }
    // ─────────────────────────────────────────────────────────────────────────
    @PutMapping("/{id}/traiter")
    public ResponseEntity<Map<String, Object>> traiterJustificatif(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        Map<String, Object> result = new HashMap<>();

        Optional<Justificatif> opt = justificatifRepository.findById(id);
        if (opt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Justificatif introuvable.");
            return ResponseEntity.notFound().build();
        }

        Justificatif j = opt.get();
        String decision    = body.getOrDefault("decision", "").toUpperCase();
        String commentaire = body.getOrDefault("commentaire", "");

        if (!decision.equals("ACCEPTE") && !decision.equals("REFUSE")) {
            result.put("success", false);
            result.put("message", "Décision invalide. Utilisez ACCEPTE ou REFUSE.");
            return ResponseEntity.badRequest().body(result);
        }

        j.setStatutValidation(decision);
        j.setCommentaireProf(commentaire);
        justificatifRepository.save(j);

        // ── Notification à l'étudiant ────────────────────────────────────────
        String module  = j.getSeance() != null && j.getSeance().getCours() != null
                ? j.getSeance().getCours().getLibelle() : "votre module";
        String dateFmt = j.getSeance() != null && j.getSeance().getDateHeureDebut() != null
                ? j.getSeance().getDateHeureDebut().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "—";

        if (decision.equals("ACCEPTE")) {
            envoyerNotif(
                j.getEtudiant(),
                "✅ Justificatif accepté — " + module,
                "Votre justificatif pour la séance du " + dateFmt + " (" + module + ") "
                    + "a été accepté par votre professeur."
                    + (commentaire.isBlank() ? "" : " Commentaire : " + commentaire),
                "VERT"
            );
        } else {
            envoyerNotif(
                j.getEtudiant(),
                "❌ Justificatif refusé — " + module,
                "Votre justificatif pour la séance du " + dateFmt + " (" + module + ") "
                    + "a été refusé par votre professeur."
                    + (commentaire.isBlank() ? "" : " Motif : " + commentaire),
                "ROUGE"
            );
        }

        result.put("success", true);
        result.put("message", "Justificatif " + decision.toLowerCase() + " avec succès.");
        return ResponseEntity.ok(result);
    }
}