package com.example.presence_server.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    // ── L'adresse expéditeur autorisée sur Brevo ──────────────────────────────
    private final String EXPEDITEUR_AUTORISE = "unickeckalerte@gmail.com";

    // ── Logo exact du site (Incliné, Squircle Noir #1a1c1e & Grille Bento) ────
    private String getLogoHtml() {
        return """
            <div style="text-align: center; margin-bottom: 32px;">
              <div style="display: inline-block;">
                <div style="
                  width: 72px; height: 72px;
                  background-color: #1a1c1e;
                  border-radius: 20px;
                  display: inline-block;
                  box-shadow: 0 12px 30px rgba(26, 28, 30, 0.18);
                  padding: 18px;
                  box-sizing: border-box;
                ">
                  <table border="0" cellpadding="0" cellspacing="3" style="margin: 0 auto; width: 36px; height: 36px;">
                    <tr>
                      <td style="background-color: #ffffff; border-radius: 3px; width: 16px; height: 16px;"></td>
                      <td style="background-color: #ffffff; border-radius: 3px; width: 16px; height: 16px;"></td>
                    </tr>
                    <tr>
                      <td style="background-color: #ffffff; border-radius: 3px; width: 16px; height: 16px;"></td>
                      <td style="background-color: #ffffff; border-radius: 3px; width: 16px; height: 16px;"></td>
                    </tr>
                  </table>
                </div>
                <div style="
                  font-family: 'Manrope', 'Inter', -apple-system, sans-serif;
                  font-size: 26px; font-weight: 900; letter-spacing: -1.5px;
                  color: #1a1c1e; margin-top: 12px;
                ">
                  UniCheck <span style="color: #10b981;">QR</span>
                </div>
              </div>
            </div>
        """;
    }

    // ── Wrapper premium (Ombres douces, coins arrondis à 32px, Canvas clean) ──
    private String wrapHtml(String content) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>UniCheck QR</title>
              <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@700;800;900&family=Inter:wght@300;400;500;600;700&family=Noto+Sans+Arabic:wght@400;700&display=swap" rel="stylesheet">
              <style>
                .body-text { font-family: 'Inter', 'Noto Sans Arabic', -apple-system, sans-serif; }
                .display-text { font-family: 'Manrope', -apple-system, sans-serif; }
              </style>
            </head>
            <body style="margin:0; padding:0; background-color:#f4f6f8; -webkit-font-smoothing:antialiased;">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f6f8; padding:50px 16px;">
                <tr>
                  <td align="center">
                    <table width="100%%" cellpadding="0" cellspacing="0"
                           style="max-width:540px; background:#ffffff;
                                  border-radius:32px; overflow:hidden;
                                  box-shadow:0 24px 60px -12px rgba(26,28,30,0.08);
                                  border:1px solid rgba(26,28,30,0.04);">
                      
                      <tr>
                        <td style="padding:48px 40px 12px;">
                          %s
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:12px 40px 48px;">
                          %s
                        </td>
                      </tr>
                      
                      <tr>
                        <td style="background:#fafbfc; padding:28px 40px;
                                   text-align:center; border-top:1px solid #f0f2f5;">
                          <p class="body-text" style="margin:0; color:#8a94a6; font-size:11px;
                                                       font-weight:700; letter-spacing:1px; text-transform:uppercase;">
                            © 2026 UniCheck QR • Suivi Académique Sécurisé
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
        """.formatted(getLogoHtml(), content);
    }

    // ── Bouton Pill officiel de l'UI (#1a1c1e & shadow discret) ──────────────
    private String btnHtml(String label, String url) {
        return """
            <div style="text-align:center; margin-top:36px;">
              <a href="%s" class="display-text"
                 style="display:inline-block; background-color:#1a1c1e;
                        color:#ffffff; padding:16px 36px;
                        text-decoration:none; border-radius:9999px;
                        font-weight:800; font-size:15px; letter-spacing:-0.2px;
                        box-shadow:0 8px 20px rgba(26, 28, 30, 0.15);">
                %s
              </a>
            </div>
        """.formatted(url, label);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. ALERTE ABSENCE
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerAlerteAbsence(String toEmail, String nomEtudiant,
                                      String module, String nomProf, long nbAbsences) {
        System.out.println("📧 [EMAIL] Tentative envoi alerte absence → " + toEmail);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            
            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("⚠️ UniCheck — Alerte assiduité : " + module);

            String body = """
                <div style="text-align:center; margin-bottom:28px;">
                  <span class="display-text" style="background:#fef2f2; color:#ef4444; 
                               padding:8px 16px; border-radius:9999px; font-size:11px;
                               font-weight:900; letter-spacing:1.2px; text-transform:uppercase;
                               border:1px solid rgba(239,68,68,0.1);">
                    Seuil Critique Atteint
                  </span>
                </div>

                <h2 class="display-text" style="margin:0 0 16px; color:#1a1c1e; font-size:26px;
                               font-weight:900; letter-spacing:-0.8px; text-align:center;">
                  Bonjour %s,
                </h2>
                
                <p class="body-text" style="margin:0 0 28px; color:#606e80; font-size:15px;
                             line-height:1.6; text-align:center; font-weight:400;">
                  Le système UniCheck a détecté un taux d'absence trop élevé pour le module 
                  <strong style="color:#1a1c1e; font-weight:700;">%s</strong> 
                  (assuré par M. %s).
                </p>

                <div style="background:#fafafa; border-radius:24px;
                            padding:28px; text-align:center; margin-bottom:28px;
                            border:1px solid #f0f2f5;">
                  <p class="body-text" style="margin:0 0 6px; font-size:12px; font-weight:700;
                               color:#8a94a6; text-transform:uppercase; letter-spacing:1px;">
                    Absences cumulées
                  </p>
                  <p class="display-text" style="margin:0; font-size:56px; font-weight:900;
                               color:#ef4444; line-height:1; letter-spacing:-2px;">
                    %d
                  </p>
                </div>

                <p class="body-text" style="margin:0; font-size:14px; color:#606e80; line-height:1.6; text-align:center;">
                  Attention, dépasser le quota réglementaire remet en cause la validation directe de votre semestre. Régularisez votre situation au plus vite.
                </p>
            """.formatted(nomEtudiant, module, nomProf, nbAbsences);

            body += btnHtml("Déposer un justificatif", "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Alerte absence envoyée → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec alerte absence → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. BIENVENUE (Étudiant & Professeur)
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerMailBienvenue(String toEmail, String nomUtilisateur, String role) {
        System.out.println("📧 [EMAIL] Tentative envoi bienvenue → " + toEmail + " (" + role + ")");
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            
            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("🚀 Bienvenue sur UniCheck !");

            boolean isProf = role.equalsIgnoreCase("Enseignant");
            String features = isProf
                ? """
                    <li style="margin-bottom:14px;">⚡ Génération de <strong>QR Codes dynamiques</strong> sécurisés.</li>
                    <li style="margin-bottom:14px;">🎯 Suivi d'arrivée des étudiants en temps réel.</li>
                    <li style="margin-bottom:14px;">📂 Gestion de l'historique complet des fiches de présence.</li>
                    <li style="margin-bottom:0;">📊 Tableaux de bord et statistiques d'assiduité automatiques.</li>
                  """
                : """
                    <li style="margin-bottom:14px;">📱 Scan instantané des badges et QR Codes enseignants.</li>
                    <li style="margin-bottom:14px;">🔔 Notifications en temps réel et alertes d'absences.</li>
                    <li style="margin-bottom:14px;">📄 Dépôt et suivi simplifié de vos justificatifs d'absence.</li>
                    <li style="margin-bottom:0;">📉 Consultation transparente de votre feuille de présence globale.</li>
                  """;

            String body = """
                <div style="text-align:center; margin-bottom:28px;">
                  <span class="display-text" style="background:#ecfdf5; color:#10b981; 
                               padding:8px 16px; border-radius:9999px; font-size:11px;
                               font-weight:900; letter-spacing:1.2px; text-transform:uppercase;
                               border:1px solid rgba(16,185,129,0.1);">
                    Compte %s Activé
                  </span>
                </div>

                <h2 class="display-text" style="margin:0 0 16px; color:#1a1c1e; font-size:26px;
                               font-weight:900; letter-spacing:-0.8px; text-align:center;">
                  Bienvenue, %s !
                </h2>
                
                <p class="body-text" style="margin:0 0 28px; color:#606e80; font-size:15px;
                             line-height:1.6; text-align:center;">
                  Votre espace académique est prêt. Découvrez les fonctionnalités clés à votre disposition dès aujourd'hui :
                </p>

                <div style="background:#fafafa; border-radius:24px;
                            padding:28px 24px; border:1px solid #f0f2f5;">
                  <ul class="body-text" style="margin:0; padding-left:20px; color:#475569;
                               font-size:14px; line-height:1.7; font-weight:500;">
                    %s
                  </ul>
                </div>
            """.formatted(role, nomUtilisateur, features);

            body += btnHtml("Accéder à mon espace", "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Bienvenue envoyé → " + toEmail);

        } catch (Exception e) {
            System.err.println("❌ [EMAIL] Échec bienvenue → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. NOTIFICATION PLANNING
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerNotifPlanning(String toEmail, String nomDestinataire,
                                      String sujet, String contenu) {
        System.out.println("📧 [EMAIL] Tentative envoi planning → " + toEmail);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            
            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("📅 UniCheck — " + sujet);

            String body = """
                <div style="text-align:center; margin-bottom:28px;">
                  <span class="display-text" style="background:#eff6ff; color:#2563eb; 
                               padding:8px 16px; border-radius:9999px; font-size:11px;
                               font-weight:900; letter-spacing:1.2px; text-transform:uppercase;
                               border:1px solid rgba(37,99,235,0.1);">
                    Mise à jour du Planning
                  </span>
                </div>

                <h2 class="display-text" style="margin:0 0 16px; color:#1a1c1e; font-size:26px;
                               font-weight:900; letter-spacing:-0.8px; text-align:center;">
                  Bonjour %s,
                </h2>
                
                <p class="body-text" style="margin:0 0 28px; color:#606e80; font-size:15px;
                             line-height:1.6; text-align:center;">
                  L'administration a apporté des modifications à votre emploi du temps :
                </p>

                <div style="background:#fafafa; border-radius:24px;
                            padding:24px; border:1px solid #f0f2f5; text-align:center;
                            font-size:15px; color:#1a1c1e; font-weight:600; line-height:1.6;" class="body-text">
                  %s
                </div>
            """.formatted(nomDestinataire, contenu);

            body += btnHtml("Consulter mon planning", "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Planning envoyé → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec planning → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }
}