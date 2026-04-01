import os
import time
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker, declarative_base

DATABASE_URL = os.getenv(
    "DATABASE_URL",
    "postgresql://myuser:mypassword@localhost:5432/myappdb"
)

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()


def wait_for_database(max_retries: int = 60, delay: int = 2):
    """Aspetta che il database sia disponibile"""
    for attempt in range(max_retries):
        try:
            with engine.connect() as conn:
                conn.execute(text("SELECT 1"))
            print("✓ Database is ready!")
            return
        except Exception as e:
            print(f"Database not ready, attempt {attempt + 1}/{max_retries} - Error: {type(e).__name__}")
            time.sleep(delay)
    raise RuntimeError("Could not connect to database after retries")


def ensure_schema() -> None:
    with engine.begin() as conn:
        user_table_exists = conn.execute(
            text(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = 'users'
                )
                """
            )
        ).scalar()

        if not user_table_exists:
            return

        existing_columns = {
            row[0]
            for row in conn.execute(
                text(
                    """
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'users'
                    """
                )
            )
        }

        if "firebase_uid" not in existing_columns:
            conn.execute(text("ALTER TABLE users ADD COLUMN firebase_uid VARCHAR"))

        if "name" not in existing_columns:
            conn.execute(text("ALTER TABLE users ADD COLUMN name VARCHAR"))

        if "surname" not in existing_columns:
            conn.execute(text("ALTER TABLE users ADD COLUMN surname VARCHAR"))

        if "display_name" not in existing_columns:
            conn.execute(text("ALTER TABLE users ADD COLUMN display_name VARCHAR"))

        if "provider" not in existing_columns:
            conn.execute(
                text(
                    "ALTER TABLE users ADD COLUMN provider VARCHAR DEFAULT 'firebase'"
                )
            )

        if "friend_ids" not in existing_columns:
            conn.execute(text("ALTER TABLE users ADD COLUMN friend_ids JSON DEFAULT '[]'::json"))

        if "created_at" not in existing_columns:
            conn.execute(
                text(
                    "ALTER TABLE users ADD COLUMN created_at TIMESTAMP DEFAULT NOW()"
                )
            )

        conn.execute(
            text(
                """
                DO $$
                BEGIN
                    IF NOT EXISTS (
                        SELECT 1
                        FROM pg_constraint
                        WHERE conname = 'users_firebase_uid_key'
                    ) THEN
                        ALTER TABLE users
                        ADD CONSTRAINT users_firebase_uid_key UNIQUE (firebase_uid);
                    END IF;
                END $$;
                """
            )
        )

        group_invites_exists = conn.execute(
            text(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_name = 'group_invites'
                )
                """
            )
        ).scalar()

        if not group_invites_exists:
            return

        group_invite_columns = {
            row[0]
            for row in conn.execute(
                text(
                    """
                    SELECT column_name
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'group_invites'
                    """
                )
            )
        }

        if "invited_by_firebase_uid" not in group_invite_columns:
            conn.execute(
                text(
                    "ALTER TABLE group_invites ADD COLUMN invited_by_firebase_uid VARCHAR"
                )
            )


def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
