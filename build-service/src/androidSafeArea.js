import {
  promises as fs
} from "fs";
import path from "path";

const SAFE_AREA_PROVIDER =
  "com.appforge.runtime.AppForgeSafeAreaProvider";

const SAFE_AREA_MARKER =
  "appforge.safearea";

async function exists(
  file
) {
  try {
    return (
      await fs.stat(
        file
      )
    )
      .isFile();
  } catch {
    return false;
  }
}

async function findGradleManifest(
  androidProjectDir
) {
  const direct =
    path.join(
      androidProjectDir,
      "app",
      "src",
      "main",
      "AndroidManifest.xml"
    );

  if (
    await exists(
      direct
    )
  ) {
    return direct;
  }

  const ignored =
    new Set([
      ".git",
      ".gradle",
      "build",
      "node_modules",
      ".dart_tool"
    ]);

  const matches =
    [];

  async function visit(
    dir,
    depth
  ) {
    if (
      depth > 6 ||
      matches.length > 24
    ) {
      return;
    }

    let entries;

    try {
      entries =
        await fs.readdir(
          dir,
          {
            withFileTypes:
              true
          }
        );
    } catch {
      return;
    }

    for (
      const entry of
      entries
    ) {
      if (
        ignored.has(
          entry.name
        )
      ) {
        continue;
      }

      const full =
        path.join(
          dir,
          entry.name
        );

      if (
        entry.isDirectory()
      ) {
        await visit(
          full,
          depth + 1
        );
        continue;
      }

      if (
        entry.isFile() &&
        entry.name ===
          "AndroidManifest.xml"
      ) {
        const normalized =
          full
            .replaceAll(
              "\\",
              "/"
            )
            .toLowerCase();

        if (
          normalized.endsWith(
            "/src/main/androidmanifest.xml"
          )
        ) {
          matches.push(
            full
          );
        }
      }
    }
  }

  await visit(
    androidProjectDir,
    0
  );

  matches.sort(
    (
      a,
      b
    ) => {
      const appA =
        a
          .replaceAll(
            "\\",
            "/"
          )
          .includes(
            "/app/src/main/"
          )
          ? 0
          : 1;

      const appB =
        b
          .replaceAll(
            "\\",
            "/"
          )
          .includes(
            "/app/src/main/"
          )
          ? 0
          : 1;

      return (
        appA - appB ||
        a.length - b.length
      );
    }
  );

  if (
    !matches.length
  ) {
    throw new Error(
      "AppForge safe-area: Android app manifest bulunamadı."
    );
  }

  return matches[0];
}

function javaProviderSource() {
  return `package com.appforge.runtime;

import android.app.Activity;
import android.app.Application;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class AppForgeSafeAreaProvider extends ContentProvider {

    private static final Map<Activity, Boolean> INSTALLED =
        Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public boolean onCreate() {
        if (getContext() == null) {
            return true;
        }

        final Application app =
            (Application) getContext().getApplicationContext();

        app.registerActivityLifecycleCallbacks(
            new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityCreated(
                    Activity activity,
                    Bundle state
                ) {
                    apply(activity);
                }

                @Override
                public void onActivityResumed(
                    Activity activity
                ) {
                    apply(activity);
                }

                @Override public void onActivityStarted(Activity activity) {}
                @Override public void onActivityPaused(Activity activity) {}
                @Override public void onActivityStopped(Activity activity) {}
                @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
                @Override public void onActivityDestroyed(Activity activity) {}
            }
        );

        return true;
    }

    private static void apply(
        Activity activity
    ) {
        if (
            activity == null ||
            INSTALLED.containsKey(activity)
        ) {
            return;
        }

        final Window window =
            activity.getWindow();

        if (window == null) {
            return;
        }

        window
            .getDecorView()
            .post(
                () -> {
                    if (
                        INSTALLED.containsKey(activity)
                    ) {
                        return;
                    }

                    final View content =
                        window
                            .getDecorView()
                            .findViewById(
                                android.R.id.content
                            );

                    if (content == null) {
                        return;
                    }

                    final int baseLeft =
                        content.getPaddingLeft();
                    final int baseTop =
                        content.getPaddingTop();
                    final int baseRight =
                        content.getPaddingRight();
                    final int baseBottom =
                        content.getPaddingBottom();

                    content.setOnApplyWindowInsetsListener(
                        (
                            view,
                            insets
                        ) -> {
                            if (
                                insets == null
                            ) {
                                return null;
                            }

                            view.setPadding(
                                baseLeft +
                                    insets.getSystemWindowInsetLeft(),
                                baseTop +
                                    insets.getSystemWindowInsetTop(),
                                baseRight +
                                    insets.getSystemWindowInsetRight(),
                                baseBottom +
                                    insets.getSystemWindowInsetBottom()
                            );

                            return insets;
                        }
                    );

                    INSTALLED.put(
                        activity,
                        Boolean.TRUE
                    );

                    content.requestApplyInsets();
                }
            );
    }

    @Override
    public Cursor query(
        Uri uri,
        String[] projection,
        String selection,
        String[] selectionArgs,
        String sortOrder
    ) {
        return null;
    }

    @Override
    public String getType(
        Uri uri
    ) {
        return null;
    }

    @Override
    public Uri insert(
        Uri uri,
        ContentValues values
    ) {
        return null;
    }

    @Override
    public int delete(
        Uri uri,
        String selection,
        String[] selectionArgs
    ) {
        return 0;
    }

    @Override
    public int update(
        Uri uri,
        ContentValues values,
        String selection,
        String[] selectionArgs
    ) {
        return 0;
    }
}
`;
}

function providerManifestNode() {
  return `
        <!-- appforge.safearea -->
        <provider
            android:name="${SAFE_AREA_PROVIDER}"
            android:authorities="\${applicationId}.appforge.safearea"
            android:exported="false"
            android:initOrder="100" />
`;
}

function injectManifestProvider(
  manifest
) {
  if (
    manifest.includes(
      SAFE_AREA_PROVIDER
    ) ||
    manifest.includes(
      SAFE_AREA_MARKER
    )
  ) {
    return manifest;
  }

  if (
    /<\/application\s*>/i.test(
      manifest
    )
  ) {
    return manifest.replace(
      /<\/application\s*>/i,
      providerManifestNode() +
        "    </application>"
    );
  }

  const selfClosing =
    manifest.match(
      /<application\b([^>]*)\/>/i
    );

  if (
    selfClosing
  ) {
    return manifest.replace(
      selfClosing[0],
      `<application${selfClosing[1]}>
${providerManifestNode()}    </application>`
    );
  }

  throw new Error(
    "AppForge safe-area: <application> manifest düğümü bulunamadı."
  );
}

export async function installGradleAndroidSafeArea({
  androidProjectDir
}) {
  if (
    !androidProjectDir
  ) {
    throw new Error(
      "AppForge safe-area: Android proje dizini eksik."
    );
  }

  const manifestFile =
    await findGradleManifest(
      androidProjectDir
    );

  const mainDir =
    path.dirname(
      manifestFile
    );

  const providerFile =
    path.join(
      mainDir,
      "java",
      "com",
      "appforge",
      "runtime",
      "AppForgeSafeAreaProvider.java"
    );

  await fs.mkdir(
    path.dirname(
      providerFile
    ),
    {
      recursive: true
    }
  );

  await fs.writeFile(
    providerFile,
    javaProviderSource(),
    "utf8"
  );

  const manifest =
    await fs.readFile(
      manifestFile,
      "utf8"
    );

  const patched =
    injectManifestProvider(
      manifest
    );

  if (
    patched !==
      manifest
  ) {
    await fs.writeFile(
      manifestFile,
      patched,
      "utf8"
    );
  }

  return {
    manifestFile,
    providerFile
  };
}

function safeDotnetPackage(
  packageName
) {
  const value =
    String(
      packageName ||
      ""
    )
      .trim();

  if (
    !/^[A-Za-z_]\w*(\.[A-Za-z_]\w*)+$/.test(
      value
    )
  ) {
    throw new Error(
      "AppForge safe-area: .NET package name geçersiz."
    );
  }

  return value;
}

export async function installDotnetAndroidSafeArea({
  projectRoot,
  packageName
}) {
  if (
    !projectRoot
  ) {
    throw new Error(
      "AppForge safe-area: .NET proje kökü eksik."
    );
  }

  const pkg =
    safeDotnetPackage(
      packageName
    );

  const dir =
    path.join(
      projectRoot,
      "AppForge"
    );

  const file =
    path.join(
      dir,
      "AppForgeSafeAreaProvider.cs"
    );

  await fs.mkdir(
    dir,
    {
      recursive: true
    }
  );

  const source =
`using Android.App;
using Android.Content;
using Android.Database;
using Android.OS;
using Android.Views;

namespace AppForge.SafeArea
{
    [ContentProvider(
        new string[] { "${pkg}.appforge.safearea" },
        Exported = false,
        InitOrder = 100
    )]
    public sealed class AppForgeSafeAreaProvider : Android.Content.ContentProvider
    {
        public override bool OnCreate()
        {
            var app =
                Context?.ApplicationContext as Application;

            if (app != null)
            {
                app.RegisterActivityLifecycleCallbacks(
                    new SafeAreaCallbacks()
                );
            }

            return true;
        }

        public override ICursor Query(
            Android.Net.Uri uri,
            string[] projection,
            string selection,
            string[] selectionArgs,
            string sortOrder
        ) => null;

        public override string GetType(
            Android.Net.Uri uri
        ) => null;

        public override Android.Net.Uri Insert(
            Android.Net.Uri uri,
            ContentValues values
        ) => null;

        public override int Delete(
            Android.Net.Uri uri,
            string selection,
            string[] selectionArgs
        ) => 0;

        public override int Update(
            Android.Net.Uri uri,
            ContentValues values,
            string selection,
            string[] selectionArgs
        ) => 0;

        private sealed class SafeAreaCallbacks :
            Java.Lang.Object,
            Application.IActivityLifecycleCallbacks
        {
            public void OnActivityCreated(
                Activity activity,
                Bundle savedInstanceState
            )
            {
                Apply(activity);
            }

            public void OnActivityResumed(
                Activity activity
            )
            {
                Apply(activity);
            }

            public void OnActivityStarted(Activity activity) {}
            public void OnActivityPaused(Activity activity) {}
            public void OnActivityStopped(Activity activity) {}
            public void OnActivitySaveInstanceState(Activity activity, Bundle outState) {}
            public void OnActivityDestroyed(Activity activity) {}

            private static void Apply(
                Activity activity
            )
            {
                var window =
                    activity?.Window;

                if (window == null)
                {
                    return;
                }

                window.DecorView.Post(
                    () =>
                    {
                        var content =
                            window.DecorView.FindViewById(
                                Android.Resource.Id.Content
                            );

                        if (content == null)
                        {
                            return;
                        }

                        var left =
                            content.PaddingLeft;
                        var top =
                            content.PaddingTop;
                        var right =
                            content.PaddingRight;
                        var bottom =
                            content.PaddingBottom;

                        content.SetOnApplyWindowInsetsListener(
                            new SafeAreaInsetsListener(
                                left,
                                top,
                                right,
                                bottom
                            )
                        );

                        content.RequestApplyInsets();
                    }
                );
            }
        }

        private sealed class SafeAreaInsetsListener :
            Java.Lang.Object,
            View.IOnApplyWindowInsetsListener
        {
            private readonly int left;
            private readonly int top;
            private readonly int right;
            private readonly int bottom;

            public SafeAreaInsetsListener(
                int left,
                int top,
                int right,
                int bottom
            )
            {
                this.left = left;
                this.top = top;
                this.right = right;
                this.bottom = bottom;
            }

            public WindowInsets OnApplyWindowInsets(
                View view,
                WindowInsets insets
            )
            {
                view.SetPadding(
                    left + insets.SystemWindowInsetLeft,
                    top + insets.SystemWindowInsetTop,
                    right + insets.SystemWindowInsetRight,
                    bottom + insets.SystemWindowInsetBottom
                );

                return insets;
            }
        }
    }
}
`;

  await fs.writeFile(
    file,
    source,
    "utf8"
  );

  return {
    sourceFile:
      file
  };
}

export async function installUnityAndroidSafeArea({
  projectRoot
}) {
  if (
    !projectRoot
  ) {
    throw new Error(
      "AppForge safe-area: Unity proje kökü eksik."
    );
  }

  const dir =
    path.join(
      projectRoot,
      "Assets",
      "AppForge"
    );

  const file =
    path.join(
      dir,
      "AppForgeSafeAreaRuntime.cs"
    );

  await fs.mkdir(
    dir,
    {
      recursive: true
    }
  );

  const source =
`using UnityEngine;

public sealed class AppForgeSafeAreaRuntime : MonoBehaviour
{
    [RuntimeInitializeOnLoadMethod(
        RuntimeInitializeLoadType.BeforeSceneLoad
    )]
    private static void Bootstrap()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        var existing =
            GameObject.Find(
                "__AppForgeSafeArea"
            );

        if (existing != null)
        {
            return;
        }

        var go =
            new GameObject(
                "__AppForgeSafeArea"
            );

        Object.DontDestroyOnLoad(
            go
        );

        go.AddComponent<AppForgeSafeAreaRuntime>();
#endif
    }

    private void Start()
    {
        Apply();
    }

    private void OnApplicationFocus(
        bool hasFocus
    )
    {
        if (hasFocus)
        {
            Apply();
        }
    }

    private static void Apply()
    {
#if UNITY_ANDROID && !UNITY_EDITOR
        using (
            var unityPlayer =
                new AndroidJavaClass(
                    "com.unity3d.player.UnityPlayer"
                )
        )
        {
            var activity =
                unityPlayer.GetStatic<AndroidJavaObject>(
                    "currentActivity"
                );

            if (activity == null)
            {
                return;
            }

            activity.Call(
                "runOnUiThread",
                new AndroidJavaRunnable(
                    () =>
                    {
                        var content =
                            activity.Call<AndroidJavaObject>(
                                "findViewById",
                                16908290
                            );

                        var resources =
                            activity.Call<AndroidJavaObject>(
                                "getResources"
                            );

                        if (
                            content == null ||
                            resources == null
                        )
                        {
                            return;
                        }

                        var statusId =
                            resources.Call<int>(
                                "getIdentifier",
                                "status_bar_height",
                                "dimen",
                                "android"
                            );

                        var navigationId =
                            resources.Call<int>(
                                "getIdentifier",
                                "navigation_bar_height",
                                "dimen",
                                "android"
                            );

                        var top =
                            statusId > 0
                                ? resources.Call<int>(
                                    "getDimensionPixelSize",
                                    statusId
                                )
                                : 0;

                        var bottom =
                            navigationId > 0
                                ? resources.Call<int>(
                                    "getDimensionPixelSize",
                                    navigationId
                                )
                                : 0;

                        content.Call(
                            "setPadding",
                            content.Call<int>(
                                "getPaddingLeft"
                            ),
                            top,
                            content.Call<int>(
                                "getPaddingRight"
                            ),
                            bottom
                        );
                    }
                )
            );
        }
#endif
    }
}
`;

  await fs.writeFile(
    file,
    source,
    "utf8"
  );

  return {
    sourceFile:
      file
  };
}
