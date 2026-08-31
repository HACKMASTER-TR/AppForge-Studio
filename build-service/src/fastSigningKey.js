import { promises as fs } from "fs";
import path from "path";

export const DEFAULT_FAST_DEBUG_KEYSTORE =
  "/tmp/appforge-signing/debug.keystore";

export function resolveFastDebugKeystorePath() {
  return (
    process.env.APPFORGE_FAST_DEBUG_KEYSTORE ||
    DEFAULT_FAST_DEBUG_KEYSTORE
  );
}

export async function materializeFastDebugKeystore({
  base64 =
    process.env.APPFORGE_FAST_DEBUG_KEYSTORE_B64 || "",
  targetPath =
    resolveFastDebugKeystorePath()
} = {}) {
  const encoded = String(base64).trim();

  if (!encoded) {
    return {
      materialized: false,
      path: targetPath
    };
  }

  if (
    encoded.length % 4 !== 0 ||
    !/^[A-Za-z0-9+/]+={0,2}$/.test(encoded)
  ) {
    throw new Error(
      "APPFORGE_FAST_DEBUG_KEYSTORE_B64 geçersiz base64."
    );
  }

  const bytes =
    Buffer.from(encoded, "base64");

  if (bytes.length < 512) {
    throw new Error(
      "APPFORGE FAST debug keystore beklenenden küçük."
    );
  }

  const resolved =
    path.resolve(targetPath);

  await fs.mkdir(
    path.dirname(resolved),
    { recursive: true }
  );

  await fs.writeFile(
    resolved,
    bytes,
    { mode: 0o600 }
  );

  await fs.chmod(
    resolved,
    0o600
  );

  return {
    materialized: true,
    path: resolved,
    size: bytes.length
  };
}
