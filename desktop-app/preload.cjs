const { contextBridge } = require("electron");

contextBridge.exposeInMainWorld("AppForgeDesktop", {
  apiBaseUrl: process.env.APPFORGE_API_BASE_URL || "https://appforge-studio-production.up.railway.app"
});
