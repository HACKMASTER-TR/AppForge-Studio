CREATE SEQUENCE IF NOT EXISTS appforge_build_no_seq;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS build_no BIGINT;

/*
 * Eski build kayıtlarını kronolojik olarak numaralandır.
 * Böylece ilk migration sonucunda AF numaraları rastgele sıraya bağlı olmaz.
 */
WITH current_max AS (
    SELECT COALESCE(MAX(build_no), 0)::BIGINT AS max_no
    FROM appforge_builds
),
missing AS (
    SELECT
        b.id,
        current_max.max_no
            + ROW_NUMBER() OVER (
                ORDER BY b.created_at ASC, b.id ASC
              ) AS new_build_no
    FROM appforge_builds b
    CROSS JOIN current_max
    WHERE b.build_no IS NULL
)
UPDATE appforge_builds b
SET build_no = missing.new_build_no
FROM missing
WHERE b.id = missing.id;

SELECT setval(
    'appforge_build_no_seq',
    GREATEST(
        COALESCE(
            (SELECT MAX(build_no) FROM appforge_builds),
            0
        ),
        1
    ),
    COALESCE(
        (SELECT MAX(build_no) FROM appforge_builds),
        0
    ) > 0
);

ALTER TABLE appforge_builds
ALTER COLUMN build_no
SET DEFAULT nextval('appforge_build_no_seq');

ALTER TABLE appforge_builds
ALTER COLUMN build_no
SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_appforge_builds_build_no
ON appforge_builds(build_no);
