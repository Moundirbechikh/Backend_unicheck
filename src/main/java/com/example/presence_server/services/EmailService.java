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

    private final String EXPEDITEUR_AUTORISE = "unickeckalerte@gmail.com";
    private final String URL_CONNEXION = "https://unicheck-drab.vercel.app/connexion";

    // --- LOGO REUSABLE EN PUR CSS (REPRODUIT LA GRILLE DE MANIÈRE ÉLÉGANTE ET INCLINÉE) ---
    private String getLogoHtml() {
        return """
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin: 0 auto; text-align: center;">
              <tr>
                <td align="center" style="padding-bottom: 12px;">
                  <div style="display: inline-block; width: 56px; height: 56px; background-color: #0f172a; border-radius: 16px; padding: 12px; box-sizing: border-box; transform: rotate(3deg); -webkit-transform: rotate(3deg); -moz-transform: rotate(3deg); box-shadow: 0 10px 25px rgba(15,23,42,0.15); vertical-align: middle;">
                    <table border="0" cellpadding="0" cellspacing="3" style="width: 100%; height: 100%;">
                      <tr>
                        <td style="background-color: #10b981; border-radius: 4px; width: 50%; height: 50%;"></td>
                        <td style="background-color: #ffffff; border-radius: 4px;"></td>
                      </tr>
                      <tr>
                        <td style="background-color: #ffffff; border-radius: 4px;"></td>
                        <td style="background-color: #ffffff; border-radius: 4px;"></td>
                      </tr>
                    </table>
                  </div>
                </td>
              </tr>
              <tr>
                <td align="center">
                  <div style="font-family: 'Manrope', 'Outfit', Inter, sans-serif; font-weight: 800; font-size: 22px; color: #0f172a; letter-spacing: -0.5px; margin: 0;">
                    UniCheck <span style="font-weight: 900; color: #0f172a;">QR</span>
                  </div>
                </td>
              </tr>
            </table>
        """;
    }

    // Wrapper global (Card blanche centrée, typographie moderne et footer)
    private String wrapHtml(String content) {
        return """
            <!doctype html>
            <html lang="fr">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>UniCheck QR</title>
              <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@700;800;900&family=Inter:wght@400;600;700&display=swap" rel="stylesheet">
            </head>
            <body style="margin:0; padding:0; background-color:#f1f5f9; -webkit-font-smoothing:antialiased;">
              <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="padding:40px 16px; background:#f1f5f9;">
                <tr>
                  <td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:580px; border-radius:24px; overflow:hidden; background:#ffffff; border:1px solid #e6eef6; box-shadow:0 25px 50px rgba(2,6,23,0.05);">
                      <tr>
                        <td style="padding:40px 40px 20px; text-align:center;">
                          %s
                        </td>
                      </tr>
                      <tr>
                        <td style="padding:0 40px 40px;">
                          %s
                        </td>
                      </tr>
                      <tr>
                        <td style="background:#f8fafc; padding:20px 40px; text-align:center; border-top:1px solid #e6eef6;">
                          <p style="margin:0; font-family:'Inter', Arial, sans-serif; color:#64748b; font-size:12px; font-weight:600; text-transform:uppercase; letter-spacing:1px;">
                            © 2026 UniCheck QR • Excellence Académique
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

    // Bouton d'action (CTA) principal stylisé
    private String btnHtml(String label, String url) {
        return """
            <table role="presentation" cellpadding="0" cellspacing="0" style="margin:24px auto 0; text-align:center;">
              <tr>
                <td align="center">
                  <a href="%s" style="display:inline-block; padding:14px 32px; background:#0f172a; color:#ffffff; text-decoration:none; border-radius:999px; font-weight:700; font-family:'Manrope', Arial, sans-serif; font-size:15px; box-shadow:0 8px 20px rgba(15,23,42,0.15); transition: all 0.2s ease;">
                    %s
                  </a>
                </td>
              </tr>
            </table>
        """;
    }

    // Badge d'état dynamique (Alerte, Succès, Info)
    private String statusPill(String text, String bg, String color) {
        return """
            <div style="display:inline-block; padding:6px 12px; border-radius:999px; background:%s; color:%s; font-weight:800; font-size:11px; font-family:'Inter', Arial, sans-serif; letter-spacing:0.8px; text-transform:uppercase;">
              %s
            </div>
        """.formatted(bg, color, text);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. ALERTE ABSENCE CRITIQUE
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerAlerteAbsence(String toEmail, String nomEtudiant, String module, String nomProf, long nbAbsences) {
        System.out.println("📧 [EMAIL] Tentative envoi alerte absence → " + toEmail);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("⚠️ UniCheck — Alerte assiduité critique : " + module);

            String header = statusPill("Seuil critique atteint", "#fff1f2", "#dc2626");

            String body = """
                <div style="text-align:center; margin-bottom:20px;">%s</div>

                <h2 style="font-family:'Manrope', Arial, sans-serif; color:#0f172a; font-size:22px; margin:0 0 12px; font-weight:800; text-align:center;">
                  Bonjour %s,
                </h2>

                <p style="font-family:'Inter', Arial, sans-serif; color:#475569; font-size:15px; line-height:1.6; text-align:center; margin:0 0 20px;">
                  Le système automatisé a détecté un taux d'absence élevé nécessitant votre attention pour le module
                  <strong style="color:#0f172a; font-weight:800;">%s</strong> (Enseigné par %s).
                </p>

                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="margin:20px 0;">
                  <tr>
                    <td align="center">
                      <div style="display:inline-block; background:#fff7f8; border-radius:16px; padding:18px 24px; border:1px solid #fde2e6; text-align:center; min-width:180px;">
                        <div style="font-family:'Manrope', Arial, sans-serif; font-size:12px; color:#64748b; font-weight:700; text-transform:uppercase; letter-spacing:1px; margin-bottom:4px;">
                          Absences cumulées
                        </div>
                        <div style="font-family:'Manrope', Arial, sans-serif; font-size:44px; color:#dc2626; font-weight:900; line-height:1;">
                          %d
                        </div>
                      </div>
                    </td>
                  </tr>
                </table>

                <p style="font-family:'Inter', Arial, sans-serif; color:#64748b; font-size:14px; line-height:1.6; text-align:center; background-color:#fffbeb; border:1px solid #fef3c7; padding:14px; border-radius:14px; margin:0 0 10px;">
                  💡 <strong>Rappel :</strong> Dépasser le quota réglementaire compromet directement la validation de votre semestre. Veuillez téléverser vos justificatifs depuis votre espace en ligne au plus vite.
                </p>
                """.formatted(header, nomEtudiant, module, nomProf, nbAbsences);

            body += btnHtml("Déposer un justificatif", URL_CONNEXION);

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Alerte absence envoyée avec succès → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec alerte absence → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. BIENVENUE COMPTE ACTIVÉ (AVEC SECTIONS DÉTAILLÉES PAR RÔLE)
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerMailBienvenue(String toEmail, String nomUtilisateur, String role) {
        System.out.println("📧 [EMAIL] Tentative envoi bienvenue → " + toEmail + " (" + role + ")");
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("🚀 Bienvenue sur UniCheck — Votre espace " + role + " est prêt !");

            String header = statusPill("Espace activé", "#ecfdf5", "#059669");

            // Récupération de la liste personnalisée selon le rôle
            String specificDetails = role.equalsIgnoreCase("Enseignant") ? """
                <li>Générez vos QR Codes dynamiques rafraîchis toutes les 5s</li>
                <li>Suivez en direct le flux d'arrivée des étudiants en cours</li>
                <li>Validez et gérez l'historique des présences et des absences</li>
                <li>Consultez les graphiques et statistiques d'assiduité globales</li>
            """ : """
                <li>Scannez vos badges via QR Code de présence en classe</li>
                <li>Recevez des alertes d'absence instantanées par module</li>
                <li>Justifiez vos absences directement depuis votre smartphone</li>
                <li>Suivez votre feuille de présence consolidée en temps réel</li>
            """;

            String body = """
                <div style="text-align:center; margin-bottom:20px;">%s</div>

                <h2 style="font-family:'Manrope', Arial, sans-serif; color:#0f172a; font-size:22px; margin:0 0 12px; font-weight:800; text-align:center;">
                  Bienvenue %s,
                </h2>

                <p style="font-family:'Inter', Arial, sans-serif; color:#475569; font-size:15px; line-height:1.6; text-align:center; margin:0 0 24px;">
                  Votre compte en tant que <strong>%s</strong> a été initialisé avec succès sur notre plateforme de gestion d'assiduité nouvelle génération.
                </p>

                <div style="background:#f8fafc; border-radius:16px; padding:20px; margin-bottom:12px; border:1px solid #e6eef6; text-align:left;">
                    <div style="margin-bottom:12px;">
                        <span style="color:#006c49; font-weight:800; font-size:12px; text-transform:uppercase; letter-spacing:1px; font-family:'Manrope', sans-serif;">Au programme sur votre espace :</span>
                    </div>
                    <ul style="margin:0; padding-left:20px; color:#334155; font-family:'Inter', sans-serif; font-size:14px; line-height:2.1;">
                        %s
                    </ul>
                </div>
                """.formatted(header, nomUtilisateur, role, specificDetails);

            body += btnHtml("Accéder à mon espace", URL_CONNEXION);

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Bienvenue envoyé avec succès → " + toEmail);

        } catch (Exception e) {
            System.err.println("❌ [EMAIL] Échec bienvenue → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. NOTIFICATION DE PLANNING / MISE À JOUR
    // ─────────────────────────────────────────────────────────────────────────
    public void envoyerNotifPlanning(String toEmail, String nomDestinataire, String sujet, String contenu) {
        System.out.println("📧 [EMAIL] Tentative envoi planning → " + toEmail);
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");

            helper.setFrom(EXPEDITEUR_AUTORISE);
            helper.setTo(toEmail);
            helper.setSubject("📅 UniCheck — " + sujet);

            String header = statusPill("Mise à jour planning", "#eff6ff", "#2563eb");

            String body = """
                <div style="text-align:center; margin-bottom:20px;">%s</div>

                <h2 style="font-family:'Manrope', Arial, sans-serif; color:#0f172a; font-size:22px; margin:0 0 12px; font-weight:800; text-align:center;">
                  Bonjour %s,
                </h2>

                <p style="font-family:'Inter', Arial, sans-serif; color:#475569; font-size:15px; line-height:1.6; text-align:center; margin:0 0 20px;">
                  %s
                </p>

                <div style="background:#f8fafc; border-radius:16px; padding:16px; border:1px solid #e6eef6; text-align:center; margin-bottom:10px;">
                  <p style="margin:0; font-family:'Inter', Arial, sans-serif; font-size:14px; color:#475569; font-weight:600;">
                    Connectez-vous pour visualiser les modifications ou détails en temps réel.
                  </p>
                </div>
                """.formatted(header, nomDestinataire, contenu);

            body += btnHtml("Voir mon planning", URL_CONNEXION);

            helper.setText(wrapHtml(body), true);
            mailSender.send(msg);
            System.out.println("✅ [EMAIL] Planning envoyé avec succès → " + toEmail);

        } catch (MessagingException e) {
            System.err.println("❌ [EMAIL] Échec planning → " + toEmail + " | Erreur : " + e.getMessage());
        }
    }
}