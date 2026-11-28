import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read=p=>fs.readFileSync(new URL('../../'+p,import.meta.url),'utf8');
const base='android-app/app/src/main/java/com/appforge/studio/';
test('R69 scanner is read-only and bounded, no symlink following or cleanup authorization',()=>{
 const scan=read(base+'build/R69StorageInventory.kt');
 assert.match(scan,/Files\.walkFileTree\(rootPath,/);
 assert.match(scan,/LinkOption\.NOFOLLOW_LINKS/);
 assert.match(scan,/MessageDigest\.getInstance\("SHA-256"\)/);
 assert.match(scan,/loc\.managed && size>=minDuplicateBytes/);
 assert.match(scan,/maxHashBytes-hashed/);
 assert.match(scan,/distinctBy \{ it\.key/);
 assert.doesNotMatch(scan,/Files\.(delete|move|copy|create)|deleteRecursively|writeText|writeBytes/);
 const ui=read(base+'AppForgeStorageDetailsScreen.kt');
 assert.match(ui,/R69StorageInventory\.scan/);
 assert.match(ui,/ModuleInstallOperations\.busy\.value/);
 assert.match(ui,/Fiziksel tasarruf veya silme izni değildir/);
});
