/*
 * AppForge successful-project quota V2
 *
 * FREE:
 *   1 successful distinct project.
 *
 * PRO MONTHLY:
 *   50 successful distinct projects per subscription period.
 *
 * Failed/cancelled builds never consume successful-project quota.
 */

CREATE TABLE IF NOT EXISTS appforge_pro_monthly_project_slots (
    user_id UUID NOT NULL
        REFERENCES appforge_users(id)
        ON DELETE CASCADE,

    cycle_end TIMESTAMPTZ NOT NULL,

    package_name TEXT NOT NULL,

    first_success_at TIMESTAMPTZ
        NOT NULL
        DEFAULT NOW(),

    last_seen_at TIMESTAMPTZ
        NOT NULL
        DEFAULT NOW(),

    PRIMARY KEY (
        user_id,
        cycle_end,
        package_name
    )
);

CREATE INDEX IF NOT EXISTS idx_appforge_pro_monthly_slots_user_cycle
ON appforge_pro_monthly_project_slots (
    user_id,
    cycle_end,
    first_success_at
);


CREATE TABLE IF NOT EXISTS appforge_project_quota_reservations (
    user_id UUID NOT NULL
        REFERENCES appforge_users(id)
        ON DELETE CASCADE,

    quota_key TEXT NOT NULL,

    package_name TEXT NOT NULL,

    created_at TIMESTAMPTZ
        NOT NULL
        DEFAULT NOW(),

    expires_at TIMESTAMPTZ
        NOT NULL,

    PRIMARY KEY (
        user_id,
        quota_key,
        package_name
    )
);

CREATE INDEX IF NOT EXISTS idx_appforge_quota_reservations_expiry
ON appforge_project_quota_reservations (
    expires_at
);


/*
 * SQL migration dosyaları servis başlangıcında yeniden
 * uygulanabildiği için veri dönüştürme işlemleri marker ile
 * yalnız bir kez çalıştırılır.
 */
CREATE TABLE IF NOT EXISTS appforge_migration_markers (
    migration_key TEXT PRIMARY KEY,

    applied_at TIMESTAMPTZ
        NOT NULL
        DEFAULT NOW()
);


DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM appforge_migration_markers
        WHERE migration_key =
            'success-project-quota-v2-legacy-slot-cleanup'
    ) THEN

        /*
         * Eski sistem proje oluşturulurken/kaydedilirken FREE
         * slot ayırıyordu.
         *
         * Başarılı build'i olmayan eski kayıtlar artık kota
         * tüketmemeli.
         */
        DELETE FROM appforge_free_project_slots slot
        WHERE NOT EXISTS (
            SELECT 1
            FROM appforge_builds build
            WHERE build.user_id =
                    slot.user_id
              AND build.package_name =
                    slot.package_name
              AND build.status =
                    'success'
        );


        /*
         * Başarılı build'i bulunan gerçek eski slotları koru.
         */
        UPDATE appforge_free_project_slots slot
        SET last_seen_at =
            GREATEST(
                slot.last_seen_at,
                COALESCE(
                    (
                        SELECT MAX(
                            build.completed_at
                        )
                        FROM appforge_builds build
                        WHERE build.user_id =
                                slot.user_id
                          AND build.package_name =
                                slot.package_name
                          AND build.status =
                                'success'
                    ),
                    slot.last_seen_at
                )
            );


        INSERT INTO appforge_migration_markers (
            migration_key
        )
        VALUES (
            'success-project-quota-v2-legacy-slot-cleanup'
        )
        ON CONFLICT (
            migration_key
        )
        DO NOTHING;

    END IF;
END
$$;
