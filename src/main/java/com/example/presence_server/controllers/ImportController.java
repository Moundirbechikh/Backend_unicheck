package com.example.presence_server.controllers;

import com.example.presence_server.models.Etudiant;
import com.example.presence_server.repositories.EtudiantRepository;
import com.example.presence_server.repositories.UserRepositorie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = "*", allowCredentials = "true")
public class ImportController {

    @Autowired
    private EtudiantRepository etudiantRepository;

    @Autowired
    private UserRepositorie userRepositorie;

    private String getAnneeUniversitaire() {
        int year = LocalDate.now().getYear();
        int startYear = LocalDate.now().getMonthValue() >= 9 ? year : year - 1;
        return startYear + "/" + (startYear + 1);
    }

    @PostMapping("/etudiants")
    public ResponseEntity<Map<String, Object>> importerEtudiants(
            @RequestParam("file") MultipartFile file) {

        Map<String, Object> result = new HashMap<>();

        if (file.isEmpty()) {
            result.put("success", false);
            result.put("message", "Le fichier est vide.");
            return ResponseEntity.badRequest().body(result);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".csv")) {
            result.put("success", false);
            result.put("message", "Seuls les fichiers .csv sont acceptés.");
            return ResponseEntity.badRequest().body(result);
        }

        int importes = 0, doublons = 0, erreurs = 0;
        List<String> lignesErreur = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                result.put("success", false);
                result.put("message", "Fichier CSV vide ou sans en-tête.");
                return ResponseEntity.badRequest().body(result);
            }

            String[] headers = headerLine.split("[;,]");
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim().toLowerCase(), i);
            }

            String[] required = {"matriculeetudiant", "nom", "prenom"};
            for (String col : required) {
                if (!colIndex.containsKey(col)) {
                    result.put("success", false);
                    result.put("message", "Colonne obligatoire manquante : " + col
                            + ". Attendu : MatriculeEtudiant, Nom, Prenom, Sex, DateNaissance, CarteRFID");
                    return ResponseEntity.badRequest().body(result);
                }
            }

            String ligne;
            int numLigne = 1;

            while ((ligne = reader.readLine()) != null) {
                numLigne++;
                if (ligne.isBlank()) continue;
                String[] cols = ligne.split("[;,]", -1);

                try {
                    String matricule = getCol(cols, colIndex, "matriculeetudiant").trim();
                    String nom       = getCol(cols, colIndex, "nom").trim();
                    String prenom    = getCol(cols, colIndex, "prenom").trim();
                    String rfid      = getCol(cols, colIndex, "carterfid");

                    if (matricule.isBlank() || nom.isBlank() || prenom.isBlank()) {
                        erreurs++;
                        lignesErreur.add("Ligne " + numLigne + " : matricule/nom/prénom vide.");
                        continue;
                    }

                    if (etudiantRepository.existsByMatricule(matricule)) {
                        doublons++;
                        continue;
                    }

                    Etudiant etudiant = new Etudiant();
                    etudiant.setMatricule(matricule);
                    etudiant.setNom(nom);
                    etudiant.setPrenom(prenom);
                    // ✅ email et motDePasse à NULL — l'étudiant les définira lui-même
                    etudiant.setEmail(null);
                    etudiant.setMotDePasse(null);
                    etudiant.setCodeQrFixe(rfid.isBlank() ? null : rfid.trim());
                    etudiant.setAnneeUniversitaire(getAnneeUniversitaire());

                    etudiantRepository.save(etudiant);
                    importes++;

                } catch (Exception e) {
                    erreurs++;
                    lignesErreur.add("Ligne " + numLigne + " : " + e.getMessage());
                }
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "Erreur de lecture : " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }

        result.put("success",      true);
        result.put("importes",     importes);
        result.put("doublons",     doublons);
        result.put("erreurs",      erreurs);
        result.put("lignesErreur", lignesErreur);
        result.put("message",      importes + " étudiant(s) importé(s) avec succès.");
        return ResponseEntity.ok(result);
    }

    private String getCol(String[] cols, Map<String, Integer> index, String name) {
        Integer i = index.get(name);
        if (i == null || i >= cols.length) return "";
        return cols[i] == null ? "" : cols[i];
    }
}