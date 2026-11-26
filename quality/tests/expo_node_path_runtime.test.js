import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

test('Expo Node bootstrap exposes the pinned runtime to npm shebangs when no system Node exists',()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'af-expo-node-path-'));
 try {
  const node=path.join(dir,'node/bin');fs.mkdirSync(node,{recursive:true});fs.mkdirSync(path.join(dir,'empty'));
  fs.writeFileSync(path.join(node,'node'),'#!/bin/sh\nif [ "$1" = --version ]; then echo v22.23.3; else echo NPM_USES_PINNED_NODE; fi\n',{mode:0o755});
  fs.writeFileSync(path.join(node,'npm'),'#!/usr/bin/env node\n// npm-like shebang fixture\n',{mode:0o755});
  const source=fs.readFileSync(new URL('../../android-app/app/src/main/assets/device-build/install-toolchain.sh',import.meta.url),'utf8');
  const definition=source.match(/ensure_node_22\(\) \{[\s\S]*?\n\}/)?.[0];assert(definition);
  const script=`NODE_HOME="$1/node"\nPATH="$1/empty"\n${definition}\nensure_node_22\n"$NODE_HOME/bin/npm" --version\n`;
  const result=spawnSync('/bin/sh',['-eu','-c',script,'fixture',dir],{encoding:'utf8'});
  assert.equal(result.status,0,result.stderr);assert(result.stdout.includes('NPM_USES_PINNED_NODE'));
 } finally {fs.rmSync(dir,{recursive:true,force:true});}
});
