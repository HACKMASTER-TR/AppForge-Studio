CREATE SEQUENCE IF NOT EXISTS appforge_build_no_seq;

ALTER TABLE appforge_builds
ADD COLUMN IF NOT EXISTS build_no BIGINT;

UPDATE appforge_builds
SET build_no = nextval('appforge_build_no_seq')
WHERE build_no IS NULL;

SELECT setval(
    'appforge_build_no_seq',
    GREATEST(
        COALESCE(
            (SELECT MAX(build_no) FROM appforge_builds),
            1
        ),
        1
    ),
    EXISTS(
        SELECT 1
        FROM appforge_builds
        WHERE build_no IS NOT NULL
    )
);

ALTER TABLE appforge_builds
ALTER COLUMN build_no
SET DEFAULT nextval('appforge_build_no_seq');

ALTER TABLE appforge_builds
ALTER COLUMN build_no
SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_appforge_builds_build_no
ON appforge_builds(build_no);
