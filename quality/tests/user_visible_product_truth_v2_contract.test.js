import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";

const read = async path =>
  readFile(
    new URL("../../" + path, import.meta.url),
    "utf8"
  );

const settings =
  await read(
    "android-app/app/src/main/java/com/appforge/studio/AppForgeSettingsScreens.kt"
  );

const knowledge =
  await read(
    "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeKnowledgeBase.kt"
  );

const main =
  await read(
    "android-app/app/src/main/java/com/appforge/studio/MainActivity.kt"
  );

const assistant =
  await read(
    "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeAssistantIntegration.kt"
  );

const advisor =
  await read(
    "android-app/app/src/main/java/com/appforge/studio/ai/AppForgeBuildErrorAdvisor.kt"
  );

const i18n =
  await read(
    "android-app/app/src/main/java/com/appforge/studio/i18n/StudioI18n.kt"
  );

test(
  "Legal Center matches accountless device-local lifetime product",
  () => {
    assert.match(settings, /Cihazda build ve proje verileri/);
    assert.match(settings, /Normal AppForge kullanımı hesapsızdır/);
    assert.match(settings, /AppForge Pro Ömür Boyu tek seferlik Google Play satın alımıdır/);

    assert.doesNotMatch(
      settings,
      /Pro Aylık|Ek kota paketleri yalnız|Bulut build ve proje verileri/
    );
  }
);

test(
  "knowledge base contains only current AppForge product truth",
  () => {
    assert.match(
      knowledge,
      /Normal AppForge kullanımı hesapsızdır/
    );

    assert.match(
      knowledge,
      /Pro Ömür Boyu/
    );

    assert.doesNotMatch(
      knowledge,
      /Pro Aylık|toplam 5 farklı proje|Hesabımı nasıl silebilirim\?/
    );
  }
);

test(
  "Project Library has no retired free-project trial counter",
  () => {
    assert.doesNotMatch(
      main,
      /Deneme Hakkı|yeni proje hakkın kaldı|Ücretsiz denemede toplam/
    );

    assert.doesNotMatch(
      main,
      /trialSlotsUsed|serverFreeProjectUsed/
    );
  }
);

test(
  "assistant routes accountless and lifetime-only product surfaces",
  () => {
    assert.match(
      assistant,
      /Hesapsız kullanımı aç/
    );

    assert.match(
      assistant,
      /Pro Ömür Boyu/
    );

    assert.doesNotMatch(
      assistant,
      /Hesabı aç|Oturum ve Build Service bağlantısını yönet|Pro Aylık/
    );
  }
);

test(
  "legacy build responses cannot advertise retired plans",
  () => {
    assert.match(
      advisor,
      /Eski proje kotası yanıtı algılandı/
    );

    assert.match(
      advisor,
      /Eski aylık Pro yanıtı algılandı/
    );

    assert.doesNotMatch(
      advisor,
      /FREE proje hakkı doldu|FREE hesabın|50 başarılı farklı proje hakkı|Pro \/ Pro Aylık/
    );
  }
);

test(
  "translation catalog cannot surface monthly AppForge Pro",
  () => {
    assert.match(
      i18n,
      /Pro Ömür Boyu/
    );

    assert.doesNotMatch(
      i18n,
      /Pro Aylık|Pro Monthly|Pro Monatlich|Pro شهري/
    );

    assert.doesNotMatch(
      i18n,
      /"pro_monthly"|"pro_monthly_desc"|"subscribe_monthly"/
    );
  }
);
