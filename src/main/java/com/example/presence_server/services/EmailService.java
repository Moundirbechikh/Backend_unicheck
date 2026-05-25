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

    // ── Logo version miniature (inspiré du site) ─────────────────────────────
    private String getLogoHtml() {
        return """
            <div style="text-align:center; margin-bottom:24px;">
              <div style="display:inline-block; position:relative;">
                <div style="
                  width:48px; height:48px;
                  background-color:#0f172a;
                  border-radius:12px;
                  transform:rotate(-4deg);
                  display:inline-flex;
                  align-items:center;
                  justify-content:center;
                  box-shadow:0 10px 15px -3px rgba(15, 23, 42, 0.2);
                  margin-bottom:8px;
                ">
                  <table border="0" cellpadding="0" cellspacing="2" style="width:24px; height:24px;">
                    <tr>
                      <td style="background:#10b981; border-radius:3px; width:10px; height:10px;"></td>
                      <td style="background:#ffffff; border-radius:3px; width:10px; height:10px;"></td>
                    </tr>
                    <tr>
                      <td style="background:#ffffff; border-radius:3px; width:10px; height:10px;"></td>
                      <td style="background:#ffffff; border-radius:3px; width:10px; height:10px;"></td>
                    </tr>
                  </table>
                </div>
                <div style="
                  font-family:'Outfit', 'Inter', -apple-system, sans-serif;
                  font-size:20px; font-weight:800; letter-spacing:-0.5px;
                  color:#0f172a; margin-top:8px;
                ">UniCheck</div>
              </div>
            </div>
        """;
    }

    // ── Wrapper commun pour tous les mails (Design Tailwind-like) ─────────────
    private String wrapHtml(String content) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
              <meta charset="UTF-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
              <title>UniCheck QR</title>
              <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700;800&display=swap" rel="stylesheet">
            </head>
            <body style="margin:0; padding:0; background-color:#f8fafc;
                         font-family:'Outfit', 'Inter', -apple-system, sans-serif;">
              <table width="100%%" cellpadding="0" cellspacing="0"
                     style="background-color:#f8fafc; padding:40px 20px;">
                <tr><td align="center">
                  <table width="100%%" cellpadding="0" cellspacing="0"
                         style="max-width:520px; background:#ffffff;
                                border-radius:24px; overflow:hidden;
                                box-shadow:0 20px 40px -10px rgba(0,0,0,0.05);
                                border:1px solid #e2e8f0;">
                    <tr>
                      <td style="padding:40px 40px 10px;">
                        %s
                      </td>
                    </tr>
                    <tr>
                      <td style="padding:10px 40px 40px;">
                        %s
                      </td>
                    </tr>
                    <tr>
                      <td style="background:#f8fafc; padding:24px 40px;
                                 text-align:center; border-top:1px solid #e2e8f0;">
                        <p style="margin:0; color:#64748b; font-size:12px;
                                  font-weight:600; letter-spacing:0.5px;">
                          © 2026 UniCheck QR • Excellence Académique
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

    // ── Bouton CTA (Entièrement arrondi comme sur ton UI) ─────────────────────
    private String btnHtml(String label, String url) {
        return """
            <div style="text-align:center; margin-top:32px;">
              <a href="%s"
                 style="display:inline-block; background-color:#0f172a;
                        color:#ffffff; padding:14px 32px;
                        text-decoration:none; border-radius:9999px;
                        font-weight:600; font-size:15px;
                        transition:all 0.3s ease;
                        box-shadow:0 4px 6px -1px rgba(15, 23, 42, 0.2);">
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
            
            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("⚠️ UniCheck — Alerte assiduité : " + module);

            String body = """
                <div style="background:#fef2f2; border-radius:12px;
                            padding:12px 16px; margin-bottom:24px; text-align:center;">
                  <p style="margin:0; color:#dc2626; font-size:12px;
                             font-weight:700; letter-spacing:1px; text-transform:uppercase;">
                    Seuil critique atteint
                  </p>
                </div>

                <h2 style="margin:0 0 16px; color:#0f172a; font-size:24px;
                           font-weight:800; letter-spacing:-0.5px; text-align:center;">
                  Bonjour %s,
                </h2>
                
                <p style="margin:0 0 24px; color:#475569; font-size:15px;
                          line-height:1.6; text-align:center;">
                  Le système a détecté un taux d'absence élevé pour le module 
                  <strong style="color:#0f172a; font-weight:700;">%s</strong> 
                  (enseigné par %s).
                </p>

                <div style="background:#f8fafc; border-radius:16px;
                            padding:24px; text-align:center; margin-bottom:24px;
                            border:1px solid #e2e8f0;">
                  <p style="margin:0 0 8px; font-size:13px; font-weight:600;
                             color:#64748b; text-transform:uppercase; letter-spacing:1px;">
                    Absences cumulées
                  </p>
                  <p style="margin:0; font-size:48px; font-weight:800;
                             color:#dc2626; line-height:1;">
                    %d
                  </p>
                </div>

                <p style="margin:0; font-size:14px; color:#475569; line-height:1.6; text-align:center;">
                  Dépasser le quota peut compromettre la validation du semestre. Pensez à déposer vos justificatifs rapidement.
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
                    <li style="margin-bottom:12px;">📊 Générez vos QR Codes dynamiques</li>
                    <li style="margin-bottom:12px;">👁 Suivez en direct l'arrivée de vos étudiants</li>
                    <li style="margin-bottom:12px;">📋 Gérez l'historique des présences</li>
                    <li style="margin-bottom:0;">📈 Consultez les statistiques globales</li>
                  """
                : """
                    <li style="margin-bottom:12px;">📱 Scannez le QR Code de votre professeur</li>
                    <li style="margin-bottom:12px;">🔔 Recevez des alertes d'absence</li>
                    <li style="margin-bottom:12px;">📄 Justifiez vos absences via l'app</li>
                    <li style="margin-bottom:0;">📊 Suivez votre feuille de présence</li>
                  """;

            String body = """
                <div style="background:#ecfdf5; border-radius:12px;
                            padding:12px 16px; margin-bottom:24px; text-align:center;">
                  <p style="margin:0; color:#059669; font-size:12px;
                             font-weight:700; letter-spacing:1px; text-transform:uppercase;">
                    Espace %s activé
                  </p>
                </div>

                <h2 style="margin:0 0 16px; color:#0f172a; font-size:24px;
                           font-weight:800; letter-spacing:-0.5px; text-align:center;">
                  Bienvenue %s,
                </h2>
                
                <p style="margin:0 0 24px; color:#475569; font-size:15px;
                          line-height:1.6; text-align:center;">
                  Votre compte est officiellement opérationnel. Voici ce que vous pouvez faire dès maintenant :
                </p>

                <div style="background:#f8fafc; border-radius:16px;
                            padding:24px; border:1px solid #e2e8f0;">
                  <ul style="margin:0; padding-left:20px; color:#334155;
                             font-size:14px; line-height:1.6; font-weight:500;">
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
    // 3. NOTIFICATION PLANNING (admin → changement de planning)
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
                <div style="background:#eff6ff; border-radius:12px;
                            padding:12px 16px; margin-bottom:24px; text-align:center;">
                  <p style="margin:0; color:#2563eb; font-size:12px;
                             font-weight:700; letter-spacing:1px; text-transform:uppercase;">
                    Mise à jour planning
                  </p>
                </div>

                <h2 style="margin:0 0 16px; color:#0f172a; font-size:24px;
                           font-weight:800; letter-spacing:-0.5px; text-align:center;">
                  Bonjour %s,
                </h2>
                
                <p style="margin:0 0 24px; color:#475569; font-size:15px;
                          line-height:1.6; text-align:center;">
                  %s
                </p>

                <div style="background:#f8fafc; border-radius:16px;
                            padding:20px; border:1px solid #e2e8f0; text-align:center;">
                  <p style="margin:0; font-size:14px; color:#475569; font-weight:500;">
                    Connectez-vous pour voir les modifications en détail.
                  </p>
                </div>
            """.formatted(nomDestinataire, contenu);

            body += btnHtml("Voir mon planning", "https://unicheck-drab.vercel.app/connexion");

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Planning envoyé → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec planning → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }
}