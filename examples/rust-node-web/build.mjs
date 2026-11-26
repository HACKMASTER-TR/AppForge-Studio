import {mkdir,copyFile} from "node:fs/promises";
await mkdir("dist",{recursive:true});
await copyFile("index.html","dist/index.html");
console.log("Real Node web build complete; AppForge stages the verified Rust WASM resource afterward.");
