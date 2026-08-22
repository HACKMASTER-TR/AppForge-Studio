import test from "node:test";
import assert from "node:assert/strict";
import {
  evaluateSubscription,
  evaluateOneTimeProduct
} from "../src/playVerifier.js";

test("active subscription with future expiry grants entitlement", () => {
  const result =
    evaluateSubscription(
      {
        subscriptionState:
          "SUBSCRIPTION_STATE_ACTIVE",
        acknowledgementState:
          "ACKNOWLEDGEMENT_STATE_PENDING",
        lineItems: [
          {
            productId:
              "premium_monthly",
            expiryTime:
              "2030-01-01T00:00:00Z"
          }
        ]
      },
      "premium_monthly",
      new Date(
        "2026-08-22T00:00:00Z"
      )
    );

  assert.equal(
    result.entitlement,
    true
  );
});

test("expired subscription never grants entitlement", () => {
  const result =
    evaluateSubscription(
      {
        subscriptionState:
          "SUBSCRIPTION_STATE_EXPIRED",
        lineItems: [
          {
            productId:
              "premium_monthly",
            expiryTime:
              "2020-01-01T00:00:00Z"
          }
        ]
      },
      "premium_monthly",
      new Date(
        "2026-08-22T00:00:00Z"
      )
    );

  assert.equal(
    result.entitlement,
    false
  );
});

test("pending one-time purchase does not grant entitlement", () => {
  const result =
    evaluateOneTimeProduct(
      {
        purchaseStateContext: {
          purchaseState:
            "PENDING"
        },
        productLineItem: [
          {
            productId:
              "premium_unlock",
            productOfferDetails: {
              consumptionState:
                "CONSUMPTION_STATE_YET_TO_BE_CONSUMED"
            }
          }
        ]
      },
      "premium_unlock"
    );

  assert.equal(
    result.entitlement,
    false
  );
});

test("purchased one-time product grants entitlement", () => {
  const result =
    evaluateOneTimeProduct(
      {
        purchaseStateContext: {
          purchaseState:
            "PURCHASED"
        },
        acknowledgementState:
          "ACKNOWLEDGEMENT_STATE_PENDING",
        productLineItem: [
          {
            productId:
              "premium_unlock",
            productOfferDetails: {
              consumptionState:
                "CONSUMPTION_STATE_YET_TO_BE_CONSUMED"
            }
          }
        ]
      },
      "premium_unlock"
    );

  assert.equal(
    result.entitlement,
    true
  );
});
