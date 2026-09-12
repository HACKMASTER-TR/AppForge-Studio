import express from "express";
import {
  createClientHardeningRouter
} from "./src/clientHardening.js";

const originalUse =
  express.application.use;

const installedApps =
  new WeakSet();

express.application.use =
  function appForgeHardenedUse(
    ...args
  ) {
    if (
      !installedApps.has(this)
    ) {
      installedApps.add(this);

      originalUse.call(
        this,
        createClientHardeningRouter()
      );
    }

    return originalUse.apply(
      this,
      args
    );
  };

await import("./server.js");
