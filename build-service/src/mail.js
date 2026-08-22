import nodemailer from "nodemailer";
import { config } from "./config.js";

let transport = null;

function transporter() {
  if (transport) return transport;

  if (!config.smtpHost) {
    throw new Error(
      "SMTP yapılandırılmamış."
    );
  }

  transport =
    nodemailer.createTransport({
      host: config.smtpHost,
      port: config.smtpPort,
      secure: config.smtpSecure,
      auth:
        config.smtpUser
          ? {
              user: config.smtpUser,
              pass: config.smtpPass
            }
          : undefined
    });

  return transport;
}

export async function sendVerificationEmail(
  email,
  token
) {
  const url =
    `${config.publicBaseUrl}/studio/?verify=${encodeURIComponent(token)}`;

  await transporter().sendMail({
    from: config.emailFrom,
    to: email,
    subject:
      "AppForge e-posta doğrulama",
    text:
      `AppForge hesabınızı doğrulamak için bağlantıyı açın:\n\n` +
      `${url}\n\nBu bağlantı süreli olarak geçerlidir.`
  });
}

export async function sendPasswordResetEmail(
  email,
  token
) {
  const url =
    `${config.publicBaseUrl}/studio/?reset=${encodeURIComponent(token)}`;

  await transporter().sendMail({
    from: config.emailFrom,
    to: email,
    subject:
      "AppForge parola sıfırlama",
    text:
      `Parolanızı sıfırlamak için bağlantıyı açın:\n\n` +
      `${url}\n\nBu isteği siz yapmadıysanız işlemi yok sayabilirsiniz.`
  });
}

export async function sendTeamInviteEmail({
  email,
  token,
  teamName,
  role
}) {
  const url =
    `${config.publicBaseUrl}/studio/?teamInvite=${encodeURIComponent(token)}`;

  await transporter().sendMail({
    from: config.emailFrom,
    to: email,
    subject:
      `${teamName} sizi AppForge takımına davet etti`,
    text:
      `AppForge takım daveti\n\n` +
      `Takım: ${teamName}\n` +
      `Rol: ${role}\n\n` +
      `Daveti kabul etmek için:\n${url}\n\n` +
      `Bağlantı süreli olarak geçerlidir.`
  });
}
