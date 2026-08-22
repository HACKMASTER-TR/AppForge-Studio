CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS appforge_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name TEXT NOT NULL DEFAULT '',
    role TEXT NOT NULL DEFAULT 'user'
        CHECK (role IN ('user', 'admin')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS appforge_api_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    prefix TEXT NOT NULL,
    last_used_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS appforge_projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    package_name TEXT NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, package_name)
);

CREATE TABLE IF NOT EXISTS appforge_builds (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    project_id UUID REFERENCES appforge_projects(id) ON DELETE SET NULL,
    app_name TEXT NOT NULL,
    package_name TEXT NOT NULL,
    status TEXT NOT NULL
        CHECK (status IN ('queued', 'building', 'success', 'failed', 'cancelled')),
    progress INTEGER NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    output_type TEXT NOT NULL DEFAULT 'both',
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    preflight JSONB NOT NULL DEFAULT '[]'::jsonb,
    logs JSONB NOT NULL DEFAULT '[]'::jsonb,
    outputs JSONB NOT NULL DEFAULT '{}'::jsonb,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_builds_user_created
ON appforge_builds(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_builds_status_created
ON appforge_builds(status, created_at);

CREATE TABLE IF NOT EXISTS appforge_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    category TEXT NOT NULL DEFAULT 'general',
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID REFERENCES appforge_users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS appforge_localizations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES appforge_projects(id) ON DELETE CASCADE,
    locale TEXT NOT NULL,
    strings JSONB NOT NULL DEFAULT '{}'::jsonb,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(project_id, locale)
);

CREATE TABLE IF NOT EXISTS appforge_publish_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES appforge_users(id) ON DELETE CASCADE,
    build_id UUID NOT NULL REFERENCES appforge_builds(id) ON DELETE CASCADE,
    track TEXT NOT NULL DEFAULT 'internal',
    status TEXT NOT NULL DEFAULT 'draft',
    release_name TEXT,
    release_notes JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO appforge_templates (slug, name, description, category, config, is_system)
VALUES
(
    'blank-webview',
    'Boş WebView',
    'Temel HTML/URL tabanlı Android uygulaması.',
    'general',
    '{"sourceMode":"URL","features":{"fileUpload":true,"downloads":true}}'::jsonb,
    TRUE
),
(
    'portfolio',
    'Portföy',
    'Portföy ve kişisel site için sade WebView şablonu.',
    'business',
    '{"sourceMode":"URL","orientation":"portrait","features":{"downloads":true}}'::jsonb,
    TRUE
),
(
    'html-game',
    'HTML5 Oyun',
    'Yerel HTML5 oyunları için tam ekran uygulama.',
    'game',
    '{"sourceMode":"LOCAL","orientation":"landscape","features":{"fullscreen":true,"offlineCache":true}}'::jsonb,
    TRUE
),
(
    'storefront',
    'Mağaza',
    'E-ticaret sitesi için dosya yükleme, kamera ve deep-link odaklı şablon.',
    'commerce',
    '{"sourceMode":"URL","features":{"fileUpload":true,"camera":true,"downloads":true}}'::jsonb,
    TRUE
)
ON CONFLICT (slug) DO NOTHING;
