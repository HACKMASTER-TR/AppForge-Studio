import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const read = s => fs.readFileSync(new URL('../../'+s,import.meta.url),'utf8');
const theme=read('android-app/app/src/main/java/com/appforge/studio/AppForgeUiTokens.kt');
const home=read('android-app/app/src/main/java/com/appforge/studio/ui/StudioHomeDashboard.kt');
const modules=read('android-app/app/src/main/java/com/appforge/studio/R66ModuleRow.kt');
const settings=read('android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt');
test('R30 theme uses AMOLED surface variants and turquoise outlines',()=>{
  for (const p of ['outline = Accent','outlineVariant = Accent','surfaceContainer = Bg','surfaceContainerHighest = Bg','surfaceTint = Bg','Color(0xFF43D7FF)']) assert.ok(theme.includes(p),p);
});
test('R30 typography follows actual device dp and pins only font scale',()=>{
  assert.match(theme,/Density\(physicalDensity\.density, 1f\)/);
  assert.doesNotMatch(theme,/screenWidthDp = targetWidthDp|displayScale/);
  for (const name of ['displayLarge','titleMedium','bodySmall','labelLarge']) assert.ok(theme.includes(name+' = base.'+name+'.copy(fontFamily = FontFamily.SansSerif)'));
});
test('R30 card boundaries are visible in home, module center and settings',()=>{
  assert.ok(home.includes('BorderStroke(1.dp, Color(0xFF43D7FF))'));
  assert.ok(home.includes('.border(1.dp, Color(0xFF43D7FF), RoundedCornerShape(28.dp))'));
  for (const [key,s] of [['home',home],['modules',modules],['settings',settings]]) {
    if(key==='modules')assert.match(s,/\.border\(1.dp,Color\(0xFF328FA9\),cardShape\)/,key);
    else assert.match(s,/OutlinedCard\(/,key);
    assert.doesNotMatch(s,/^\s*ElevatedCard\(/m,key);
    assert.doesNotMatch(s,/^\s*Card\(/m,key);
  }
});
