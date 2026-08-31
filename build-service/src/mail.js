import nodemailer from "nodemailer";
import { config } from "./config.js";

let transport = null;

function mailjetConfigured() {
  return Boolean(
    config.mailjetApiKey &&
    config.mailjetSecretKey &&
    config.mailjetSenderEmail
  );
}

function transporter() {
  if (transport) return transport;

  if (!config.smtpHost) {
    throw new Error(
      "E-posta sağlayıcısı yapılandırılmamış."
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

function mailjetAuthorization() {
  const credentials =
    `${config.mailjetApiKey}:${config.mailjetSecretKey}`;

  return (
    "Basic " +
    Buffer.from(
      credentials,
      "utf8"
    ).toString("base64")
  );
}

async function sendViaMailjet({
  email,
  subject,
  text,
  html
}) {
  if (!mailjetConfigured()) {
    throw new Error(
      "Mailjet yapılandırılmamış."
    );
  }

  const controller =
    new AbortController();

  const timeout =
    setTimeout(
      () => controller.abort(),
      15000
    );

  try {
    const response =
      await fetch(
        "https://api.mailjet.com/v3.1/send",
        {
          method: "POST",
          headers: {
            Authorization:
              mailjetAuthorization(),
            "Content-Type":
              "application/json",
            Accept:
              "application/json"
          },
          body:
            JSON.stringify({
              Messages: [
                {
                  From: {
                    Email:
                      config.mailjetSenderEmail,
                    Name:
                      config.mailjetSenderName
                  },
                  To: [
                    {
                      Email: email
                    }
                  ],
                  Subject: subject,
                  TextPart: text,
                  ...(html
                    ? {
                        HTMLPart: html
                      }
                    : {})
                }
              ]
            }),
          signal:
            controller.signal
        }
      );

    const raw =
      await response.text();

    let result = {};

    try {
      result =
        raw
          ? JSON.parse(raw)
          : {};
    } catch {
      result = {
        raw
      };
    }

    const status =
      result?.Messages?.[0]?.Status;

    if (
      !response.ok ||
      status !== "success"
    ) {
      const detail =
        result?.ErrorMessage ||
        result?.Messages?.[0]
          ?.Errors?.[0]
          ?.ErrorMessage ||
        result?.Messages?.[0]
          ?.Errors?.[0]
          ?.ErrorIdentifier ||
        raw ||
        `HTTP ${response.status}`;

      throw new Error(
        `Mailjet gönderim hatası: ${String(detail).slice(0, 500)}`
      );
    }

    return result;
  } catch (error) {
    if (
      error?.name ===
      "AbortError"
    ) {
      throw new Error(
        "Mailjet isteği zaman aşımına uğradı."
      );
    }

    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

async function sendMessage({
  email,
  subject,
  text,
  html
}) {
  // Railway SMTP kısıtlamalarına takılmamak için
  // Mailjet HTTPS API varsa öncelikle onu kullan.
  if (mailjetConfigured()) {
    return sendViaMailjet({
      email,
      subject,
      text,
      html
    });
  }

  return transporter().sendMail({
    from: config.emailFrom,
    to: email,
    subject,
    text,
    ...(html
      ? { html }
      : {})
  });
}

export async function sendVerificationEmail(
  email,
  token
) {
  const url =
    `${config.publicBaseUrl}/studio/?verify=${encodeURIComponent(token)}`;

  await sendMessage({
    email,
    subject:
      "AppForge e-posta doğrulama",
    text:
      `AppForge hesabınızı doğrulamak için bağlantıyı açın:\n\n` +
      `${url}\n\n` +
      `Bu bağlantı süreli olarak geçerlidir.`,
    html:
      `<p>AppForge hesabınızı doğrulamak için aşağıdaki bağlantıyı açın.</p>` +
      `<p><a href="${url}">E-posta adresimi doğrula</a></p>` +
      `<p>Bu bağlantı süreli olarak geçerlidir.</p>`
  });
}

export async function sendPasswordResetEmail(
  email,
  token
) {
  const url =
    `${config.publicBaseUrl}/studio/?reset=${encodeURIComponent(token)}`;

  await sendMessage({
    email,
    subject:
      "AppForge parola sıfırlama",
    text:
      `Parolanızı sıfırlamak için bağlantıyı açın:\n\n` +
      `${url}\n\n` +
      `Bu isteği siz yapmadıysanız işlemi yok sayabilirsiniz.`,
    html:
      `<p>AppForge parolanızı sıfırlamak için aşağıdaki bağlantıyı açın.</p>` +
      `<p><a href="${url}">Parolamı sıfırla</a></p>` +
      `<p>Bu isteği siz yapmadıysanız bu e-postayı yok sayabilirsiniz.</p>`
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

  await sendMessage({
    email,
    subject:
      `${teamName} sizi AppForge takımına davet etti`,
    text:
      `AppForge takım daveti\n\n` +
      `Takım: ${teamName}\n` +
      `Rol: ${role}\n\n` +
      `Daveti kabul etmek için:\n${url}\n\n` +
      `Bağlantı süreli olarak geçerlidir.`,
    html:
      `<p><strong>AppForge takım daveti</strong></p>` +
      `<p>Takım: ${teamName}<br>Rol: ${role}</p>` +
      `<p><a href="${url}">Takım davetini aç</a></p>` +
      `<p>Bağlantı süreli olarak geçerlidir.</p>`
  });
}

export async function verifyMailTransport() {
  if (mailjetConfigured()) {
    // /ready endpoint'ini harici mail sağlayıcısının
    // gecikmesine bağlamıyoruz. Gerçek gönderim ayrıca test edilir.
    return {
      ok: true,
      configured: true,
      provider: "mailjet",
      required: false,
      latencyMs: 0,
      error: null
    };
  }

  if (!config.smtpHost) {
    return {
      ok: !config.smtpRequired,
      configured: false,
      provider: null,
      required: config.smtpRequired,
      latencyMs: 0,
      error:
        config.smtpRequired
          ? "E-posta sağlayıcısı tanımlı değil."
          : null
    };
  }

  const started = Date.now();

  try {
    await transporter().verify();

    return {
      ok: true,
      configured: true,
      provider: "smtp",
      required: config.smtpRequired,
      latencyMs:
        Date.now() - started,
      error: null
    };
  } catch (error) {
    return {
      ok: false,
      configured: true,
      provider: "smtp",
      required: config.smtpRequired,
      latencyMs:
        Date.now() - started,
      error:
        String(
          error?.message ||
          error
        ).slice(0, 500)
    };
  }
}
