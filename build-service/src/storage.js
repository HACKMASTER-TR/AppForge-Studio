import {
  promises as fs,
  createReadStream
} from "fs";
import path from "path";
import crypto from "crypto";
import {
  S3Client,
  PutObjectCommand,
  GetObjectCommand,
  DeleteObjectCommand
} from "@aws-sdk/client-s3";
import {
  getSignedUrl
} from "@aws-sdk/s3-request-presigner";
import { config } from "./config.js";

function s3Client() {
  return new S3Client({
    region:
      config.s3Region,
    endpoint:
      config.s3Endpoint ||
      undefined,
    forcePathStyle:
      config.s3ForcePathStyle,
    credentials: {
      accessKeyId:
        config.s3AccessKeyId,
      secretAccessKey:
        config.s3SecretAccessKey
    }
  });
}

const s3 =
  config.storageDriver ===
  "s3"
    ? s3Client()
    : null;

function localPath(key) {
  return path.join(
    config.sharedInputRoot,
    key
  );
}

function outputLocalPath(key) {
  return path.join(
    config.outputRoot,
    key
  );
}

async function ensureParent(file) {
  await fs.mkdir(
    path.dirname(file),
    { recursive: true }
  );
}

async function bodyToFile(
  body,
  target
) {
  await ensureParent(
    target
  );

  if (
    body &&
    typeof body[
      Symbol.asyncIterator
    ] === "function"
  ) {
    const handle =
      await fs.open(
        target,
        "w"
      );

    try {
      for await (
        const chunk of body
      ) {
        await handle.write(
          Buffer.from(chunk)
        );
      }
    } finally {
      await handle.close();
    }

    return;
  }

  if (
    body?.transformToByteArray
  ) {
    const bytes =
      await body
        .transformToByteArray();

    await fs.writeFile(
      target,
      Buffer.from(bytes)
    );

    return;
  }

  throw new Error(
    "S3 body akış türü desteklenmiyor."
  );
}

async function fileMetadata(file) {
  const stat =
    await fs.stat(file);

  const hash =
    crypto.createHash(
      "sha256"
    );

  for await (
    const chunk of
      createReadStream(file)
  ) {
    hash.update(chunk);
  }

  return {
    sizeBytes:
      stat.size,
    sha256:
      hash.digest("hex")
  };
}

export function inputKey(
  buildId,
  name
) {
  return (
    `inputs/${buildId}/${name}`
  );
}

export function outputKey(
  buildId,
  name
) {
  return (
    `${buildId}/${name}`
  );
}

export async function putInput(
  buildId,
  name,
  tempFile
) {
  const key =
    inputKey(
      buildId,
      name
    );

  if (
    config.storageDriver ===
    "s3"
  ) {
    const stat =
      await fs.stat(
        tempFile
      );

    await s3.send(
      new PutObjectCommand({
        Bucket:
          config.s3Bucket,
        Key: key,
        Body:
          createReadStream(
            tempFile
          ),
        ContentLength:
          stat.size
      })
    );

    await fs.rm(
      tempFile,
      { force: true }
    );

    return {
      driver: "s3",
      key
    };
  }

  const dest =
    localPath(key);

  await ensureParent(
    dest
  );

  try {
    await fs.rename(
      tempFile,
      dest
    );
  } catch (error) {
    if (
      error?.code !==
      "EXDEV"
    ) {
      throw error;
    }

    await fs.copyFile(
      tempFile,
      dest
    );

    await fs.rm(
      tempFile,
      {
        force: true
      }
    );
  }

  return {
    driver: "local",
    key
  };
}

export async function materializeInput(
  ref,
  target
) {
  if (!ref) return null;

  if (
    ref.driver ===
    "s3"
  ) {
    const result =
      await s3.send(
        new GetObjectCommand({
          Bucket:
            config.s3Bucket,
          Key:
            ref.key
        })
      );

    await bodyToFile(
      result.Body,
      target
    );

    return target;
  }

  const source =
    localPath(
      ref.key
    );

  await ensureParent(
    target
  );

  await fs.copyFile(
    source,
    target
  );

  return target;
}

export async function deleteInput(
  ref
) {
  if (!ref) return;

  if (
    ref.driver ===
    "s3"
  ) {
    await s3.send(
      new DeleteObjectCommand({
        Bucket:
          config.s3Bucket,
        Key:
          ref.key
      })
    );

    return;
  }

  await fs.rm(
    localPath(
      ref.key
    ),
    { force: true }
  );
}

export async function putOutput(
  buildId,
  name,
  sourceFile
) {
  const key =
    outputKey(
      buildId,
      name
    );

  const metadata =
    await fileMetadata(
      sourceFile
    );

  if (
    config.storageDriver ===
    "s3"
  ) {
    await s3.send(
      new PutObjectCommand({
        Bucket:
          config.s3Bucket,
        Key:
          key,
        Body:
          createReadStream(
            sourceFile
          ),
        ContentLength:
          metadata.sizeBytes,
        ContentType:
          name.endsWith(".aab")
            ? "application/octet-stream"
            : "application/vnd.android.package-archive",
        Metadata: {
          sha256:
            metadata.sha256
        }
      })
    );

    return {
      driver: "s3",
      key,
      name,
      ...metadata
    };
  }

  const dest =
    outputLocalPath(
      key
    );

  await ensureParent(
    dest
  );

  await fs.copyFile(
    sourceFile,
    dest
  );

  return {
    driver: "local",
    key,
    name,
    ...metadata
  };
}


export async function materializeOutput(
  outputRef,
  target
) {
  if (!outputRef) {
    return null;
  }

  if (
    outputRef.driver ===
    "s3"
  ) {
    const result =
      await s3.send(
        new GetObjectCommand({
          Bucket:
            config.s3Bucket,
          Key:
            outputRef.key
        })
      );

    await bodyToFile(
      result.Body,
      target
    );

    return target;
  }

  const source =
    outputLocalPath(
      outputRef.key
    );

  await ensureParent(
    target
  );

  await fs.copyFile(
    source,
    target
  );

  return target;
}

export async function deliveryUrl(
  outputRef,
  expiresInSeconds = 300
) {
  if (!outputRef) {
    return null;
  }

  if (
    outputRef.driver ===
    "s3"
  ) {
    return getSignedUrl(
      s3,
      new GetObjectCommand({
        Bucket:
          config.s3Bucket,
        Key:
          outputRef.key,
        ResponseContentDisposition:
          `attachment; filename="${
            outputRef.name ||
            "app-output"
          }"`
      }),
      {
        expiresIn:
          expiresInSeconds
      }
    );
  }

  return null;
}

export function localOutputFile(
  outputRef
) {
  if (
    !outputRef ||
    outputRef.driver !==
      "local"
  ) {
    return null;
  }

  return outputLocalPath(
    outputRef.key
  );
}
