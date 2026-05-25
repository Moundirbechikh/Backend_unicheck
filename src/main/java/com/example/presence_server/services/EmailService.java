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

    // ── Logo identique à l'app (carré noir incliné + grille) ─────────────────
    private String getLogoHtml() {
        return """
            <div style="text-align:center; margin-bottom:36px;">
              <div style="display:inline-block; position:relative;">
                <div style="
                  width:64px; height:64px;
                  background-color:#1a1c1e;
                  border-radius:16px;
                  transform:rotate(3deg);
                  display:inline-flex;
                  align-items:center;
                  justify-content:center;
                  box-shadow:0 12px 30px rgba(26,28,30,0.25);
                  margin-bottom:12px;
                ">
                  <table border="0" cellpadding="0" cellspacing="3"
                         style="width:36px; height:36px;">
                    <tr>
                      <td style="background:#10b981; border-radius:4px;
                                 width:16px; height:16px;"></td>
                      <td style="background:#ffffff; border-radius:4px;
                                 width:16px; height:16px;"></td>
                    </tr>
                    <tr>
                      <td style="background:#ffffff; border-radius:4px;
                                 width:16px; height:16px;"></td>
                      <td style="background:#ffffff; border-radius:4px;
                                 width:16px; height:16px;"></td>
                    </tr>
                  </table>
                </div>
                <div style="
                  font-family:'Segoe UI', Roboto, -apple-system, sans-serif;
                  font-size:22px; font-weight:900; letter-spacing:-1px;
                  color:#1a1c1e; margin-top:10px;
                ">UniCheck QR</div>
              </div>
            </div>
        """;
    }

    // ── Wrapper commun pour tous les mails ────────────────────────────────────
    private String wrapHtml(String content) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>UniCheck QR</title>
            </head>
            <body style="margin:0; padding:0; background-color:#f1f4f2;
                         font-family:'Segoe UI', Roboto, -apple-system, sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0"
                     style="background-color:#f1f4f2; padding:40px 20px;">
                <tr><td align="center">
                  <table width="100%%" cellpadding="0" cellspacing="0"
                         style="max-width:560px; background:#ffffff;
                                border-radius:32px; overflow:hidden;
                                box-shadow:0 20px 60px rgba(26,28,30,0.08);
                                border:1px solid #e8ece9;">
                    <tr>
                      <td style="background:#1a1c1e; padding:36px 40px 32px;
                                 text-align:center;">
                        %s
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:40px 40px 36px;">
                        %s
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#f1f4f2; padding:20px 40px;
                                 text-align:center; border-top:1px solid #e8ece9;">
                        <p style="margin:0; color:#94a3b8; font-size:11px;
                                  text-transform:uppercase; letter-spacing:1.5px;
                                  font-weight:600;">
                          © 2026 UniCheck QR &nbsp;•&nbsp; Excellence Académique
                        </p>
                      </td>
                    </tr>
                  </table>
                </td></tr>
              </table>
            </body>
            </html>
        """.formatted(getLogoHtml(), content);
    }

    // ── Bouton CTA ────────────────────────────────────────────────────────────
    private String btnHtml(String label, String url) {
        return """
            <div style="text-align:center; margin-top:32px;">
              <a href="%s"
                 style="display:inline-block; background-color:#1a1c1e;
                        color:#ffffff; padding:16px 36px;
                        text-decoration:none; border-radius:24px;
                        font-weight:800; font-size:14px;
                        letter-spacing:0.5px;
                        box-shadow:0 8px 20px rgba(26,28,30,0.20);">
                %s
              </a>
            </div>
        """.formatted(url, label);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. ALERTE ABSENCE (≥ 4 absences)
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerAlerteAbsence(String toEmail, String nomEtudiant,
                                      String module, String nomProf, long nbAbsences) {
        System.out.println("📧 [EMAIL] Tentative envoi alerte absence → " + toEmail);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            
            // 💡 CORRECTION ICI : Définition explicite de l'expéditeur
            helper.setFrom(EXPEDITEUR_AUTORISE);
            
            helper.setTo(toEmail);
            helper.setSubject("⚠️ UniCheck — Alerte assiduité : " + module);

            String body = """
                <div style="background:#fef2f2; border-left:4px solid #ef4444;
                            padding:14px 18px; border-radius:12px; margin-bottom:28px;">
                  <p style="margin:0; color:#b91c1c; font-size:11px;
                             font-weight:800; text-transform:uppercase;
                             letter-spacing:1.5px;">⚠ Seuil critique atteint</p>
                </div>

                <h2 style="margin:0 0 12px; color:#1a1c1e; font-size:22px;
                           font-weight:900; letter-spacing:-0.5px;">
                  Bonjour %s,
                </h2>
                <p style="margin:0 0 24px; color:#475569; font-size:15px;
                          line-height:1.7;">
                  Le système de suivi a détecté un taux d'absence critique pour
                  le module <strong style="color:#1a1c1e;">%s</strong>
                  (enseigné par <strong style="color:#1a1c1e;">%s</strong>).
                </p>

                <div style="background:#f1f4f2; border-radius:20px;
                            padding:28px; text-align:center; margin-bottom:28px;
                            border:1px solid #e8ece9;">
                  <p style="margin:0 0 6px; font-size:12px; font-weight:700;
                             color:#64748b; text-transform:uppercase;
                             letter-spacing:1px;">Absences cumulées</p>
                  <p style="margin:0; font-size:56px; font-weight:900;
                             color:#ef4444; line-height:1; letter-spacing:-2px;">
                    %d
                  </p>
                </div>

                <div style="background:#fffbeb; border:1px solid #fef3c7;
                            border-radius:14px; padding:16px 18px;
                            margin-bottom:8px;">
                  <p style="margin:0; font-size:13px; color:#92400e;
                             line-height:1.6;">
                    💡 <strong>Rappel :</strong> Dépasser ce quota compromet
                    la validation de votre semestre. Déposez vos justificatifs
                    dès maintenant depuis votre espace personnel.
                  </p>
                </div>
            """.formatted(nomEtudiant, module, nomProf, nbAbsences);

            body += btnHtml("Déposer un justificatif",
                    "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Alerte absence envoyée → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec alerte absence → " + toEmail
                    + " | Erreur : " + e.getMessage());
            e.printStackTrace();
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
            
            // 💡 CORRECTION ICI : Définition explicite de l'expéditeur
            helper.setFrom(EXPEDITEUR_AUTORISE);
            
            helper.setTo(toEmail);
            helper.setSubject("🚀 Bienvenue sur UniCheck — Espace " + role + " activé !");

            boolean isProf = role.equalsIgnoreCase("Enseignant");
            String features = isProf
                ? """
                    <li style="margin-bottom:8px;">📊 Générez vos QR Codes dynamiques (rotation 10s)</li>
                    <li style="margin-bottom:8px;">👁 Suivez en direct l'arrivée de vos étudiants</li>
                    <li style="margin-bottom:8px;">📋 Gérez l'historique des présences par groupe</li>
                    <li style="margin-bottom:8px;">📈 Consultez les statistiques d'assiduité globales</li>
                  """
                : """
                    <li style="margin-bottom:8px;">📱 Scannez le QR Code de votre professeur</li>
                    <li style="margin-bottom:8px;">🔔 Recevez des alertes d'absence en temps réel</li>
                    <li style="margin-bottom:8px;">📄 Justifiez vos absences directement depuis l'app</li>
                    <li style="margin-bottom:8px;">📊 Suivez votre feuille de présence consolidée</li>
                  """;

            String body = """
                <div style="text-align:center; margin-bottom:28px;">
                  <span style="display:inline-block; background:#d1f4e0;
                               color:#006c49; font-size:11px; font-weight:800;
                               text-transform:uppercase; letter-spacing:1.5px;
                               padding:8px 18px; border-radius:50px;">
                    Espace %s activé ✓
                  </span>
                </div>

                <h2 style="margin:0 0 12px; color:#1a1c1e; font-size:22px;
                           font-weight:900; letter-spacing:-0.5px; text-align:center;">
                  Bonjour %s,
                </h2>
                <p style="margin:0 0 28px; color:#475569; font-size:15px;
                          line-height:1.7; text-align:center;">
                  Votre compte <strong style="color:#1a1c1e;">%s</strong>
                  est opérationnel sur UniCheck QR.
                </p>

                <div style="background:#f1f4f2; border-radius:20px;
                            padding:24px 28px; border:1px solid #e8ece9;">
                  <p style="margin:0 0 14px; font-size:11px; font-weight:800;
                             text-transform:uppercase; letter-spacing:1px;
                             color:#006c49;">Au programme :</p>
                  <ul style="margin:0; padding-left:18px; color:#334155;
                              font-size:14px; line-height:1.6;">
                    %s
                  </ul>
                </div>
            """.formatted(role, nomUtilisateur, role, features);

            body += btnHtml("Accéder à mon espace",
                    "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Bienvenue envoyé → " + toEmail);

        } catch (Exception e) {
            System.err.println("❌ [EMAIL] Échec bienvenue → " + toEmail
                    + " | Erreur : " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. NOTIFICATION PLANNING (admin → changement de planning)
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerNotifPlanning(String toEmail, String nomDestinataire,
                                      String sujet, String contenu) {
        System.out.println("📧 [EMAIL] Tentative envoi planning → " + toEmail);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            
            // 💡 CORRECTION ICI : Définition explicite de l'expéditeur
            helper.setFrom(EXPEDITEUR_AUTORISE);
            
            helper.setTo(toEmail);
            helper.setSubject("📅 UniCheck Planning — " + sujet);

            String body = """
                <div style="text-align:center; margin-bottom:28px;">
                  <span style="display:inline-block; background:#e0f2fe;
                               color:#0369a1; font-size:11px; font-weight:800;
                               text-transform:uppercase; letter-spacing:1.5px;
                               padding:8px 18px; border-radius:50px;">
                    📅 Mise à jour planning
                  </span>
                </div>

                <h2 style="margin:0 0 12px; color:#1a1c1e; font-size:22px;
                           font-weight:900; letter-spacing:-0.5px;">
                  Bonjour %s,
                </h2>
                <p style="margin:0 0 24px; color:#475569; font-size:15px;
                          line-height:1.7;">%s</p>

                <div style="background:#f1f4f2; border-radius:16px;
                            padding:20px 24px; border:1px solid #e8ece9;">
                  <p style="margin:0; font-size:13px; color:#64748b;
                             line-height:1.6;">
                    Connectez-vous à votre espace pour voir votre planning mis à jour.
                  </p>
                </div>
            """.formatted(nomDestinataire, contenu);

            body += btnHtml("Voir mon planning",
                    "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Planning envoyé → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec planning → " + toEmail
                    + " | Erreur : " + e.getMessage());
            e.printStackTrace();
        }
    }
}