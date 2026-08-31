const GAME_SIGNALS =
  /(^|[\s._-])(game|gaming|oyun(?:u|lar[ıi]?)?|arcade|puzzle|racing|runner|platformer|shooter|rpg)($|[\s._-])/i;

export function resolveAndroidAppCategory(
  config
) {
  const requested =
    String(
      config?.appCategory ||
      "auto"
    )
      .trim()
      .toLowerCase();

  if (
    requested ===
    "game"
  ) {
    return "game";
  }

  if (
    requested ===
    "none"
  ) {
    return null;
  }

  const technology =
    String(
      config?.sourceTechnology ||
      ""
    ).toLowerCase();

  const engine =
    String(
      config?.sourceBuildEngine ||
      ""
    ).toLowerCase();

  if (
    technology.includes(
      "unity"
    ) ||
    engine.includes(
      "unity"
    )
  ) {
    return "game";
  }

  const searchable =
    [
      config?.appName,
      config?.sourceTechnologyLabel,
      config?.sourceLabel,
      config?.templateId,
      config?.templateCategory
    ]
      .filter(Boolean)
      .join(" ");

  return GAME_SIGNALS.test(
    searchable
  )
    ? "game"
    : null;
}

export function androidAppCategoryAttribute(
  config
) {
  const category =
    resolveAndroidAppCategory(
      config
    );

  return category
    ? `\n        android:appCategory="${category}"`
    : "";
}
