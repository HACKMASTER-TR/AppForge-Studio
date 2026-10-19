import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { createHash } from 'node:crypto';

const root = path.resolve(new URL('../../', import.meta.url).pathname);
const read = rel => fs.readFileSync(path.join(root, rel), 'utf8');

test('V7 PersistentOperationGraph defines REMOVAL and ROLLBACK stages and atomic terminal subtree pruning', () => {
  const graph = read('android-app/app/src/main/java/com/appforge/studio/build/PersistentOperationGraph.kt');
  const coordinator = read('android-app/app/src/main/java/com/appforge/studio/build/DownloadCoordinator.kt');
  const screen = read('android-app/app/src/main/java/com/appforge/studio/OfflineBuildPackScreen.kt');

  assert.match(graph, /enum class OperationStage \{[^}]*REMOVAL,\s*ROLLBACK\s*\}/);
  assert.match(graph, /fun pruneRetained\(rows:List<DownloadOperationNode>,maxNodes:Int=512\):List<DownloadOperationNode>/);
  assert.match(graph, /private val TERMINAL_STATES=setOf\(DownloadState\.COMPLETED,DownloadState\.CANCELLED,DownloadState\.FAILED\)/);
  assert.match(graph, /if\(nodes\.all\{it\.operationState in TERMINAL_STATES\}\)Triple\(root,ids,nodes\.maxOf\{it\.updatedAt\}\)/);
  assert.match(graph, /if\(!Files\.isSymbolicLink\(tmp\.toPath\(\)\)\)Files\.deleteIfExists\(tmp\.toPath\(\)\)/);

  assert.match(coordinator, /ModulePhase\.REMOVING,ModulePhase\.REMOVED->OperationStage\.REMOVAL/);
  assert.match(coordinator, /ModulePhase\.ROLLING_BACK->OperationStage\.ROLLBACK/);
  assert.match(coordinator, /ModulePhase\.READY,ModulePhase\.REMOVED->DownloadState\.COMPLETED/);
  assert.match(coordinator, /val bounded=PersistentOperationGraph\.pruneRetained\(rows\)/);

  assert.match(screen, /fun recordLifecycle\(event:ModuleProgress\)\{DownloadCoordinator\.modulePhase\(event\);publish\(event\)\}/);
});

test('V7 terminal subtree pruning algorithm preserves RECOVERY_REQUIRED and active subtrees while evicting oldest terminal subtrees atomically', () => {
  const terminal = new Set(['COMPLETED', 'CANCELLED', 'FAILED']);
  const id = (...parts) => createHash('sha256').update(parts.join('\0')).digest('hex');

  function pruneRetained(rows, maxNodes = 512) {
    assert(maxNodes >= 1 && maxNodes <= 512);
    if (rows.length <= maxNodes) return rows;
    const working = [...rows];
    while (working.length > maxNodes) {
      const childrenByParent = new Map();
      for (const n of working) {
        if (n.parentOperationId !== null) {
          const list = childrenByParent.get(n.parentOperationId) ?? [];
          list.push(n);
          childrenByParent.set(n.parentOperationId, list);
        }
      }
      const subtree = rootId => {
        const visited = new Set();
        const queue = [rootId];
        while (queue.length > 0) {
          const cur = queue.shift();
          if (!visited.has(cur)) {
            visited.add(cur);
            for (const c of childrenByParent.get(cur) ?? []) queue.push(c.operationId);
          }
        }
        return visited;
      };
      const byId = new Map(working.map(n => [n.operationId, n]));
      const candidates = [];
      for (const rootNode of working) {
        if (rootNode.parentOperationId !== null || rootNode.stage !== 'MODULE') continue;
        const ids = subtree(rootNode.operationId);
        const nodes = [...ids].map(k => byId.get(k)).filter(Boolean);
        if (nodes.every(n => terminal.has(n.operationState))) {
          const maxUpdated = Math.max(...nodes.map(n => n.updatedAt));
          candidates.push({ rootNode, ids, maxUpdated });
        }
      }
      if (candidates.length === 0) break;
      candidates.sort((a, b) =>
        a.maxUpdated - b.maxUpdated ||
        a.rootNode.createdAt - b.rootNode.createdAt ||
        a.rootNode.operationId.localeCompare(b.rootNode.operationId)
      );
      const chosen = candidates[0].ids;
      for (let i = working.length - 1; i >= 0; i--) {
        if (chosen.has(working[i].operationId)) working.splice(i, 1);
      }
    }
    return working;
  }

  const r1 = id('module', 'op-1', 'NODE_WEB');
  const c1 = id(r1, 'INSTALLATION');
  const r2 = id('module', 'op-2', 'NODE_WEB');
  const c2 = id(r2, 'ARTIFACT_DOWNLOAD');
  const r3 = id('module', 'op-3', 'ANDROID_CORE');
  const c3 = id(r3, 'REMOVAL');

  const rows = [
    { operationId: r1, parentOperationId: null, stage: 'MODULE', operationState: 'COMPLETED', createdAt: 1, updatedAt: 10 },
    { operationId: c1, parentOperationId: r1, stage: 'INSTALLATION', operationState: 'COMPLETED', createdAt: 1, updatedAt: 12 },
    { operationId: r2, parentOperationId: null, stage: 'MODULE', operationState: 'COMPLETED', createdAt: 2, updatedAt: 5 },
    { operationId: c2, parentOperationId: r2, stage: 'ARTIFACT_DOWNLOAD', operationState: 'RECOVERY_REQUIRED', createdAt: 2, updatedAt: 6 },
    { operationId: r3, parentOperationId: null, stage: 'MODULE', operationState: 'COMPLETED', createdAt: 3, updatedAt: 20 },
    { operationId: c3, parentOperationId: r3, stage: 'REMOVAL', operationState: 'COMPLETED', createdAt: 3, updatedAt: 25 },
  ];

  const pruned = pruneRetained(rows, 4);
  assert.equal(pruned.length, 4);
  assert(!pruned.some(n => n.operationId === r1 || n.operationId === c1), 'oldest complete subtree must be evicted atomically');
  assert(pruned.some(n => n.operationId === r2) && pruned.some(n => n.operationId === c2), 'RECOVERY_REQUIRED subtree must never be evicted');
  assert(pruned.some(n => n.operationId === r3) && pruned.some(n => n.operationId === c3), 'newer terminal subtree must remain');
});

test('V7 ModuleOperationJournal and RuntimeGenerationStore enforce disk preflight, URL rejection, and partial COPYING reclamation', () => {
  const journal = read('android-app/app/src/main/java/com/appforge/studio/build/ModuleOperationJournal.kt');
  const generation = read('android-app/app/src/main/java/com/appforge/studio/build/RuntimeGenerationStore.kt');

  assert.match(journal, /internal class ModuleOperationJournal\(private val file:File,private val availableBytes:\(File\)->Long=\{it\.usableSpace\}\)/);
  assert.match(journal, /!e\.operationId\.contains\(":\/\/"\) && !e\.detail\.contains\("https:\/\/"\) && !e\.detail\.contains\("http:\/\/"\)/);
  assert.match(journal, /require\(availableBytes\(parent\)>bytes\.size\+65536L\)/);
  assert.match(journal, /if\(!Files\.isSymbolicLink\(part\.toPath\(\)\)\)Files\.deleteIfExists\(part\.toPath\(\)\)/);

  assert.match(generation, /private fun atomicWrite\(target:File,bytes:ByteArray,availableBytes:\(\)->Long=defaultAvailableBytes\)/);
  assert.match(generation, /require\(availableBytes\(\)>bytes\.size\+65536L\)/);
  assert.match(generation, /require\(availableBytes\(\)>attrs\.size\(\)\+64L\*1024\*1024\)\{"Kopyalama sırasında depolama alanı tükendi; aktif ortam korundu\."\}/);
  assert.match(generation, /if\(state=="COPYING" && !old\.exists\(\) && activeEntries!=null && stageEntries!=null && treeDigest\(activeEntries\)==j\.getString\("previousDigest"\) && stageEntries\.all\{\(k,v\)->activeEntries\[k\]==v\}\)/);
  assert.match(generation, /fun quarantinedDirectories\(\):List<File>/);
  assert.match(generation, /fun quarantinedBytes\(\):Long/);
});

test('V7 RuntimeArchiveRemoval, OwnedModuleFiles, ModuleOwnershipStore, and RootfsActivationTransaction enforce storage and conflict guards', () => {
  const archiveRemoval = read('android-app/app/src/main/java/com/appforge/studio/build/RuntimeArchiveRemoval.kt');
  const ownedFiles = read('android-app/app/src/main/java/com/appforge/studio/build/OwnedModuleFiles.kt');
  const ownershipStore = read('android-app/app/src/main/java/com/appforge/studio/build/ModuleOwnershipStore.kt');
  const rootfsTx = read('android-app/app/src/main/java/com/appforge/studio/terminal/RootfsActivationTransaction.kt');

  assert.match(archiveRemoval, /require\(availableBytes\(parent\)>bytes\.size\+65536L\)/);
  assert.match(archiveRemoval, /require\(j\.optInt\("schema",1\)==1\)/);
  assert.match(archiveRemoval, /require\(!active\.exists\(\)\)\{"Aktif runtime ile kurtarma arşivi birlikte var; ikisi de korundu\."\}/);

  assert.match(ownedFiles, /require\(availableBytes\(parent\)>bytes\.size\+65536L\)/);
  assert.match(ownedFiles, /saved\.listFiles\(\)\.orEmpty\(\)\.none\{Files\.isSymbolicLink\(it\.toPath\(\)\)\}/);

  assert.match(ownershipStore, /source\.inputStream\(\)\.use\{input->java\.io\.FileOutputStream\(tmp\)\.use\{out->input\.copyTo\(out\);out\.fd\.sync\(\)\}\}/);
  assert.match(ownershipStore, /if\(!Files\.isSymbolicLink\(tmp\.toPath\(\)\)\)Files\.deleteIfExists\(tmp\.toPath\(\)\)/);
  assert.match(ownershipStore, /require\(availableBytes\(f\.parentFile\)>bytes\.size\+65536L\)/);

  assert.match(rootfsTx, /require\(availableBytes\(parent\)>bytes\.size\+65536L\)/);
  assert.match(rootfsTx, /require\(availableBytes\(restore\)>metadata\.length\(\)\+65536L\)/);
});
