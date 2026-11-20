import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const screen=readFileSync(new URL('../../android-app/app/src/main/java/com/appforge/studio/AdminAiRouterScreen.kt', import.meta.url),'utf8');
test('R30 Admin AI result Card has an explicit Material3 import',()=>{
  assert.match(screen,/^import androidx\.compose\.material3\.Card$/m);
  assert.match(screen,/result\?\.let\s*\{\s*answer\s*->\s*item\s*\{\s*Card\(/);
  assert.match(screen,/^import androidx\.compose\.material3\.OutlinedCard$/m);
});
