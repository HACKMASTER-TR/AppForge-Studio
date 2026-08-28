const { contextBridge, ipcRenderer } = require("electron");

const auth = Object.freeze({
  getToken: () => ipcRenderer.invoke("appforge:auth:get"),
  setToken: token => ipcRenderer.invoke(
    "appforge:auth:set",
    typeof token === "string" ? token : ""
  ),
  clearToken: () => ipcRenderer.invoke("appforge:auth:clear")
});

contextBridge.exposeInMainWorld("AppForgeDesktop", Object.freeze({
  apiBaseUrl: process.env.APPFORGE_API_BASE_URL || "https://appforge-studio-production.up.railway.app",
  auth
}));