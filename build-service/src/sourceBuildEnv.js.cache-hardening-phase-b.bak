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
    "JAVA_HOME",
    "ANDROID_HOME",
    "ANDROID_SDK_ROOT",
    "ANDROID_NDK_HOME",
    "ANDROID_NDK_ROOT",
    "GRADLE_HOME",
    "FLUTTER_HOME",
    "DOTNET_ROOT",
    "DOTNET_CLI_HOME",
    "NUGET_PACKAGES",
    "PUB_CACHE",
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
