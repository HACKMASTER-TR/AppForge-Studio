import {
  config
} from "./config.js";


export function quotaAddonCatalog() {
  return [
    {
      productId:
        config.studioQuota10ProductId,
      projectBonus: 10,
      buildBonus: 20
    },
    {
      productId:
        config.studioQuota25ProductId,
      projectBonus: 25,
      buildBonus: 50
    },
    {
      productId:
        config.studioQuota50ProductId,
      projectBonus: 50,
      buildBonus: 100
    }
  ];
}


export function quotaAddonForProduct(
  productId
) {
  const normalized =
    String(
      productId || ""
    ).trim();

  return (
    quotaAddonCatalog()
      .find(
        item =>
          item.productId ===
          normalized
      ) ||
    null
  );
}


export function quotaAddonProductIds() {
  return quotaAddonCatalog()
    .map(
      item =>
        item.productId
    );
}
