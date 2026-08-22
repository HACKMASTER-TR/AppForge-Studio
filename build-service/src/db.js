import pg from "pg";
import fs from "fs/promises";
import path from "path";
import { fileURLToPath } from "url";
import { config } from "./config.js";

const { Pool } = pg;

export const pool = new Pool({
  connectionString: config.databaseUrl,
  max: Number(process.env.PG_POOL_MAX || 10),
  ssl:
    String(process.env.PG_SSL || "false").toLowerCase() === "true"
      ? { rejectUnauthorized: false }
      : undefined
});

export async function query(text, params = []) {
  return pool.query(text, params);
}

export async function tx(fn) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const result = await fn(client);
    await client.query("COMMIT");
    return result;
  } catch (error) {
    await client.query("ROLLBACK");
    throw error;
  } finally {
    client.release();
  }
}

export async function migrate() {
  const here = path.dirname(fileURLToPath(import.meta.url));
  const sqlDir = path.resolve(here, "../sql");
  const files = (await fs.readdir(sqlDir))
    .filter(name => name.endsWith(".sql"))
    .sort();

  for (const name of files) {
    const sql = await fs.readFile(path.join(sqlDir, name), "utf8");
    await pool.query(sql);
  }
}

export async function closeDb() {
  await pool.end();
}
