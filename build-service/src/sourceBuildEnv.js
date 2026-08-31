const SAFE_SOURCE_ENV_KEYS =
  new Set([
    "PATH",
    "PREFIX",
    "LD_PRELOAD",
    "LD_LIBRARY_PATH",
    "SHELL",
    "LANG",
    "LANGUAGE",
    "LC_ALL",
    "LC_CTYPE",
    "TERM",
    "USER",
    "LOGNAME",
    "HOME",
    "JAVA_HOME",
    "ANDROID_HOME",
    "ANDROID_SDK_ROOT",
    "ANDROID_NDK_HOME",
    "ANDROID_NDK_ROOT",
    "GRADLE_HOME",
    "GRADLE_USER_HOME",
    "FLUTTER_HOME",
    "FLUTTER_SUPPRESS_ANALYTICS",
    "FLUTTER_ALREADY_LOCKED",
    "DART_SUPPRESS_ANALYTICS",
    "DOTNET_ROOT",
    "DOTNET_CLI_HOME",
    "DOTNET_CLI_TELEMETRY_OPTOUT",
    "DOTNET_NOLOGO",
    "DOTNET_SKIP_FIRST_TIME_EXPERIENCE",
    "NUGET_XMLDOC_MODE",
    "NUGET_PACKAGES",
    "PUB_CACHE",
    "PIP_CACHE_DIR",
    "XDG_CACHE_HOME",
    "XDG_DATA_HOME",
    "XDG_STATE_HOME",
    "APPFORGE_USER_CACHE_ROOT",
    "COREPACK_HOME",
    "COREPACK_ENABLE_NETWORK",
    "COREPACK_DEFAULT_TO_LATEST",
    "COREPACK_ENABLE_DOWNLOAD_PROMPT",
    "YARN_CACHE_FOLDER",
    "NPM_CONFIG_CACHE",
    "npm_config_cache",
    "npm_execpath"
  ]);

export function createSourceBuildEnv(
  overrides = {},
  inherited = process.env
) {
  const env =
    {};

  for (
    const key of
    SAFE_SOURCE_ENV_KEYS
  ) {
    const value =
      inherited?.[key];

    if (
      value !== undefined &&
      value !== null &&
      String(
        value
      ).length >
        0
    ) {
      env[key] =
        String(
          value
        );
    }
  }

  return {
    ...env,
    ...overrides
  };
}

export function sourceBuildEnvKeys() {
  return [
    ...SAFE_SOURCE_ENV_KEYS
  ]
    .sort();
}
