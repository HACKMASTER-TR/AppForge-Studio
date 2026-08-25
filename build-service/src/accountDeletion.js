import { query } from "./db.js";
import {
  deleteInput,
  deleteOutput
} from "./storage.js";

function collectStorageRefs(
  value,
  result = new Map()
) {
  if (!value) {
    return result;
  }

  if (Array.isArray(value)) {
    for (const item of value) {
      collectStorageRefs(
        item,
        result
      );
    }

    return result;
  }

  if (
    typeof value === "object"
  ) {
    const driver =
      String(
        value.driver || ""
      );

    const key =
      String(
        value.key || ""
      );

    if (
      (
        driver === "s3" ||
        driver === "local"
      ) &&
      key
    ) {
      result.set(
        `${driver}:${key}`,
        {
          driver,
          key,
          name:
            value.name || null
        }
      );
    }

    for (
      const child of
      Object.values(value)
    ) {
      collectStorageRefs(
        child,
        result
      );
    }
  }

  return result;
}

export async function deleteAccountData(
  userId
) {
  const safeUserId =
    String(
      userId || ""
    ).trim();

  if (!safeUserId) {
    const error =
      new Error(
        "Kullanıcı kimliği gerekli."
      );

    error.statusCode = 400;
    throw error;
  }

  /*
   * Çalışan bir build varken hesabı silmek,
   * worker'ın kullandığı dosyaları yarıda
   * kaldırabileceği için silmeyi engelliyoruz.
   */
  const activeBuilds =
    await query(
      `SELECT id
       FROM appforge_builds
       WHERE user_id = $1
         AND status IN (
           'queued',
           'building'
         )
       LIMIT 1`,
      [
        safeUserId
      ]
    );

  if (
    activeBuilds.rows.length >
    0
  ) {
    const error =
      new Error(
        "Aktif build tamamlanmadan hesap silinemez."
      );

    error.statusCode = 409;
    throw error;
  }

  /*
   * DB kayıtları silinmeden önce kullanıcının
   * oluşturduğu build dosyalarının storage
   * referanslarını topluyoruz.
   */
  const builds =
    await query(
      `SELECT
         id,
         outputs
       FROM appforge_builds
       WHERE user_id = $1`,
      [
        safeUserId
      ]
    );

  const jobs =
    await query(
      `SELECT payload
       FROM appforge_build_jobs
       WHERE user_id = $1`,
      [
        safeUserId
      ]
    );

  const outputRefs =
    new Map();

  for (
    const build of
    builds.rows
  ) {
    collectStorageRefs(
      build.outputs,
      outputRefs
    );
  }

  const inputRefs =
    new Map();

  for (
    const job of
    jobs.rows
  ) {
    collectStorageRefs(
      job.payload,
      inputRefs
    );
  }

  /*
   * Build cache HIT durumunda başka bir kullanıcının
   * build kaydı aynı storage çıktısını referans ediyor
   * olabilir. Böyle bir dosyayı fiziksel olarak silmek
   * diğer kullanıcının build çıktısını bozardı.
   */
  const otherBuilds =
    await query(
      `SELECT outputs
       FROM appforge_builds
       WHERE user_id <> $1`,
      [
        safeUserId
      ]
    );

  const otherOutputRefs =
    new Map();

  for (
    const build of
    otherBuilds.rows
  ) {
    collectStorageRefs(
      build.outputs,
      otherOutputRefs
    );
  }

  let deletedOutputCount =
    0;

  let sharedOutputCount =
    0;

  /*
   * APK/AAB/EXE çıktıları.
   * Yalnızca başka kullanıcı tarafından
   * referans edilmeyen fiziksel dosyalar silinir.
   */
  for (
    const ref of
    outputRefs.values()
  ) {
    const refId =
      `${ref.driver}:${ref.key}`;

    if (
      otherOutputRefs.has(
        refId
      )
    ) {
      sharedOutputCount +=
        1;

      continue;
    }

    await deleteOutput(
      ref
    );

    deletedOutputCount +=
      1;
  }

  /*
   * Proje ZIP'i, icon, keystore,
   * Firebase config vb. build girdileri.
   */
  for (
    const ref of
    inputRefs.values()
  ) {
    await deleteInput(
      ref
    );
  }

  /*
   * appforge_users satırının silinmesi,
   * CASCADE bağlı kullanıcı verilerini de
   * kaldırır.
   */
  const deleted =
    await query(
      `DELETE FROM appforge_users
       WHERE id = $1
       RETURNING id`,
      [
        safeUserId
      ]
    );

  if (
    deleted.rows.length ===
    0
  ) {
    const error =
      new Error(
        "Hesap bulunamadı."
      );

    error.statusCode = 404;
    throw error;
  }

  return {
    deleted: true,
    deletedBuildCount:
      builds.rows.length,
    deletedOutputCount,
    sharedOutputCount,
    deletedInputCount:
      inputRefs.size
  };
}
