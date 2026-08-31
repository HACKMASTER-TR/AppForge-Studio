const { contextBridge, ipcRenderer } = require("electron");

const auth = Object.freeze({
  getToken: () => ipcRenderer.invoke("appforge:auth:get"),
  setToken: token => ipcRenderer.invoke("appforge:auth:set", typeof token === "string" ? token : ""),
  clearToken: () => ipcRenderer.invoke("appforge:auth:clear")
});

const store = Object.freeze({
  get: key => ipcRenderer.invoke("appforge:store:get", key),
  set: (key, value) => ipcRenderer.invoke("appforge:store:set", key, value),
  remove: key => ipcRenderer.invoke("appforge:store:remove", key)
});

const keystore = Object.freeze({
  save: payload => ipcRenderer.invoke("appforge:keystore:save", payload),
  get: () => ipcRenderer.invoke("appforge:keystore:get"),
  clear: () => ipcRenderer.invoke("appforge:keystore:clear")
});

const localAi = Object.freeze({
  chat: payload => ipcRenderer.invoke("appforge:local-ai:chat", payload)
});

const security = Object.freeze({
  getState: () => ipcRenderer.invoke("appforge:security:state")
});

contextBridge.exposeInMainWorld("AppForgeDesktop", Object.freeze({
  apiBaseUrl: process.env.APPFORGE_API_BASE_URL || "https://appforge-studio-production.up.railway.app",
  auth,
  store,
  keystore,
  localAi,
  security
}));
