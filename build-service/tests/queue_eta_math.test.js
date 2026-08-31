import test from "node:test";
import assert from "node:assert/strict";

import {
  estimateQueueWaitSeconds
} from "../src/queueEstimate.js";

test(
  "busy single worker gives one average build wait",
  () => {
    assert.equal(
      estimateQueueWaitSeconds({
        ahead: 0,
        compatibleWorkerSlots: 1,
        runningCompatibleJobs: 1,
        averageBuildSeconds: 90
      }),
      90
    );
  }
);

test(
  "free slot allows first queued build immediately",
  () => {
    assert.equal(
      estimateQueueWaitSeconds({
        ahead: 0,
        compatibleWorkerSlots: 2,
        runningCompatibleJobs: 1,
        averageBuildSeconds: 90
      }),
      0
    );
  }
);

test(
  "one job ahead consumes remaining free slot",
  () => {
    assert.equal(
      estimateQueueWaitSeconds({
        ahead: 1,
        compatibleWorkerSlots: 2,
        runningCompatibleJobs: 1,
        averageBuildSeconds: 90
      }),
      90
    );
  }
);

test(
  "retry delay can dominate worker wait",
  () => {
    assert.equal(
      estimateQueueWaitSeconds({
        ahead: 0,
        compatibleWorkerSlots: 2,
        runningCompatibleJobs: 0,
        averageBuildSeconds: 90,
        availableInSeconds: 120
      }),
      120
    );
  }
);

test(
  "ETA unavailable without worker capacity",
  () => {
    assert.equal(
      estimateQueueWaitSeconds({
        ahead: 5,
        compatibleWorkerSlots: 0,
        runningCompatibleJobs: 0,
        averageBuildSeconds: 90
      }),
      null
    );
  }
);
